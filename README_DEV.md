# README_DEV.md

Настройка окружения для разработки.

## Обязательные runtime-моды

Create 6.0.10 требует два своих runtime-мода. Без них сервер не стартует:

```
Missing or unsupported mandatory dependencies:
	Mod ID: 'ponder', Requested by: 'create'
	Mod ID: 'flywheel', Requested by: 'create'
```

Они должны лежать в `run/mods/`, а не подключаться как Gradle-зависимости.

### Почему не через Gradle dependencies

Добавление jar-мода в dev classpath (`localRuntime` / `implementation`) ломает
запуск NeoForge:

```
Caused by: java.lang.NoClassDefFoundError: com/tterrag/registrate/AbstractRegistrate
```

Create, Ponder и Catnip используют Registrate. Когда jar мода попадает в
classpath вместо mods-папки, module layer NeoForge собирается некорректно.

### Состав run/mods

| Файл | Источник |
|---|---|
| `create-1.21.1-6.0.10.jar` | полный (не slim) jar Create |
| `electroenergetics-1.21.1-1.1.2.jar` | `libs/electroenergetics-1.21.1-1.1.2.jar` |
| `ponder-neoforge-1.0.82+mc1.21.1.jar` | `https://maven.createmod.net/net/createmod/ponder/ponder-neoforge/1.0.82%2Bmc1.21.1/` |
| `flywheel-neoforge-1.0.6+mc1.21.1.jar` | распространяется вместе с Create |

Полный Create jar берётся из gradle-кэша:

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
