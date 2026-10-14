# Compatibility entry point; publication is still an explicit operator action.
param(
  [Parameter(Mandatory)][ValidatePattern('^alpha-\d+\.\d+\.\d+$')][string]$Version,
  [ValidateRange(0,2147483647)][int]$Sequence = 0,
  [Parameter(Mandatory)][string]$Instance,
  [ValidatePattern('^[a-zA-Z0-9_.@:-]+$')][string]$SshTarget = 'root@186.246.28.214'
)
& (Join-Path $PSScriptRoot 'Release.ps1') publish $Version -Instance $Instance -Sequence $Sequence -SshTarget $SshTarget
