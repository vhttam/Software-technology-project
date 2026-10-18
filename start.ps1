<#
.SYNOPSIS
    Khởi động toàn bộ Hệ thống Thuyết minh Tự động Đa ngôn ngữ bằng Docker.
.PARAMETER Full
    Sử dụng cấu hình Full (chạy SQL Server thay vì H2). Mặc định là Lite (<4GB RAM).
.PARAMETER Build
    Buộc biên dịch lại các Docker image trước khi khởi động.
#>
param(
    [switch]$Full,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ" -ForegroundColor Cyan
Write-Host "  Chế độ: $(if ($Full) { 'FULL (SQL Server + RabbitMQ)' } else { 'LITE (H2 + RabbitMQ, tối ưu RAM)' })" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Kiểm tra Docker CLI và Docker daemon
$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (!$dockerCmd) {
    Write-Host "[LỖI] Không tìm thấy lệnh 'docker'. Vui lòng cài đặt Docker Desktop và đảm bảo lệnh docker có trong PATH." -ForegroundColor Red
    exit 1
}

try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker daemon is not running." }
} catch {
    Write-Host "[LỖI] Docker Desktop chưa được khởi động hoặc chưa sẵn sàng." -ForegroundColor Red
    Write-Host "-> Vui lòng mở ứng dụng Docker Desktop trên máy và thử lại sau khi Docker đã chạy." -ForegroundColor Yellow
    exit 1
}

# 2. Kiểm tra xung đột cổng
$portsToCheck = @(3000, 8080, 5672, 15672)
if ($Full) { $portsToCheck += 1433 }

$conflicts = @()
foreach ($p in $portsToCheck) {
    $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        $conflicts += [pscustomobject]@{ Port = $p; PID = $conn.OwningProcess; ProcessName = if ($proc) { $proc.ProcessName } else { 'Unknown' } }
    }
}

if ($conflicts.Count -gt 0) {
    Write-Host "[CẢNH BÁO] Phát hiện cổng đang bị chiếm bởi tiến trình khác:" -ForegroundColor Yellow
    foreach ($c in $conflicts) {
        Write-Host "  - Cổng $($c.Port): Tiến trình '$($c.ProcessName)' (PID: $($c.PID))" -ForegroundColor Yellow
    }
    Write-Host "Vui lòng giải phóng cổng trên hoặc tắt ứng dụng xung đột trước khi tiếp tục.`n" -ForegroundColor Yellow
}

# 3. Chuẩn bị file cấu hình .env
$envFile = Join-Path $repoRoot '.env'
$envExample = Join-Path $repoRoot '.env.example'
if (!(Test-Path -LiteralPath $envFile)) {
    Write-Host "[INFO] Chưa tìm thấy file .env. Đang tự động tạo từ .env.example với các khóa bảo mật ngẫu nhiên..." -ForegroundColor Green
    if (Test-Path -LiteralPath $envExample) {
        $content = Get-Content -LiteralPath $envExample -Raw
        $randomSecret = [System.Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)) -replace '[^a-zA-Z0-9]', 'x'
        $content = $content -replace 'local-dev-signing-secret-change-me-32-characters', $randomSecret
        $content = $content -replace 'local-dev-auth-caller-token-change-me', ("tok-auth-" + [Guid]::NewGuid().ToString('N'))
        $content = $content -replace 'local-dev-content-caller-token-change-me', ("tok-content-" + [Guid]::NewGuid().ToString('N'))
        $content = $content -replace 'local-dev-narration-caller-token-change-me', ("tok-narration-" + [Guid]::NewGuid().ToString('N'))
        $content = $content -replace 'local-dev-translation-caller-token-change-me', ("tok-trans-" + [Guid]::NewGuid().ToString('N'))
        $content = $content -replace 'local-dev-tts-caller-token-change-me', ("tok-tts-" + [Guid]::NewGuid().ToString('N'))
        $content = $content -replace 'local-dev-gateway-caller-token-change-me', ("tok-gw-" + [Guid]::NewGuid().ToString('N'))
        Set-Content -LiteralPath $envFile -Value $content -Encoding utf8
    } else {
        Write-Host "[CẢNH BÁO] Không tìm thấy .env.example, hệ thống sẽ sử dụng các giá trị mặc định nội bộ." -ForegroundColor Yellow
    }
}

# 4. Khởi động Docker Compose
$composeArgs = @('compose')
if ($Full) {
    $composeArgs += @('-f', 'docker-compose.yml', '-f', 'docker-compose.full.yml', '--profile', 'full')
}
if ($Build) {
    $composeArgs += @('up', '-d', '--build')
} else {
    $composeArgs += @('up', '-d')
}

Write-Host "[INFO] Đang khởi động các dịch vụ qua Docker Compose..." -ForegroundColor Green
Push-Location $repoRoot
try {
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[LỖI] docker compose up thất bại. Vui lòng kiểm tra log lỗi bên trên." -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}

# 5. Chờ các dịch vụ đạt trạng thái healthy
Write-Host "[INFO] Đang kiểm tra trạng thái hoạt động của hệ thống (liveness/readiness)..." -ForegroundColor Green
$maxAttempts = 90
$ready = $false

for ($i = 1; $i -le $maxAttempts; $i++) {
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 2
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/health" -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($res.status -eq 'UP' -or $res.data.state -eq 'UP') {
            $ready = $true
            break
        }
    } catch {}
}

Write-Host ""
if ($ready) {
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host "  HỆ THỐNG ĐÃ SẴN SÀNG HOẠT ĐỘNG!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "  - Giao diện Quản trị Web:    http://localhost:3000" -ForegroundColor Cyan
    Write-Host "  - Cổng API Gateway:          http://localhost:8080" -ForegroundColor Cyan
    Write-Host "  - Bảng điều khiển RabbitMQ:  http://localhost:15672 (User: cnpm)" -ForegroundColor Cyan
    Write-Host "  - Tài khoản đăng nhập mẫu:   admin (Mật khẩu được lưu trong file .env)" -ForegroundColor Yellow
    Write-Host "`nĐể theo dõi log:   .\logs.ps1"
    Write-Host "Để dừng hệ thống:  .\stop.ps1"
} else {
    Write-Host "[CẢNH BÁO] Các container đang khởi động nhưng API Gateway chưa phản hồi UP sau $maxAttempts lần thử." -ForegroundColor Yellow
    Write-Host "Bạn có thể kiểm tra log chi tiết bằng lệnh: .\logs.ps1" -ForegroundColor Yellow
}
