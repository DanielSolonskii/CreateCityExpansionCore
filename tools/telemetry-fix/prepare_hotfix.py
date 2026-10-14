"""Patch only server telemetry classes into the approved baseline artifact."""
import pathlib,zipfile,hashlib,json
root=pathlib.Path(__file__).resolve().parents[2]
baseline=pathlib.Path('E:/CreateCityExpansionCore_MERGED/CreateServerPlatform/launcher/release-input/alpha-0.1.10-client/instances/alpha/mods/createcityexpansioncore-1.0.1.jar')
assert hashlib.sha256(baseline.read_bytes()).hexdigest()=='e90fc11952ad8eebacab5f504f428d5c37b35aad91be88ba6ee3a91c63fc49c9'
classes=root/'build/classes/java/main/com/example/examplemod/auth'
names=['GameBackendClient.class','GameBackendClient$InvalidTelemetry.class','TelemetryBuffer.class','ServerTelemetryBridge.class','ServerTelemetryBridge$Batch.class','ServerTelemetryBridge$Bridge.class','ServerTelemetryBridge$Event.class','ServerTelemetryBridge$Session.class','ServerTelemetryBridge$Snapshot.class']
prefix='com/example/examplemod/auth/'
replacements={prefix+n:(classes/n).read_bytes() for n in names}
output=pathlib.Path(__file__).parent/'createcityexpansioncore-1.0.1-telemetry-hotfix.jar'
with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED) as new:
 assert not any(n.startswith('META-INF/') and n.endswith(('.SF','.RSA','.DSA')) for n in old.namelist()),'Signed baseline needs original signing workflow'
 for info in old.infolist():
  new.writestr(info,replacements.get(info.filename,old.read(info.filename)))
 for name,data in replacements.items():
  if name not in old.namelist():new.writestr(name,data)
with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(output) as new:
 for name in old.namelist():
  if name not in replacements:assert old.read(name)==new.read(name),'Unrelated entry changed'
 assert set(new.namelist())-set(old.namelist())=={prefix+'GameBackendClient$InvalidTelemetry.class'}
print(json.dumps({'artifact':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'patchedClasses':len(replacements),'unrelatedEntries':'byte-identical'}))
