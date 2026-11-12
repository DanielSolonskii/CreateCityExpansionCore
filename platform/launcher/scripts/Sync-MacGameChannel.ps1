param(
  [ValidatePattern('^(?!-)[a-zA-Z0-9_.@:-]+$')][string]$SshTarget = 'root@186.246.28.214',
  [ValidatePattern('^alpha-\d+\.\d+\.\d+$')][string]$ExpectedVersion,
  [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedManifest
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$key = (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../src-tauri/release-public-key.hex')).Trim()
if ($key -notmatch '^[a-f0-9]{64}$') { throw 'Invalid release public key' }
$remote = '/opt/create-server/secrets/macos-sync-' + [Guid]::NewGuid().ToString('N')
ssh -o BatchMode=yes $SshTarget "umask 077; mkdir '$remote'"
if ($LASTEXITCODE -ne 0) { throw 'Cannot allocate protected Mac publication staging' }
try {
  foreach ($name in @('refresh-channel.py','sync-channel.py')) {
    scp -o BatchMode=yes (Join-Path $PSScriptRoot "macos-publication/$name") "${SshTarget}:${remote}/$name"
    if ($LASTEXITCODE -ne 0) { throw 'Mac helper transfer failed; channel not promoted' }
  }
  $command = "python3 -B '$remote/sync-channel.py' --public-key '$key'"
  if ($ExpectedVersion) { $command += " --expected-version '$ExpectedVersion'" }
  if ($ExpectedManifest) { $command += " --expected-source '$ExpectedManifest'" }
  ssh -o BatchMode=yes $SshTarget $command
  if ($LASTEXITCODE -ne 0) {
    throw 'Mac sync failed or transport interrupted. Windows publication is NOT rolled back. Inspect public channels; retry Release.ps1 sync-macos, not Windows publish.'
  }
} finally {
  # Only the two known files under this invocation's exact UUID directory.
  ssh -o BatchMode=yes $SshTarget "rm -f '$remote/refresh-channel.py' '$remote/sync-channel.py'; rmdir '$remote'"
  if ($LASTEXITCODE -ne 0) { Write-Warning "Mac publication staging cleanup pending: $remote" }
}
