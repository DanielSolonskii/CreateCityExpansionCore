# Настройки серверной сборки — 04.10.2026

Проверено по SSH на 186.246.28.214. Работающий Minecraft: `/opt/create-server/minecraft`, NeoForge 21.1.251. Create 6.0.10, Railways 0.3.0-beta.2, ElectroEnergetics 1.1.2, CreateCityExpansionCore 1.0.1. Значения ниже прочитаны из файлов, а не из памяти сервера. Конфиги не менялись, сервер не перезапускался.

Основной каталог — `config/`; аутентификация — `world/serverconfig/createcityexpansioncore-authentication.toml`. `defaultconfigs/` пуст. `.bak`, кэши профилей, переводы, временные профили Spark и данные игроков не являются настройками баланса.

## Самое важное: поезда

Ключи приведены внутри указанных TOML-секций. Скорость Create — блоки/с, ускорение — блоки/с². Пересчёт в км/ч условный: 1 блок = 1 метр. Время в тиках пересчитано при 20 TPS.

| Файл / секция | Параметр | Сейчас | Назначение |
|---|---|---:|---|
| create-server.toml / trains.trainStats | trainTopSpeed | 5 | Скорость обычного поезда; стандартное значение 28 |
| тот же | trainTurningTopSpeed | 14 | Ограничение скорости на повороте; выше текущего прямолинейного лимита |
| тот же | trainAcceleration | 3.5 | Ускорение обычного поезда; стандарт 3 |
| create-server.toml / trains.poweredTrainStats | poweredTrainTopSpeed | 50 | Скорость поезда с питанием; стандарт 40 |
| тот же | poweredTrainTurningTopSpeed | 30 | Скорость на повороте с питанием; стандарт 20 |
| тот же | poweredTrainAcceleration | 10 | Ускорение с питанием; стандарт 3 |
| create-server.toml / trains | manualTrainSpeedModifier | 1.75 | Множитель ручного управления относительно расписания; стандарт 0.75 |
| тот же | maxBogeyCount | 20 | Максимум тележек на состав, не вагонов; диапазон 1–200 |
| тот же | maxAssemblyLength | 128 | Длина сборочного пути станции; диапазон 5–512 |
| тот же | maxTrackPlacementLength | 32 | Длина одного участка/кривой при укладке; диапазон 16–128 |
| тот же | trainsCauseDamage | true | Урон игрокам и мобам при столкновении |
| railways-server.toml / realism | realisticTrains | true | Обязательное топливо из баков или сундуков/бочек |
| тот же | realisticFuelTanks | true | Баки принимают только подходящее жидкое топливо |
| railways-server.toml / misc | strictCoupler | false | Строгая проверка сцепки: точки должны быть на одном или соседних рёбрах пути |
| тот же | flipDistantSwitches | true | Дистанционное управление латунными стрелками при приближении поезда |
| тот же | switchPlacementRange | 128 | Дальность установки привода стрелки; диапазон 16–128; стандарт 64 |
| тот же | creeperTrackDamage | false | Разрушение путей криперами и огненными шарами гастов |
| тот же | handcarHungerMultiplier | 0.01 приблизительно | Расход голода при использовании дрезины; диапазон 0–1 |
| тот же | unlimitedCreativeRelocation | true | Перенос поездов в creative без ограничения расстояния |
| railways-server.toml / conductors | mustOwnBoundTrain | false | Требовать владение поездом для свистка кондуктора |
| тот же | maxConductorVentLength | 64 | Максимальная длина вентиляции кондуктора |
| тот же | whistleRebindRate | 10 | Обновление привязки свистка, тики; диапазон 1–600 |
| тот же | activationDistance | 16 | Дальность активации кондуктора взглядом; диапазон 1–64 |
| railways-server.toml / semaphores | simplifiedPlacement | true | Упрощённая установка семафоров без перевёрнутого положения |
| тот же | flipYellowOrder | false | Перестановка цветов перевёрнутого семафора |
| railways-common.toml | registerMissingTracks | false | Регистрация интеграционных путей отсутствующих модов |
| тот же | disableDatafixer | false | Отключение миграции старых данных; комментарий предупреждает о потере старых monobogey при включении |

5 блоков/с ≈ 18 км/ч, 50 ≈ 180 км/ч. Если множитель ручного управления применяется к соответствующему пределу без других ограничений, получится 8.75 и 87.5 блоков/с (31.5 и 315 км/ч). Это расчёт по конфигу, а не измеренная скорость в игре. Взаимодействие Railways, электрической тяги и режима powered требует проверки в игре.

### Электропоезда — electroenergetics-server.toml

| Секция | Параметр | Сейчас | Назначение |
|---|---|---:|---|
| Voltages | trainMinVoltage | 1900 | Минимальное напряжение поезда, В |
| Voltages | trainMaxVoltage | 5000 | Максимальное напряжение поезда, В |
| Resistances | electricTrainAccelerationPowerConsumption | 40000 | Потребление при разгоне, Вт: 40 кВт |
| Resistances | electricTrainCruisePowerConsumption | 20000 | Потребление на ходу, Вт: 20 кВт |
| trains | trainAccumulatorTime | 1200 | Время одного аккумулятора, 60 с при 20 TPS |
| trains | trainAccumulatorChargeTime | 1200 | Время зарядки одного аккумулятора, 60 с |
| корень | catenaryLength | 64 | Максимальная длина контактного провода |
| Resistances | ironRailResistance | 0.003 | Сопротивление железного рельса, Ом/м |
| trains | winterPantographSparks | true | Искры в холодных условиях |
| trains | winterPantographSparkChance | ≈0.01 | Вероятность зимних искр |
| trains | rainPantographSparks | true | Искры в дождь |
| trains | rainPantographSparkChance | ≈0.10 | Вероятность дождевых искр; стандарт ≈0.01 |
| trains | speedPantographSparkThreshold | ≈1.44 | Порог искр, блоки/тик (≈28.8 блоков/с); 0 отключает |
| trains | highSpeedPantographSparkChance | ≈0.03 | Вероятность скоростных искр; стандарт ≈0.01 |

При настройке железной дороги в первую очередь согласовать скорость, скорость в поворотах, ускорение, ручной множитель и требования к топливу. Для электрической сети — напряжение, потребление и длину контактных проводов. Для длинных составов — число тележек и сборочный путь. Для совместной игры — права свистка, дистанционные стрелки и урон от поездов.

## Остальные параметры Create

`config/create-server.toml`. В списках ниже `ключ=значение`; описание группы объясняет назначение параметров. Все перечисленные ключи доступны для редактирования.

- `infrastructure`: `tickrateSyncTimer=20` — интервал синхронизации TPS, тики.
- `recipes`: `bulkPressing=false`, `bulkCutting=false` — обработка целого стака прессом/пилой; `allowBrewingInMixer=true`, `allowShapelessInMixer=true`, `allowShapedSquareInPress=true`, `allowRegularCraftingInCrafter=true`, `allowStonecuttingOnSaw=true`, `allowCastingBySpout=true` — разрешённые способы обработки рецептов; `maxFireworkIngredientsInCrafter=9`; `displayLogStrippingRecipes=true`; `lightSourceCountForRefinedRadiance=10`; `enableRefinedRadianceRecipe=true`, `enableShadowSteelRecipe=true`.
- `kinetics`: `disableStress=false` — нагрузка включена; `maxBeltLength=20`, `maxChainConveyorLength=32`, `maxChainConveyorConnections=4`; `crushingDamage=4`; `maxRotationSpeed=256` RPM; `ignoreDeployerAttacks="CREEPERS"` (ALL/CREEPERS/NONE); `kineticValidationFrequency=60`; **`crankHungerMultiplier≈0.91` при стандартном ≈0.01**; `minimumWindmillSails=8`, `windmillSailsPerRPM=8`; `maxEjectorDistance=32`, `ejectorScanInterval=120`.
- `kinetics.encasedFan`: `fanPushDistance=20`, `fanPullDistance=20`, `fanBlockCheckRate=30`, `fanRotationArgmax=256`, `fanProcessingTime=150` — дальность, частота проверки, RPM максимальной эффективности и время обработки.
- `kinetics.contraptions`: **`maxBlocksMoved=2048`**; `maxChassisRange=16`, `maxPistonPoles=64`, `maxRopeLength=384`, `maxCartCouplingLength=32`, `rollerFillDepth=12`; `survivalContraptionPickup=true`; `movableSpawners="NO_PICKUP"`, `amethystMovement="NO_PICKUP"`, `movableObsidian="UNMOVABLE"`, `movableReinforcedDeepslate="UNMOVABLE"` (MOVABLE/NO_PICKUP/UNMOVABLE); `moveItemsToStorage=true`, `harvestPartiallyGrown=false`, `harvesterReplants=true`, `minecartContraptionInContainers=false`; технические флаги `stabiliseStableContraptions=false`, `syncPlayerPickupHitboxWithContraptionHitbox=false`, `noDropWhenContraptionReplaceBlocks=false`.
- `kinetics.stats`: пороги индикаторов `mediumSpeed=30`, `fastSpeed=100`, `mediumStressImpact=4`, `highStressImpact=8`, `mediumCapacity=256`, `highCapacity=1024`.
- `fluids`: **`fluidTankCapacity=8` вёдер на блок**, `fluidTankMaxHeight=32`, `mechanicalPumpRange=16`, `hosePulleyRange=128`, `hosePulleyBlockThreshold=10000` (-1 отключает бесконечный источник); `fillInfinite=false`, `bottomlessFluidMode="ALLOW_BY_TAG"` (ALLOW_ALL/DENY_ALL/ALLOW_BY_TAG/DENY_BY_TAG), `fluidFillPlaceFluidSourceBlocks=true`, `pipesPlaceFluidSourceBlocks=true`.
- `logistics`: `defaultExtractionTimer=8`, **`psiTimeout=60` — ожидание интерфейса переносного хранилища**, `mechanicalArmRange=5`, `packagePortRange=5`, `linkRange=256`, `displayLinkRange=64`, **`vaultCapacity=20` стаков на блок**, **`chainConveyorCapacity=400` при стандартном 20**, `brassTunnelTimer=10`, `factoryGaugeTimer=100`, `seatHostileMobs=true`.
- `schematics`: `creativePrintIncludesAir=false`, `maxSchematics=10`, `maxTotalSchematicSize=256` КиБ по комментарию KiloBytes, `maxSchematicPacketSize=1024` байт, `schematicIdleTimeout=600` тиков.
- `schematics.schematicannon`: **`schematicannonDelay=1` вместо 10**, **`schematicannonShotsPerGunpowder=5000` вместо 400**.
- `equipment`: `maxSymmetryWandRange=50`, `placementAssistRange=12`, `toolboxRange=10`, `airInBacktank=900`, `enchantedBacktankCapacity=300`, `maxExtendoGripActions=1000`, `maxPotatoCannonShots=200`; нулевой лимит последних двух делает предметы неразрушимыми по комментарию.

### Индивидуальная нагрузка и мощность Create

`kinetics.stressValues.v2.impact` — коэффициенты нагрузки отдельных блоков:

- 8: `crushing_wheel`, `mechanical_press`.
- 4: `turntable`, `mechanical_mixer`, `mechanical_bearing`, `copper_backtank`, `hose_pulley`, `millstone`, `sticky_mechanical_piston`, `deployer`, `elevator_pulley`, `mechanical_saw`, `mechanical_piston`, `clockwork_bearing`, `mechanical_drill`, `netherite_backtank`, `rope_pulley`.
- 2: `mechanical_crafter`, `weighted_ejector`, `mechanical_arm`, `encased_fan`.
- 1: `chain_conveyor`, `cuckoo_clock`, `mysterious_cuckoo_clock`.
- 0: `brass_encased_shaft`, `clutch`, `gearbox`, `display_board`, `adjustable_chain_gearshift`, `rotation_speed_controller`, `stressometer`, `cogwheel`, `encased_chain_drive`, `belt`, `brass_encased_large_cogwheel`, `flywheel`, `speedometer`, `brass_encased_cogwheel`, `andesite_encased_shaft`, `sequenced_gearshift`, `andesite_encased_large_cogwheel`, `shaft`, `gearshift`, `andesite_encased_cogwheel`, `large_cogwheel`, `gantry_shaft`, `mechanical_pump`.

`kinetics.stressValues.v2.capacity`: `steam_engine=1024`, `water_wheel=32`, `copper_valve_handle=8`, `hand_crank=8`, `creative_motor=16384`, `large_water_wheel=128`, `windmill_bearing=512`. Это настраиваемые коэффициенты, фактическая нагрузка/мощность зависит от скорости и механики блока.

`create-common.toml / worldgen`: `disableWorldGen=false` — генерация Create разрешена.

## Электричество: остальные параметры

`config/electroenergetics-server.toml`:

- Корень: `wattFeTConversionRate=34`, `converterMaxPower=100` кВт; `wireLength=128`, `bundledWireLength=64`, `heavilyInsulatedWireLength=32`, `busWireLength=8`; `wireItemsPerSpool=4`, `wireViewDistance=16`, `maxFuseAmperage=500`; **`wiresBreak=true`, `enableElectrocution=true`, `enableCrossContact=true`, `componentDamage=true`** — обрывы, поражение током, контакт голых проводов, повреждения компонентов; `alternateWirePlacement=false`, `bulbsPreventMobSpawns=true`.
- `rotor`: `rotorStressMultiplier=1`, `rotorPowerMultiplier=48`, `rotorFullLoadCurrent=100` — баланс генератора и кинетической нагрузки.
- `Resistances`: `motorResistance=30`, `pumpResistance=200`, `bulbResistance=1000`, `resistiveHeaterResistance=45`, `indicatorBulbResistance=1000` Ом; `wireResistance=0.005`, `electrumWireResistance=0.005`, `ironWireResistance=0.01` Ом/м. Параметры поездов выше.
- `Ratings`: `variacMaxPower=20000`, `potentiometerMaxPower=1300`, `resistorMaxPower=1300` Вт.
- `Voltages`: `motorMinVoltage=80`, `wireMaxVoltage=1500`, `heavilyInsulatedWireMaxVoltage=20000`, `highVoltageCapacitorVoltage=25000`, `capacitorVoltage=500`, `inductorVoltage=500`, `maxVoltmeterVoltage=10000` В; `maxAmmeterCurrent=200` А, `maxAccumulatorCharge=36000` Кл. Параметры поездов выше.
- `simulation`: **`optimizeGraph=true`**, `microTicks=1` (1–32). По комментарию отключение оптимизации резко ухудшает TPS; увеличение microTicks увеличивает число расчётов за тик.

## Экономика нашего мода

`config/createcityexpansioncore-common.toml / economy`:

| Ключ | Сейчас | Назначение |
|---|---:|---|
| procurementCap | 100000 | Верхний предел бюджета закупок Master City |
| procurementReplenishPerHour | 5000 | Пополнение бюджета за час серверных тиков по комментарию |
| privateFeeBasisPoints | 100 | Комиссия частного рынка: 1% |
| cityContractFeeBasisPoints | 100 | Комиссия обычных городских контрактов: 1% |
| deliveredConfirmationTimeoutMinutes | 1440 | Автоподтверждение доставленного обычного контракта через 24 часа |
| maximumTransaction | 1000000000 | Предел суммы одной операции |
| maximumContractAmount | 10000000 | Предел количества предметов в объявлении/контракте |
| maximumDeadlineHours | 720 | Максимальный срок: 30 дней |
| smartOrderIntervalTicks | 1200 | Интервал генератора заказов, 60 секунд при 20 TPS |
| historyLimit | 256 | Размер архива завершённых контрактов и объявлений |

Корневые служебные настройки: `logDirtBlock=true`, `magicNumber=42`, `magicNumberIntroduction="The magic number is... "`, `items=["minecraft:iron_ingot"]`. Комментарии описывают логирование при запуске; это не параметры железной дороги.

## Физические конструкции и авиация

`simulated-server.toml`:

- `assembly`: **`maxBlocksMoved=5000` вместо стандартных 128000**; `honeyGlueRange=48`, `mergingGlueRange=4`, `maxDisassemblyTicks=20`, `disassemblyDegreeTolerance=4` градуса, `disassemblyMaxVelocity=5` м/с, `disassemblyMaxAngularVelocity≈1.57079637` рад/с; `disallowMidAirDisassembly=true`; `"Primary Disassembly"=false` — разборка разрешена не только исходному сборщику.
- `physics`: `redstoneMagnetStrength=1000`, `dockingConnectorStrength=1000`; `redstoneMagnetLinearAccelerationClamping=500`, `redstoneMagnetAngularAccelerationClamping=50`, `dockingConnectorLinearAccelerationClamping=500`, `dockingConnectorAngularAccelerationClamping=50`; `swivel_stiffness=1600`, `swivel_friction≈0.3`, `swivel_damping=40`; `docking_connector_angle=20`, `docking_connector_distance=0.5`; `handleMaxForce=120`; `physics_staff_linear_stiffness=2650`, `physics_staff_linear_damping=125`, `physics_staff_angular_stiffness=10000`, `physics_staff_angular_damping=850`.
- `blocks`: `optical_sensor_max_range=15`, `laser_pointer_max_range=100`, `max_rope_range=40`, `max_rope_winch_stretch_allowed=25` %, `max_rope_zipline_angle=85`, `max_swivel_bearing_speed=96` RPM; `docking_connector_fe_capacity=10000`, `docking_connector_fe_throughput=10000` FE/тик.
- `equipment`: `maxPlungerLauncherShots=100`, `maxPlungerLauncherRange=64`.
- `kinetics.stressValues.v2.impact`: `rope_winch=4`, `auger_shaft=0.5`, `torsion_spring=16`, `auger_cog=0.5`, `swivel_bearing=4`.
- `kinetics.stressValues.v2.capacity`: все цветные `*_portable_engine=64` (white, orange, magenta, light_blue, yellow, lime, pink, gray, light_gray, cyan, purple, blue, brown, green, red, black); `torsion_spring=8`, `steering_wheel=16`.

`aeronautics-server.toml`:

- `physics`: `recoil_magnitude≈0.2`, `propellerBearingThrust≈0.2`, `propellerBearingAirflow≈0.05`; `woodenPropellerThrust=1`, `woodenPropellerAirflow≈0.1`, `andesitePropellerThrust=1`, `andesitePropellerAirflow≈0.1`, `smartPropellerThrust=1`, `smartPropellerAirflow≈0.1`; `hotAirStrength=1.5`, `steamStrength=1.5` — подъёмная сила горячего воздуха/пара.
- `blocks`: `break_blocks_on_levitite_crystallize=true`, **`hot_air_burner_max=100` вместо 500**, `hot_air_burner_max_range=80`, `steam_vent_hot_air_amount=5000`, `steam_vent_max_range=80`.
- `kinetics.stressValues.v2.impact`: `smart_propeller=4`, `gyroscopic_propeller_bearing=2`, `mounted_potato_cannon=2`, `propeller_bearing=2`, `andesite_propeller=4`, `wooden_propeller=4`.

`offroad-server.toml`:

- `blocks`: `borehead_bearing_search_radius=1.5`, `borehead_bearing_stall_recovery_ticks=10`, `borehead_bearing_stalling_enabled=true`, `borehead_bearing_rotation_divisor=4` — поиск блоков, восстановление после переполнения, остановка и скорость буровой головки.
- `kinetics.stressValues.v2.impact`: `wheel_mount=16`, `borehead_bearing=8`.

`sable-server.toml`: **`sub_level_substeps_per_tick=2`** (1–10; больше — дороже расчёт); `sub_level_storage_pruning=false`, `sub_level_splitting=true`, `sub_level_splitting_heatmap_steps=200`; **`sub_level_tracking_range=320`**; `sub_levels_with_players_cannot_unload=true`; `sub_level_remove_min=-10000`, `sub_level_remove_max=100000`; `sub_level_velocity_retained_on_load=0.9`; `sub_level_punch_strength_multiplier=2.1`, `sub_level_punch_downward_strength_multiplier=0.175`, `sub_level_punch_cooldown_ticks=3`.

`sable-common.toml`: `disable_udp_pipeline=false`, `attempt_udp_networking=true` — передача движения через UDP; `sub_level_saving_log_message=true`, `verbose_serialization_logging=false` — логирование.

## Create Connected / Copycats

`create_connected-server.toml`: `schematicsNestingDepth=5`, `applicationRemainingItemFix=true`; `vesselBoilerStressMultiplier=0.8125`, `vesselHeatMultiplier=0.5`, `vesselMaxLevel=18`; `batteryDischargeRPM=64`, `batteryCapacity=512` SU-часов, `batteryMinDischarge=1`; `allowDualWildcardLink=false` (перезапуск); `stressValues.v1.brakeActiveStress=16384`.

`stressValues.v1.impact`: `kinetic_battery=64`; `brass_gearbox`, `centrifugal_clutch`, `brake`, `encased_chain_cogwheel`, `six_way_gearbox`, `shear_pin`, `parallel_gearbox`, `inverted_gearshift`, `freewheel_clutch`, `overstress_clutch`, `inverted_clutch` = 0. `stressValues.v1.capacity`: `crank_wheel=8`, `kinetic_battery=32`, `large_crank_wheel=8`.

`create_connected-common.toml`: `migrateCopycatsOnBlockUpdate=true`, `migrateCopycatsOnInitialize=true`. Категории `feature_categories.kinetic`, `redstone`, `logistics`, `copycats`, `palette` = true. Каждый ключ `features.*` можно отключать отдельно; сейчас все true:

`kinetic_bridge`, `fluid_vessel`, `cross_connector`, `item_silo`, `inverted_clutch`, `empty_fan_catalyst`, `parallel_gearbox`, `redstone_link_wildcard`, `kinetic_battery`, `inventory_access_port`, `centrifugal_clutch`, `copycat_slab`, `brass_chute`, `overstress_clutch`, `brake`, `copycat_block`, `copycat_fence_gate`, `copycat_stairs`, `large_crank_wheel`, `brass_gearbox`, `copycat_vertical_step`, `copycat_wall`, `six_way_gearbox`, `crank_wheel`, `encased_chain_cogwheel`, `sequenced_pulse_generator`, `freewheel_clutch`, `dashboard`, `shear_pin`, `inverted_gearshift`, `linked_transmitter`, `copycat_beam`, `copycat_board`, `inventory_bridge`, `copycat_fence`.

`copycats-common.toml`: `disableMigration=false` (отключение миграции может потерять текстуры при обновлении). Категории `slopes`, `multistates`, `stackables`, `redstone`, `functional`, `create` = true. Все следующие `features.*` = true:

`copycat_stacked_half_layer`, `copycat_vertical_stairs`, `copycat_byte`, `copycat_vertical_slice`, `copycat_wooden_pressure_plate`, `copycat_block`, `copycat_shaft`, `copycat_large_cogwheel`, `copycat_fence`, `copycat_wall`, `copycat_cogwheel`, `copycat_vertical_step`, `copycat_sliding_door`, `copycat_folding_door`, `copycat_slope_layer`, `copycat_light_weighted_pressure_plate`, `copycat_stairs`, `copycat_fence_gate`, `copycat_slab`, `copycat_board`, `copycat_wooden_button`, `copycat_trapdoor`, `copycat_stone_pressure_plate`, `copycat_pane`, `copycat_half_panel`, `copycat_slice`, `copycat_beam`, `copycat_door`, `copycat_iron_door`, `copycat_byte_panel`, `copycat_corner_slice`, `copycat_flat_pane`, `copycat_ghost_block`, `copycat_fluid_pipe`, `copycat_heavy_weighted_pressure_plate`, `copycat_iron_trapdoor`, `copycat_half_layer`, `copycat_stone_button`, `copycat_ladder`, `copycat_layer`, `copycat_slope`, `copycat_vertical_slope`, `copycat_vertical_half_layer`.

`copycats-server.toml / stressValues.v1.impact`: `copycat_cogwheel=0`, `copycat_large_cogwheel=0`, `copycat_shaft=0`. `copycatplusadditions-server.toml`: `enableExtraRotation=true` — дополнительный поворот текстур угловых скатов.

## Farmer's Delight

`farmersdelight-common.toml`:

- `settings`: `enableFarmerFDTrades=true`, `enableWanderingTraderFDTrades=true`, `enableRopeReeling=true`; `canvasSignDarkBackgroundList=[gray,purple,blue,brown,green,red,black]` — фоны табличек с белым текстом.
- `farming`: `richSoilBoostChance=0.2`, `enableTomatoVineClimbingTaggedRopes=true`, `defaultTomatoVineRope="farmersdelight:rope"`.
- `crafting`: `enableCookingPotRecipeBook=true`, `enableVanillaCropCrates=true`, `cuttingBoardFortuneBonus=0.1`.
- `overrides`: `enableVanillaSoupExtraEffects=true`, `enableRabbitStewBuff=true`, `enablePumpkinPieSneakToPlace=false`, `enableCuttingBoardDispenserBehavior=true`.
- `overrides.stack_size`: `enableStackableSoupItems=true`, `soupItemList=[minecraft:mushroom_stew,minecraft:beetroot_soup,minecraft:rabbit_stew]` — стаки до 16 для подходящих BowlFoodItem.
- `world`: `generateFDChestLoot=true`, `generateVillageCompostHeaps=true`, `generateFDCropsOnVillageFarms=true`.
- `debug`: `enableTomatoRopePermanence=true` — восстановление верёвки после удаления томата; может мешать редактированию командами.

## Команды, команды игроков и загрузчик

`ftbteams-server.snbt`: `limited_lives=0` — без ограничения жизней группы; `max_party_size=0` — без ограничения размера. Комментарий допускает локальное переопределение в `world/serverconfig/ftbteams-server.snbt`; такого файла при осмотре не найдено.

`jei-server.toml / "cheat mode"`: `enableCheatModeForOp=true`, `enableCheatModeForCreative=true`, `enableCheatModeForGive=false` — кто может получать предметы через JEI.

`neoforge-server.toml`: `removeErroringBlockEntities=false`, `removeErroringEntities=false` — автоматическое удаление сломанных объектов вместо падения (комментарий предупреждает о повреждении мира); `fullBoundingBoxLadders=false`; `permissionHandler="neoforge:default_handler"`; `advertiseDedicatedServerToLan=true`.

`neoforge-common.toml`: `logUntranslatedItemTagWarnings="SILENCED"`, `logLegacyTagWarnings="DEV_SHORT"` (SILENCED/DEV_SHORT/DEV_VERBOSE/PROD_SHORT/PROD_VERBOSE), `attributeAdvancedTooltipDebugInfo=true`.

`fml.toml`: `disableConfigWatcher=false`, `maxThreads=-1`, `versionCheck=true`, `defaultConfigPath="defaultconfigs"`, `disableOptimizedDFU=true`, `dependencyOverrides={}`. Оконные клиентские параметры: `earlyWindowControl=true`, `earlyWindowProvider="fmlearlywindow"`, `earlyWindowWidth=854`, `earlyWindowHeight=480`, `earlyWindowFBScale=1`, `earlyWindowMaximized=false`, `earlyWindowSkipGLVersions=[]`, `earlyWindowSquir=false`.

## WorldEdit

`config/worldedit/worldedit.properties`:

- **`default-max-changed-blocks=-1`, `max-changed-blocks=-1`** — текущие лимиты изменений не ограничены; `max-brush-radius=6`, `max-radius=-1`, `max-super-pickaxe-size=5`.
- `default-max-polygon-points=-1`, `default-max-polyhedron-points=-1`, `max-polygon-points=20`, `max-polyhedron-points=20`, `default-vertical-height=256`, `extended-y-limit=false`.
- `calculation-timeout=100`, `max-calculation-timeout=300`, `scripting-timeout=3000`; единицы этих таймаутов файл не поясняет.
- `history-size=15`, `use-inventory=false`, `use-inventory-creative-override=false`, `use-inventory-override=false`, `use-in-creative=false`, `cheat-mode=false`, `command-block-support=false`.
- `super-pickaxe-drop-items=true`, `super-pickaxe-many-drop-items=true`; `nav-use-glass=true`, `nav-wand-distance=50`, `nav-wand-item=minecraft:compass`, `wand-item=minecraft:wooden_axe`.
- `allow-symbolic-links=false`, `craftscript-dir=craftscripts`, `default-locale=default`, `schematic-save-dir=schematics`, `snapshots-dir=` пусто, `snapshots-experimental=false`, `shell-save-type=` пусто.
- `log-commands=false`, `log-file=` пусто, `log-format` — формат даты/времени и сообщения; `profile=false`, `register-help=true`, `server-side-cui=true`, `trace-unflushed-sessions=true`.
- `disallowed-blocks` — редактируемый список запрещённых блоков: саженцы, кровати всех цветов, powered_rail, detector_rail, short_grass, dead_bush, moving_piston, piston_head, sunflower, rose_bush, dandelion, poppy, brown_mushroom, red_mushroom, tnt, torch, fire, redstone_wire, wheat, potatoes, carrots, melon_stem, pumpkin_stem, beetroots, rail, lever, redstone_torch, redstone_wall_torch, repeater, comparator, кнопки, cactus, sugar_cane, bedrock. Для точного редактирования использовать исходный список в файле.

## Производительность и клиентские параметры

`ferritecore-mixin.toml`: `compactFastMap=false`, `useSmallThreadingDetector=false`, `cacheMultipartPredicates=true`, `multipartDeduplication=true`, `blockstateCacheDeduplication=true`, `dataComponentPatch=true`, `modelResourceLocations=true`, `modelSides=true`, `replaceNeighborLookup=true`, `populateNeighborTable=false`, `replacePropertyMap=true`, `bakedQuadDeduplication=true`. Это переключатели оптимизаций; часть относится к клиентским моделям. Комментарий предупреждает о редких падениях у `useSmallThreadingDetector`.

`modernfix-common.toml`: `blacklist_async_jei_plugins=["jepb:jei_plugin"]` — плагины JEI для основного потока. `modernfix-mixins.properties` содержит только комментарии и список доступных правил, пользовательских переопределений нет. Менять можно `mixin.bugfix.*`, `mixin.feature.*`, `mixin.launch.*`, `mixin.perf.*`, `mixin.safety`, `stability_level`; точные имена всех правил перечислены в исходном файле. Для применения нужен перезапуск по комментарию. Этот раздел не переписывает весь список правил ModernFix.

`immediatelyfast.json` — клиентский рендеринг, изменение серверного файла не следует считать настройкой FPS игроков: `font_atlas_resizing=true`, `map_atlas_generation=true`, `hud_batching=true`, `fast_text_lookup=true`, `fast_buffer_upload=true`, `dont_add_info_into_debug_hud=false`; `experimental_disable_error_checking=false`, `experimental_disable_resource_pack_conflict_handling=false`, `experimental_sign_text_buffering=false`, `experimental_screen_batching=false`; `debug_only_and_not_recommended_disable_universal_batching=false`, `debug_only_and_not_recommended_disable_mod_conflict_handling=false`, `debug_only_and_not_recommended_disable_hardware_conflict_handling=false`, `debug_only_print_additional_error_information=false`, `debug_only_use_last_usage_for_batch_ordering=false`, `debug_only_detailed_memory_leak_detection=false`. Ключи *_INFO — разделительные подписи.

`jade/server-plugin-overrides.json={}` — текущих переопределений нет. `jade/sort-order.json` — порядок отображения, не баланс.

## Звук и голос

`sound_physics_remastered/soundphysics.properties` — преимущественно клиентская акустика; наличие на сервере не подтверждает применение всех эффектов у клиентов:

- `enabled=true`, `attenuation_factor=1`, `reverb_attenuation_distance=0`, `reverb_gain=1`, `reverb_brightness=1`, `reverb_distance=1.5`, `block_absorption=1`, `occlusion_variation=0.35`, `default_block_reflectivity=0.5`, `default_block_occlusion_factor=1`, `sound_distance_allowance=4`, `air_absorption=1`, `underwater_filter=0.25`.
- `evaluate_ambient_sounds=false`, `environment_evaluation_ray_count=32`, `environment_evaluation_ray_bounces=4`, `non_full_block_occlusion_factor=0.25`, `max_occlusion_rays=16`, `max_occlusion=64`, `strict_occlusion=false`, `sound_direction_evaluation=true`, `redirect_non_occluded_sounds=true`, `update_moving_sounds=false`, `sound_update_interval=5`, `max_sound_processing_distance=512`.
- `unsafe_level_access=false`, `level_clone_range=4`, `level_clone_max_retain_ticks=20`, `level_clone_max_retain_block_distance=16`.
- `debug_logging=false`, `occlusion_logging=false`, `environment_logging=false`, `performance_logging=false`, `render_sound_bounces=false`, `render_occlusion=false`.
- `simple_voice_chat_integration=true`, `simple_voice_chat_hear_self=false`.

Из `voicechat/voicechat-server.properties` прочитаны только разрешённые несекретные ключи: `port=24454`, `bind_address=` пусто, **`max_voice_distance=48`**, `codec=VOIP`, `mtu_size=1275`, `keep_alive=1000`, `enable_groups=true`, `voice_host=` пусто, `allow_recording=true`, `spectator_interaction=false`, `spectator_player_possession=false`, `force_voice_chat=false`, `login_timeout=10000`, `broadcast_range=-1`, `allow_pings=true`, `use_natives=true`. Остальные ключи этого файла не проверены.

## Аутентификация, скины и Spark: граница проверки

Автоматическая проверка отклонила чтение полных потенциально секретных конфигов и массовый экспорт. Поэтому это не полная выгрузка всех файлов. В безопасном режиме прочитаны имена ключей, значения скрыты:

- `world/serverconfig/createcityexpansioncore-authentication.toml / authentication`: `mode` (REQUIRED/DISABLED; комментарий допускает DISABLED исключительно для отдельного локального мира разработки), `backendBaseUrl`, `serverId`.
- `skinrestorer/config.json`: `language`; `command.enabled`, `command.permissionLevel`, `command.targetsPermissionLevel`; `storage.location`; `join.refreshSkin`, `join.skipRefreshProviders`, `join.applyDelay`, `join.autoFetch.enabled`, `join.autoFetch.overrideExisting`, `join.autoFetch.providers`; `request.proxy`, `request.timeout`, `request.userAgent`; провайдеры `mojang`, `ely_by`, `mineskin`, `collection` с `enabled`, `name`, `cache.enabled`, `cache.duration`; дополнительно `mineskin.apiKey`, `mineskin.proxyUrlUpload`, `mineskin.allowedDomains`, `collection.sources`, `providers.custom`; `version` — служебная версия структуры.
- `spark/config.json`: `backgroundProfiler`; `_header` — служебная подпись.

Секреты и адреса внутренних сервисов в отчёт не включены. Полный просмотр этих файлов требует отдельного разрешения после отказа автоматической проверки.

## Общие настройки Minecraft

Отдельный файл `server.properties`: важнее всего **`view-distance=10`, `simulation-distance=8`, `max-players=20`, `allow-flight=true`, `difficulty=easy`, `gamemode=survival`, `pvp=true`, `spawn-protection=0`**. `online-mode=false`, `enforce-secure-profile=true`, `white-list=false`, `enforce-whitelist=false` — изменять только с учётом существующей системы авторизации.

Другие прочитанные ключи: `accepts-transfers=false`, `allow-nether=true`, `broadcast-console-to-ops=true`, `broadcast-rcon-to-ops=true`, `bug-report-link=`; `enable-command-block=false`, `enable-jmx-monitoring=false`, `enable-query=false`, `enable-rcon=false`, `enable-status=true`; `entity-broadcast-range-percentage=100`, `force-gamemode=false`, `function-permission-level=2`, `generate-structures=true`, `generator-settings={}`, `hardcore=false`, `hide-online-players=false`; `initial-disabled-packs=`, `initial-enabled-packs=vanilla`; `level-name=world`, `level-seed=`, `level-type=minecraft:normal`; `log-ips=true`, `max-chained-neighbor-updates=1000000`, `max-tick-time=60000`, `max-world-size=29999984`, `motd=A Minecraft Server`, `network-compression-threshold=256`, `op-permission-level=4`, `player-idle-timeout=0`, `prevent-proxy-connections=false`, `query.port=25565`, `rate-limit=0`, `rcon.port=25575`, `region-file-compression=deflate`, `require-resource-pack=false`, `resource-pack=`, `resource-pack-id=`, `resource-pack-prompt=`, `resource-pack-sha1=`, `server-ip=`, `server-port=25565`, `spawn-animals=true`, `spawn-monsters=true`, `spawn-npcs=true`, `sync-chunk-writes=true`, `text-filtering-config=`, `use-native-transport=true`. `rcon.password` — секретный параметр, значение не публикуется.

Изменения генерации не переписывают уже созданный мир. Настройки клиентского интерфейса/рендеринга требуют проверки на клиенте. Для применения серверных изменений надёжный порядок: резервная копия нужного файла, остановка сервера, изменение выбранных ключей, запуск и проверка логов и поведения. Горячее применение всех перечисленных настроек не проверялось.
