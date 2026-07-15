$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env from .env.example. Add an AI key there when needed.'
}
docker compose up --build -d --wait
docker compose ps
Write-Host 'LocalHub: http://localhost:5173'
Write-Host 'Health:   http://localhost:8081/actuator/health'
