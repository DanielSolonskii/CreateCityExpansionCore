# CreateCityExpansionCore — исходные рецепты

Версия проекта: 1.0.1  
Ветка: codex/next-ai-work  
Количество рецептов: 29

Этот файл объединяет исходное содержимое всех recipe JSON из namespace `createcityexpansioncore`. Заголовок каждой секции — исходный путь файла в проекте.

## `src/main/resources/data/createcityexpansioncore/recipe/blasting/technical_ceramic.json`

```json
{
  "type": "minecraft:blasting",
  "category": "misc",
  "ingredient": {
    "item": "createcityexpansioncore:ceramic_compound"
  },
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:technical_ceramic"
  },
  "experience": 0.35,
  "cookingtime": 100
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/compacting/reinforced_concrete.json`

```json
{
  "type": "create:compacting",
  "ingredients": [
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:dry_concrete_mix"
    },
    {
      "item": "createcityexpansioncore:reinforcement_bundle"
    },
    {
      "item": "createcityexpansioncore:reinforcement_bundle"
    },
    {
      "type": "neoforge:single",
      "amount": 1000,
      "fluid": "minecraft:water"
    }
  ],
  "results": [
    {
      "count": 8,
      "id": "createcityexpansioncore:reinforced_concrete"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/crafting/dispatcher_key.json`

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "key": {
    "S": {
      "item": "create:brass_sheet"
    },
    "M": {
      "item": "minecraft:compass"
    },
    "E": {
      "item": "create:electron_tube"
    },
    "R": {
      "item": "minecraft:redstone"
    },
    "A": {
      "item": "create:andesite_alloy"
    }
  },
  "pattern": [
    " R ",
    "EA ",
    "SM "
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:dispatcher_key"
  }
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/crafting/magnetic_analyzer.json`

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "key": {
    "C": {
      "item": "create:copper_sheet"
    },
    "R": {
      "item": "minecraft:redstone"
    },
    "A": {
      "item": "create:andesite_alloy"
    },
    "M": {
      "item": "minecraft:compass"
    },
    "I": {
      "item": "create:iron_sheet"
    },
    "E": {
      "item": "create:electron_tube"
    }
  },
  "pattern": [
    "CRC",
    "AMA",
    "IEI"
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:magnetic_analyzer"
  }
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/crafting/sleeper_pack.json`

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "key": {
    "S": {
      "tag": "minecraft:wooden_slabs"
    },
    "F": {
      "item": "createcityexpansioncore:industrial_fastener_pack"
    }
  },
  "pattern": [
    "SFS",
    "SFS"
  ],
  "result": {
    "count": 4,
    "id": "createcityexpansioncore:sleeper_pack"
  }
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/cutting/copper_busbar.json`

```json
{
  "type": "create:cutting",
  "ingredients": [
    {
      "tag": "c:plates/copper"
    }
  ],
  "results": [
    {
      "count": 4,
      "id": "createcityexpansioncore:copper_busbar"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/cutting/industrial_fastener_pack.json`

```json
{
  "type": "create:cutting",
  "ingredients": [
    {
      "item": "createcityexpansioncore:steel_plate"
    }
  ],
  "results": [
    {
      "count": 8,
      "id": "createcityexpansioncore:industrial_fastener_pack"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/cutting/reinforcement_bundle.json`

```json
{
  "type": "create:cutting",
  "ingredients": [
    {
      "item": "createcityexpansioncore:structural_beam"
    }
  ],
  "results": [
    {
      "count": 2,
      "id": "createcityexpansioncore:reinforcement_bundle"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/cutting/structural_beam.json`

```json
{
  "type": "create:cutting",
  "ingredients": [
    {
      "item": "createcityexpansioncore:steel_billet"
    }
  ],
  "results": [
    {
      "count": 2,
      "id": "createcityexpansioncore:structural_beam"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/city_pylon.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    },
    "B": {
      "item": "createcityexpansioncore:structural_beam"
    },
    "C": {
      "item": "create:brass_casing"
    },
    "R": {
      "item": "createcityexpansioncore:reinforced_concrete"
    },
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    }
  },
  "pattern": [
    "SBS",
    "RHR",
    "CIS"
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:city_pylon"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/contract_intake.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "F": {
      "item": "create:brass_funnel"
    },
    "C": {
      "item": "create:smart_chute"
    },
    "O": {
      "item": "create:content_observer"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    },
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    }
  },
  "pattern": [
    "HFO",
    "CIS",
    "   "
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:contract_intake"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/control_block.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    },
    "C": {
      "item": "create:brass_casing"
    },
    "P": {
      "item": "create:precision_mechanism"
    },
    "F": {
      "item": "createcityexpansioncore:industrial_fastener_pack"
    }
  },
  "pattern": [
    "SCS",
    "PCP",
    "SFS"
  ],
  "result": {
    "count": 4,
    "id": "createcityexpansioncore:control_block"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/drill_unit.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "D": {
      "item": "create:mechanical_drill"
    },
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    },
    "M": {
      "item": "electroenergetics:magnet"
    }
  },
  "pattern": [
    "DHD",
    "SMS",
    "DID",
    "MHM"
  ],
  "result": {
    "count": 2,
    "id": "createcityexpansioncore:drill_unit"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/mine_controller.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    },
    "P": {
      "item": "create:precision_mechanism"
    },
    "B": {
      "item": "create:brass_casing"
    },
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    },
    "C": {
      "item": "electroenergetics:capacitor"
    },
    "R": {
      "item": "electroenergetics:resistor"
    }
  },
  "pattern": [
    "SBS",
    "RCI",
    "IPH"
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:mine_controller"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/mine_frame.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "B": {
      "item": "createcityexpansioncore:structural_beam"
    },
    "R": {
      "item": "createcityexpansioncore:reinforced_concrete"
    },
    "F": {
      "item": "createcityexpansioncore:industrial_fastener_pack"
    }
  },
  "pattern": [
    "BFB",
    "BFB",
    "RBR",
    "RBR"
  ],
  "result": {
    "count": 24,
    "id": "createcityexpansioncore:mine_frame"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/mine_gateway.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "C": {
      "item": "create:smart_chute"
    },
    "F": {
      "item": "create:brass_funnel"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    },
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    }
  },
  "pattern": [
    "HFH",
    "SCI",
    "SSI"
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:mine_gateway"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/mine_power_port.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "C": {
      "item": "createcityexpansioncore:copper_busbar"
    },
    "T": {
      "item": "createcityexpansioncore:technical_ceramic"
    },
    "N": {
      "item": "electroenergetics:connector"
    },
    "I": {
      "item": "electroenergetics:insulator"
    },
    "P": {
      "item": "createcityexpansioncore:power_distribution_module"
    }
  },
  "pattern": [
    "HCH",
    "TNT",
    "IPI"
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:mine_power_port"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/power_distribution_module.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "C": {
      "item": "createcityexpansioncore:copper_busbar"
    },
    "T": {
      "item": "createcityexpansioncore:technical_ceramic"
    },
    "X": {
      "item": "electroenergetics:transformer_core"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    }
  },
  "pattern": [
    "CTC",
    "TXT",
    "IHI"
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:power_distribution_module"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/technical_block.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "A": {
      "item": "create:andesite_casing"
    },
    "S": {
      "item": "createcityexpansioncore:steel_plate"
    },
    "C": {
      "item": "createcityexpansioncore:copper_busbar"
    },
    "F": {
      "item": "createcityexpansioncore:industrial_fastener_pack"
    }
  },
  "pattern": [
    "ASA",
    "SCS",
    "FAF"
  ],
  "result": {
    "count": 24,
    "id": "createcityexpansioncore:technical_block"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mechanical_crafting/warehouse_gateway.json`

```json
{
  "type": "create:mechanical_crafting",
  "accept_mirrored": false,
  "category": "misc",
  "key": {
    "H": {
      "item": "createcityexpansioncore:heavy_machine_frame"
    },
    "V": {
      "item": "create:item_vault"
    },
    "P": {
      "item": "create:packager"
    },
    "L": {
      "item": "create:stock_link"
    },
    "I": {
      "item": "createcityexpansioncore:industrial_control_unit"
    },
    "B": {
      "item": "create:brass_sheet"
    }
  },
  "pattern": [
    "VPL",
    "HIB",
    "B  "
  ],
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:warehouse_gateway"
  },
  "show_notification": false
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mixing/ceramic_compound.json`

```json
{
  "type": "create:mixing",
  "ingredients": [
    {
      "item": "minecraft:clay_ball"
    },
    {
      "item": "minecraft:clay_ball"
    },
    {
      "item": "minecraft:clay_ball"
    },
    {
      "item": "minecraft:clay_ball"
    },
    {
      "item": "minecraft:quartz"
    },
    {
      "item": "minecraft:flint"
    }
  ],
  "results": [
    {
      "count": 4,
      "id": "createcityexpansioncore:ceramic_compound"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mixing/dry_concrete_mix.json`

```json
{
  "type": "create:mixing",
  "ingredients": [
    {
      "tag": "c:sands"
    },
    {
      "tag": "c:sands"
    },
    {
      "tag": "c:sands"
    },
    {
      "tag": "c:sands"
    },
    {
      "tag": "c:gravels"
    },
    {
      "tag": "c:gravels"
    },
    {
      "tag": "c:gravels"
    },
    {
      "tag": "c:gravels"
    },
    {
      "item": "minecraft:calcite"
    },
    {
      "item": "minecraft:calcite"
    }
  ],
  "results": [
    {
      "count": 8,
      "id": "createcityexpansioncore:dry_concrete_mix"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mixing/industrial_coke.json`

```json
{
  "type": "create:mixing",
  "heat_requirement": "heated",
  "ingredients": [
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    },
    {
      "item": "minecraft:coal"
    }
  ],
  "results": [
    {
      "count": 8,
      "id": "createcityexpansioncore:industrial_coke"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/mixing/steel_billet.json`

```json
{
  "type": "create:mixing",
  "heat_requirement": "heated",
  "ingredients": [
    {
      "tag": "c:ingots/iron"
    },
    {
      "tag": "c:ingots/iron"
    },
    {
      "tag": "c:ingots/iron"
    },
    {
      "tag": "c:ingots/iron"
    },
    {
      "item": "createcityexpansioncore:industrial_coke"
    },
    {
      "item": "createcityexpansioncore:industrial_coke"
    },
    {
      "item": "minecraft:calcite"
    }
  ],
  "results": [
    {
      "count": 4,
      "id": "createcityexpansioncore:steel_billet"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/pressing/rail_profile.json`

```json
{
  "type": "create:pressing",
  "ingredients": [
    {
      "item": "createcityexpansioncore:structural_beam"
    }
  ],
  "results": [
    {
      "count": 2,
      "id": "createcityexpansioncore:rail_profile"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/pressing/steel_plate.json`

```json
{
  "type": "create:pressing",
  "ingredients": [
    {
      "item": "createcityexpansioncore:steel_billet"
    }
  ],
  "results": [
    {
      "count": 1,
      "id": "createcityexpansioncore:steel_plate"
    }
  ]
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/sequenced_assembly/heavy_machine_frame.json`

```json
{
  "type": "create:sequenced_assembly",
  "ingredient": {
    "item": "create:andesite_casing"
  },
  "loops": 2,
  "results": [
    {
      "id": "createcityexpansioncore:heavy_machine_frame"
    }
  ],
  "sequence": [
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_heavy_machine_frame"
        },
        {
          "item": "createcityexpansioncore:steel_plate"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_heavy_machine_frame"
        }
      ]
    },
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_heavy_machine_frame"
        },
        {
          "item": "createcityexpansioncore:structural_beam"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_heavy_machine_frame"
        }
      ]
    },
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_heavy_machine_frame"
        },
        {
          "item": "createcityexpansioncore:industrial_fastener_pack"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_heavy_machine_frame"
        }
      ]
    },
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_heavy_machine_frame"
        },
        {
          "item": "create:andesite_alloy"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_heavy_machine_frame"
        }
      ]
    }
  ],
  "transitional_item": {
    "id": "createcityexpansioncore:incomplete_heavy_machine_frame"
  }
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/sequenced_assembly/industrial_control_unit.json`

```json
{
  "type": "create:sequenced_assembly",
  "ingredient": {
    "item": "create:precision_mechanism"
  },
  "results": [
    {
      "id": "createcityexpansioncore:industrial_control_unit"
    }
  ],
  "sequence": [
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_industrial_control_unit"
        },
        {
          "item": "create:electron_tube"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_industrial_control_unit"
        }
      ]
    },
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_industrial_control_unit"
        },
        {
          "item": "electroenergetics:capacitor"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_industrial_control_unit"
        }
      ]
    },
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_industrial_control_unit"
        },
        {
          "item": "electroenergetics:resistor"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_industrial_control_unit"
        }
      ]
    },
    {
      "type": "create:pressing",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_industrial_control_unit"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_industrial_control_unit"
        }
      ]
    },
    {
      "type": "create:deploying",
      "ingredients": [
        {
          "item": "createcityexpansioncore:incomplete_industrial_control_unit"
        },
        {
          "item": "createcityexpansioncore:copper_busbar"
        }
      ],
      "results": [
        {
          "id": "createcityexpansioncore:incomplete_industrial_control_unit"
        }
      ]
    }
  ],
  "transitional_item": {
    "id": "createcityexpansioncore:incomplete_industrial_control_unit"
  }
}
```

## `src/main/resources/data/createcityexpansioncore/recipe/smelting/technical_ceramic.json`

```json
{
  "type": "minecraft:smelting",
  "category": "misc",
  "ingredient": {
    "item": "createcityexpansioncore:ceramic_compound"
  },
  "result": {
    "count": 1,
    "id": "createcityexpansioncore:technical_ceramic"
  },
  "experience": 0.35,
  "cookingtime": 200
}
```

