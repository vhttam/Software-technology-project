param(
    [switch]$UseH2,
    [switch]$Build,
    [switch]$Wait
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $repoRoot '.env'
$stateDir = Join-Path $repoRoot '.local-data'
$logDir = Join-Path $stateDir 'logs'
$stateFile = Join-Path $stateDir 'processes.json'
$composeFile = Join-Path $repoRoot 'docker-compose.yml'

if (!(Test-Path -LiteralPath $envFile)) {
    throw 'Missing .env. Copy .env.example to .env, set local values, then rerun this script.'
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$settings = @{}
foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
    $trimmed = $line.Trim()
    if (!$trimmed -or $trimmed.StartsWith('#')) { continue }
    $split = $trimmed.IndexOf('=')
    if ($split -lt 1) { continue }
    $key = $trimmed.Substring(0, $split).Trim()
    $value = $trimmed.Substring($split + 1).Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $settings[$key] = $value
    Set-Item -Path "Env:$key" -Value $value
}

if (!$UseH2) {
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI was not found. Install Docker Desktop or rerun with -UseH2.' }
    Push-Location $repoRoot
    try { docker compose -f $composeFile up -d; if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed.' } }
    finally { Pop-Location }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $ready = $true
        foreach ($infrastructure in @('sqlserver', 'rabbitmq')) {
            $container = docker compose -f $composeFile ps --status running -q $infrastructure
            if ($LASTEXITCODE -ne 0 -or !$container) { $ready = $false; break }
            $health = docker inspect --format '{{.State.Health.Status}}' $container
            if ($LASTEXITCODE -ne 0 -or $health -ne 'healthy') { $ready = $false; break }
        }
        if ($ready) { break }
        Start-Sleep -Seconds 2
    }
    if (!$ready) { throw 'SQL Server and RabbitMQ did not become healthy. Check docker compose logs.' }
    $sqlTool = '/opt/mssql-tools18/bin/sqlcmd'
    $password = $settings['MSSQL_SA_PASSWORD']
    $databaseNames = @('authdb','contentdb','narrationdb','translationdb','ttsdb')
    $sql = ($databaseNames | ForEach-Object { "IF DB_ID(N'$_') IS NULL CREATE DATABASE [$_];" }) -join ' '
    $env:SQLCMDPASSWORD = $password
    docker compose -f $composeFile exec -T sqlserver $sqlTool -S localhost -U sa -C -Q $sql
    Remove-Item Env:SQLCMDPASSWORD -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create service databases with sqlcmd in the SQL Server container.' }
}

$java = Get-Command java -ErrorAction SilentlyContinue
if (!$java) { throw 'Java 17 or newer was not found on PATH.' }
$webDir = Join-Path $repoRoot 'web'
$pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
if (!$pnpm) {
    $runtimePnpm = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback/pnpm.cmd'
    if (Test-Path -LiteralPath $runtimePnpm) { $pnpm = [pscustomobject]@{ Source = $runtimePnpm } }
}
if (!$pnpm) { throw 'pnpm was not found. Install Node.js 22+ and pnpm 11.25.0, then rerun this script.' }
if ($Build -or !(Test-Path -LiteralPath (Join-Path $webDir 'node_modules'))) {
    Push-Location $webDir
    try { & $pnpm.Source install --frozen-lockfile; if ($LASTEXITCODE -ne 0) { throw 'pnpm install failed.' } }
    finally { Pop-Location }
}

$env:JWT_PRIVATE_KEY_PATH = Join-Path $stateDir 'auth-signing-key.pk8'
$commands = @(
    @{ name='auth-service'; port=8081; db='authdb' },
    @{ name='content-service'; port=8082; db='contentdb' },
    @{ name='narration-service'; port=8083; db='narrationdb' },
    @{ name='translation-service'; port=8084; db='translationdb' },
    @{ name='tts-service'; port=8085; db='ttsdb' },
    @{ name='api-gateway'; port=8080; db=$null }
)
$started = [System.Collections.Generic.List[object]]::new()

foreach ($service in $commands) {
    $directory = Join-Path $repoRoot $service.name
    $jar = Join-Path $directory "target/$($service.name)-1.0.0-SNAPSHOT.jar"
    $wrapper = Join-Path $directory 'mvnw.cmd'
    if ($Build -or !(Test-Path -LiteralPath $jar)) {
        Push-Location $directory
        try {
            if (Test-Path -LiteralPath $wrapper) {
                & $wrapper '-DskipTests' 'package'
            } elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
                & mvn '-DskipTests' 'package'
            } else {
                docker run --rm -v "${directory}:/app" -w /app maven:3.9-eclipse-temurin-17-alpine mvn -DskipTests package
            }
            if ($LASTEXITCODE -ne 0) { throw "Build failed for $($service.name)." }
        }
        finally { Pop-Location }
    }
    $env:PORT = [string]$service.port
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:AMQP_ENABLED = if ($UseH2) { 'false' } else { 'true' }
    $env:FLYWAY_ENABLED = 'true'
    if ($UseH2) {
        $h2Dir = (Join-Path $stateDir "h2\$($service.db)") -replace '\\', '/'
        $env:DB_URL = "jdbc:h2:file:$h2Dir;AUTO_SERVER=TRUE;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        $env:DB_DRIVER = 'org.h2.Driver'
        $env:DB_USER = 'sa'
        $env:DB_PASSWORD = ''
    }
 elseif ($service.db) {
        $env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=$($service.db);encrypt=true;trustServerCertificate=true"
        $env:DB_DRIVER = 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
        $env:DB_USER = 'sa'
        $env:DB_PASSWORD = $settings['MSSQL_SA_PASSWORD']
    }
    $env:AUTH_SERVICE_URL = 'http://localhost:8081'
    $env:CONTENT_SERVICE_URL = 'http://localhost:8082'
    $env:NARRATION_SERVICE_URL = 'http://localhost:8083'
    $env:TRANSLATION_SERVICE_URL = 'http://localhost:8084'
    $env:TTS_SERVICE_URL = 'http://localhost:8085'
    $env:API_GATEWAY_URL = 'http://localhost:8080'
    $env:OBJECT_STORAGE_PATH = (Join-Path $repoRoot ($settings['OBJECT_STORAGE_PATH'] -replace '/', '\'))
    $stdout = Join-Path $logDir "$($service.name).log"
    $stderr = Join-Path $logDir "$($service.name).err.log"
    $process = Start-Process -FilePath $java.Source -ArgumentList @('-jar', $jar) -WorkingDirectory $directory `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $started.Add([pscustomobject]@{ name=$service.name; pid=$process.Id; port=$service.port; jar=$jar })
    Set-Content -LiteralPath $stateFile -Value ($started | ConvertTo-Json -Depth 4) -Encoding utf8
}

$webProcess = Start-Process -FilePath $pnpm.Source -ArgumentList @('dev', '--host', '127.0.0.1') -WorkingDirectory $webDir `
    -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'web.log') -RedirectStandardError (Join-Path $logDir 'web.err.log')
$started.Add([pscustomobject]@{ name='web'; pid=$webProcess.Id; port=3000; jar=$null })

$pending = [System.Collections.Generic.List[int]]::new()
foreach ($service in $started) { if ($service.port -gt 0) { $pending.Add([int]$service.port) } }

for ($attempt = 0; $attempt -lt 90 -and $pending.Count -gt 0; $attempt++) {
    foreach ($port in @($pending.ToArray())) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health" -TimeoutSec 2
            if ($health.status -eq 'UP' -or $health.data.state -eq 'UP') {
                $null = $pending.Remove($port)
                $entry = $started | Where-Object { $_.port -eq $port } | Select-Object -First 1
                $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($entry -and $listener) { $entry | Add-Member -NotePropertyName listenerPid -NotePropertyValue $listener.OwningProcess -Force }
            }
        } catch { }
    }
    if ($pending.Count -gt 0) { Start-Sleep -Seconds 1 }
}
if ($pending.Count -gt 0) {
    throw "Liveness check failed on port(s): $($pending -join ', '). Check logs in $logDir."
}

Set-Content -LiteralPath $stateFile -Value ($started | ConvertTo-Json -Depth 4) -Encoding utf8

Write-Host 'Started local processes:'
$started | ForEach-Object { Write-Host ("  {0,-20} PID {1,-8} http://localhost:{2}" -f $_.name, $_.pid, $_.port) }
Write-Host "Logs: $logDir"
if ($UseH2) { Write-Host 'H2 and RabbitMQ are disabled for the five application processes.' }
else { Write-Host 'SQL Server and RabbitMQ are running from docker-compose.yml.' }

if ($Wait) {
    Write-Host 'Processes running in background. Waiting...'
    while ($true) {
        Start-Sleep -Seconds 10
    }
}
