# Builds the optimised (release) apps and installs them on every connected
# phone and watch, then compiles them ahead of time so they start fast.
#
# Run from the project folder:   .\scripts\install.ps1
# Skip the build step:            .\scripts\install.ps1 -NoBuild

param([switch]$NoBuild)

$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$package = 'com.august.intervaltimer'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"

if (-not $NoBuild) {
    Push-Location $project
    try {
        .\gradlew.bat assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    } finally {
        Pop-Location
    }
}

$phoneApk = "$project\app\build\outputs\apk\release\app-release.apk"
$watchApk = "$project\wear\build\outputs\apk\release\wear-release.apk"

$devices = adb devices | Select-Object -Skip 1 |
    Where-Object { $_ -match '\tdevice$' } |
    ForEach-Object { ($_ -split '\t')[0] }

if (-not $devices) { throw 'No phone or watch connected (check adb devices).' }

foreach ($serial in $devices) {
    # Watches report "watch" in their build characteristics.
    $isWatch = (adb -s $serial shell getprop ro.build.characteristics) -match 'watch'
    $apk = if ($isWatch) { $watchApk } else { $phoneApk }
    $kind = if ($isWatch) { 'watch' } else { 'phone' }

    Write-Host "Installing on $kind ($serial)..."
    adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "Install failed on $serial." }

    # A fresh install runs slowly until Android compiles it in the background;
    # compiling now makes it fast from the first launch.
    adb -s $serial shell cmd package compile -m speed -f $package | Out-Null
}

Write-Host 'Done.'
