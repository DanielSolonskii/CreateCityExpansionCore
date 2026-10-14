import json,pathlib,subprocess
env={}
for line in pathlib.Path('/opt/create-server/secrets/telegram-bot.env').read_text().splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        k,v=line.split('=',1);env[k]=v.strip().strip('\"').strip("'")
token=env['TELEGRAM_BOT_TOKEN']
config='url = "https://api.telegram.org/bot'+token+'/getMe"\nmax-time = 20\nsilent\n'
r=subprocess.run(['runuser','-u','createtelegram','--','curl','--config','-'],input=config,text=True,capture_output=True)
print('getMe transport exit:',r.returncode)
try:
    data=json.loads(r.stdout);print('getMe ok:',data.get('ok'),'error_code:',data.get('error_code'))
except Exception:print('getMe response unavailable')
r=subprocess.run(['runuser','-u','createtelegram','--','curl','--max-time','5','-s','http://127.0.0.1:8080/api/health'],text=True,capture_output=True)
try: print('Bot UID local Backend health:',json.loads(r.stdout).get('status'))
except Exception: print('Bot UID Backend health unavailable')
