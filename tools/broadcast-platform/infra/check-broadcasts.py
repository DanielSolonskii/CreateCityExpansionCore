"""Read-only smoke checks: no announcement, registration or player mutation."""
import json,os,pathlib,pwd,socket,urllib.request,urllib.error
root=pathlib.Path('/opt/create-server')
config=dict(line.split('=',1) for line in (root/'secrets/telegram-bot.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
user=pwd.getpwnam('createtelegram');os.setgroups([]);os.setgid(user.pw_gid);os.setuid(user.pw_uid)
original=socket.getaddrinfo
socket.getaddrinfo=lambda host,port,family=0,type=0,proto=0,flags=0:original(host,port,socket.AF_INET,type,proto,flags)
try:
    me=json.load(urllib.request.urlopen('https://api.telegram.org/bot'+config['TELEGRAM_BOT_TOKEN']+'/getMe',timeout=20))
    assert me['ok'];print('Telegram getMe OK @'+me['result']['username'],flush=True)
    base=config.get('BACKEND_URL','http://127.0.0.1:8080').rstrip('/');assert base=='http://127.0.0.1:8080'
    admin=int(config['REPORT_ADMIN_TELEGRAM_IDS'].split(',')[0])
    def get(path):return json.load(urllib.request.urlopen(urllib.request.Request(base+path,headers={'X-Bot-Service-Secret':config['BOT_BACKEND_SECRET']}),timeout=5))
    rows=get('/api/internal/broadcasts?telegramUserId='+str(admin));assert isinstance(rows,list)
    assert get('/api/health')['status']=='UP'
    assert get('/api/internal/telegram/users/'+str(admin))['status']=='ACTIVE'
    print('Bot -> Backend direct localhost OK; broadcasts API authorized; linked admin ACTIVE',flush=True)
    try:get('/api/internal/broadcasts?telegramUserId=1');raise AssertionError('Non-admin accepted')
    except urllib.error.HTTPError as e:assert e.code==403
    print('Non-admin denied 403; no player broadcasts sent during smoke check',flush=True)
except Exception:
    print('SMOKE CHECK FAILED; credential-bearing details suppressed',flush=True);raise SystemExit(1)
