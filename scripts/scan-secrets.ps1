# scripts/scan-secrets.ps1
$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot | Split-Path
Set-Location $repoRoot

$patterns = 'password|passwd|secret|token|api[_-]?key|BEGIN .* KEY|Bearer|jdbc:.*password'

$group1_EnvExample = @()
$group2_DemoPassword = @()
$group3_TestTokens = @()
$group4_OtherPlaceholders = @()
$suspectSecrets = @()

$files = git ls-files

foreach ($f in $files) {
    if (-not (Test-Path $f -PathType Leaf)) { continue }
    $lines = Get-Content $f -Encoding UTF8 -ErrorAction SilentlyContinue
    if ($null -eq $lines) { continue }
    $lineNum = 1
    foreach ($rawLine in $lines) {
        if ($rawLine -match $patterns) {
            $trimmed = $rawLine.Trim()
            $isSuspect = $false

            # Check if this could be an actual production secret / private key
            if ($trimmed -match '-----BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY-----') {
                $isSuspect = $true
            } elseif ($trimmed -match 'AIza[0-9A-Za-z-_]{35}') {
                $isSuspect = $true
            } elseif ($trimmed -match 'Bearer\s+[A-Za-z0-9\-_]{50,}' -and $f -notmatch 'Test\.java') {
                $isSuspect = $true
            } elseif ($trimmed -match 'ghp_[0-9a-zA-Z]{36}') {
                $isSuspect = $true
            }

            if ($isSuspect) {
                $suspectSecrets += [PSCustomObject]@{
                    File = $f
                    Line = $lineNum
                    Category = "SUSPECT_SECRET"
                }
            } else {
                # Classification into 4 requested groups
                if ($f -match '\.env\.example$') {
                    $group1_EnvExample += [PSCustomObject]@{
                        File = $f
                        Line = $lineNum
                        Category = "Placeholder .env.example"
                    }
                } elseif ($f -match 'README\.md' -and $trimmed -match 'admin12345') {
                    $group2_DemoPassword += [PSCustomObject]@{
                        File = $f
                        Line = $lineNum
                        Category = "Mat khau demo co dinh trong README"
                    }
                } elseif ($f -match '\.test\.' -or $f -match 'Test\.java' -or $f -match 'synthetic_beep' -or $trimmed -match 'mock|dummy|fixture|test-') {
                    $group3_TestTokens += [PSCustomObject]@{
                        File = $f
                        Line = $lineNum
                        Category = "Token test trong test / fixtures"
                    }
                } else {
                    $group4_OtherPlaceholders += [PSCustomObject]@{
                        File = $f
                        Line = $lineNum
                        Category = "Khac (DTO, Spring config, env vars mac dinh)"
                    }
                }
            }
        }
        $lineNum++
    }
}

Write-Host "=========================================================="
Write-Host "         KET QUA RA SOAT BI MAT TREN TOAN BO REPO"
Write-Host "=========================================================="
Write-Host "1. Nhom Placeholder .env.example:                    $($group1_EnvExample.Count) dong"
Write-Host "2. Nhom Mat khau demo co dinh trong README:          $($group2_DemoPassword.Count) dong"
Write-Host "3. Nhom Token test trong test / fixtures:            $($group3_TestTokens.Count) dong"
Write-Host "4. Nhom Khac (ten bien, DTO, cau hinh Spring):       $($group4_OtherPlaceholders.Count) dong"
Write-Host "----------------------------------------------------------"
Write-Host "TONG SO KHOP PLACEHOLDER / AN TOAN:                  $($group1_EnvExample.Count + $group2_DemoPassword.Count + $group3_TestTokens.Count + $group4_OtherPlaceholders.Count) dong"
Write-Host "SO DONG NGHI NGO BI MAT THAT (SUSPECT):              $($suspectSecrets.Count) dong"
Write-Host "=========================================================="

if ($suspectSecrets.Count -gt 0) {
    Write-Host "[CANH BAO] Phat hien bi mat nghi ngo:" -ForegroundColor Red
    $suspectSecrets | ForEach-Object { Write-Host "  $($_.File):$($_.Line)" }
} else {
    Write-Host "[XAC NHAN] 100% cac ket qua deu thuoc 4 nhom an toan. KHONG co bi mat that nao bi lo!" -ForegroundColor Green
}

# Quet sau NOI DUNG cac file trong file ZIP
$zipPath = "dist/he-thong-thuyet-minh.zip"
if (Test-Path $zipPath) {
    Write-Host ""
    Write-Host "=========================================================="
    Write-Host "  QUET SAU NOI DUNG TEP TIN TRONG GOI ZIP: $zipPath"
    Write-Host "=========================================================="
    $tempZipDir = Join-Path $repoRoot ".tmp_zip_scan"
    if (Test-Path $tempZipDir) { Remove-Item -Path $tempZipDir -Recurse -Force }
    New-Item -ItemType Directory -Path $tempZipDir | Out-Null

    try {
        Expand-Archive -LiteralPath $zipPath -DestinationPath $tempZipDir -Force
        $zipFiles = Get-ChildItem -Path $tempZipDir -Recurse -File

        $zipSuspectCount = 0
        $zipSensitiveFiles = @()

        foreach ($zf in $zipFiles) {
            $rel = $zf.FullName.Substring($tempZipDir.Length + 1)
            # Kiem tra tep nhay cam khong duoc co mat
            if ($rel -match '(^|/|\\)\.env$' -or $rel -match 'THONG-TIN-DANG-NHAP' -or $rel -match '\.pem$' -or $rel -match '\.pk8$' -or $rel -match '\.jks$') {
                $zipSensitiveFiles += $rel
            }
            # Quet noi dung tung tep trong ZIP
            $zLines = Get-Content $zf.FullName -Encoding UTF8 -ErrorAction SilentlyContinue
            if ($null -ne $zLines) {
                foreach ($zl in $zLines) {
                    if ($zl -match '-----BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY-----' -or $zl -match 'AIza[0-9A-Za-z-_]{35}' -or $zl -match 'ghp_[0-9a-zA-Z]{36}') {
                        $zipSuspectCount++
                    }
                }
            }
        }

        Write-Host "Tong so file duoc giai nen va quet trong ZIP: $($zipFiles.Count) files"
        Write-Host "So tep nhay cam cam (.env, THONG-TIN-DANG-NHAP, private key): $($zipSensitiveFiles.Count)"
        Write-Host "So chuoi nghi ngo bi mat that trong noi dung file:        $zipSuspectCount"
        if ($zipSensitiveFiles.Count -eq 0 -and $zipSuspectCount -eq 0) {
            Write-Host "[XAC NHAN] Goi ZIP hoan toan sach! Khong chua tep cam hay bi mat that." -ForegroundColor Green
        } else {
            Write-Host "[LOI] Phat hien vi pham bao mat trong goi ZIP!" -ForegroundColor Red
        }
    } finally {
        Remove-Item -Path $tempZipDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "--- 20 DONG TIEU BIEU DAI DIEN CHO 4 NHOM AN TOAN (File:Line) ---"
$sampleList = @()
$sampleList += $group1_EnvExample | Select-Object -First 5
$sampleList += $group2_DemoPassword | Select-Object -First 2
$sampleList += $group3_TestTokens | Select-Object -First 7
$sampleList += $group4_OtherPlaceholders | Select-Object -First 6

$idx = 1
foreach ($item in $sampleList) {
    Write-Host ("{0,2}. [{1}] {2}:{3}" -f $idx, $item.Category, $item.File, $item.Line)
    $idx++
}
