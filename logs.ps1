<#
.SYNOPSIS
    Theo dõi log của các container trong hệ thống.
.PARAMETER Service
    Tên dịch vụ cụ thể cần xem log (ví dụ: api-gateway, tts-service, web, v.v.). Nếu để trống sẽ xem tất cả.
#>
param(
    [string]$Service
)

$ErrorActionPreference = 'Continue'
$repoRoot = $PSScriptRoot

Push-Location $repoRoot
try {
    if ($Service) {
        & docker compose logs -f $Service
    } else {
        & docker compose logs -f
    }
} finally {
    Pop-Location
}
