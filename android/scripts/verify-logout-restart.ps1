param(
    [string]$Adb = "adb",
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$deviceArguments = if ($Serial) { @("-s", $Serial) } else { @() }
$runner = "com.nurseflow.app.test/com.nurseflow.app.test.IsolatedSessionTestRunner"
$testClass = "com.nurseflow.app.core.session.LogoutRestartDeviceTest"

function Invoke-LogoutPhase([string]$Phase, [string]$Method) {
    $result = & $Adb @deviceArguments shell am instrument -w -r -e logoutRestartPhase $Phase -e class "$testClass#$Method" $runner 2>&1
    $exitCode = $LASTEXITCODE
    $result | Write-Output
    $output = $result -join "`n"
    if ($exitCode -ne 0 -or $output -notmatch 'OK \(1 tests?\)' -or
        $output -notmatch '(?m)^INSTRUMENTATION_STATUS_CODE: 0\s*$') {
        throw "Logout restart verification failed: $Phase"
    }
}

Invoke-LogoutPhase "prepare" "prepareFailedDeletionAndOfflineRevocation"
& $Adb @deviceArguments shell am force-stop com.nurseflow.app
if ($LASTEXITCODE -ne 0) { throw "Could not stop the prepared test process" }
Invoke-LogoutPhase "verify" "verifyNewProcessCannotRestoreResidualCredential"
