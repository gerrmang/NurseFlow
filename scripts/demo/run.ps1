param(
    [int]$Port = 18096,
    [switch]$Verify,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js 22 이상이 필요합니다.' }
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME을 JDK 21 경로로 설정하세요.' }
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Port는 1024~65535여야 합니다.' }
Push-Location (Join-Path $taskRoot 'backend')
try {
    $buildArgs = @('bootJar', '--no-daemon')
    if ($Offline) { $buildArgs += '--offline' }
    & ./gradlew.bat @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'backend bootJar 빌드 실패' }
    $jars = @(Get-ChildItem -LiteralPath 'build/libs' -Filter '*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' })
    if ($jars.Count -ne 1) { throw '실행 가능한 bootJar가 정확히 하나여야 합니다.' }
    $nodeArgs = @((Join-Path $PSScriptRoot 'local.mjs'), '--jar', $jars[0].FullName, '--port', "$Port")
    if ($Verify) { $nodeArgs += '--verify' }
    & node @nodeArgs
    if ($LASTEXITCODE -ne 0) { throw "시연 실행 실패 (exit $LASTEXITCODE)" }
} finally { Pop-Location }
