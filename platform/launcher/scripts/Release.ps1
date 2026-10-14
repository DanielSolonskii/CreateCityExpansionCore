param(
  [Parameter(Mandatory, Position=0)][ValidateSet('dry-run','server-deploy','publish','server-rollback')][string]$Action,
  [Parameter(Position=1)][ValidatePattern('^alpha-\d+\.\d+\.\d+$')][string]$Version,
  [string]$Instance = (Join-Path (Split-Path $PSScriptRoot -Parent) 'release-input\client'),
  [string]$ServerSource = (Join-Path (Split-Path $PSScriptRoot -Parent) 'release-input\server'),
  [int]$Sequence = 0,
  [string]$Output = (Join-Path (Split-Path $PSScriptRoot -Parent) 'releases\workflow'),
  [string]$Previous,
  [switch]$Offline,
  [ValidatePattern('^(?!-)[a-zA-Z0-9_.@:-]+$')][string]$SshTarget = 'root@186.246.28.214',
  [ValidatePattern('^/opt/create-server/backups/server-deploy-[a-f0-9]{32}$')][string]$Backup,
  [switch]$RestoreWorld
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Checked([string]$Program, [string[]]$Arguments) {
  & $Program @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$Program failed; release stopped. No implicit next action." }
}
if ($Offline -and $Action -ne 'dry-run') { throw 'Offline is dry-run only' }
if ($RestoreWorld -and $Action -ne 'server-rollback') { throw '-RestoreWorld is only an explicit server rollback option' }
if ($Action -ne 'server-rollback' -and !$Version) { throw 'Specify alpha-X.Y.Z' }
if ($Action -eq 'server-rollback' -and !$Backup) { throw 'Specify -Backup from deployment report' }
New-Item -ItemType Directory -Force -Path $Output | Out-Null
# A workstation lock prevents two scripts from racing on the same draft.
$localLock = [System.IO.File]::Open((Join-Path $Output 'workflow.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
  $plan = $null
  if ($Action -ne 'server-rollback') {
    if (!$Previous) {
      if ($Offline) { throw '-Offline requires a previously signed -Previous manifest' }
      $Previous = Join-Path $Output 'previous-alpha.json'
      Checked node @((Join-Path $PSScriptRoot 'fetch-release.mjs'), '--output', $Previous)
    }
    $parameters = @((Join-Path $PSScriptRoot 'release-workflow.mjs'), '--instance', $Instance, '--version', $Version,
      '--output', $Output, '--previous', $Previous, '--sequence', $(if ($Sequence -eq 0) { 'auto' } else { "$Sequence" }))
    if (Test-Path -LiteralPath $ServerSource) { $parameters += @('--server-source', $ServerSource) }
    Checked node $parameters
    $prepared = Join-Path $Output "prepared\$Version"
    $plan = Get-Content -Raw -LiteralPath (Join-Path $prepared 'plan.json') | ConvertFrom-Json
    if ($Action -eq 'dry-run') {
      if (!$Offline -and ($plan.server.files.Count + $plan.server.remove.Count -gt 0)) {
        Checked node @((Join-Path $PSScriptRoot 'inspect-server.mjs'), '--ssh', $SshTarget,
          '--manifest', (Join-Path $prepared 'server\server-release.json'), '--output', (Join-Path $prepared 'server-dry-run.json'))
      }
      if ($Offline) { Write-Host 'Offline: server candidates shown; actual VDS diff requires online dry-run.' }
      return
    }
    if ($Action -eq 'publish' -and ($plan.server.files.Count + $plan.server.remove.Count -gt 0)) {
      $receiptPath = Join-Path $prepared 'server-deployed.json'
      if (!(Test-Path -LiteralPath $receiptPath)) { throw 'Run explicit server-deploy first for this client+server candidate' }
      $receipt = Get-Content -Raw -LiteralPath $receiptPath | ConvertFrom-Json
      if ($receipt.manifestSha256 -ne $plan.manifestSha256 -or $receipt.serverManifestSha256 -ne $plan.serverManifestSha256) { throw 'Candidate changed since server deployment. Re-run dry-run and server-deploy.' }
      $inspectionPath = Join-Path $prepared 'server-before-publish.json'
      Checked node @((Join-Path $PSScriptRoot 'inspect-server.mjs'), '--ssh', $SshTarget,
        '--manifest', (Join-Path $prepared 'server\server-release.json'), '--output', $inspectionPath, '--require-ready', 'true')
      $inspection = Get-Content -Raw -LiteralPath $inspectionPath | ConvertFrom-Json
      if ($inspection.plan.operations.Count -ne 0) { throw 'Server files no longer match the successful candidate. Client channel not switched.' }
    }
  }
  # Only explicit deployment actions create a protected, unique remote upload directory.
  $remote = '/opt/create-server/secrets/release-upload-' + [Guid]::NewGuid().ToString('N')
  Checked ssh @('-o','BatchMode=yes',$SshTarget,"umask 077; mkdir '$remote'")
  try {
    foreach ($helper in @('deploy-release.py','server-release.py')) {
      Checked scp @('-o','BatchMode=yes',(Join-Path $PSScriptRoot $helper),"${SshTarget}:${remote}/$helper")
    }
    if ($Action -eq 'server-rollback') {
      $restore = $(if ($RestoreWorld) { ' --restore-world' } else { '' })
      Checked ssh @('-o','BatchMode=yes',$SshTarget,"python3 -B '$remote/server-release.py' rollback --backup '$Backup'$restore")
    } elseif ($Action -eq 'server-deploy') {
      if ($plan.server.files.Count + $plan.server.remove.Count -eq 0) { throw 'No server candidates: nothing to deploy' }
      $dryRun = Join-Path $prepared 'server-dry-run.json'
      Checked node @((Join-Path $PSScriptRoot 'inspect-server.mjs'), '--ssh', $SshTarget,
        '--manifest', (Join-Path $prepared 'server\server-release.json'), '--output', $dryRun)
      $inspection = Get-Content -Raw -LiteralPath $dryRun | ConvertFrom-Json
      $archive = Join-Path $prepared 'server.tar'
      Checked tar @('-cf',$archive,'-C',$prepared,'server')
      Checked scp @('-o','BatchMode=yes',$archive,"${SshTarget}:${remote}/server.tar")
      $result = @(& ssh -o BatchMode=yes $SshTarget "python3 -B '$remote/server-release.py' deploy --archive '$remote/server.tar' --version '$Version' --expected-plan '$($inspection.planSha256)'")
      $deployExit = $LASTEXITCODE
      $result | ForEach-Object { Write-Host $_ } # Preserve the failure/backup report before throwing.
      if ($deployExit -ne 0) { throw 'Server deployment failed or transport interrupted. Client channel unchanged; inspect the report/backups before retrying.' }
      $report = ($result -join "`n") | ConvertFrom-Json
      if ($report.status -notin @('DEPLOYED','NO_CHANGES')) { throw 'Server has not passed readiness' }
      [System.IO.File]::WriteAllText((Join-Path $prepared 'server-deployed.json'), (@{manifestSha256=$plan.manifestSha256;serverManifestSha256=$plan.serverManifestSha256;report=$report} | ConvertTo-Json -Depth 10))
    } elseif ($Action -eq 'publish') {
      $inventory = Join-Path $prepared 'remote-objects.txt'
      $known = @(Checked ssh @('-o','BatchMode=yes',$SshTarget,'find /opt/create-server/distribution/releases/objects -maxdepth 1 -type f -printf "%f\n"'))
      [System.IO.File]::WriteAllLines($inventory, [string[]]$known)
      $archive = Join-Path $prepared 'client.tar'
      Checked node @((Join-Path $PSScriptRoot 'package-release.mjs'), '--source',$Output,'--version',$Version,'--inventory',$inventory,'--archive',$archive)
      Checked scp @('-o','BatchMode=yes',$archive,"${SshTarget}:${remote}/client.tar")
      $key = (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../src-tauri/release-public-key.hex')).Trim()
      if ($key -notmatch '^[a-f0-9]{64}$') { throw 'Invalid public key' }
      Checked ssh @('-o','BatchMode=yes',$SshTarget,"python3 -B '$remote/deploy-release.py' --archive '$remote/client.tar' --version '$Version' --expected-previous '$($plan.expectedPrevious)' --public-key '$key'")
    }
  } finally {
    # Remove only files in this exact UUID upload directory, never backups or releases.
    # Failure to clean staging must not claim an already committed publication failed.
    ssh -o BatchMode=yes $SshTarget "rm -f '$remote/deploy-release.py' '$remote/server-release.py' '$remote/server.tar' '$remote/client.tar'; rmdir '$remote'"
    if ($LASTEXITCODE -ne 0) { Write-Warning "Upload cleanup pending: $remote. Check printed publication/deployment result." }
  }
} finally { $localLock.Dispose() }
