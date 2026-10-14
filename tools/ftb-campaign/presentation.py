"""Authored branch layout and recipe-based progression; stable quest/task keys."""
POSITIONS = {}
def place(rows):
    for row in rows.strip().splitlines():
        key,x,y=row.split();POSITIONS[key]=(float(x),float(y))
place('''
welcome 0 0
wood 0 1
tools 0 2
stone 1 2
food 0 3
furnace 2 1
fuel 3 1
rawiron 2 3
iron 4 2
bed -1 4
farm 0 4
animals -0.5 5
bees 0.5 5
fishing -1 3
boat -1 1
storage -1 2
torches 3 0
safety 4 0
shield 5 1
bucket 5 2
armor 5 3
shears 5 4
redstone 5 0
compass 4 4
explore 4 5
enchant 3 4
anvil 3 5
nether 6 2
brewing 6 3
end 7 2
villagers 1 4
ponder 0 0
alloy 1 0
shafts 2 -1
wheel 3 -1
stress 4 -1
casing 2 1
press 4 1
sheets 5 1
quartz 5 2
tube 6 2
mixer 6 0
burner 6 -1
brass 7 -0.5
automation 4 2
wrench 1 1
gearing 2 -2
clutch 2 -3
redcontrol 2 -4
signals 2 -5
sequencegear 1 -4
wind 3 -2
largewheel 4 -2
handcrank 3 -3
meters 4 -3
display 4 -4
clock 3 -4
mill 1 -1
saw 0 -1
fan 0 -2
wash -1 -3
smoke 0 -3
blast 0 -4
haunt -1 -4
compacting 4 0
speedcontrol 8 -1
superheat 6 -2
crafter 8 0
crushing 9 0
deployer 8 1
precision 9 1
pipes 6 3
tank 7 3
spout 6 4
drain 5 4
hose 7 4
steam 8 3
valve 5 3
foodcreate 6 5
glue 2 2
bearing 2 3
piston 1 3
pulley 0 3
gantry 1 4
elevator 0 4
drill 1 2
harvest 2 4
plough 2 5
roller 3 4
contraption 3 3
controller 3 5
decoration 0 1
schematic -1 1
cannon -1 2
equipment 8 2
backtank 9 2
diving 9 3
grip 9 4
potato 8 4
analyzer 0 0
reading 1 0
anomaly 2 -0.5
deposit 2 0.5
site 3 0.5
cityintro 0 0
found 1 0
territory 2 -0.5
worldmap 3 -0.5
roles 2 0.5
project 4 0
mineplan 0 0
steel 1 -1
components 2 -1
assemblyparts 3 -1
mineparts 4 -1
footprint 4 0
assembled 5 0
eepower 1 1
eemeasure 2 1
readiness 6 0
startmine 7 0
firstoutput 8 0
diagnose 9 0
orechain 8 1
chunkintro 0 0
chunkitem 1 0
chunklimits 2 -0.5
chunkcare 3 0
''')
ICONS = dict(welcome='minecraft:compass',wood='minecraft:oak_log',food='minecraft:bread',fuel='minecraft:coal',bed='minecraft:red_bed',farm='minecraft:wheat',animals='minecraft:lead',boat='minecraft:oak_boat',safety='minecraft:lantern',nether='minecraft:obsidian',enchant='minecraft:enchanting_table',brewing='minecraft:brewing_stand',villagers='minecraft:emerald',bees='minecraft:bee_nest',explore='minecraft:map',end='minecraft:ender_eye',ponder='create:schematic_and_quill',stress='create:stressometer',automation='create:mechanical_belt',wash='minecraft:water_bucket',smoke='minecraft:campfire',blast='minecraft:lava_bucket',haunt='minecraft:soul_campfire',elevator='create:elevator_pulley',contraption='create:mechanical_bearing',signals='minecraft:comparator',decoration='create:andesite_casing',equipment='create:goggles',foodcreate='create:sweet_roll',controller='create:linked_controller',reading='minecraft:compass',anomaly='minecraft:lodestone',deposit='minecraft:raw_iron_block',site='minecraft:filled_map',cityintro='minecraft:bell',found='createcityexpansioncore:city_pylon',territory='minecraft:oak_fence',worldmap='minecraft:map',roles='minecraft:player_head',project='minecraft:book',mineplan='createcityexpansioncore:mine_controller',footprint='minecraft:stone_bricks',assembled='createcityexpansioncore:mine_frame',eepower='electroenergetics:voltmeter',readiness='minecraft:lever',startmine='minecraft:redstone_torch',firstoutput='minecraft:raw_iron',diagnose='electroenergetics:clamp_meter',orechain='create:crushed_raw_iron',chunkintro='minecraft:ender_pearl',chunklimits='minecraft:map',chunkcare='minecraft:clock')

def revise(quests):
    by={q['key']:q for q in quests}
    if 'quartz' not in by:
        quests.append(dict(ch='create',key='quartz',ru='Полированный розовый кварц',en='Polished Rose Quartz',deps=['sheets'],items=['create:polished_rose_quartz'],body_ru='Соедините кварц с восемью порциями красной пыли, затем отполируйте розовый кварц наждачной бумагой. Бумага и песок дают наждачную бумагу. Полировка выполняется вручную; латунь и смеситель для неё не нужны. Кварц вместе с железной пластиной станет электронной лампой.',body_en='Combine quartz with eight portions of redstone dust, then polish the rose quartz with sand paper. Paper and sand make sand paper. Polishing works by hand without brass or a mixer. Combine the polished crystal with an iron sheet for an electron tube.',side=False))
        by={q['key']:q for q in quests}
    changes={'shafts':'alloy','wheel':'shafts','stress':'wheel','press':'stress casing','automation':'press','burner':'sheets','brass':'mixer burner','tube':'sheets quartz','eepower':'mineplan','readiness':'assembled eemeasure','fan':'shafts casing','saw':'shafts casing','mill':'shafts casing','drill':'shafts casing','precision':'deployer sheets','foodcreate':'spout','grip':'equipment','potato':'equipment','backtank':'equipment'}
    for k,deps in changes.items():by[k]['deps']=deps.split()
    ICONS['automation']='create:belt_connector'
    by['quartz']['deps']=['ponder']
    POSITIONS['quartz']=(0,-1)
    POSITIONS['saw']=(-1,0)
    POSITIONS['compacting']=(4,2.8)
    POSITIONS['mill']=(-1,-1)
    POSITIONS['tube']=(5,0)
    by['tube']['body_ru']='Соедините полированный розовый кварц с железной пластиной. Это самостоятельная ветвь после пресса: латунь, горелка и смеситель не требуются. Электронная лампа понадобится магнитному анализатору и ряду управляющих механизмов. Можно отправиться на разведку, продолжая изучать остальные ветви мастерской позже.'
    by['tube']['body_en']='Combine polished rose quartz with an iron sheet. This is a separate branch after the press; brass, a burner and a mixer are unnecessary. Electron tubes are used in the magnetic analyzer and control mechanisms. Begin surveying now and return to the other workshop branches later.'
    replacements={'CCEC ':'',' CCEC':'','City Pylon':'городской пилон','WorldMap':'Карта мира','Solo-игрок':'Одиночный игрок','JEI/Ponder EE':'справочнике рецептов и наглядных демонстрациях электрических устройств','JEI':'справочнику рецептов','Ponder':'наглядным демонстрациям','ElectroEnergetics':'Электроэнергетика','EE':'электроэнергетики','крафтить':'изготавливать','крафта':'изготовления','крафт':'изготовление','деплойер':'механическая рука','Деплойер':'Механическая рука','депо':'рабочая площадка','Депо':'Рабочая площадка','Чанклоадер':'Загрузчик чанка','чанклoader':'загрузчик чанка','чанклоадер':'загрузчик чанка','250 mB':'250 мБ','редстоун':'красная пыль','Редстоун':'Красная пыль'}
    for q in quests:
        for field in ('ru','body_ru'):
            for old,new in replacements.items():q[field]=q[field].replace(old,new)
        for field in ('en','body_en'):q[field]=q[field].replace('CCEC ','').replace(' CCEC','')
    by['ponder']['ru']='Рецепты и наглядные демонстрации'
    by['gearing']['ru']='Соотношение скоростей'
    by['clutch']['ru']='Отключение и смена направления'
    by['spout']['ru']='Наполнение предметов жидкостью'
    by['drain']['ru']='Слив жидкости из предметов'
    by['worldmap']['ru']='Карта мира: видеть регион'
    by['eepower']['ru']='Электрическая сеть'
    assert set(POSITIONS)==set(by)
