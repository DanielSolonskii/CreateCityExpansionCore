# Текущее состояние CCEC


**Выпуск на рабочий сервер временно заблокирован:** текущая source-ветка поддерживает Economy 13, а production — Economy 15/Cargo. Прежний сервер восстановлен, клиентские каналы не переключены. [Причина, восстановление и план совместимости](docs/releases/2026-11-03-compatibility-incident.md).
**Дата среза: 2026-11-03. Версия мода: 1.0.2.14.** Область — этот репозиторий со всеми вложенными проектами. База аудита: `ff4959ec8267291c36dc245357c27fba28cf8216`; изменения логистики и документации включаются в итоговый коммит этого среза.

Это описание реализации и проверок исходников. Текущий production и игровой runtime в рамках этого аудита не подтверждаются. Полные результаты и открытые вопросы: [docs/audit-2026-11-02](docs/audit-2026-11-02/README.md).

## Проекты и версии

| Часть | Текущая основа | Источник |
| --- | --- | --- |
| Мод | Minecraft 1.21.1, NeoForge 21.1.251, JDK 21, Gradle 9.2.1 | [gradle.properties](gradle.properties), [wrapper](gradle/wrapper/gradle-wrapper.properties) |
| Create / EE | 6.0.10-280 / 1.21.1-1.1.2 | [build.gradle](build.gradle), [dev runtime](gradle/dev-mods.gradle) |
| Backend | Java 21 / Spring Boot 4.0.8, версия 0.1.0-SNAPSHOT | [platform/backend](platform/backend/README.md) |
| Launcher | React 19 / TypeScript / Vite / Tauri 2, package 0.1.13 | [platform/launcher](platform/launcher/README.md) |
| Telegram bot | Java 21, версия 0.1.0-SNAPSHOT | [platform/telegram-bot](platform/telegram-bot/README.md) |
| Infra | Примеры развёртывания, отдельные эксплуатационные действия | [platform/infra](platform/infra/README.md) |

Корневой Gradle не включает platform-модули. Их состояние и ограничения: [platform/STATE.md](platform/STATE.md). Соседний самостоятельный CreateServerPlatform исключён по согласованной области работы.

## Реализованные домены мода

| Домен | Что есть в исходниках | Основные точки входа |
| --- | --- | --- |
| Города | Пилон, участники/роли, приглашения, территория, развитие I → II и перенос идентичности | [city](src/main/java/com/example/examplemod/city) |
| Городские проекты | Реестр проектов, зоны, требования и складские/контрактные связи | [projects](src/main/java/com/example/examplemod/projects) |
| Доступ и транспорт | Терминал регистрации, права на блоки и зарегистрированный транспорт Create; опциональная совместимость Sable/Simulated; пульт доступа | [block](src/main/java/com/example/examplemod/block), [mixin](src/main/java/com/example/examplemod/mixin) |
| Геология | Пространственные залежи, общий reserve, локальный potential, анализатор | [geology](src/main/java/com/example/examplemod/geology) |
| Шахта | Валидатор 10×3×10, EE-питание, производственный цикл, физический шлюз | [multiblock/MineMultiblock](src/main/java/com/example/examplemod/multiblock/MineMultiblock.java), [mine](src/main/java/com/example/examplemod/mine) |
| Производство | Материалы, рецепты Create/EE, доменная печь, коксовая батарея, сталь | [recipe data](src/main/resources/data/createcityexpansioncore/recipe), [blast](src/main/java/com/example/examplemod/blast), [coke](src/main/java/com/example/examplemod/coke), [steel](src/main/java/com/example/examplemod/steel) |
| Железная дорога | Станции, парк, линии/расписания, собственность путей, planner, route guard, резервы и расчёт рейса | [railway](src/main/java/com/example/examplemod/railway) |
| Логистика | Прямые рейсы партиями, опубликованный поезд продавца, центры и замороженный маршрут посылки, физическая приёмка | [FreightLogistics](src/main/java/com/example/examplemod/economy/FreightLogistics.java), [руководство](docs/logistics/freight-logistics-v2-ru.md) |
| Отмена груза | Запрет новых коробок, частичные выплаты/возвраты, резерв за груз в пути, физическое восстановление и OP-команды | [FreightCancellation](src/main/java/com/example/examplemod/economy/FreightCancellation.java), [FreightCommands](src/main/java/com/example/examplemod/economy/FreightCommands.java) |
| Экономика и банк | Личные счета, городская казна, договоры, escrow, Master City и procurement; дорожный бюджет отдельно от товара | [economy](src/main/java/com/example/examplemod/economy) |
| Частный рынок | Публикация/принятие, денежный escrow, ручная передача и подтверждение, административный спор | [PrivateListing](src/main/java/com/example/examplemod/economy/PrivateListing.java) |
| WorldMap / HUD | Рельеф и слои, города/территория/рельсы, выбранная доставка, схема остановок и полученная геометрия; миникарта/границы/HUD | [client/map](src/main/java/com/example/examplemod/client/map), [hud](src/main/java/com/example/examplemod/hud) |
| Социальные системы | Профиль, сообщество, отношения, альянсы, onboarding, daily и уведомления | [profile](src/main/java/com/example/examplemod/profile), [community](src/main/java/com/example/examplemod/community), [social](src/main/java/com/example/examplemod/social), [alliance](src/main/java/com/example/examplemod/alliance), [onboarding](src/main/java/com/example/examplemod/onboarding), [daily](src/main/java/com/example/examplemod/daily) |
| Admission/auth | Серверная проверка допуска, game-session/bridge интеграция и телеметрия | [auth](src/main/java/com/example/examplemod/auth) |
| Chunk loader | Квоты, владение и сохранение загрузчиков | [chunkloader](src/main/java/com/example/examplemod/chunkloader) |
| Devtools | Workbench рецептов и последовательная сборка; отдельные проверки | [devtools](src/main/java/com/example/examplemod/devtools/README.md) |

TRUSTED/PARTNER не дают автоматически право менять территорию: для этого есть отдельный opt-in разрешений. Наличие базового API не означает полную игровую приёмку совместимости Aeronautics или всех операций пульта.

## Физические и финансовые правила

Груз проходит по штатным предметам и Create Package. Только проверенная приёмка в конечной точке двигает поставку и расчёт; транзитный центр не является получателем. Один активный source assignment удерживает свою отправочную точку.

Региональный маршрут задаётся упорядоченными центрами (до четырёх) и физическими расписаниями поездов на плечах. Это не автоматическое открытие произвольной сети и не резервирование всех поездов по всей цепочке одной кнопкой.

Отмена сохраняет действующие печати коробок в пути. Резерв освобождается по физическому получению или возврату отправителем; одного тайм-аута для выдачи и предметов, и денег недостаточно. Дорожный лимит — разрешение расходовать существующую казну, а не дополнительный баланс. [Подробности и ограничения](docs/logistics/logistics-audit-2026-11-02-ru.md).

Частные объявления не выполняют атомарную передачу предметов между инвентарями: их текущая модель содержит ручное подтверждение сторон. Её нельзя описывать как автоматическую физическую P2P-доставку.

## Геология, шахта и развитие

Текущий codec геологии — **формат 4**, процедурный generator — **версия 2**. Macro-cell 768×768 служит пространственным индексом; ресурс входит в идентичность генерации. Залежи имеют независимые quality/size, пространственную форму и общий расходуемый запас.

Переход generator 1 → 2 намеренно отбрасывает старую геометрию/идентичности и пересоздаёт геологию для устранения совпадающих залежей разных ресурсов. Это не обещание сохранить каждый прежний deposit/reserve. Неизвестный будущий формат codec переводится в unavailable с сохранением raw-data для записи без уничтожения. [Источник](src/main/java/com/example/examplemod/geology/GeologySavedDataCodec.java).

Шахта занимает 10×3×10: **298 блоков и две сервисные воздушные клетки**. Текущий состав: 151 Mine Frame, 128 Technical Block, 16 Drill Unit, Controller, Power Port и Gateway. Рабочий диапазон питания 500–600 В; номинал 10 кВт, минимум 8,5 кВт, standby 120 Вт. Железо использует Fe-equivalent: 5 единиц резерва → 10 физических предметов сырьевой цепочки.

Для уровня II нужны первичная зарегистрированная шахта, промышленный ресурс и предусмотренные взносы. Лимит проектов: 1 до II уровня, 2 начиная со II. Актуальные условия задаёт [CityDevelopmentRequirements](src/main/java/com/example/examplemod/city/CityDevelopmentRequirements.java).

## Сохранения

| Реестр | Текущий контракт |
| --- | --- |
| Economy/Freight/Bank/Projects | Внешний формат 13; отказ codec удерживает raw NBT и блокирует доступ после native loader, без создания пустых счетов |
| Railway | Формат 5; будущий формат сохраняется как raw NBT, доступ отклоняется после native loader; принудительная запись сохраняет raw-data |
| Geology | Формат 4, generator 2; отдельные правила миграции/перегенерации и сохранение raw-data при unavailable |
| City и часть вспомогательных SavedData | Не имеют единого общего формата; ограничения downgrade-hardening перечислены в аудите |

UUID точек, печатей, контрактов и финансовых операций сохраняются. Прямое редактирование/удаление world SavedData не является способом обновления проекта.

## Проверки и оставшиеся границы

Корневые `build/check` выполняют проверки доменов, ресурсов, миграций и интерфейсной геометрии. Workbench/sequence checks запускаются отдельно с init scripts. Результаты, инвентарь и остаточные риски находятся в [полном аудите](docs/audit-2026-11-02/README.md).

Не подтверждены этим срезом: игровой OpenGL/GUI/network прогон, production VDS и PostgreSQL-интеграция на реальной БД. В launcher snapshot выявлены отсутствующие Rust-модули; состояние сборок platform нельзя выводить из зелёной корневой CI.

Не реализованы как законченные механики: симуляция Create в выгруженном мире, traction/passenger billing, полный диспетчерский прогноз сигналов/расписаний и автоматическое распределение всех поездов многоступенчатой поставки. Полная геометрия выбранной доставки и административное разрешение действительно потерянного груза остаются дальнейшими задачами.

## Навигация

[Игроку](GUIDE_RU.md) · [Разработчику](README_DEV.md) · [Контекст](MASTER_CONTEXT.md) · [Решения](DECISIONS.md) · [Платформа](platform/STATE.md)

Прежние датированные STATE и audit-каталоги сохраняются как история; актуальный вход — этот файл и аудит 2026-11-02.
