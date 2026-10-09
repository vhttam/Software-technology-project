$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not (Test-Path 'infrastructure/keys/jwt-public.pem')) {
        throw 'Missing infrastructure/keys/jwt-public.pem. Create the local RSA key pair as described in README.md.'
    }

    if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8080' }
    if (-not $env:JWT_PUBLIC_KEY_PATH) { $env:JWT_PUBLIC_KEY_PATH = 'file:./infrastructure/keys/jwt-public.pem' }
    if (-not $env:GATEWAY_SERVICE_TOKEN) { $env:GATEWAY_SERVICE_TOKEN = 'local_gateway_token_change_me' }
    if (-not $env:NARRATION_SERVICE_TOKEN) { $env:NARRATION_SERVICE_TOKEN = 'local_narration_token_change_me' }
    if (-not $env:APP_MOCKS_ENABLED) { $env:APP_MOCKS_ENABLED = 'true' }
    if (-not $env:NARRATION_SERVICE_URL) { $env:NARRATION_SERVICE_URL = 'http://localhost:8082' }
    if (-not $env:AUTH_SERVICE_URL) { $env:AUTH_SERVICE_URL = 'http://localhost:8081' }
    if (-not $env:CONTENT_SERVICE_URL) { $env:CONTENT_SERVICE_URL = 'http://localhost:8083' }

    Write-Host 'Starting API Gateway at http://localhost:8080. Press Ctrl+C to stop.'
    & .\mvnw.cmd -f api-gateway/pom.xml spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "API Gateway exited with code $LASTEXITCODE." }
}
finally {
    Pop-Location
}
