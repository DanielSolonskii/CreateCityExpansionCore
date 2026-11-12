param([Parameter(Mandatory)][string]$Project,[Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9.+_-]+$')][string]$ModVersion)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $Project
try {
  & ./gradlew.bat assemble --offline --no-configuration-cache --console=plain "-Pmod_version=$ModVersion"
  if ($LASTEXITCODE -ne 0) { throw 'Candidate build failed' }
} finally { Pop-Location }
