# Разработка CCEC

Актуализировано: **2026-11-04**. Этот документ относится к Minecraft-моду в корне репозитория. Вложенные backend, launcher и bot имеют собственные сборки: [platform/STATE.md](platform/STATE.md).

## Среда

- JDK 21; версия проверяется командой `java -version`.
- Gradle 9.2.1 через включённые `gradlew` / `gradlew.bat`.
- Интернет для первого разрешения зависимостей. Offline-сборка требует заполненного кэша.
- Закреплённые версии Minecraft/NeoForge/Create/EE: [gradle.properties](gradle.properties), [build.gradle](build.gradle).

Из PowerShell при необходимости задайте кэш в доступном каталоге:

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
.\gradlew.bat build --no-configuration-cache --console=plain
```

В обычной среде достаточно `.\gradlew.bat build`. В macOS/Linux используйте `bash ./gradlew build`. Сборка не требует заранее установленного игрового профиля.

## Compile API и dev runtime

[gradle/dev-mods.gradle](gradle/dev-mods.gradle) разделяет compile API и полные моды:

- compile: Create `6.0.10-280:slim`, Ponder `1.0.82+mc1.21.1`, EE и опциональные API из `libs/`;
- runtime: полный Create, внешний Ponder и EE в `run/mods/`;
- полный Create содержит JarJar-зависимости Flywheel 1.0.6, Registrate MC1.21-1.3.0+67 и Ponder 1.0.82.

`prepareDevMods` скачивает закреплённые runtime-артефакты и копирует EE. Он не удаляет пользовательские файлы и отказывает при конфликтующих версиях Create/Ponder/EE.

```powershell
.\gradlew.bat verifyDevMods
.\gradlew.bat runClient
# Отдельный осознанный запуск сервера разработки:
.\gradlew.bat runServer
```

Задачи `runClient`, `runServer`, `runData` и `runGameTestServer` вызывают подготовку автоматически. Обычные `build`/`check` игру не запускают. Не переносите полные mod JAR в `runtimeClasspath`: существующая схема NeoForge загружает их из `run/mods/`.

## Основная проверка

```powershell
.\gradlew.bat build --offline --no-configuration-cache --console=plain
git diff --check
```

Уберите `--offline`, если зависимости ещё не загружены. `check` включает проверки финансовых реестров и миграций, действующей Cargo-доставки/маршрутов, территории и разрешений, геологии, проектов, профиля/сообщества, HUD, ресурсов и геометрии интерфейсов. Актуальные задачи определяет [build.gradle](build.gradle).

Редакторы рецептов имеют отдельные init scripts и не включены в обычный `check`:

```powershell
.\gradlew.bat checkRecipeWorkbench -I src/main/java/com/example/examplemod/devtools/check.gradle --offline --no-configuration-cache
.\gradlew.bat checkSequenceRecipe -I src/main/java/com/example/examplemod/devtools/check-sequence.gradle --offline --no-configuration-cache
```

[Описание devtools](src/main/java/com/example/examplemod/devtools/README.md). Фактические результаты полного прогона: [аудит](docs/audit-2026-11-04/README.md).

## Граница проверок

Успешный Gradle подтверждает компиляцию и выполненные проверки кода. Native storage regression checks используют настоящий DimensionDataStorage с изолированными файлами: future-format, cache, native async save и поддерживаемый формат. Проверки удалённого Railway в старой квитанции не являются проверкой Cargo. Он не подтверждает OpenGL, GUI Scale, реальный обмен Create Postbox/поезд, поведение Sable/Simulated или сетевую приёмку живого Minecraft.

Для изменений логистики используйте [актуальный Cargo checklist](docs/audit-2026-11-04/README.md). Для остальных доменов проверяйте взаимодействия, роли/отказы, сохранение после перезахода и перезапуска на отдельном тестовом мире. Установленную клиентскую/серверную сборку определяйте по версии и хэшу фактического JAR.

## Сохранения

- Целевая версия CCEC 1.0.2.15: Economy FormatVersion 16, CargoCommerce Version 2, Cargo shipments Version 2; чтение действующих production Economy 15 / Cargo Version 1 должно проверяться миграциями.
- Остальные Cargo SavedData: Version 1. Старые Freight/Railway удалены из действующей базы, их задачи не следует восстанавливать только ради старого checklist.
- `META-INF/ccec-save-formats.json` объявляет диапазоны для preflight release helper; metadata и реальные decoder constants проверяются вместе. Ошибки codec не должны превращаться в пустые счета через native SavedData fallback.
- Geology: формат 4 и generator 2. Переход со старого генератора намеренно пересоздаёт геологию; это отдельное правило, а не обычная миграция остатка залежи.
- Другие реестры и их границы перечислены в [CURRENT_STATE.md](CURRENT_STATE.md).

Не понижайте сборку на новых данных и не регенерируйте мир для исправления проблемы отображения. Сначала сохраните копию и проверьте реальный формат и причину ошибки.

## Git и GitNexus

Основная GitHub-ветка — `main`. Работайте в отдельной `codex/<задача>` ветке, если сессия не требует текущей ветки. Не включайте чужие незавершённые изменения и содержимое `run/` или секреты в коммит.

Следуйте [AGENTS.md](AGENTS.md): graph query/context для связей, upstream impact перед изменением существующих символов, HIGH/CRITICAL предупреждение, проверка UNKNOWN по исходникам и `detect_changes` перед коммитом. Индекс должен соответствовать HEAD и исходникам. Если инкрементальное обновление даёт неполные связи, выполните полное:

```powershell
node .gitnexus/run.cjs analyze --index-only --force
node .gitnexus/run.cjs status --json
node .gitnexus/run.cjs detect-changes --scope all --repo .
```

Если runner ещё не создан, используйте установленный GitNexus либо bootstrap из инструкции AGENTS. Для taint/PDG-аудита требуется индекс с `--pdg`; отсутствие слоя не означает отсутствие проблем.

## Карта документации

- [README](README.md) — вход для GitHub.
- [CURRENT_STATE](CURRENT_STATE.md) — актуальный инвентарь и ограничения.
- [MASTER_CONTEXT](MASTER_CONTEXT.md) — принципы и архитектура.
- [DECISIONS](DECISIONS.md) — решения и актуальные уточнения.
- [GUIDE_RU](GUIDE_RU.md) — действия игрока.
- [Аудит](docs/audit-2026-11-04/README.md) — проверки и evidence.
- Датированные прежние отчёты сохраняют исторические факты и не определяют текущий production.
