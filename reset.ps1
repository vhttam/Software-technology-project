<#
.SYNOPSIS
    Dừng hệ thống và xóa sạch dữ liệu/volume để đưa về trạng thái cài đặt ban đầu.
#>
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

if (!$Force) {
    $confirm = Read-Host "CẢNH BÁO: Thao tác này sẽ xóa toàn bộ CSDL và file âm thanh cục bộ. Bạn có chắc chắn muốn tiếp tục? (y/N)"
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Host "Hủy bỏ thao tác." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host "[INFO] Đang dừng hệ thống và xóa sạch volumes..." -ForegroundColor Yellow
Push-Location $repoRoot
try {
    docker compose -f docker-compose.yml -f docker-compose.full.yml --profile full down -v --remove-orphans 2>$null
    docker compose down -v --remove-orphans
    if (Test-Path -LiteralPath (Join-Path $repoRoot '.local-data')) {
        Remove-Item -Recurse -Force -LiteralPath (Join-Path $repoRoot '.local-data') -ErrorAction SilentlyContinue
    }
    Write-Host "[THÀNH CÔNG] Toàn bộ dữ liệu và container đã được dọn sạch hoàn toàn." -ForegroundColor Green
} finally {
    Pop-Location
}
