# Create: City Expansion Core

Индустриальный мод для Minecraft 1.21.1 / NeoForge: города, геология, промышленная шахта, производство Create и физическая доставка грузов.

Версия исходников задаётся свойством `mod_version` в [gradle.properties](gradle.properties). Сведения о публикациях привязаны к дате и квитанции конкретного выпуска. [Аудит кода от 18 ноября 2026](docs/audit-2026-11-18/README.md) · [Срез развёртывания от 7 ноября](CURRENT_STATE.md) · [Восстановление после инцидента с сохранениями](docs/releases/2026-11-03-compatibility-incident.md).

## Состав репозитория

**Deploy CCEC на сервер и клиент Windows/macOS:** `./Deploy.ps1` из корня. Проверка без публикации: `./Deploy.ps1 -DryRun`. [Инструкция](platform/docs/quick-deploy.md).

| Проект | Назначение | Документация |
| --- | --- | --- |
| CCEC, `src/` | Minecraft-мод, клиентские экраны и серверная логика | [Состояние](CURRENT_STATE.md), [разработка](README_DEV.md), [игроку](GUIDE_RU.md) |
| `platform/backend/` | Spring Boot API: учётные записи, admission, bridge, launcher/companion | [README](platform/backend/README.md) |
| `platform/launcher/` | React/Vite UI, Rust/Tauri launcher и выпуск игровых профилей | [README](platform/launcher/README.md) |
| `platform/telegram-bot/` | Telegram-бот, community/companion workers | [README](platform/telegram-bot/README.md) |
| `platform/infra/` | Конфигурационные примеры и эксплуатационные инструкции | [README](platform/infra/README.md) |
| `tools/`, `art/` | Утилиты, исходники моделей и исторические пакеты | [Аудит ресурсов](docs/audit-2026-11-18/README.md) |
| `server-datapacks/` | Четыре отдельно устанавливаемых набора рецептов для Electro Energetics, Create Propulsion, TFMG и Create Big Cannons | [Установка и зависимости](server-datapacks/README.md) |

Вложенная платформа имеет отдельные сборки: корневой Gradle её не проверяет. Соседний самостоятельный CreateServerPlatform не входит в эту работу.

## Возможности

- Города: пилон, роли, приглашения, территория, развитие и проекты, отношения и отдельные разрешения доступа.
- Магнитный анализатор показывает неистощимые жилы, а добыча промышленной шахты зависит от местного потенциала и питания Electro Energetics. Новые ячейки геологии определяются seed мира и измерением, уже разведанные читаются из сохранения.
- Материалы и Create-рецепты, dependency override datapack, редактор рецептов для разработки.
- Действующая **Cargo-логистика**: Cargo Router/Ticker, точки, склады, станции, поезда, линии, расписания Create и конечная физическая приёмка.
- Личные счета, городская казна, банк и CargoCommerce с денежным резервом и подтверждёнными выплатами.
- WorldMap, миникарта/HUD, профиль, сообщество, альянсы и первые шаги.
- Регистрационный терминал и защита транспорта Create, опциональная совместимость Sable/Simulated, пульт доступа.
- Промышленные машины и чертежи, загрузчик чанков с учётом владельца, DC-мост между TFMG и Electro Energetics. [Настройка питания шахты](server-datapacks/README.md#powering-a-tier-i-mine).
- Компании, публичный флот, тарифы перевозчиков, сборы за использование путей и торговля главного города.
- База знаний загружает статьи и иллюстрации с игрового сервера. Администратор публикует новую редакцию командой `/ccec knowledge reload`. [Формат статей и синхронизация](docs/knowledge-sync.md).

Старые Freight/Railway и отдельные blast/coke/steel мультиблоки относятся к прежней ветке и исключены из действующей Cargo-базы. Их исторические документы сохраняются с пометками.

## Логистика

Откройте центр командой `/ccec logistics`. Он объединяет регистрацию оборудования, доступ, склады, линии, предложения и отправления. Груз перемещается настоящими коробками Create; промежуточный узел не считается конечной доставкой. [Руководство игрока](GUIDE_RU.md).

При отмене до первой попытки отправки резерв возвращается плательщику. Если Create уже получил запрос и результат отправки неизвестен, окончательный расчёт ждёт подтверждения приёмки или изъятия физических коробок. [Чартеры и мастер отправки](docs/logistics/charter-dispatch-and-wizard-2026-11-06.md).

## Сборка

| Компонент | Версия |
| --- | --- |
| Minecraft / NeoForge | 1.21.1 / 21.1.251 |
| JDK / Gradle Wrapper | 21 / 9.2.1 |
| Create / Electro Energetics | 6.0.10-280 / 1.21.1-1.1.2 |
| Create: The Factory Must Grow (TFMG) | 1.2.0, обязателен на сервере и клиенте |

Windows PowerShell:

```powershell
git clone https://github.com/DanielSolonskii/CreateCityExpansionCore.git
cd CreateCityExpansionCore
.\gradlew.bat build
```

macOS/Linux:

```sh
git clone https://github.com/DanielSolonskii/CreateCityExpansionCore.git
cd CreateCityExpansionCore
bash ./gradlew build
```

Обычная сборка создаёт `build/libs/createcityexpansioncore-<mod_version>.jar`. Для исходников 1.0.2.18 это `createcityexpansioncore-1.0.2.18.jar`. Первое разрешение зависимостей требует доступа к сети. Для игры используется полный Create, для компиляции — slim. [Подготовка среды разработки](README_DEV.md).

При переходе на сборку с исправленной синхронизацией жителей обновите мод на сервере и клиентах вместе. Пакет списка жителей теперь включает измерение пилона.

Версии Economy, CargoCommerce, отправлений, владельцев и публичного флота перечислены в манифесте совместимости внутри JAR. Процедура релиза сверяет заголовки файлов мира с диапазонами конкретной сборки перед установкой и при откате. [Манифест форматов](src/main/resources/META-INF/ccec-save-formats.json).

## Проверки и ограничения

GitHub Actions выполняет корневой `./gradlew build`. Задача `check` запускает предметные проверки мода и анализ состава JAR. Для проверки синхронизации базы знаний используйте `./gradlew -I tools/knowledge-check.init.gradle checkKnowledgeSync`. [Конфигурация CI](.github/workflows/build.yml).

Команда `./gradlew -I tools/auger-gametest.init.gradle runGameTestServer` запускает отдельный игровой сервер для проверки промышленной шахты и ежедневных наград. Gradle копирует в тестовый каталог проверенные JAR Create, Ponder, Electro Energetics и Create: The Factory Must Grow. Для TFMG 1.2.0 подготовка использует локальный `../tfmg-1.2.0.jar`, если он есть, либо получает закреплённый выпуск из CurseMaven. Путь к другой локальной копии задаётся параметром `-PtfmgJar=/путь/к/tfmg-1.2.0.jar`.

Backend и Telegram-бот собираются своими Gradle Wrapper. В `platform/launcher` команды `npm ci`, `npm test` и `npm run build` проверяют интерфейс. В `src-tauri` команда `cargo test --locked` проверяет Rust-часть на поддерживаемой платформе. Установщик выпускается отдельно. [Сборка лаунчера](platform/launcher/README.md) · [История публикаций](platform/STATE.md).

## Лицензирование и вклад

Метаданные CCEC: **All Rights Reserved**. [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) относится к исходному NeoForged MDK-шаблону; сторонние моды и ресурсы имеют собственные условия.

Основная ветка GitHub — `main`. Изменения выполняются в `codex/<задача>` с сохранением чужой работы. Анализ связей перед изменением и коммитом: [AGENTS.md](AGENTS.md). [Архитектура](MASTER_CONTEXT.md) · [Решения](DECISIONS.md).
