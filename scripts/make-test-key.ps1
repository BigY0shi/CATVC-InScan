#requires -Version 7
<#
Makes the shop's signing key, once, and saves it in the repository's secrets so CI can make
test builds (docs/TEST-BUILD.md). Run it on the owner's PC:

    pwsh -File scripts\make-test-key.ps1

It asks for one password, twice. The password is never printed, never written to a file and
never put on a command line. Keep it in a password manager: without the key file AND its
password no later build can update the app on a phone.

    -NoUpload   make and check the key only; save nothing on GitHub and start no build
#>
param(
  [string]$KeyFile = (Join-Path $HOME "inscan-release.jks"),
  [string]$Repo = "BigY0shi/CATVC-InScan",
  [switch]$NoUpload
)
$ErrorActionPreference = "Stop"

function Stop-WithMessage([string]$message) {
  Write-Host "STOPPED: $message" -ForegroundColor Red
  exit 1
}

if (Test-Path $KeyFile) {
  Stop-WithMessage "A key already exists at $KeyFile. This script never replaces a key. If that one is the shop's key, save it with the three 'gh secret set' commands in docs\TEST-BUILD.md."
}
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue)?.Source
if (-not $keytool) { Stop-WithMessage "keytool was not found. It comes with any JDK; install one or add its bin folder to PATH." }
if (-not $NoUpload) {
  gh auth status --hostname github.com *> $null
  if ($LASTEXITCODE -ne 0) { Stop-WithMessage "The GitHub CLI is not signed in. Run: gh auth login" }
}

if ([Console]::IsInputRedirected) {
  # Piped in (for example from a password manager's command line): the same password on two lines.
  $first = [Console]::In.ReadLine()
  $again = [Console]::In.ReadLine()
}
else {
  $first = ConvertFrom-SecureString -AsPlainText (Read-Host "Pick a password for the key (12 or more characters)" -AsSecureString)
  $again = ConvertFrom-SecureString -AsPlainText (Read-Host "Type it again" -AsSecureString)
}
if ([string]::IsNullOrEmpty($first)) { Stop-WithMessage "No password was given. Nothing was made." }
if ($first -cne $again) { Stop-WithMessage "The two passwords are not the same. Nothing was made." }
if ($first.Length -lt 12) { Stop-WithMessage "The password is shorter than 12 characters. Nothing was made." }
if ($first -notmatch '^[\x21-\x7E]+$') {
  Stop-WithMessage "Use only plain keyboard letters, digits and symbols, with no spaces or accented letters: other characters do not survive the trip to CI. Nothing was made."
}

# keytool and gh read the password from this variable, which lives only in this process.
$env:INSCAN_KEY_PASSWORD = $first
try {
  & $keytool -genkeypair -keystore $KeyFile -storetype PKCS12 -alias upload -keyalg RSA -keysize 4096 `
    -validity 10000 -dname "CN=Country ATV InScan" -storepass:env INSCAN_KEY_PASSWORD
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $KeyFile)) { Stop-WithMessage "keytool could not make the key (exit $LASTEXITCODE)." }

  # Open the new key with the password, to prove the pair works before anything is uploaded.
  $listing = & $keytool -list -v -keystore $KeyFile -alias upload -storepass:env INSCAN_KEY_PASSWORD
  if ($LASTEXITCODE -ne 0) { Stop-WithMessage "The new key at $KeyFile did not open with that password. Nothing was uploaded." }
  $fingerprint = ($listing | Select-String "SHA256:" | Select-Object -First 1).Line.Trim()
  Write-Host "Key made: $KeyFile"
  Write-Host "Signer $fingerprint"
  Write-Host "Every test build must print this same SHA-256."

  if ($NoUpload) {
    Write-Host "Nothing was saved on GitHub (-NoUpload)."
    exit 0
  }

  [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeyFile)) | gh secret set KEYSTORE_BASE64 --repo $Repo
  if ($LASTEXITCODE -ne 0) { Stop-WithMessage "Saving KEYSTORE_BASE64 failed. The key file is fine; fix the GitHub sign-in and use the 'gh secret set' commands in docs\TEST-BUILD.md." }
  $env:INSCAN_KEY_PASSWORD | gh secret set STORE_PASSWORD --repo $Repo
  if ($LASTEXITCODE -ne 0) { Stop-WithMessage "Saving STORE_PASSWORD failed. See docs\TEST-BUILD.md step 3." }
  $env:INSCAN_KEY_PASSWORD | gh secret set KEY_PASSWORD --repo $Repo
  if ($LASTEXITCODE -ne 0) { Stop-WithMessage "Saving KEY_PASSWORD failed. See docs\TEST-BUILD.md step 3." }
}
finally {
  $env:INSCAN_KEY_PASSWORD = $null
}

gh workflow run CI --ref main --repo $Repo
if ($LASTEXITCODE -ne 0) { Stop-WithMessage "The key is saved, but the build did not start. Start it with: gh workflow run CI --ref main --repo $Repo" }
Write-Host ""
Write-Host "Saved, and a test build has started. Now back up BOTH of these outside the repo:"
Write-Host "  1. the file $KeyFile"
Write-Host "  2. the password you just typed"
