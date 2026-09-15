$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { Copy-Item '.env.example' $envFile }
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$' -and -not [Environment]::GetEnvironmentVariable($Matches[1], 'Process')) {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$docker = (Get-Command docker -ErrorAction Stop).Source
$java = (Get-Command java -ErrorAction Stop).Source
$maven = (Get-Command mvn -ErrorAction Stop).Source
$npm = (Get-Command npm -ErrorAction Stop).Source

& $docker compose up mysql redis kafka -d --wait
& $maven -DskipTests package

$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = $env:MYSQL_ROOT_PASSWORD
$env:SPRING_REDIS_HOST = '127.0.0.1'
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = '127.0.0.1:9092'
$env:LOCALHUB_KAFKA_ENABLED = 'true'
$env:LOCALHUB_SECKILL_QUEUE = 'kafka'
$env:LOCALHUB_CANAL_ENABLED = 'false'
$env:LOCALHUB_AI_ENABLED = 'false'

Start-Process -FilePath $java -ArgumentList '-jar','target\localhub-0.0.1-SNAPSHOT.jar' `
    -WorkingDirectory $root -RedirectStandardOutput 'target\backend-runtime.log' `
    -RedirectStandardError 'target\backend-runtime.err.log' -WindowStyle Hidden
Start-Process -FilePath 'C:\Windows\System32\cmd.exe' -ArgumentList '/c',"`"$npm`" run dev" `
    -WorkingDirectory (Join-Path $root 'frontend') -RedirectStandardOutput 'frontend-runtime.log' `
    -RedirectStandardError 'frontend-runtime.err.log' -WindowStyle Hidden

Write-Host 'LocalHub starting: http://localhost:5173'
Write-Host 'Health: http://localhost:8081/actuator/health'
