[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$')][string]$Version,
    [Parameter(Mandatory)][string]$InstallerPath,
    [string]$SshTarget = 'root@186.246.28.214',
    [switch]$Approve,
    [switch]$Stage,
    [switch]$ApproveExisting,
    [int]$Sequence,
    [switch]$Mandatory,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
if ($SshTarget -notmatch '^[A-Za-z0-9_-]+@[A-Za-z0-9.-]+$') { throw 'Invalid SSH target' }
$installer = Get-Item -LiteralPath $InstallerPath
if ($installer.PSIsContainer -or $installer.Extension -ne '.exe') { throw 'Expected a Windows .exe installer' }
$sha = (Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$filename = 'RE-CREATED-Launcher-x64-setup.exe'
$url = "https://api.recreated.ru/launcher/latest/$filename"
Write-Host "Launcher $Version; size $($installer.Length); SHA-256 $sha"
Write-Host "Versioned: https://api.recreated.ru/launcher/versions/$Version/$filename"
Write-Host "Latest: $url"
if ($DryRun) { Write-Host 'DRY-RUN: no upload/publication'; return }
if (-not $Stage -and -not $Approve -and -not $ApproveExisting) { throw 'Choose -Stage before E2E or -ApproveExisting after successful E2E' }
if ($Stage -and ($Approve -or $ApproveExisting)) { throw 'Stage and approval are separate actions' }
if (-not $ApproveExisting -and $Sequence -lt 1) { throw 'Explicit increasing -Sequence required' }
$key = (Get-Content (Join-Path $PSScriptRoot '../../launcher/src-tauri/release-public-key.hex') -Raw).Trim()
if ($key -notmatch '^[a-f0-9]{64}$') { throw 'Pinned public key missing' }
$helper = Join-Path $PSScriptRoot 'publish-launcher.py'
if (-not (Test-Path -LiteralPath $helper)) { throw 'Publication helper missing' }
$remote = (& ssh -o BatchMode=yes $SshTarget 'umask 077; mktemp -d /tmp/recreated-launcher-publish.XXXXXXXX').Trim()
if ($LASTEXITCODE -ne 0 -or $remote -notmatch '^/tmp/recreated-launcher-publish\.[A-Za-z0-9]+$') { throw 'Unable to allocate protected staging' }
# Staging retained on failure for inspection; never delete broad directories.
& scp -q $installer.FullName "${SshTarget}:$remote/installer.exe"
if ($LASTEXITCODE -ne 0) { throw "Installer upload failed; inspect $remote" }
& scp -q $helper "${SshTarget}:$remote/publish-launcher.py"
if ($LASTEXITCODE -ne 0) { throw "Helper upload failed; inspect $remote" }
$arguments = "--public-key $key"
if ($ApproveExisting) { $arguments += ' --approve-existing' }
else { $arguments += " --sequence $Sequence" }
if ($Stage) { $arguments += ' --stage' }
if ($Mandatory) { $arguments += ' --mandatory' }
& ssh -o BatchMode=yes $SshTarget "python3 $remote/publish-launcher.py $Version $remote/installer.exe $sha $arguments"
if ($LASTEXITCODE -ne 0) { throw "Publication rejected; previous latest retained unless server reports a completed pointer switch. Inspect $remote" }
if ($Stage) { $url = "https://api.recreated.ru/launcher/versions/$Version/$filename" }
$download = Join-Path ([System.IO.Path]::GetTempPath()) ("recreated-launcher-verify-" + [guid]::NewGuid().ToString('N') + '.exe')
Invoke-WebRequest -Uri $url -OutFile $download
if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha) {
    throw "Public HTTPS verification failed; inspect $download and server latest before distribution"
}
Write-Host "PUBLIC_DOWNLOAD_VERIFIED: $download"
& ssh -o BatchMode=yes $SshTarget "rm -- $remote/installer.exe $remote/publish-launcher.py; rmdir -- $remote"
if ($LASTEXITCODE -ne 0) { Write-Warning "Publication successful; remove remaining protected staging $remote manually" }
