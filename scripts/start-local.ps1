$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$docker = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$java = 'C:\Program Files\Zulu\zulu-8\bin\java.exe'
$maven = 'C:\Users\hello\tools\apache-maven-3.9.11\bin\mvn.cmd'
$npm = 'C:\Program Files\nodejs\npm.cmd'

& $docker compose up mysql redis kafka -d --wait
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-8'
& $maven -DskipTests package

$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '123456'
$env:SPRING_REDIS_HOST = '127.0.0.1'
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = '127.0.0.1:9092'
$env:LOCALHUB_KAFKA_ENABLED = 'true'
$env:LOCALHUB_SECKILL_QUEUE = 'kafka'
$env:LOCALHUB_CANAL_ENABLED = 'false'
$env:LOCALHUB_AI_ENABLED = 'false'

Start-Process -FilePath $java -ArgumentList '-jar','target\hm-dianping-0.0.1-SNAPSHOT.jar' `
    -WorkingDirectory $root -RedirectStandardOutput 'target\backend-runtime.log' `
    -RedirectStandardError 'target\backend-runtime.err.log' -WindowStyle Hidden
Start-Process -FilePath 'C:\Windows\System32\cmd.exe' -ArgumentList '/c',"`"$npm`" run dev" `
    -WorkingDirectory (Join-Path $root 'frontend') -RedirectStandardOutput 'frontend-runtime.log' `
    -RedirectStandardError 'frontend-runtime.err.log' -WindowStyle Hidden

Write-Host 'LocalHub starting: http://localhost:5173'
Write-Host 'Health: http://localhost:8081/actuator/health'
