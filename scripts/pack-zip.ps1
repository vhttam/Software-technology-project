<#
.SYNOPSIS
    Đóng gói mã nguồn dự án thành file ZIP sạch phục vụ bàn giao và triển khai.
    Tự động loại bỏ .git, build cache, logs, thư mục tạm và các file nhạy cảm.
#>
param(
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$distDir = Join-Path $repoRoot 'dist'

if (!$OutputPath) {
    if (!(Test-Path -LiteralPath $distDir)) {
        New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    }
    $OutputPath = Join-Path $distDir 'he-thong-thuyet-minh.zip'
} else {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
}

if (Test-Path -LiteralPath $OutputPath) {
    Remove-Item -Force -LiteralPath $OutputPath
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  DONG GOI DU AN THUYET MINH DA NGON NGU (ZIP)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "[INFO] Thu muc nguon: $repoRoot" -ForegroundColor Gray
Write-Host "[INFO] File nen dich: $OutputPath" -ForegroundColor Gray

# Danh sách mẫu cần loại trừ
$excludePatterns = @(
    '(^|[\\/])\.git([\\/]|$)',
    '(^|[\\/])\.idea([\\/]|$)',
    '(^|[\\/])\.vscode([\\/]|$)',
    '(^|[\\/])node_modules([\\/]|$)',
    '(^|[\\/])target([\\/]|$)',
    '(^|[\\/])dist([\\/]|$)',
    '(^|[\\/])\.local-data([\\/]|$)',
    '\.log$',
    '(^|[\\/])\.env$',
    '(^|[\\/])THONG-TIN-DANG-NHAP\.txt$',
    '\.pk8$',
    '\.key$',
    '\.pem$',
    '(^|[\\/])PROJECT_OVERVIEW\.md$'
)

# Thu thập danh sách file hợp lệ
$allFiles = Get-ChildItem -Path $repoRoot -Recurse -File
$filesToZip = [System.Collections.Generic.List[System.IO.FileInfo]]::new()

foreach ($file in $allFiles) {
    $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
    $shouldExclude = $false
    foreach ($pat in $excludePatterns) {
        if ($rel -match $pat) {
            $shouldExclude = $true
            break
        }
    }
    if (!$shouldExclude) {
        $filesToZip.Add($file)
    }
}

Write-Host "[INFO] Tong so file dua vao goi nen: $($filesToZip.Count)" -ForegroundColor Green

# Tạo file ZIP sử dụng .NET ZipArchive
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipFile = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($zipFile, [System.IO.Compression.ZipArchiveMode]::Create)

try {
    foreach ($file in $filesToZip) {
        $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
        $entry = $archive.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $fileStream = [System.IO.FileStream]::new($file.FullName, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $entryStream = $entry.Open()
            try {
                $fileStream.CopyTo($entryStream)
            } finally {
                $entryStream.Dispose()
            }
        } finally {
            $fileStream.Dispose()
        }
    }
} finally {
    $archive.Dispose()
    $zipFile.Dispose()
}

$zipInfo = Get-Item -LiteralPath $OutputPath
$sizeMb = [Math]::Round($zipInfo.Length / 1MB, 2)
$hash = (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  DONG GOI THANH CONG!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ("  - Duong dan:  {0}" -f $OutputPath)
Write-Host ("  - Kich thuoc: {0} MB ({1} bytes)" -f $sizeMb, $zipInfo.Length)
Write-Host ("  - SHA256:     {0}" -f $hash)
Write-Host "  - File loai tru: .git, node_modules, target, logs, .env, PROJECT_OVERVIEW.md"
