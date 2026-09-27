# Industrial Mine — visual v2

Редактируемый источник: `industrial_mine.bbmodel`, Generic Model / Blockbench 5.x.
Отдельные attachments: `power_module.bbmodel`, `output_module.bbmodel`.
Общий пиксельный атлас: `industrial_mine.png`, 64×64, встроен в каждый проект.
Генератор: `tools/build_industrial_mine.py` (Python, Pillow, numpy).
Он перезаписывает эти исходники, runtime JSON и превью; ручные правки сначала сохранять отдельно.

## Художественный результат

Продолжена уже начатая геометрия v2, без возврата к v1. Удалены runtime-части
`static_structure`/`support` и код высокого внешнего периметра. Машина имеет
толстую литую базу, боковые силовые балки, передние смазочные агрегаты, задний
редукторный картер и центральное буровое ложе. Четыре редукторных поддона несут
открытые вращающиеся колёса; каждый питает четыре толстых вала. Общая передача
соединяет поддоны с главным приводом и задним моторным корпусом. Внутренние
порталы поддерживают верхние узлы, локальные мостки расположены на уровне
верхней физической поверхности структуры. Высокого забора и угловых башен нет.

Ширина основного приводного узла с опорами — около 3.6 блока. Проекция реальной
машины занимает 67.7% footprint: измерение по сетке 0.05 блока, без нижней плиты
и сервисного настила. Геометрия: 539 кубов основного проекта, 16 отдельных валов,
10 runtime assets; 26 отрисовок частей за кадр на одну видимую шахту.
`preview_isometric.png`, `preview_front.png`, `preview_top.png` — программные
рендеры тех же геометрии/UV, не скриншоты Minecraft. Позиции портов в превью примерные.

## Координаты и runtime export

Исходник обращён на NORTH. Controller находится в (0,0,0) исходника, его
каноническая позиция внутри структуры — (4,1,0). Геометрия основана на координатах
относительно нижнего угла блока Controller. В Blockbench одна единица — 1/16 блока.
База в этих координатах занимает X=−4…6, Y=−1…0, Z=0…10; верхний модуль достигает
Y=3.45. Полный визуальный объём выше физического multiblock 10×3×10, как предусмотрено ТЗ.

Java ограничивает координаты элементов диапазоном −16…32. Поэтому generator
записывает координаты runtime JSON с уменьшением в 8 раз, а BER масштабирует
каждую часть в 8 раз. UV делятся на 4 (64px → Java UV 0…16), независимо от масштаба
геометрии. Не экспортировать весь 160px исходник напрямую в vanilla JSON.
Полный контракт позиций и масштабов — `assembly_manifest.json`.

Runtime JSON: `src/main/resources/assets/createcityexpansioncore/models/block/industrial_mine/`.
Основная часть `fixed_core` не содержит вращающихся валов/колёс, активной линзы
и динамических интерфейсов. Каждая из этих частей рисуется отдельно тем же BER.

## Динамические интерфейсы

Серверный `MineMultiblock.validate` остаётся единственным источником правил.
Controller синхронизирует два дополнительных BlockPos только при изменениях
semantic state/assembly/interface positions. Второго client placement validator нет:
клиент вызывает тот же canonical validator как защиту от устаревшего состояния.

Корпуса `power_module` и `output_module` ставятся в фактические блоки интерфейсов.
Их NORTH-asset поворачивается наружу вокруг (.5,0,.5) с помощью canonical
`interfaceOutward`. Для углового Gateway приоритет NORTH/SOUTH соответствует
порядку проверки сторон у validator. Power в углу невозможен из-за двух service-air.

`power_contacts` — отдельно поворачиваемая часть Power Port. Она следует реальному
BlockState.FACING электрического блока, поскольку EE размещает узлы по нему,
даже если этот facing отличается от внешней стороны корпуса. Клеммы совпадают
с EE node positions (.25,.5,0) / (.75,.5,0) до поворота. BlockState не изменяется.

Anchors: DRILL_GROUP_NW/NE/SW/SE, MAIN_DRIVE, CONTROL_PANEL — фиксированные.
POWER_PORT/OUTPUT_GATE (и совместимые имена POWER/OUTPUT) требуют экземпляр
Controller и используют реальные синхронизированные позиции. При отсутствии
валидных данных возвращают null, без fallback на фиксированную сторону.
Anchors и BER используют один и тот же поворот вокруг центра Controller.

## Скрытие underlying blocks

`AssembledBlockVisibility` — клиентский reusable реестр владельцев и неизменяемый
снимок BlockPos → BlockState для потоков сборки чанков. BakedModel-wrapper отдаёт
пустой render type set/список quads только при совпадении live-позиции и точного
состояния реального блока. Item-модели исключаются по variant `inventory`, а
preview worlds не используют позиции из live hidden map. Для будущей машины нужно зарегистрировать
имена её block models и передавать реестру подтверждённый набор блоков.

Mine-адаптер проверяет серверный assembled flag, наличие загруженных чанков,
актуальную структуру и совпадение найденных интерфейсов. Скрывает ровно 298
mandatory blocks; две EE service-air клетки исключены. При invalid/false,
удалении Controller, выгрузке затронутого чанка или уровня владение снимается.
Изменение скрытия инвалидирует затронутые terrain sections и соседей. При
локальном изменении реакция занимает до одного client tick плюс обычный rebuild
terrain mesh. Смена мира очищает снимок без обращения к уничтоженному ViewArea.
Blockstate registry keys (`mine_frame#normal` и facing-варианты) оборачиваются
явно; `block/...` принимается для совместимости с additional model loaders.

Никаких setBlock/AIR, изменений collision, selection, capabilities, inventories
или электрических устройств. Физический объём 10×3×10 сохраняется даже там,
где assembled skin имеет визуальные промежутки. Это ожидаемое ограничение skin;
положение взаимодействий Controller/Power/Gateway совпадает с реальными блоками.

## Анимация и производительность

Один BER, только Controller; GeckoLib и дополнительные BlockEntity отсутствуют.
STARTING/RUNNING разгоняют привод до 30°/с за 60 client ticks. STOPPING и другие
нерабочие состояния дают выбег до 40 ticks. Угол в покое сохраняется. Редукторы
вращаются в противоположную сторону, валы — вдвое быстрее; группы валов имеют
разнесённое по фазе вертикальное движение ±0.035 блока. Нет серверных пакетов
на кадр, анимационные фазы не сохраняются в server NBT.

Граница отрисовки — 96 блоков от Controller; частицы — до 40 блоков, не чаще
одного burst за 3 ticks, с защитой от повторения в одном tick. Частицы только
RUNNING и привязаны к четырём drill anchors. Global VFX framework/CRE-62 не затронут.
Все неподвижные и динамические модели проходят один solid buffer. Линза состояния
использует FULL_BRIGHT, остальная машина — обычный lightmap.

## Проверки и ограничения

`gradlew build` включает `checkMineVisualCoordinates`: проверяются 64 позиции
буров, 116 допустимых power-layouts, наружные нормали Gateway по всему периметру,
все 4 ориентации, отрицательные координаты и состав 298 member positions.
Generator проверяет UUID, число валов, пределы Java JSON, зазоры вращающихся
роторов и площадь проекции. Отчёт: `asset_validation.json`.

Dedicated server запущен в отдельном временном мире на localhost и штатно
остановлен. В class-load log нет `CreateCityExpansionCoreClient` и
`com.example.examplemod.client.render.*`. Подключение реального Minecraft-клиента
к этому серверу и visual gameplay checklist пока не выполнены.

Ручная проверка перед художественным принятием:

- assembled silhouette, 4 orientations, Power/Gateway на разных сторонах;
- реальные EE-провода при разных FACING Power Port;
- clipping, hitboxes, доступ к Controller/Gateway и ходьба по service walkways;
- STARTING/RUNNING/STOPPING/STOPPED/NO_POWER/OUTPUT_BLOCKED и индикаторы;
- invalid, удаление Controller, восстановление обычных моделей;
- частичная выгрузка чанков, reload мира, reconnect, F3+T;
- 2–3 шахты рядом, distance culling, частицы;
- guide/Ponder и подключение клиента к dedicated server.

Альтернативные terrain-renderer моды (например Embeddium/Sodium), меняющие
RenderChunkRegion/model-data pipeline, отдельно не проверены.
