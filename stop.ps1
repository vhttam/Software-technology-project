<#
.SYNOPSIS
    Dừng toàn bộ container của Hệ thống Thuyết minh Tự động Đa ngôn ngữ.
#>
param()

$ErrorActionPreference = 'Continue'
$repoRoot = $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  DUNG HE THONG THUYET MINH TU DONG DA NGON NGU" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "[INFO] Dang dung cac container cua he thong..." -ForegroundColor Yellow

Push-Location $repoRoot
try {
    & docker compose -f docker-compose.yml -f docker-compose.full.yml --profile full down 2>&1 | Out-Host
    & docker compose down 2>&1 | Out-Host
    Write-Host ""
    Write-Host "[THANH CONG] Toan bo dich vu da duoc dung an toan (du lieu duoc bao toan trong volumes)." -ForegroundColor Green
} catch {
    Write-Host "[THANH CONG] Da dung cac container." -ForegroundColor Green
} finally {
    Pop-Location
}
