# Creates signing/release.keystore for PaqetNG release signing.
# Reads credentials from signing/keystore-credentials (ignored by git), or prompts if missing.
# File format: KEY_ALIAS=paqetng, KEYSTORE_PASSWORD=xxx, KEY_PASSWORD=xxx (one per line)
# Requires JDK (keytool) - use Android Studio's JDK or set JAVA_HOME.
$ErrorActionPreference = "Stop"

$Keytool = "keytool"
if ($env:JAVA_HOME) {
    $Keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (-not (Test-Path $Keytool)) { $Keytool = "keytool" }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SigningDir = Join-Path $ScriptDir "signing"
$KeystorePath = Join-Path $SigningDir "release.keystore"
$CredsPath = Join-Path $SigningDir "keystore-credentials"

New-Item -ItemType Directory -Force -Path $SigningDir | Out-Null

function Get-Value($lines, $key) {
    $line = $lines | Where-Object { $_ -match "^\s*$key\s*=" } | Select-Object -First 1
    if ($line) { ($line -replace "^\s*$key\s*=\s*", "").Trim() }
}

$Alias = $null
$StorePass = $null
$KeyPass = $null

if (Test-Path $CredsPath) {
    $lines = Get-Content $CredsPath
    $Alias = Get-Value $lines "KEY_ALIAS"
    $StorePass = Get-Value $lines "KEYSTORE_PASSWORD"
    $KeyPass = Get-Value $lines "KEY_PASSWORD"
    if (-not $KeyPass) { $KeyPass = $StorePass }
}

$prompted = $false
if (-not $StorePass) {
    Write-Host "Credentials file not found or incomplete: $CredsPath"
    Write-Host "Enter values (they will be saved to the file for next time)"
    Write-Host ""
    if (-not $Alias) { $Alias = Read-Host "Key alias (default: paqetng)"; if ([string]::IsNullOrWhiteSpace($Alias)) { $Alias = "paqetng" } }
    $StorePass = Read-Host "Keystore password" -AsSecureString
    $StorePass = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($StorePass))
    $keyInput = Read-Host "Key password (press Enter to use same as keystore password)"
    $KeyPass = if ([string]::IsNullOrWhiteSpace($keyInput)) { $StorePass } else { $keyInput }
    $prompted = $true
} else {
    if (-not $Alias) { $Alias = "paqetng" }
    if (-not $KeyPass) { $KeyPass = $StorePass }
}

# Save credentials to file when we prompted (so next run reads from file)
if ($prompted) {
    Set-Content -Path $CredsPath -Value @(
        "KEY_ALIAS=$Alias",
        "KEYSTORE_PASSWORD=$StorePass",
        "KEY_PASSWORD=$KeyPass"
    ) -Encoding utf8
    Write-Host "Credentials saved to $CredsPath (ignored by git)"
    Write-Host ""
}

if (Test-Path $KeystorePath) {
    Write-Host "Keystore already exists: $KeystorePath"
    Write-Host "Delete it first if you want to regenerate."
    Write-Host ""
    Write-Host "To get base64 for GitHub Secret ANDROID_KEYSTORE_BASE64, run:"
    Write-Host "  [Convert]::ToBase64String([IO.File]::ReadAllBytes('$KeystorePath'))"
    exit 0
}

& $Keytool -genkey -v -keystore $KeystorePath -keyalg RSA -keysize 2048 -validity 10000 `
    -alias $Alias -storepass $StorePass -keypass $KeyPass `
    -dname "CN=PaqetNG, OU=Android, O=PaqetNG, L=Unknown, ST=Unknown, C=US"

$Bytes = [IO.File]::ReadAllBytes($KeystorePath)
$Base64 = [Convert]::ToBase64String($Bytes)

Write-Host ""
Write-Host "Keystore created: $KeystorePath"
Write-Host ""
Write-Host "=============================================="
Write-Host "Add these as GitHub Repository Secrets:"
Write-Host "  Settings -> Secrets and variables -> Actions -> New repository secret"
Write-Host "=============================================="
Write-Host ""
Write-Host "1. ANDROID_KEYSTORE_BASE64"
Write-Host "   Value: (copy the single line below - no line breaks)"
Write-Host $Base64
Write-Host ""
Write-Host "2. ANDROID_KEYSTORE_PASSWORD"
Write-Host "   Value: $StorePass"
Write-Host ""
Write-Host "3. ANDROID_KEY_ALIAS"
Write-Host "   Value: $Alias"
Write-Host ""
Write-Host "4. ANDROID_KEY_PASSWORD"
Write-Host "   Value: $KeyPass"
Write-Host "=============================================="
