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
    # Gradle writes progress to stderr; under 'Stop', PowerShell 5.1 would treat that as a fatal error.
    $ErrorActionPreference = 'Continue'
    try { & (Join-Path $proj 'gradlew.bat') assembleRelease --console=plain *> $log } finally { Pop-Location }
    return $LASTEXITCODE
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
