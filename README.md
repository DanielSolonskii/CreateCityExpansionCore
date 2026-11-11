# Create: City Expansion Core

Индустриальный мод для Minecraft 1.21.1 / NeoForge: города, геология, промышленная шахта, производство Create и физическая доставка грузов.

**Актуализировано 2026-11-04. Целевая версия CCEC — 1.0.2.15.** Выпуск этой версии находится на проверке совместимости; установка и переключение клиентских каналов подтверждаются отдельной квитанцией. [Текущее состояние](CURRENT_STATE.md) · [аудит](docs/audit-2026-11-04/README.md) · [причина и восстановление предыдущего сбоя](docs/releases/2026-11-03-compatibility-incident.md).

## Состав репозитория

**Deploy CCEC на сервер и клиент Windows/macOS:** `./Deploy.ps1` из корня. Проверка без публикации: `./Deploy.ps1 -DryRun`. [Инструкция](platform/docs/quick-deploy.md).

| Проект | Назначение | Документация |
| --- | --- | --- |
| CCEC, `src/` | Minecraft-мод, клиентские экраны и серверная логика | [Состояние](CURRENT_STATE.md), [разработка](README_DEV.md), [игроку](GUIDE_RU.md) |
| `platform/backend/` | Spring Boot API: учётные записи, admission, bridge, launcher/companion | [README](platform/backend/README.md) |
| `platform/launcher/` | React/Vite UI, Rust/Tauri launcher и выпуск игровых профилей | [README](platform/launcher/README.md) |
| `platform/telegram-bot/` | Telegram-бот, community/companion workers | [README](platform/telegram-bot/README.md) |
| `platform/infra/` | Конфигурационные примеры и эксплуатационные инструкции | [README](platform/infra/README.md) |
| `tools/`, `art/` | Утилиты, исходники моделей и исторические пакеты | [Аудит](docs/audit-2026-11-04/README.md) |

Вложенная платформа имеет отдельные сборки: корневой Gradle её не проверяет. Соседний самостоятельный CreateServerPlatform не входит в эту работу.

## Возможности

- Города: пилон, роли, приглашения, территория, развитие и проекты, отношения и отдельные разрешения доступа.
- Геология и промышленная шахта: пространственные месторождения, магнитный анализатор, общий расходуемый запас и питание Electro Energetics.
- Материалы и Create-рецепты, dependency override datapack, редактор рецептов для разработки.
- Действующая **Cargo-логистика**: Cargo Router/Ticker, точки, склады, станции, поезда, линии, расписания Create и конечная физическая приёмка.
- Личные счета, городская казна, банк и CargoCommerce с денежным резервом и подтверждёнными выплатами.
- WorldMap, миникарта/HUD, профиль, сообщество, альянсы и первые шаги.
- Регистрационный терминал и защита транспорта Create, опциональная совместимость Sable/Simulated, пульт доступа.

Старые Freight/Railway и отдельные blast/coke/steel мультиблоки относятся к прежней ветке и исключены из действующей Cargo-базы. Их исторические документы сохраняются с пометками.

## Логистика

Откройте центр командой `/ccec logistics`. Он объединяет регистрацию оборудования, доступ, склады, линии, предложения и отправления. Груз перемещается настоящими коробками Create; промежуточный узел не считается конечной доставкой. [Руководство игрока](GUIDE_RU.md).

Версия 1.0.2.15 объединяет улучшения карты с работающей Cargo-базой и переносит отмену на любом этапе: новые отправки прекращаются, а резерв за физический или ещё не доказанный груз закрывается по приёмке либо изъятию. Автоматического возвратного пути и удаления коробок нет. Финальные проверки функций и остаточные ограничения фиксируются в [актуальном аудите](docs/audit-2026-11-04/README.md).

## Сборка

| Компонент | Версия |
| --- | --- |
| Minecraft / NeoForge | 1.21.1 / 21.1.251 |
| JDK / Gradle Wrapper | 21 / 9.2.1 |
| Create / Electro Energetics | 6.0.10-280 / 1.21.1-1.1.2 |

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

Артефакт: `build/libs/createcityexpansioncore-1.0.2.15.jar`. Для первого разрешения зависимостей нужен интернет; `--offline` требует подготовленного кэша. Полный Create нужен для игрового runtime, slim — для компиляции. [Подготовка dev-среды и проверки](README_DEV.md).

Сохранения: **Economy 16, CargoCommerce 2, Cargo shipments 2** в целевой сборке; остальные Cargo-реестры — 1. Кандидат содержит описание форматов, а release helper сверяет его с настоящим миром до остановки/установки. Номер версии JAR сам по себе не доказывает совместимость.

## Проверки и ограничения

[GitHub Actions Build](.github/workflows/build.yml) выполняет корневой `./gradlew build`. [Актуальный аудит](docs/audit-2026-11-04/README.md) отделяет восстановленную базу, интеграционную сборку и production. Headless проверки не подтверждают OpenGL, игровой обмен Create, физическое восстановление коробок или GUI на конкретном клиенте.

Вложенный native launcher имеет известные отсутствующие Rust-модули; зелёный frontend не означает готовый installer. [Состояние платформы](platform/STATE.md).

## Лицензирование и вклад

Метаданные CCEC: **All Rights Reserved**. [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) относится к исходному NeoForged MDK-шаблону; сторонние моды и ресурсы имеют собственные условия.

Основная ветка GitHub — `main`. Изменения выполняются в `codex/<задача>` с сохранением чужой работы. Анализ связей перед изменением и коммитом: [AGENTS.md](AGENTS.md). [Архитектура](MASTER_CONTEXT.md) · [Решения](DECISIONS.md).
