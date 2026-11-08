$ErrorActionPreference = 'Stop'
$base = $PSScriptRoot
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
$mixin = Get-ChildItem "$cache/net.fabricmc/sponge-mixin/0.15.2+mixin.0.8.7" -Recurse -Filter '*.jar' | Where-Object Name -NotMatch 'sources|javadoc' | Select-Object -First 1
$loader = Get-ChildItem "$cache/net.neoforged.fancymodloader/loader/4.0.44" -Recurse -Filter '*.jar' | Where-Object Name -NotMatch 'sources|javadoc' | Select-Object -First 1
if (!$mixin -or !$loader) { throw 'Required cached Java dependencies not found' }
$classes = Join-Path $base ('build/classes-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem "$base/src" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& javac -proc:none --release 21 -cp "$($mixin.FullName);$($loader.FullName)" -d $classes @sources
if ($LASTEXITCODE) { throw 'Compilation failed' }
$artifact = Join-Path $base 'build/recreated-yuushya-quiet-1.0.0.jar'
& jar --create --file $artifact -C $classes . -C "$base/resources" .
if ($LASTEXITCODE) { throw 'Packaging failed' }
Get-FileHash $artifact -Algorithm SHA256
