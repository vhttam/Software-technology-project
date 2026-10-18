$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stateFile = Join-Path $repoRoot '.local-data/processes.json'
if (!(Test-Path -LiteralPath $stateFile)) { Write-Host 'No local process list found.'; exit 0 }
$entries = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
foreach ($entry in $entries) {
    $expectedJar = if ($entry.jar) { [regex]::Escape([string]$entry.jar) } else { $null }
    $expectedWeb = [regex]::Escape((Join-Path $repoRoot 'web'))
    $listener = Get-NetTCPConnection -State Listen -LocalPort $entry.port -ErrorAction SilentlyContinue | Select-Object -First 1
    $listenerPid = if ($listener) { $listener.OwningProcess } else { $null }
    $pids = @($entry.pid, $entry.listenerPid, $listenerPid) | Where-Object { $_ } | Select-Object -Unique
    $stopped = $false
    foreach ($pidToStop in $pids) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $pidToStop" -ErrorAction SilentlyContinue
        if (!$owner) { continue }
        $matchesTask = if ($entry.name -eq 'web') {
            $owner.CommandLine -match $expectedWeb -or $owner.CommandLine -match 'pnpm\.cmd.*dev'
        } elseif ($entry.name -eq 'local-job-worker') {
            $owner.CommandLine -match 'LocalJobWorker'
        } else {
            $owner.CommandLine -match $expectedJar
        }

        if (!$matchesTask) {
            Write-Host "Skipped PID ${pidToStop}: process identity no longer matches $($entry.name)."
            continue
        }
        Stop-Process -Id $pidToStop -Force
        $stopped = $true
    }
    if ($stopped) { Write-Host "Stopped $($entry.name) and its tracked listener process." }
}
Remove-Item -LiteralPath $stateFile -Force
