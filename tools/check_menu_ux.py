"""Read-only production checks, executed using the Bot's selective WireGuard UID."""
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
    def get(path):
        request=urllib.request.Request(base+path,headers={'X-Bot-Service-Secret':config['BOT_BACKEND_SECRET']})
        return json.load(urllib.request.urlopen(request,timeout=5))
    status=get('/api/internal/bridge/status');print('HOME STATUS',status['state'],status['playerCount'],'/',status['maxPlayers'],flush=True)
    admin=get('/api/internal/bridge/admin?telegramUserId=652868839')
    print('ADMIN STATS events24h',admin['events24h'],'sessions',admin['sessions'],'playtimeSeconds',admin['playtimeSeconds'],flush=True)
    print('ADMIN PLAYERS',len(admin['players']),flush=True)
    summary=get('/api/internal/reports/summary?telegramUserId=652868839');print('OPEN REPORTS',summary['openReports'],flush=True)
    account=get('/api/internal/telegram/users/652868839');assert account['status']=='ACTIVE';print('Existing linked account ACTIVE',flush=True)
    for path in ['/api/internal/bridge/admin?telegramUserId=1','/api/internal/reports/summary?telegramUserId=1']:
        try:get(path);raise AssertionError('Non-admin accepted')
        except urllib.error.HTTPError as error:assert error.code==403
    print('Bot -> Backend direct localhost OK; non-admin denied 403; Launcher URL configured',bool(config.get('LAUNCHER_DOWNLOAD_URL')),flush=True)
except Exception:
    print('CHECK FAILED; credential-bearing error details suppressed',flush=True);raise SystemExit(1)
