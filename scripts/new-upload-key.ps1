# Creates the Google Play upload key. Run it ONCE, ever.
#
#   .\scripts\new-upload-key.ps1
#
# It makes two things:
#   1. the key itself:  %USERPROFILE%\keystores\intervaltimer-upload.jks
#   2. keystore.properties in the project folder, holding the key's password
#      (git ignores this file, so the password never reaches GitHub)
#
# Back both up somewhere off this PC. If they are lost, uploads stop until
# Google resets the upload key, which takes a support request and a few days.

$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$propsFile = Join-Path $project 'keystore.properties'
$folder = Join-Path $env:USERPROFILE 'keystores'
$keystore = Join-Path $folder 'intervaltimer-upload.jks'
$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'

if ((Test-Path $keystore) -or (Test-Path $propsFile)) {
    throw "An upload key or keystore.properties already exists. Nothing was changed."
}
New-Item -ItemType Directory -Force $folder | Out-Null

# A long random password. It is written to keystore.properties and never shown.
$bytes = New-Object byte[] 24
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = ([Convert]::ToBase64String($bytes)) -replace '[+/=]', 'x'

# Handed to keytool through an environment variable, so it does not appear in
# the command line.
$env:IT_UPLOAD_PASS = $password
try {
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias upload `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -storepass:env IT_UPLOAD_PASS -keypass:env IT_UPLOAD_PASS `
        -dname 'CN=August' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the key.' }
} finally {
    Remove-Item Env:\IT_UPLOAD_PASS
}

# Forward slashes: a .properties file treats a backslash as an escape.
$lines = @(
    "storeFile=$($keystore -replace '\\', '/')",
    "storePassword=$password",
    'keyAlias=upload',
    "keyPassword=$password"
)
[IO.File]::WriteAllLines($propsFile, $lines)

Write-Host "Created $keystore"
Write-Host "Created $propsFile"
Write-Host 'Back up both files somewhere off this PC.'
