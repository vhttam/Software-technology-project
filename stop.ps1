<#
.SYNOPSIS
    Dừng toàn bộ container của Hệ thống Thuyết minh Tự động Đa ngôn ngữ.
#>
param()

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

Write-Host "[INFO] Đang dừng các container của hệ thống..." -ForegroundColor Yellow
Push-Location $repoRoot
try {
    docker compose -f docker-compose.yml -f docker-compose.full.yml --profile full down 2>$null
    docker compose down
    Write-Host "[THÀNH CÔNG] Toàn bộ dịch vụ đã được dừng an toàn (dữ liệu được bảo toàn trong volumes)." -ForegroundColor Green
} finally {
    Pop-Location
}
