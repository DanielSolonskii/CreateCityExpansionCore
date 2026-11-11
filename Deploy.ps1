param(
  [switch]$DryRun,
  [switch]$ClientFirst,
  [switch]$SkipRestartDelay,
  [string]$BuildProject,
  [string]$Resume
)
$ErrorActionPreference = 'Stop'
$arguments = @((Join-Path $PSScriptRoot 'platform/launcher/scripts/quick-deploy.mjs'))
if ($ClientFirst) { $arguments += '--client-first' }
if ($SkipRestartDelay) { $arguments += '--skip-restart-delay' }
if ($BuildProject) { $arguments += @('--build-project', $BuildProject) }
if ($DryRun) { $arguments += '--dry-run' }
if ($Resume) { $arguments += @('--resume', $Resume) }
& node @arguments
if ($LASTEXITCODE -ne 0) { throw 'Deploy stopped. See the printed log and resume command.' }
