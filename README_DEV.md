# README_DEV.md

Настройка окружения для разработки.

## Обязательные runtime-моды

Create 6.0.10 требует два своих runtime-мода. Без них сервер не стартует:

```
Missing or unsupported mandatory dependencies:
	Mod ID: 'ponder', Requested by: 'create'
	Mod ID: 'flywheel', Requested by: 'create'
```

Runtime-моды должны лежать в `run/mods/`, отдельно от dev classpath. Теперь
Gradle скачивает и подготавливает их автоматически — копировать JAR вручную не нужно.

## После клонирования GitHub

Нужны JDK 21 и интернет. Укажите `JAVA_HOME` на JDK 21, если он указывает на другую Java.
Gradle устанавливать отдельно не требуется: wrapper включён в репозиторий.

Windows PowerShell:

```powershell
.\gradlew.bat build verifyDevMods
.\gradlew.bat runClient
```

macOS/Linux:

```sh
bash ./gradlew build verifyDevMods
bash ./gradlew runClient
```

`build` больше не зависит от заранее существующего `run/mods/ponder-*.jar`:
compile-only Ponder загружается с Maven Create. `prepareDevMods` скачивает полные
Create/Ponder в отдельной конфигурации `devRuntimeMods` и копирует их вместе с
отслеживаемым Git `libs/electroenergetics-1.21.1-1.1.2.jar` в `run/mods/`.
`runClient`, `runServer`, `runData` и `runGameTestServer` вызывают подготовку автоматически.
`verifyDevMods` проверяет JAR и вложенные зависимости, не запуская Minecraft.

Задача не удаляет другие моды, конфиги, мир или пользовательские файлы. Если найдена
другая версия Create/Ponder/EE в `run/mods/`, она останавливается с понятной ошибкой:
сначала самостоятельно переместите конфликтующий JAR в backup. На production задача
ничего не устанавливает. Сборка не является деплоем.

### Почему runtime-моды не входят в runtimeClasspath

Для этого проекта полные mod JAR оставлены вне dev classpath (`localRuntime` /
`implementation`): предыдущая такая конфигурация вызывала ошибку NeoForge:

```
Caused by: java.lang.NoClassDefFoundError: com/tterrag/registrate/AbstractRegistrate
```

Поэтому Gradle разрешает compile API и полные runtime-моды отдельно. Сохраняется
проверенная проектом схема module layer; переход на другую схему не выполняется.

### Состав run/mods

| Файл | Источник |
|---|---|
| `create-1.21.1-6.0.10.jar` | Maven `com.simibubi.create:create-1.21.1:6.0.10-280`, полный JAR |
| `electroenergetics-1.21.1-1.1.2.jar` | `libs/electroenergetics-1.21.1-1.1.2.jar` |
| `ponder-neoforge-1.0.82+mc1.21.1.jar` | Maven `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1` |

Flywheel **1.0.6**, Registrate **MC1.21-1.3.0+67** и Ponder **1.0.82** также присутствуют
в `META-INF/jarjar/` полного Create JAR. Отдельный Flywheel скачивать не требуется;
внешний Ponder сохраняется согласно существующей dev-схеме проекта. Новые сторонние
JAR в Git не добавляются; версии закреплены в `gradle/dev-mods.gradle`.

Полный Create JAR автоматически разрешается Gradle по координатам:

```
com.simibubi.create:create-1.21.1:6.0.10-280
```

Артефакт `slim` (17 452 009 байт) для run/mods не подходит — нужен полный
(19 123 767 байт).

## Сборка

```
gradlew compileJava
gradlew check
gradlew build
git diff --check
```

`check` запускает не только стандартные тесты, но и headless contracts для
Freight, Railway ownership/accounting, route guard, World Map/layout,
City Pylon, relations, train management, Geology v3 и Magnetic Analyzer.
Актуальный перечень задач см. в `build.gradle`.

### Правило runtime-проверки

Успешный `compileJava/check/build` подтверждает компиляцию, persistence contracts
и чистые layout/domain invariants, но не подтверждает Minecraft interaction/render.
Ручной runtime checklist выполняется отдельно владельцем проекта.

Особенно после изменений геологии проверять:

1. Analyzer: ПКМ в воздухе открывает survey.
2. Analyzer: ПКМ по блоку использует тот же scan path.
3. Первый scan проходит без ложного cooldown; повторный ограничен 60 ticks.
4. Mine использует локальный potential и общий reserve после restart.
5. Bulk iron выдаёт ×2 physical output без ×2 расхода reserve.

После Freight/Railway изменений проверять физический путь Package, station/train
eligibility, route selection, reserve/settlement и восстановление после restart.

## Проверка рецептов

Отдельный скрипт-валидатор проверяет JSON рецептов без запуска игры:

- соответствие ширины строк паттерна
- использование всех объявленных символов key
- определение всех используемых символов
- известные namespace

Запуск сервера (`gradlew runServer`) ловит оставшиеся проблемы:
`Parsing error loading recipe <id>` в логе означает некорректный рецепт.

Ожидаемое состояние при старте:

```
[RECIPES] Override datapack enabled: createcityexpansioncore:datapacks/dependency_recipe_overrides (position TOP, always active)
Loaded 2978 recipes
```

и НОЛЬ строк `Parsing error loading recipe`.

## Канонические документы

- `MASTER_CONTEXT.md` — цели, принципы и архитектурные границы.
- `CURRENT_STATE.md` — фактически реализованные системы и ограничения.
- `DECISIONS.md` — принятые решения и причины.

Локальный MERGED worktree является source of truth. Старые chat summaries и
GitHub-ветка не должны использоваться вместо сверки с текущим кодом.
