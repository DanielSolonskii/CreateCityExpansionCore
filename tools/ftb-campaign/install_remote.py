"""Run over SSH only after uploading the built tar. Changes quest definitions only."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, tarfile

SERVER=Path('/opt/create-server/minecraft').resolve()
STAGE=Path('/tmp/ccec-ftb-campaign-20261011').resolve()
ARCHIVE=Path('/tmp/ccec-ftb-campaign-20261011.tar')

def hashes(root):
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob('*')) if p.is_file()}

def main():
    assert SERVER.is_dir() and STAGE.parent==Path('/tmp')
    STAGE.mkdir(exist_ok=True)
    with tarfile.open(ARCHIVE) as tar:tar.extractall(STAGE,filter='data')
    files=list((STAGE/'quests').rglob('*.snbt'))
    assert len(files)==10
    manifest=json.loads((STAGE/'manifest.json').read_text())
    ids=[];quests=[]
    for p in (STAGE/'quests/chapters').glob('*.snbt'):
        chapter=json.loads(p.read_text());ids.append(chapter['id'])
        for q in chapter['quests']:
            quests.append(q);ids.append(q['id'])
            assert q['rewards']==[]
            for task in q['tasks']:
                ids.append(task['id']);assert task['type'] in ('item','checkmark')
                if task['type']=='item':assert task['consume_items'] is False
    assert len(ids)==len(set(ids)) and len(quests)==manifest['quests']
    cp=os.pathsep.join([str(STAGE)]+[str(p) for p in sorted(SERVER.glob('libraries/**/*.jar'))]+[str(SERVER/'mods/ftb-library-neoforge-2101.1.36.jar')])
    check=subprocess.run(['/usr/lib/jvm/java-21-openjdk-amd64/bin/java','-Xmx256m','-cp',cp,'ValidateSnbt',str(STAGE/'quests')],cwd=STAGE,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (STAGE/'parser-validation.log').write_text(check.stdout)
    assert check.returncode==0 and 'FTB_SNBT_OK files=10' in check.stdout,check.stdout
    for name,expected in manifest['jars'].items():
        assert hashlib.sha256((SERVER/'mods'/name).read_bytes()).hexdigest()==expected,('mod drift',name)
    live=SERVER/'config/ftbquests/quests';progress=SERVER/'world/ftbquests'
    assert live.resolve().is_relative_to(SERVER) and progress.resolve().is_relative_to(SERVER)
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup=SERVER.parent/'backups'/('ftb-campaign-'+stamp);backup.mkdir(parents=True,exist_ok=False)
    shutil.copytree(live,backup/'quests')
    shutil.copytree(progress,backup/'progress')
    receipt={'backup':str(backup),'old_definitions':hashes(live),'progress_before':hashes(progress),'new_definitions':hashes(STAGE/'quests'),'manifest':manifest}
    new=live.parent/('quests.ccec-stage-'+stamp)
    assert new.resolve().is_relative_to(SERVER)
    shutil.copytree(STAGE/'quests',new)
    # Retire old definitions outside FTB's scanned directory; never remove progress.
    retired=backup/'retired-live-quests'
    os.rename(live,retired)
    try:os.rename(new,live)
    except BaseException:
        os.rename(retired,live);raise
    owner=retired.stat();
    for p in [live,*live.rglob('*')]:os.chown(p,owner.st_uid,owner.st_gid)
    assert hashes(live)==receipt['new_definitions']
    receipt['progress_after_install']=hashes(progress)
    assert receipt['progress_before']==receipt['progress_after_install'],'Progress changed during installation; inspect concurrent save'
    log=SERVER/'logs/latest.log';receipt['log_offset']=log.stat().st_size
    (backup/'receipt.json').write_text(json.dumps(receipt,indent=2))
    (STAGE/'deployment-receipt.json').write_text(json.dumps(receipt,indent=2))
    # 2101.1.36 requires a real player command source. Console and execute-as
    # cannot hot-reload; activate through an authorised restart or in-game editor.
    print(json.dumps({'backup':str(backup),'quests':len(quests),'progress_files':len(receipt['progress_before']),'progress_unchanged':True,'activation_pending':True},indent=2))

if __name__=='__main__':main()
