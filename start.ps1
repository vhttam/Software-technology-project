<#
.SYNOPSIS
    Khởi động toàn bộ Hệ thống Thuyết minh Tự động Đa ngôn ngữ bằng Docker.
.PARAMETER Full
    Sử dụng cấu hình Full (chạy SQL Server thay vì H2). Mặc định là Lite (<4GB RAM).
.PARAMETER Build
    Buộc biên dịch lại các Docker image trước khi khởi động.
.PARAMETER Demo
    Bật chế độ DEMO: Sử dụng mật khẩu admin cố định (ghi trong README), nạp sẵn dữ liệu mẫu.
.PARAMETER Lan
    Cho phép truy cập qua mạng cục bộ LAN (mặc định trong chế độ Demo chỉ lắng nghe 127.0.0.1).
#>
param(
    [switch]$Full,
    [switch]$Build,
    [switch]$Demo,
    [switch]$Lan
)

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

$isDemo = $Demo.IsPresent -or ($env:DEMO_MODE -and $env:DEMO_MODE.ToLower() -eq 'true')
$isLan = $Lan.IsPresent -or ($env:LAN_MODE -and $env:LAN_MODE.ToLower() -eq 'true')
$demoAdminUser = 'admin'
$demoAdminPass = 'admin12345'

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ" -ForegroundColor Cyan
Write-Host "  Cấu trúc: $(if ($Full) { 'FULL (SQL Server + RabbitMQ)' } else { 'LITE (H2 + RabbitMQ, tối ưu RAM)' })" -ForegroundColor Cyan
Write-Host "  Chế độ:   $(if ($isDemo) { 'DEMO (Mật khẩu cố định, nạp dữ liệu mẫu)' } else { 'TIÊU CHUẨN (Mật khẩu ngẫu nhiên an toàn)' })" -ForegroundColor Cyan
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

# 3. Chuẩn bị file cấu hình .env và xác định mật khẩu admin
$envFile = Join-Path $repoRoot '.env'
$envExample = Join-Path $repoRoot '.env.example'
$adminUser = $demoAdminUser
$adminPass = ''

if (!(Test-Path -LiteralPath $envFile)) {
    Write-Host "[INFO] Chưa tìm thấy file .env. Đang tự động tạo từ .env.example..." -ForegroundColor Green
    if (Test-Path -LiteralPath $envExample) {
        $content = Get-Content -LiteralPath $envExample -Raw
        
        # Mật khẩu admin: Demo dùng cố định, Tiêu chuẩn sinh ngẫu nhiên an toàn
        if ($isDemo) {
            $adminPass = $demoAdminPass
            $content = $content -replace 'BOOTSTRAP_ADMIN_PASSWORD=.*', "BOOTSTRAP_ADMIN_PASSWORD=$adminPass"
            $content = $content -replace 'DEMO_MODE=.*', "DEMO_MODE=true"
        } else {
            $randomBytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(6)
            $randomHex = [System.BitConverter]::ToString($randomBytes).Replace('-', '').ToLower()
            $adminPass = "Admin@" + $randomHex + "!"
            $content = $content -replace 'BOOTSTRAP_ADMIN_PASSWORD=.*', "BOOTSTRAP_ADMIN_PASSWORD=$adminPass"
            $content = $content -replace 'DEMO_MODE=.*', "DEMO_MODE=false"
        }

        # Sinh ngẫu nhiên các bí mật hệ thống và token nội bộ
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
        $adminPass = if ($isDemo) { $demoAdminPass } else { 'Admin@123456' }
    }
} else {
    # .env đã tồn tại từ lần chạy trước -> đọc lại mật khẩu từ .env hiện tại (không sinh lại)
    Write-Host "[INFO] Đọc cấu hình từ file .env hiện tại..." -ForegroundColor Gray
    $envLines = Get-Content -LiteralPath $envFile
    foreach ($line in $envLines) {
        if ($line -match '^\s*BOOTSTRAP_ADMIN_USERNAME\s*=\s*(.*)$') { $adminUser = $matches[1].Trim() }
        if ($line -match '^\s*BOOTSTRAP_ADMIN_PASSWORD\s*=\s*(.*)$') { $adminPass = $matches[1].Trim() }
    }

    # Nếu người dùng chạy lệnh có cờ -Demo, đồng bộ mật khẩu cố định vào .env để đăng nhập thuận tiện
    if ($isDemo) {
        if ($adminPass -ne $demoAdminPass) {
            $rawEnv = Get-Content -LiteralPath $envFile -Raw
            if ($rawEnv -match 'BOOTSTRAP_ADMIN_PASSWORD=') {
                $rawEnv = $rawEnv -replace 'BOOTSTRAP_ADMIN_PASSWORD=.*', "BOOTSTRAP_ADMIN_PASSWORD=$demoAdminPass"
            } else {
                $rawEnv += "`nBOOTSTRAP_ADMIN_PASSWORD=$demoAdminPass"
            }
            if ($rawEnv -match 'DEMO_MODE=') {
                $rawEnv = $rawEnv -replace 'DEMO_MODE=.*', "DEMO_MODE=true"
            } else {
                $rawEnv += "`nDEMO_MODE=true"
            }
            Set-Content -LiteralPath $envFile -Value $rawEnv -Encoding utf8
        }
        $adminPass = $demoAdminPass
    } elseif ([string]::IsNullOrWhiteSpace($adminPass)) {
        $randomBytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(6)
        $randomHex = [System.BitConverter]::ToString($randomBytes).Replace('-', '').ToLower()
        $adminPass = "Admin@" + $randomHex + "!"
        Add-Content -LiteralPath $envFile -Value "`nBOOTSTRAP_ADMIN_PASSWORD=$adminPass"
    }
}

# 4. Ghi thông tin đăng nhập ra THONG-TIN-DANG-NHAP.txt
$credFile = Join-Path $repoRoot 'THONG-TIN-DANG-NHAP.txt'
$nowStr = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
$modeDesc = if ($isDemo) { 'CHẾ ĐỘ DEMO (Mật khẩu cố định, nạp dữ liệu mẫu)' } else { 'CHẾ ĐỘ TIÊU CHUẨN (Mật khẩu ngẫu nhiên an toàn)' }
$credNote = if ($isDemo) {
    "CẢNH BÁO DEMO: Mật khẩu '$demoAdminPass' chỉ dùng cho mục đích trình diễn/giảng dạy. TUYỆT ĐỐI KHÔNG dùng trên môi trường thật!"
} else {
    "Mật khẩu admin được sinh ngẫu nhiên an toàn riêng cho phiên bản triển khai này."
}

$credContent = @"
==========================================================
THÔNG TIN ĐĂNG NHẬP HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ
==========================================================
Thời gian khởi động: $nowStr
Chế độ hoạt động:    $modeDesc

1. ĐỊA CHỈ TRUY CẬP:
   - Giao diện Quản trị Web:    http://localhost:3000
   - Cổng API Gateway:          http://localhost:8080
   - Bảng điều khiển RabbitMQ:  http://localhost:15672 (User: cnpm)

2. TÀI KHOẢN ĐĂNG NHẬP QUẢN TRỊ VIÊN:
   - Tên đăng nhập:             $adminUser
   - Mật khẩu hiện tại:         $adminPass
   - Vai trò:                   SUPER_ADMIN (Toàn quyền quản trị)

3. LỆNH ĐIỀU HÀNH THƯỜNG DÙNG:
   - Xem nhật ký hệ thống:      .\logs.ps1  (hoặc ./logs.sh)
   - Dừng toàn bộ hệ thống:     .\stop.ps1  (hoặc ./stop.sh)
   - Khôi phục / Xóa sạch:      .\reset.ps1 (hoặc ./reset.sh)

==========================================================
LƯU Ý BẢO MẬT:
- $credNote
- File này và file .env đã được tự động loại trừ khỏi kho Git và file ZIP đóng gói.
==========================================================
"@
Set-Content -LiteralPath $credFile -Value $credContent -Encoding utf8

# 5. Khởi động Docker Compose
$env:DEMO_MODE = if ($isDemo) { 'true' } else { 'false' }
$env:BIND_IP = if ($isDemo -and -not $isLan) { '127.0.0.1' } else { '0.0.0.0' }
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

# 6. Chờ các dịch vụ đạt trạng thái healthy
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
    Write-Host ""
    Write-Host "  TÀI KHOẢN ĐĂNG NHẬP QUẢN TRỊ VIÊN (ADMIN):" -ForegroundColor Green
    Write-Host "  - Tên đăng nhập:             $adminUser" -ForegroundColor White
    Write-Host "  - Mật khẩu hiện tại:         $adminPass" -ForegroundColor Yellow
    Write-Host "  - Vị trí file thông tin:     $credFile" -ForegroundColor Cyan
    
    if ($isDemo) {
        Write-Host ""
        Write-Host "  [CẢNH BÁO DEMO] Hệ thống đang chạy ở CHẾ ĐỘ DEMO với mật khẩu cố định." -ForegroundColor Yellow
        Write-Host "  -> Chỉ dùng cho mục đích trình diễn/giảng dạy, tuyệt đối không dùng trên môi trường thật!" -ForegroundColor Yellow
        Write-Host "  -> Đã nạp sẵn 2 điểm tham quan và 1 nội dung mẫu để trình diễn ngay trên Web." -ForegroundColor Green
    }
    
    Write-Host "`nĐể theo dõi log:   .\logs.ps1"
    Write-Host "Để dừng hệ thống:  .\stop.ps1"
} else {
    Write-Host "[CẢNH BÁO] Các container đang khởi động nhưng API Gateway chưa phản hồi UP sau $maxAttempts lần thử." -ForegroundColor Yellow
    Write-Host "Bạn có thể kiểm tra log chi tiết bằng lệnh: .\logs.ps1" -ForegroundColor Yellow
}
