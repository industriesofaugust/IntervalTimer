# Builds the two files to upload to Google Play, signed with the upload key:
#   app\build\outputs\bundle\release\app-release.aab    (phone)
#   wear\build\outputs\bundle\release\wear-release.aab  (watch)
#
# Run from the project folder:   .\scripts\bundle.ps1
#
# Before each upload, raise appVersionNumber in gradle.properties by one.
# Needs keystore.properties (make it once with .\scripts\new-upload-key.ps1).

$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

if (-not (Test-Path (Join-Path $project 'keystore.properties'))) {
    throw 'keystore.properties is missing. Run .\scripts\new-upload-key.ps1 once first.'
}

Push-Location $project
try {
    # The tests run first: a failing test stops the upload files being made.
    .\gradlew.bat testDebugUnitTest :app:bundleRelease :wear:bundleRelease -Pplay
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
} finally {
    Pop-Location
}

$version = Get-Content (Join-Path $project 'gradle.properties') |
    Where-Object { $_ -match '^appVersion(Name|Number)=' }
Write-Host ''
Write-Host 'Ready to upload:'
Write-Host "  $project\app\build\outputs\bundle\release\app-release.aab"
Write-Host "  $project\wear\build\outputs\bundle\release\wear-release.aab"
Write-Host ($version -join '   ')
Write-Host ''
Write-Host 'Note: these builds are signed for Play. To put builds on your own'
Write-Host 'devices, keep using .\scripts\install.ps1 (it rebuilds with the device key).'
