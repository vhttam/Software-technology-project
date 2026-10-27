<#
.SYNOPSIS
    Dừng hệ thống và xóa sạch dữ liệu/volume để đưa về trạng thái cài đặt ban đầu.
#>
param(
    [switch]$Force
)

$ErrorActionPreference = 'Continue'
$repoRoot = $PSScriptRoot

if (!$Force) {
    $confirm = Read-Host "CANH BAO: Thao tac nay se xoa toan bo CSDL va file am thanh cuc bo. Ban co chac chan muon tiep tuc? (y/N)"
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Host "Huy bo thao tac." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  DUNG VA XOA SACH DU LIEU (RESET)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "[INFO] Dang dung he thong va xoa sach volumes..." -ForegroundColor Yellow

Push-Location $repoRoot
try {
    & docker compose -f docker-compose.yml -f docker-compose.full.yml --profile full down -v --remove-orphans 2>&1 | Out-Host
    & docker compose down -v --remove-orphans 2>&1 | Out-Host
    if (Test-Path -LiteralPath (Join-Path $repoRoot '.local-data')) {
        Remove-Item -Recurse -Force -LiteralPath (Join-Path $repoRoot '.local-data') -ErrorAction SilentlyContinue
    }
    Write-Host ""
    Write-Host "[THANH CONG] Toan bo du lieu va container da duoc don sach hoan toan." -ForegroundColor Green
} finally {
    Pop-Location
}
