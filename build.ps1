# Builds the release APK and installs it on the USB-connected phone.
# Usage (from any PowerShell):  & "C:\Users\Arij\Desktop\Projects\Mobile app\build.ps1" [-NoInstall]
param([switch]$NoInstall)

$ErrorActionPreference = 'Stop'
$root = Join-Path $env:USERPROFILE 'Android'
$env:JAVA_HOME = Join-Path $root 'jdk17'
$env:ANDROID_HOME = Join-Path $root 'sdk'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$proj = $PSScriptRoot
$log = Join-Path $proj 'build\build.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null

function Invoke-Build {
    Push-Location $proj
    [Environment]::CurrentDirectory = $proj
    # Start-Process keeps stdout/stderr as plain text: redirecting a native command's stderr in
    # PowerShell 5.1 wraps each line in an error record, which garbles compiler errors.
    $err = "$log.err"
    try {
        # Not -Wait: in PowerShell 5.1 that also waits for the Gradle daemon the build may spawn, which never exits.
        $p = Start-Process -FilePath (Join-Path $proj 'gradlew.bat') -ArgumentList 'assembleRelease', '--console=plain' `
            -WorkingDirectory $proj -RedirectStandardOutput $log -RedirectStandardError $err -NoNewWindow -PassThru
        $null = $p.Handle # without touching the handle first, ExitCode comes back empty
        $p.WaitForExit()
    } finally { Pop-Location }
    Get-Content $err | Add-Content $log
    Remove-Item $err
    return $p.ExitCode
}

$code = Invoke-Build
# On Windows the previous build's classes.dex is sometimes still locked (antivirus scan); one retry clears it.
if ($code -ne 0 -and (Select-String -Path $log -Pattern 'cannot access the file' -Quiet)) {
    Write-Host 'Output file was locked; retrying once...'
    Start-Sleep -Seconds 3
    $code = Invoke-Build
}

Get-Content $log | Select-String -Pattern '^e: |^w: |What went wrong|BUILD' | ForEach-Object { $_.Line }
if ($code -ne 0) { Write-Host "Build failed; full log: $log"; exit $code }

$apk = Join-Path $proj 'app\build\outputs\apk\release\app-release.apk'
Write-Host ('APK: {0:N2} MB' -f ((Get-Item $apk).Length / 1MB))
if (-not $NoInstall) { & $adb install -r $apk }
