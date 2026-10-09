$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8082' }
    if (-not $env:DB_URL) {
        $env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=narration_db;encrypt=true;trustServerCertificate=true'
    }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'sa' }
    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = 'A_Strong_Dev_Password_123!' }
    if (-not $env:RABBITMQ_HOST) { $env:RABBITMQ_HOST = 'localhost' }
    if (-not $env:RABBITMQ_PORT) { $env:RABBITMQ_PORT = '5672' }
    if (-not $env:RABBITMQ_USERNAME) { $env:RABBITMQ_USERNAME = 'admin' }
    if (-not $env:RABBITMQ_PASSWORD) { $env:RABBITMQ_PASSWORD = 'admin_dev_password' }
    if (-not $env:GATEWAY_SERVICE_TOKEN) { $env:GATEWAY_SERVICE_TOKEN = 'local_gateway_token_change_me' }
    if (-not $env:NARRATION_SERVICE_TOKEN) { $env:NARRATION_SERVICE_TOKEN = 'local_narration_token_change_me' }
    if (-not $env:APP_MOCKS_ENABLED) { $env:APP_MOCKS_ENABLED = 'true' }
    if (-not $env:API_GATEWAY_URL) { $env:API_GATEWAY_URL = 'http://localhost:8080' }

    Write-Host 'Starting Narration Service at http://localhost:8082. Press Ctrl+C to stop.'
    & .\mvnw.cmd -f narration-service/pom.xml spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Narration Service exited with code $LASTEXITCODE." }
}
finally {
    Pop-Location
}
