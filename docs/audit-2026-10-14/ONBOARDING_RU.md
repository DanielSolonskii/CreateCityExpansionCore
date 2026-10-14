# Подключение сотрудников: Windows + Codex + Git + SSH

Актуально на 14.10.2026. Сначала прочитайте [State](STATE_RU.md). Личные аккаунты/ключи пока будут добавлены позже по решению владельца.

## 1. Получить проект

Нужны Windows, Git, Java JDK 21. Для launcher — Node.js/npm и Rust; native Tauri build дополнительно требует Microsoft C++ Build Tools и WebView2. Для Game Design/UI анализа можно сначала работать с исходниками без native-сборки.

PowerShell:

```powershell
git clone --branch codex/next-ai-work https://github.com/DanielSolonskii/CreateCityExpansionCore.git C:\Dev\CCEC
Set-Location C:\Dev\CCEC
git status --short
git rev-parse HEAD
git switch -c codex/my-task-name
```

Рабочая ветка `codex/next-ai-work` содержит актуальный срез; `main` может отличаться. Каждый сотрудник использует свой clone и свою task branch. Не работать вдвоём в общей рабочей папке владельца.

Структура:

```text
src/main/java/com/example/examplemod/    CCEC Java; client/, city/, economy/, mine/, railway/...
src/main/resources/                    assets, localization, recipes, datapack overrides
src/mineVisualTest/                     headless contract/layout checks
platform/backend/                      Spring Boot API, PostgreSQL, Flyway
platform/telegram-bot/                  Telegram bot
platform/launcher/                      React/TypeScript + Tauri/Rust
platform/infra/                        Caddy/systemd/deployment templates
tools/ftb-campaign/                     RU/EN campaign generator/validator
docs/audit-2026-10-14/                  current State, чеклист и гайды
```

## 2. Открыть в Codex

Установить Codex/ChatGPT desktop из официального источника, войти в личный аккаунт и выбрать `C:\Dev\CCEC` как локальный проект. Начать отдельный чат под свою задачу и вставить стартовый промт из ROLE_PROMPTS_RU.md.

Локальный проект даёт Codex доступ к файлам и терминальным командам в рамках разрешений. Доступ к аккаунтам GitHub/Linear и к серверу настраивается отдельно. История чатов владельца на новый компьютер не переносится автоматически: контекст должен быть в Git/docs и задаче Linear.

Официальный quickstart: https://learn.chatgpt.com/docs/quickstart
Windows sandbox: https://learn.chatgpt.com/docs/windows/windows-sandbox

## 3. Подготовить dev dependencies и проверить

```powershell
java -version
git --version
node --version
npm --version
```

**CCEC:** сначала обеспечить EE JAR в `libs/` и Ponder JAR в `run/mods/` по README_DEV.md. Для runClient/runServer также нужны full Create, Ponder, Flywheel, EE и согласованный клиентский modpack. `run/` не хранится в Git; брать нужные версии из подтверждённой сборки/официального источника, не случайные slim JAR.

```powershell
Set-Location C:\Dev\CCEC
.\gradlew.bat check build --console=plain
# Только подготовленная локальная dev-среда и тестовый мир:
.\gradlew.bat runClient
```

**Backend / bot:**

```powershell
Set-Location C:\Dev\CCEC\platform\backend
.\gradlew.bat test bootJar
Set-Location C:\Dev\CCEC\platform\telegram-bot
.\gradlew.bat test installDist
```

H2/fake HTTP tests не требуют production credentials. PostgreSQL integration tests — отдельная выделенная тестовая БД; не подключать их к рабочей базе. `.env.example` — справочник, не готовые секреты и не обещание автоматической загрузки env.

**Launcher:**

```powershell
Set-Location C:\Dev\CCEC\platform\launcher
npm ci
npm test
npm run build
npm run test:release
npm run dev
# Native mode после установки Windows build prerequisites:
npm run tauri dev
Set-Location src-tauri
cargo test --locked
```

Browser preview показывает UI, но не проверяет native TCP status, Credential Manager и Play. Production endpoint/build environment смотрите в platform/launcher/STATUS.md и platform/docs; не подставлять production secrets в frontend/VITE env.

## 4. Настроить SSH

SSH вход и platform game account — разные доступы. Для первого просмотра сервера не нужен Minecraft account; для входа в игру нужен launcher/verified platform account.

Проверка OpenSSH в PowerShell:

```powershell
ssh -V
ssh-keygen -t ed25519 -C "ccec-your-name"
Get-Content "$env:USERPROFILE\.ssh\id_ed25519.pub"
```

ssh-keygen интерактивно выбирает имя файла: не соглашаться на перезапись существующего ключа. Установить passphrase по своему решению. Владельцу передать **только .pub**. Приватный файл `id_ed25519` остаётся на личном компьютере. После выдачи личного server username:

```powershell
ssh -i "$env:USERPROFILE\.ssh\id_ed25519" your-server-user@186.246.28.214
```

При первом соединении проверить host fingerprint с владельцем через доверенный канал. Не использовать StrictHostKeyChecking=no. Известная рабочая команда владельца:

```powershell
ssh root@186.246.28.214
```

Она сработает у сотрудника только после отдельного разрешённого доступа. Общий root private key между сотрудниками не распространяется. Правила sudo/доступа к журналам согласовать с владельцем.

При проблемах:

```powershell
Test-NetConnection 186.246.28.214 -Port 22
ssh -v your-server-user@186.246.28.214
```

`Permission denied (publickey)` обычно требует проверки username/авторизованного ключа; network timeout — отдельная проблема. Перед передачей verbose output удалить usernames/локальные пути по необходимости и не передавать секреты.

## 5. Безопасный первый read-only осмотр сервера

После SSH вы уже в Linux shell, поэтому используйте Linux команды:

```bash
date -u
systemctl is-active create-server-minecraft create-server-backend create-server-telegram-bot create-server-game-tls
curl -fsS http://127.0.0.1:8080/api/health
curl -fsS https://api.recreated.ru/api/health
sha256sum /opt/create-server/minecraft/mods/createcityexpansioncore-1.0.1.jar
```

Пути: Minecraft `/opt/create-server/minecraft`; backend `/opt/create-server/backend`; bot `/opt/create-server/telegram-bot`; release distribution `/opt/create-server/distribution`; launcher distribution `/opt/create-server/launcher-distribution`.

Логи читаются узко под конкретную проблему; перед передачей в чат очистить credentials, user identifiers и private message content. Не выполнять общий `cat /opt/create-server/secrets/*`, `systemctl show ... -p Environment` и не копировать БД/мир в публичный GitHub.

Этот гайд не даёт автоматического разрешения перезапускать/обновлять production. Разработку и acceptance выполнять на локальном/выделенном тестовом окружении; release только по отдельному указанию владельца.

## 6. Подключить Linear и передать работу

Workspace: https://linear.app/re-creted
Project: https://linear.app/re-creted/project/ccec-state-klient-server-i-mod-3942ce25a40b

После приглашения войти в личный Linear account. В Codex подключить Linear и выбрать именно RE:CRETED при OAuth. Проверить через MCP workspace/teams; наличие старого createservermod connection не даёт доступа к новому workspace. Не отправлять API tokens в чат.

Использовать роли Game Design / UI/UX / Backend / Ops / Engineering. `[Код]` Done означает зафиксированную реализованную функцию; `[Приёмка]` Todo означает ещё не пройденный этап. Проверить актуальный статус перед началом.

## 7. Сделать изменение и отдать на review

До правки named symbol: GitNexus impact upstream, назвать callers/processes/risk. UNKNOWN не считать разрешением; подтвердить source/registration. Если `.gitnexus/run.cjs` ещё нет, установить GitNexus/создать индекс по AGENTS.md; generated index не коммитить.

```powershell
Set-Location C:\Dev\CCEC
git status --short
# Если индекс уже создан:
node .gitnexus/run.cjs impact "SymbolName" --direction upstream --repo .
# После правок и нужных tests:
node .gitnexus/run.cjs detect-changes --scope all --repo .
git diff --check
git diff
git add -- path/to/changed-file
git diff --cached
git commit -m "Describe the change"
git push -u origin HEAD
```

Для push нужен GitHub collaborator write или fork+PR; публичность repo даёт только чтение. PR направлять в `codex/next-ai-work`, пока владелец не выберет другую базовую ветку. В описании: проблема/поведение, изменённые файлы, tests, что осталось проверить в игре. Не коммитить весь рабочий каталог командой broad add без просмотра.
