# PowerShell script for E2E Smoke Test
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot | Split-Path

Set-Location $repoRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "    CHẠY KIỂM THỬ ĐẦU-CUỐI (E2E SMOKE TEST)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$gatewayUrl = if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8080" }

# 1. Trích xuất thông tin đăng nhập từ THONG-TIN-DANG-NHAP.txt hoặc .env
$adminUser = ""
$adminPass = ""

if (Test-Path "THONG-TIN-DANG-NHAP.txt") {
    Write-Host "Đang đọc thông tin đăng nhập từ THONG-TIN-DANG-NHAP.txt..." -ForegroundColor Gray
    $content = Get-Content "THONG-TIN-DANG-NHAP.txt" -Raw
    if ($content -match 'Tên đăng nhập\s*:\s*([^\r\n]+)') { $adminUser = $matches[1].Trim() }
    if ($content -match 'Mật khẩu\s*:\s*([^\r\n]+)') { $adminPass = $matches[1].Trim() }
}

if (-not $adminUser -and (Test-Path ".env")) {
    Write-Host "Đang đọc thông tin đăng nhập từ .env..." -ForegroundColor Gray
    $envContent = Get-Content ".env" -Raw
    if ($envContent -match 'BOOTSTRAP_ADMIN_USERNAME\s*=\s*([^\r\n]+)') { $adminUser = $matches[1].Trim() }
    if ($envContent -match 'BOOTSTRAP_ADMIN_PASSWORD\s*=\s*([^\r\n]+)') { $adminPass = $matches[1].Trim() }
}

if (-not $adminUser) { $adminUser = "admin" }
if (-not $adminPass) {
    Write-Host "[LỖI] Không tìm thấy mật khẩu admin trong THONG-TIN-DANG-NHAP.txt hoặc .env." -ForegroundColor Red
    exit 1
}

Write-Host "Tài khoản kiểm thử: $adminUser" -ForegroundColor Green

# 2. Đăng nhập qua API Gateway
Write-Host "`n=== [1/6] Đăng nhập vào API Gateway ($gatewayUrl/api/v1/auth/login) ===" -ForegroundColor Yellow
$loginBody = @{ username = $adminUser; password = $adminPass } | ConvertTo-Json -Compress
try {
    $loginRes = Invoke-RestMethod -Uri "$gatewayUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.data.accessToken
    if (-not $token) { throw "Không nhận được accessToken." }
} catch {
    Write-Host "[LỖI] Đăng nhập thất bại: $_" -ForegroundColor Red
    exit 1
}
Write-Host "Đăng nhập thành công. Đã nhận JWT token." -ForegroundColor Green

$authHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# 3. Tạo Điểm tham quan (POI)
Write-Host "`n=== [2/6] Tạo Điểm tham quan (POST /api/v1/pois) ===" -ForegroundColor Yellow
$poiBody = @{
    name = "Bảo tàng Lịch sử (Smoke Test)"
    description = "Khu trưng bày mẫu phục vụ kiểm thử luồng tự động đa ngôn ngữ."
} | ConvertTo-Json -Compress

try {
    $poiRes = Invoke-RestMethod -Uri "$gatewayUrl/api/v1/pois" -Method Post -Headers $authHeaders -Body $poiBody
    $poiId = $poiRes.data.poiId
    if (-not $poiId) { throw "Không nhận được poiId." }
} catch {
    Write-Host "[LỖI] Tạo POI thất bại: $_" -ForegroundColor Red
    exit 1
}
Write-Host "Đã tạo POI thành công. ID: $poiId" -ForegroundColor Green

# 4. Tạo Nội dung thuyết minh (Content)
Write-Host "`n=== [3/6] Tạo Nội dung thuyết minh gốc tiếng Việt (POST /api/v1/pois/$poiId/contents) ===" -ForegroundColor Yellow
$contentBody = @{
    title = "Thuyết minh gian khánh tiết"
    sourceLang = "vi"
    textContent = "Khu vực trưng bày những hiện vật lịch sử quý báu của dân tộc qua các thời kỳ phát triển văn hóa."
} | ConvertTo-Json -Compress

try {
    $contentRes = Invoke-RestMethod -Uri "$gatewayUrl/api/v1/pois/$poiId/contents" -Method Post -Headers $authHeaders -Body $contentBody
    $contentId = $contentRes.data.contentId
    if (-not $contentId) { throw "Không nhận được contentId." }
} catch {
    Write-Host "[LỖI] Tạo Nội dung thất bại: $_" -ForegroundColor Red
    exit 1
}
Write-Host "Đã tạo Nội dung thành công. ID: $contentId" -ForegroundColor Green

# 5. Nộp tác vụ thuyết minh 6 ngôn ngữ (Job)
Write-Host "`n=== [4/6] Nộp tác vụ thuyết minh 6 ngôn ngữ (POST /api/v1/jobs) ===" -ForegroundColor Yellow
$idempotencyKey = "smoke-ps-" + [Guid]::NewGuid().ToString()
$jobHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "Idempotency-Key" = $idempotencyKey
}
$jobBody = @{
    contentId = $contentId
    targets = @(
        @{ lang = "vi"; voiceId = "vi-VN-Standard-A" },
        @{ lang = "en"; voiceId = "en-US-Journey-F" },
        @{ lang = "fr"; voiceId = "fr-FR-Standard-A" },
        @{ lang = "ja"; voiceId = "ja-JP-Standard-A" },
        @{ lang = "zh"; voiceId = "cmn-CN-Standard-A" },
        @{ lang = "ko"; voiceId = "ko-KR-Standard-A" }
    )
} | ConvertTo-Json -Compress -Depth 5

try {
    $jobRes = Invoke-RestMethod -Uri "$gatewayUrl/api/v1/jobs" -Method Post -Headers $jobHeaders -Body $jobBody
    $jobId = $jobRes.data.jobId
    if (-not $jobId) { throw "Không nhận được jobId." }
} catch {
    Write-Host "[LỖI] Nộp tác vụ thất bại: $_" -ForegroundColor Red
    exit 1
}
Write-Host "Đã nộp Job thành công. ID: $jobId (gồm 6 ngôn ngữ)" -ForegroundColor Green

# 6. Chờ tác vụ hoàn thành (Poll GET /api/v1/jobs/{jobId})
Write-Host "`n=== [5/6] Chờ tác vụ hoàn thành (Poll GET /api/v1/jobs/$jobId) ===" -ForegroundColor Yellow
$jobDone = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $detail = Invoke-RestMethod -Uri "$gatewayUrl/api/v1/jobs/$jobId" -Method Get -Headers @{ "Authorization" = "Bearer $token" }
        $status = $detail.data.status
        Write-Host "  Lần kiểm tra $i: Trạng thái Job = $status" -ForegroundColor Gray
        if ($status -eq "PUBLISHED" -or $status -eq "COMPLETED") {
            $jobDone = $true
            break
        }
        if ($status -eq "FAILED" -or $status -eq "CANCELLED") {
            Write-Host "[LỖI] Tác vụ kết thúc ở trạng thái lỗi: $status" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "  Lỗi tạm thời khi poll: $_" -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 2
}

if (-not $jobDone) {
    Write-Host "[LỖI] Tác vụ không hoàn thành trong thời gian cho phép (120s)." -ForegroundColor Red
    exit 1
}
Write-Host "Tác vụ đã hoàn tất thành công!" -ForegroundColor Green

# 7. Kiểm tra tải âm thanh qua API khách tham quan
Write-Host "`n=== [6/6] Tải và kiểm tra tệp âm thanh 6 ngôn ngữ qua API khách tham quan ===" -ForegroundColor Yellow
$tempDir = Join-Path $env:TEMP ("audio_smoke_" + [Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

$langs = @("vi", "en", "fr", "ja", "zh", "ko")
try {
    foreach ($l in $langs) {
        $narrRes = Invoke-RestMethod -Uri "$gatewayUrl/api/v1/client/pois/$poiId/narrations/$l" -Method Get
        $audioUrl = $narrRes.data.audioUrl
        if (-not $audioUrl) {
            Write-Host "[LỖI] Không lấy được audioUrl cho ngôn ngữ $l." -ForegroundColor Red
            exit 1
        }
        $outFile = Join-Path $tempDir "audio_$l.mp3"
        Invoke-WebRequest -Uri $audioUrl -OutFile $outFile -UseBasicParsing
        $fileSize = (Get-Item $outFile).Length
        Write-Host "  [OK] Ngôn ngữ $l: Tải thành công ($fileSize bytes) -> $audioUrl" -ForegroundColor Green
        if ($fileSize -lt 500) {
            Write-Host "[LỖI] Tệp âm thanh $l quá nhỏ (< 500 bytes)." -ForegroundColor Red
            exit 1
        }
    }
} finally {
    Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "    TOÀN BỘ 6 BƯỚC SMOKE TEST ĐẦU-CUỐI ĐỀU THÀNH CÔNG!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
exit 0
