param(
    [int]$Threads = 50,
    [int]$Loops = 20,
    [int]$RampSeconds = 20,
    [string]$Token = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Get-Command jmeter -ErrorAction SilentlyContinue)) { throw 'JMeter 5.6+ is required.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $root "target/load-test-$stamp"
New-Item -ItemType Directory -Force $output | Out-Null
Invoke-WebRequest 'http://127.0.0.1:8081/actuator/prometheus' -OutFile (Join-Path $output 'metrics-before.txt')
jmeter -n -t tests/jmeter/localhub-smoke.jmx `
  -Jthreads=$Threads -Jloops=$Loops -Jramp=$RampSeconds -Jtoken=$Token `
  -l (Join-Path $output 'results.jtl') -e -o (Join-Path $output 'html')
Invoke-WebRequest 'http://127.0.0.1:8081/actuator/prometheus' -OutFile (Join-Path $output 'metrics-after.txt')
Write-Host "Report: $output"
