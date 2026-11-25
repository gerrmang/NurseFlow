param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Adb = 'adb',
    [switch]$WithServer
)

$ErrorActionPreference = 'Stop'
$androidRoot = Split-Path -Parent $PSScriptRoot
$resultDir = Join-Path $androidRoot 'app/build/b8a-device-results'
New-Item -ItemType Directory -Path $resultDir -Force | Out-Null

function Invoke-DeviceAdb([string[]]$DeviceArguments) {
    & $Adb -s $Serial @DeviceArguments
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed (exit $LASTEXITCODE)." }
}

$deviceState = Invoke-DeviceAdb @('get-state')
if (($deviceState -join '').Trim() -ne 'device') { throw 'The selected test device is not connected and authorized.' }
$booted = Invoke-DeviceAdb @('shell', 'getprop', 'sys.boot_completed')
if (($booted -join '').Trim() -ne '1') { throw 'Wait for Android to finish booting.' }

Invoke-DeviceAdb @('install', '-r', (Join-Path $androidRoot 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-DeviceAdb @('install', '-r', (Join-Path $androidRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
Invoke-DeviceAdb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
Invoke-DeviceAdb @('shell', 'wm', 'dismiss-keyguard')

$classes = 'com.nurseflow.app.feature.offrequest.OffRequestScreenUiTest'
$expected = 7
$instrumentation = @('shell', 'am', 'instrument', '-w', '-r')
if ($WithServer) {
    Invoke-DeviceAdb @('reverse', 'tcp:18090', 'tcp:18090')
    Invoke-DeviceAdb @('reverse', 'tcp:18091', 'tcp:18091')
    $classes += ',com.nurseflow.app.feature.offrequest.OffRequestRealServerDeviceTest'
    $instrumentation += @('-e', 'b8aE2e', 'true')
    $expected = 8
}
$instrumentation += @('-e', 'class', $classes, 'com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner')
$result = @(Invoke-DeviceAdb $instrumentation)
$result | Tee-Object -FilePath (Join-Path $resultDir 'instrumentation.txt')
$joined = $result -join "`n"
# adb may return exit 0 even when JUnit fails or a test is skipped.
if ($joined -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE: -[234]' -or
    $joined -notmatch "OK \($expected tests?\)") {
    throw "Device acceptance failed: expected $expected successful tests with no skips. See $resultDir."
}
if ($WithServer) {
    foreach ($proof in @('b8a-admin-confirmed.png', 'b8a-nurse-confirmed.png')) {
        Invoke-DeviceAdb @('pull', "/sdcard/Android/data/com.nurseflow.app/files/$proof", (Join-Path $resultDir $proof))
    }
}
Write-Output "B8a device acceptance passed: $expected tests, no failures or skips."
