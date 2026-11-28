import grp
import json
import os
from datetime import date
from pathlib import Path
import subprocess
import sys
from urllib.parse import quote

import requests


REPORT = Path("/run/create-server-work-digest/report.json")
STATE = Path("/var/lib/create-server-work-digest")


def collect():
    data = subprocess.check_output([
        "/usr/sbin/runuser", "-u", "postgres", "--", "/usr/bin/psql",
        "-XAtq", "-v", "ON_ERROR_STOP=1", "-d", "create_server",
        "-f", str(Path(__file__).with_name("daily.sql")),
    ], env={"PATH": "/usr/bin:/bin"}, timeout=45)
    rows = json.loads(data)
    if len(rows) != 4:
        raise ValueError("Missing comparison periods")
    REPORT.parent.mkdir(exist_ok=True, mode=0o750)
    group = grp.getgrnam("createtelegram").gr_gid
    os.chown(REPORT.parent, 0, group)
    REPORT.parent.chmod(0o750)
    temp = REPORT.with_suffix(".new")
    temp.write_bytes(data)
    os.chown(temp, 0, group)
    temp.chmod(0o640)
    temp.replace(REPORT)


def format_report(rows):
    current, *history = rows
    keys = ("players", "joins", "clicks", "failures")
    labels = ("Игроки со входом", "Входы в игру", "Попытки запуска", "Ошибки запуска")
    day = date.fromisoformat(current["day"])
    lines = [f"📊 <b>RE:CREATED · {day:%d.%m.%Y}</b>", "00:00–21:00 · Минск", ""]
    for key, label in zip(keys, labels):
        value = current[key]
        lines.append(f"{label}: <b>{value if value is not None else 'нет данных'}</b>")
    lines.extend(["", "<b>Изменения за тот же отрезок дня</b>", "<pre>Показатель   Вчера Неделя Месяц"])
    for key, short in zip(keys, ("Игроки", "Входы", "Попытки", "Ошибки")):
        changes = [f"{current[key] - row[key]:+d}" if current[key] is not None and row[key] is not None
                   else "н/д" for row in history]
        lines.append(f"{short:<12}{changes[0]:>5} {changes[1]:>6} {changes[2]:>5}")
    lines[-1] += "</pre>"
    lines.append("Даты сравнения: " + " · ".join(date.fromisoformat(r["day"]).strftime("%d.%m") for r in history))
    return "\n".join(lines)


def telegram(method, payload):
    with requests.Session() as session:
        session.trust_env = False
        host = os.getenv("TELEGRAM_PROXY_HOST")
        if host:
            user = os.getenv("TELEGRAM_PROXY_USERNAME")
            auth = (quote(user, safe="") + ":" + quote(os.environ["TELEGRAM_PROXY_PASSWORD"], safe="") + "@") if user else ""
            session.proxies["https"] = f"http://{auth}{host}:{int(os.environ['TELEGRAM_PROXY_PORT'])}"
        response = session.post("https://api.telegram.org/bot" + os.environ["TELEGRAM_BOT_TOKEN"] + "/" + method,
                                json=payload, timeout=(8, 30), allow_redirects=False)
        result = response.json()
        if not result.get("ok"):
            raise ValueError("Telegram rejected request")
        return result["result"]


def send():
    rows = json.loads(REPORT.read_bytes())
    receipt = STATE / date.fromisoformat(rows[0]["day"]).isoformat()
    if receipt.exists():
        if receipt.read_text() == "pending":
            raise ValueError("Previous delivery uncertain")
        return
    chat = int(os.environ["DIGEST_CHAT"])
    topic = int(os.environ["DIGEST_TOPIC"])
    payload = dict(chat_id=chat, message_thread_id=topic, text=format_report(rows), parse_mode="HTML")
    with receipt.open("x") as file:
        file.write("pending")
        file.flush()
        os.fsync(file.fileno())
    sent = telegram("sendMessage", payload)
    if sent["chat"]["id"] != chat or sent.get("message_thread_id") != topic:
        raise ValueError("Unexpected delivery destination")
    receipt.write_text(str(sent["message_id"]))


if __name__ == "__main__":
    try:
        if sys.argv[1] == "collect":
            collect()
        elif sys.argv[1] == "preview":
            print(format_report(json.loads(REPORT.read_bytes())))
        elif sys.argv[1] == "verify":
            chat = telegram("getChat", {"chat_id": int(os.environ["DIGEST_CHAT"])})
            assert chat.get("is_forum") and chat["id"] == int(os.environ["DIGEST_CHAT"])
            print("Forum accessible")
        elif sys.argv[1] == "send":
            send()
        else:
            sys.exit(2)
    except Exception as error:
        # Transport errors may contain the bot token or proxy credentials.
        sys.exit("Digest failed: " + type(error).__name__)
