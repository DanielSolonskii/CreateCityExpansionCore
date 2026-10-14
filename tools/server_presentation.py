import pathlib,subprocess,datetime,shutil,os,time
root=pathlib.Path('/opt/create-server/minecraft')
backup=pathlib.Path('/opt/create-server/backups')/('presentation-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir(mode=0o700)
def run(*args): return subprocess.check_output(args,text=True).strip()
shutil.copy2(root/'server.properties',backup/'server.properties')
if (root/'server-icon.png').exists(): shutil.copy2(root/'server-icon.png',backup/'server-icon.png')
run('tar','-cpf',str(backup/'configs.tar'),'-C',str(root),'config','world/serverconfig')
print('BACKUP',backup,flush=True)
run('systemctl','stop','create-server-minecraft')
assert run('systemctl','show','create-server-minecraft','-p','MainPID','--value')=='0'
# Back up properties again after shutdown if Minecraft updated them.
shutil.copy2(root/'server.properties',backup/'server.properties.stopped')
lines=(root/'server.properties').read_text().splitlines()
motd=r'\u00a76\u00a7lRE:CREATED\u00a7r \u00a78| \u00a7fCreate your world\n\u00a7b\u0410\u043b\u044c\u0444\u0430 \u0442\u0435\u0441\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u00a78\u2022 \u00a77Create \u00a78\u2022 \u00a77\u0413\u043e\u0440\u043e\u0434\u0430 \u00a78\u2022 \u00a77\u0416\u0435\u043b\u0435\u0437\u043d\u044b\u0435 \u0434\u043e\u0440\u043e\u0433\u0438'
lines=[line for line in lines if not line.startswith(('motd=','max-players='))]
temp=root/'server.properties.presentation-new';temp.write_text('\n'.join(lines)+'\nmax-players=15\nmotd='+motd+'\n')
os.chmod(temp,0o640);shutil.chown(temp,'createserver','createserver');os.replace(temp,root/'server.properties')
icon=pathlib.Path(__file__).with_name('server-icon.png')
data=icon.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n' and int.from_bytes(data[16:20],'big')==64 and int.from_bytes(data[20:24],'big')==64
shutil.copy2(icon,root/'server-icon.png');os.chmod(root/'server-icon.png',0o640);shutil.chown(root/'server-icon.png','createserver','createserver')
for file in backup.iterdir(): os.chmod(file,0o600)
run('systemctl','start','create-server-minecraft')
invocation=run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')
for attempt in range(180):
    assert run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')==invocation,'Unexpected restart'
    log=run('journalctl',f'_SYSTEMD_INVOCATION_ID={invocation}','--no-pager','-o','cat')
    if 'Done (' in log:
        print('READY Done; max-players=15; icon=64x64; MOTD installed',flush=True);break
    time.sleep(1)
else: raise RuntimeError('Readiness failed; backup preserved')
