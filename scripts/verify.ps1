$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host '1/4 Validating Compose configuration...'
docker compose config --quiet
Write-Host '2/4 Building and starting the complete application...'
docker compose up --build -d --wait

Write-Host '3/4 Checking health and frontend...'
$health = Invoke-RestMethod 'http://127.0.0.1:8081/actuator/health'
if ($health.status -ne 'UP') { throw "Backend is not healthy: $($health | ConvertTo-Json -Compress)" }
$frontend = Invoke-WebRequest 'http://127.0.0.1:5173' -UseBasicParsing
if ($frontend.StatusCode -ne 200) { throw 'Frontend did not return HTTP 200.' }

Write-Host '4/4 Running tests...'
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn verify
} else {
    Write-Warning 'Maven is not installed; Testcontainers tests were not executed. Install Maven 3.8+ and rerun this script.'
}

docker run --rm --add-host=host.docker.internal:host-gateway `
  -e PLAYWRIGHT_BASE_URL=http://host.docker.internal:5173 `
  -v "${root}/frontend:/work" -w /work `
  mcr.microsoft.com/playwright:v1.45.0-jammy bash -lc "npm ci && npm run test:e2e"

Write-Host 'Verification completed. Containers are left running for inspection.'
