# Готовые промты для Codex

## Первый запуск — общий контекст

```text
Работаем в моём локальном клоне CreateCityExpansionCore, ветка задачи codex/<имя>.
Сначала прочитай AGENTS.md, docs/audit-2026-10-14/STATE_RU.md,
CHECKLIST_RU.md и ONBOARDING_RU.md. Покажи cwd, git branch/status/HEAD.
Актуальная внешняя платформа находится в platform/; старые tools/*-platform —
исторические рабочие копии. Сверяй документы с фактическим кодом.

Перед правкой symbol выполни GitNexus impact upstream, назови callers/processes
и риск. Для concepts/flows сначала GitNexus query/context. UNKNOWN/пустой граф
не доказывает отсутствие использования. Перед commit — detect_changes(all).
Не менять чужие изменения, не reset/clean/broad stage.
Code/headless tests и runtime Minecraft/Windows UI acceptance отмечать отдельно.

Production SSH: <личный-username>@186.246.28.214, ключ уже настроен на моём ПК.
На сервере работать только read-only в рамках задачи. Не читать/печатать
secrets/env/credentials/личные сообщения. Deploy, restart, publish, DB/world
migration и выдача доступов только по отдельному указанию владельца.
Задача: <ссылка Linear и точный ожидаемый результат>.
```

## Game Design — первые задачи

```text
Моя роль — Game Design проекта RE:CRETED/CCEC.
Разбери stage M2/M3/M4/M6 из текущего каталога. Подготовь:
1) путь новичка от first iron/Create к geology/city/mine;
2) таблицу BOM, output/time, energy и reserve cost по актуальным source/JSON;
3) progression city level отдельно от item tier;
4) назначение CR, demand/saturation и физический freight/railway loop;
5) 10 приоритетных вопросов баланса и измеримые playtest-сценарии.

Каждый факт привяжи к файлу/строке. Не выдумывай рецепты, механики или gates.
Учти geology format 4/generator 2 и reset старых reserves; ничего не мигрируй.
create:electron_tube не требует brass; optional quests не блокируют основной путь.
Пока не менять код: результат — таблица баланса, гипотезы и проект конкретных
задач Linear с критериями принятия.
```

## UI/UX — первые задачи

```text
Моя роль — UI/UX и внешний вид RE:CRETED/CCEC.
Составь карту текущих экранов по client/screen, client/map, resources/assets
и platform/launcher/src. Сопоставь с моими игровыми screenshots.
Разделяй наблюдаемое поведение, доказанный source и предложение.

Проверяй WorldMap/Pylon/Market/Freight/Projects/Profile/Analyzer/Launcher
на min/default/max размерах, RU/EN, разных ролях и empty/loading/error/stale
состояниях. Single click market/listing/contract row = SELECT ONLY;
действия явные, подписанные и с подтверждением, сервер остаётся авторитетным.

Дай приоритетный список проблем с шагами воспроизведения, конкретными
макетами до/после и acceptance. Нативные новые Minecraft textures 32x32;
согласованные формы/палитру сохранять. UI screenshots/headless layouts
не объявлять runtime acceptance. Пока не менять gameplay/network contracts.
```

## Одна ограниченная реализация

```text
Возьми Linear <REC-...>. Сверь актуальный description, исходники и git status.
Сначала GitNexus query/context и обязательный impact upstream для symbols.
Если HIGH/CRITICAL — предупреди о реальных затронутых callers/processes.
Внеси минимальное изменение только в scope задачи. Запусти подходящие проверки.
Перед commit выполни detect_changes(all) и diff --check, просмотри staged diff.
Commit/push/PR выполняй, если они явно входят в эту задачу.
В конце: что изменено, tests, runtime acceptance и остаточные риски. Production
не трогать без отдельного разрешения.
```

## Read-only SSH диагностика

```text
Подключись SSH как <личный-username>@186.246.28.214.
Нужно проверить только <точный симптом/служба>. Найди рабочий путь службы,
ActiveState/SubState, health и относящиеся к проблеме safe log lines.
Не печатай Environment, secrets, DB rows, tickets, tokens, phone numbers
и private Telegram contents. Не выполнять restart, deploy или запись.
Отдели подтверждённое состояние от предположений и предложи конкретный тест.
```

## Дизайн-решение перед изменением

```text
Предложение: <изменение>. Сначала покажи текущий flow с source evidence,
пользовательскую проблему, 2–3 варианта и tradeoffs. Проверь impact на
server authority, economy ledgers, physical logistics, saved-data compatibility,
privacy и performance. Дай конкретный reviewable mockup/spec и acceptance.
Код/production пока не менять.
```
