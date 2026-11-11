# Текущее состояние CCEC

**Срез: 2026-11-07 (Europe/Minsk).** Мод и игровой клиент опубликованы как **alpha-0.1.38**, Launcher для Windows — **0.1.15**. Версии игрового канала и установщика независимы.

## Итоги работы 6–7 ноября

- Мод: локализация уведомления и публичной карточки городского проекта; компактный клиентский TAB с отдельной колонкой городов, цветом из City.getColorRgb(), сортировкой по UUID города, короткими подсказками клавиш и отступом под полосы боссов. Убраны золотые OP-ромбы; чат использует разделитель «·» и цвет города.
- Логистика: в рабочую базу включены чартерные разрешения/автозапуск, мастер BUY/SEND, экран сети, визуальные карточки и пояснения ошибок. Подробности: [мастер и чартеры](docs/logistics/charter-dispatch-and-wizard-2026-11-06.md), [UX-аудит](docs/logistics/cargo-ux-audit-2026-11-05.md).
- Сервер: штатный deploy alpha-0.1.38 завершился DEPLOYED / ready=true; после старта подтверждён Done, NRestarts=0. Игроки предупреждены через **ccec maintenance announce 60** с ожиданием минуты до остановки. JAR: build/libs/createcityexpansioncore-1.0.2.15-sablefix.1.jar, SHA-256 b4c845220bfddb78c003146dacbd57e49000eb5d711e162cd731d6f805577275.
- Игровой клиент: alpha-0.1.38, sequence 39, публикация PUBLISHED. Обновлён shared JAR и launch.json; серверные SavedData прошли штатный preflight совместимости. macOS-канал в этом сеансе не обновлялся.
- По прямой инструкции пользователя старые архивы мира удалены. На момент завершения deploy оставлен один архив: /opt/create-server/backups/server-deploy-b568b33f478a414ba41d433083d9e528/world.tar. Локальное копирование отменено, частичный файл удалён. Это описывает архивы мира, не служебные backups публикаций/платформы.
- Launcher 0.1.15: актуальная Companion-база с боковой навигацией и отдельным профилем, читаемые шрифты, короткие переходы, компактная подготовка игры, сглаженная скорость, корректный неопределённый индикатор проверки/установки, новости без автоматического открытия. Профиль: локальные цвет, описание, интересы, минимальные анимации и стаж регистрации; город/роль/статистика остаются серверными.

## Важный инцидент: источник лаунчера

Первый installer **0.1.14** был ошибочно собран из устаревшей импортированной platform/launcher, хотя package version совпадала с новым standalone checkout. Пользователь обнаружил старый интерфейс. **0.1.15 исправляет публикацию**: актуальные Companion-исходники восстановлены из соседнего E:/CreateCityExpansionCore_MERGED/CreateServerPlatform/launcher, затем поверх перенесены изменения текущего сеанса. Исправления синхронизированы в platform/launcher этого репозитория; именно эти исходники собраны и опубликованы как 0.1.15. Нельзя выбирать базу по одной строке версии: сверять CompanionProfile, companion.css, launcher-refresh.css и боковую навигацию. Обратного переключения на меньший semver/sequence не было.

Windows NSIS 0.1.15 опубликован APPROVED, sequence 15, обновление необязательное. Размер 5574300 байт; SHA-256 f0bd6abe63f2d139fc7eabbff195a955998186c05d513cd89c32f7cb200ba56c. Подписанный канал и installer: https://api.recreated.ru/launcher/latest/update.json и https://api.recreated.ru/launcher/versions/0.1.15/RE-CREATED-Launcher-x64-setup.exe. Закрытые signing keys не копировались.

## Граница подтверждения

Мод: compileJava / processResources / jar PASS; checkSocial — 340, checkQuietTab — 5062 headless assertions. Launcher: финальные TypeScript/Vite и Rust release/NSIS build PASS, 80 frontend tests PASS. Штатные публикации подтвердили загрузку установщика и его SHA-256. Полный install/upgrade/login/gameplay E2E и визуальная приёмка не выполнены: пользователь попросил не продолжать проверки. Не считать эти сборки доказательством runtime-поведения GUI, поездов или macOS.

Предложенные последующие изменения жизненного цикла городских проектов (следующий шаг, формы замечаний, обзор/этапы/планирование) **не реализованы**: пользователь переключил задачу на deploy и Launcher.

[Platform STATE](platform/STATE.md). Остальные разделы ниже — исторический срез 2026-11-04; их версии и ограничения не переопределяют опубликованные итоги выше.

---

## Исторический срез 2026-11-04

**Срез: 2026-11-04. Целевая версия — 1.0.2.15.** Область — этот репозиторий и все вложенные проекты. Интеграционная версия готовится к выпуску; финальный JAR, проверки и установка требуют отдельной квитанции. [Актуальный аудит](docs/audit-2026-11-04/README.md).

## Источник действующей базы

После синхронизации main старая ветка Freight оказалась новее по Git, но старее установленного Cargo-кода. Подлинные исходники восстановлены из index snapshot `0839bbb1fd43fbb3aca217478f37c5b5f28c73c1` сохранённого stash. Неизменённая восстановленная сборка содержит **751/751 побайтово одинаковых production Java-классов**. Сам stash сохранён.

Старый рабочий сервер восстановлен на JAR 1.0.2.13, SHA-256 `040766463749530001475fd3b75fc24b5e5cbd7ce5127223c60685d32b59e086`, Economy 15. Клиентские каналы остались Windows alpha-0.1.32 и macOS alpha-0.1.32-mac.1. Это проверенная квитанция восстановления, а не утверждение о более позднем deployment. [Инцидент и доказательства](docs/releases/2026-11-03-compatibility-incident.md).

## Проекты

| Часть | Основа | Документация |
| --- | --- | --- |
| Мод | Minecraft 1.21.1, NeoForge 21.1.251, JDK 21, Gradle 9.2.1 | [Разработка](README_DEV.md) |
| Create / EE | 6.0.10-280 / 1.21.1-1.1.2 | [build.gradle](build.gradle), [dev runtime](gradle/dev-mods.gradle) |
| Backend | Spring Boot 4.0.8, Java 21, Flyway V1–V14 | [README](platform/backend/README.md) |
| Launcher | React 19, TypeScript, Vite, Tauri 2; source package 0.1.13 | [README](platform/launcher/README.md) |
| Telegram bot | Java 21, OkHttp 4.12.0, source artifact 0.1.0-SNAPSHOT | [README](platform/telegram-bot/README.md) |
| Infra | Примеры конфигураций и отдельные explicit release helpers | [README](platform/infra/README.md) |

Корневой Gradle не включает `platform/`. Соседний CreateServerPlatform исключён из области. [Platform STATE](platform/STATE.md).

## Домены мода

| Домен | Текущая реализация |
| --- | --- |
| Города и территория | [city](src/main/java/com/example/examplemod/city): пилон, роли, приглашения, участки, развитие, межгородские отношения и отдельные grants |
| Городские проекты | [projects](src/main/java/com/example/examplemod/projects): требования, зоны, финансирование и снабжение |
| Геология | [geology](src/main/java/com/example/examplemod/geology): пространственные залежи, анализатор, расходуемый общий запас |
| Промышленная шахта | [mine](src/main/java/com/example/examplemod/mine): структура, EE-питание и физическая добыча |
| Cargo-оборудование | [cargo](src/main/java/com/example/examplemod/cargo): Router/Ticker, адреса/ParcelId, точки, native Create hooks и квитанции приёмки |
| Центр логистики | [cargo/center](src/main/java/com/example/examplemod/cargo/center): склады, владельцы/ACL, линии, расписания, выбор маршрута, заказы и расчёт |
| Экономика | [EconomyManager](src/main/java/com/example/examplemod/economy/EconomyManager.java): кошельки, казна, банк, проекты и CargoCommerce; старые теги сохраняются непрозрачно |
| Интерфейсы | [client/logistics](src/main/java/com/example/examplemod/client/logistics), [client/map](src/main/java/com/example/examplemod/client/map): центр, карта, состояния и направление маршрута |
| Транспорт и доступ | [block](src/main/java/com/example/examplemod/block), [mixin](src/main/java/com/example/examplemod/mixin): терминал, grants, Create/Sable/Simulated compatibility |
| Профиль/сообщество/первые шаги | Существующие server-authoritative домены и отдельные проверки |

Старые `economy/FreightLogistics`, самостоятельный Railway и металлургические blast/coke/steel мультиблоки **выведены из Cargo production** ранее. Восстановление вернуло именно этот контракт; они не считаются дополнительными действующими системами.

## Контракты логистики

Резерв денег создаётся в существующих счетах EconomyManager. Цена, права, остаток товара и маршрут повторно проверяются сервером. Создание native запроса упаковки фиксируется до обращения к Create: неоднозначная ошибка не разрешает автоматически повторить отправку или вернуть весь резерв.

Каждая физическая коробка имеет Shipment/Parcel identity, проверяемое содержимое, историю плеч и квитанцию конечной приёмки. Перевалка в Cargo Router не оплачивает товар. Выплаты за товар и оплаченные плечи требуют физического доказательства.

Цель интеграции 1.0.2.15 — отмена игроком/администратором на любом этапе, отдельный учёт коробок и физическое изъятие. Возврат неотправленного объёма разрешается только после доказательства, что он не появится позже из native очереди. Обратный маршрут не требуется. Фактическое завершение переноса и проверки фиксируются в актуальном аудите.

WorldMap сохраняет общую карту города и terrain, а действующая логистика отображается через Cargo `LogisticsMapOverlay`. Анимация направления не является GPS-наблюдением каждой коробки. Новые визуальные состояния и выбор оборудования проверяются отдельно от живого GUI.

## Сохранения и обновление

| Данные | Целевой контракт 1.0.2.15 |
| --- | --- |
| Economy | FormatVersion 16; старые поля сохраняются, native loader не должен создавать пустой баланс при отказе codec |
| CargoCommerce | Вложенная Version 2, миграция Version 1; отмена и учёт физического груза |
| Cargo shipments | Version 2, чтение Version 1 |
| Cargo points/trains/receipts/lines/schedules/warehouses/ownership | Version 1 |
| Geology | Codec 4, generator 2; переход generator 1→2 пересоздаёт геометрию/идентичности |
| City и остальные вспомогательные данные | Разные контракты, нет обещания универсальной поддержки произвольного будущего формата |

`META-INF/ccec-save-formats.json` объявляет поддерживаемые диапазоны. Deploy helper читает реальные gzip NBT заголовки до остановки, сверяет кандидат и повторяет проверку после final save. Неподдерживаемый или неизвестный Cargo-реестр блокирует установку. Файловый rollback также не разрешает downgrade данных. Отсутствие metadata в старом JAR требует ручной проверки восстановления.

Шахта: 10×3×10, **298 блоков и две сервисные воздушные клетки**; 151 Mine Frame, 128 Technical Block, 16 Drill Unit, Controller, Power Port, Gateway. Питание 500–600 В, номинал 10 кВт, минимум 8,5 кВт, standby 120 Вт. Геология, добыча и развитие города не заменялись ради исправления логистики.

## Проверки и открытые работы

Восстановленная неизменённая Cargo-база прошла `assemble check`: 62 задачи. Это подтверждение базы и сравнения production классов; результаты окончательной интеграции публикуются отдельно.

Backend: 153 tests PASS; bot: 67 PASS; launcher frontend: 72 tests и TS/Vite build PASS. Native Rust launcher имеет compile blockers: отсутствуют `social_links`, `presence`, `presence_ipc`, `settings::launcher_memory`. Нельзя объявлять installer собираемым по frontend-проверкам. Новые preflight regression checks: 12 PASS; прежние Python release tests — 11 PASS / 8 Linux-only skips на Windows.

Игровой GUI/OpenGL, реальный Create транспорт, отмена/изъятие после restart/unload и PostgreSQL acceptance требуют своего прогона. FTB campaign verifier доказывает структуру исторической книги, а не соответствие каждому предмету нынешнего Cargo JAR. Полное автоматическое распределение флота, offline simulation, пассажирские/traction tariffs и полный прогноз сигналов не заявлены готовыми.

[Игроку](GUIDE_RU.md) · [Разработчику](README_DEV.md) · [Архитектура](MASTER_CONTEXT.md) · [Решения](DECISIONS.md) · [Аудит](docs/audit-2026-11-04/README.md)

## Ограничение текущего выпуска

По запросу владельца дальнейший аудит и доработки остановлены перед публикацией. Подтверждение происхождения коробки от конкретного native запроса Create не завершено: восстановление ранее зарегистрированной коробки нельзя считать полностью защищённым от подмены, а коробка без зарегистрированного Parcel UUID не принимается для восстановления. Неопределённый груз сохраняет резерв. Полная игровая приёмка не выполнялась.
