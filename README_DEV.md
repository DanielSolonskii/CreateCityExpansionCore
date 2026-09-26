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
| `electroenergetics-1.21.1-1.1.1.jar` | `libs/electroenergetics-1.21.1-1.1.1.jar` |
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
gradlew build
git diff --check
```

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
