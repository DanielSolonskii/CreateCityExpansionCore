"""Mine equipment: shared paintable shell with purpose-made functional fittings.
Cargo's exporter is reused, not its decorative graphics for functional materials.
Run with Python, Pillow and numpy. Sources are portable Blockbench projects.
"""
from pathlib import Path
import json
import build_cargo_models as cargo
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/mine_equipment'
ASSETS = ROOT / 'src/main/resources/assets/createcityexpansioncore'
PALETTE = {
    'white': (218, 223, 222), 'orange': (205, 112, 46), 'magenta': (166, 72, 145),
    'light_blue': (89, 157, 191), 'yellow': (214, 179, 63), 'lime': (115, 167, 63),
    'pink': (208, 120, 153), 'gray': (65, 72, 76), 'light_gray': (151, 155, 152),
    'cyan': (49, 128, 137), 'purple': (111, 75, 149), 'blue': (68, 99, 162),
    'brown': (117, 80, 55), 'green': (91, 119, 61), 'red': (169, 66, 56), 'black': (39, 44, 48),
}
FACES = {'west': (0, False), 'east': (0, True), 'down': (1, False),
         'up': (1, True), 'north': (2, False), 'south': (2, True)}


def shell(name):
    m = cargo.Model(name)
    m.cube('body', 'sheet_metal', (0,0,0), (16,16,16), 'wood')
    intervals = [(0,1), (1,15), (15,16)]
    for face, (axis, positive) in FACES.items():
        for u in range(3):
            for v in range(3):
                if u == v == 1:
                    continue
                a, b = list(intervals[u]), list(intervals[v])
                lower, upper = [a[0], b[0]], [a[1], b[1]]
                lower.insert(axis, 16 if positive else -.035)
                upper.insert(axis, 16.035 if positive else 0)
                m.cube('trim', f'trim_{face}_{u}_{v}', lower, upper, 'edge')
    return m


def screen(m, controller=False):
    bottom = 4 if controller else 1.5
    bezel = 'control_bezel'
    m.cube('display', 'dark_glass', (1.5,bottom,-.01), (14.5,14.5,.9), 'screen')
    m.cube('display', 'left_bezel', (.75,bottom-.5,-.05), (1.5,15,.95), bezel)
    m.cube('display', 'right_bezel', (14.5,bottom-.5,-.05), (15.25,15,.95), bezel)
    m.cube('display', 'top_bezel', (1.5,14.5,-.05), (14.5,15,.95), bezel)
    m.cube('display', 'bottom_bezel', (1.5,bottom-.5,-.05), (14.5,bottom,.95), bezel)
    if controller:
        m.cube('controls', 'recessed_control_deck', (1.5,.75,-.08), (14.5,3.35,.4), 'machine_gasket')
        m.cube('controls', 'button_socket', (5.5,.9,-.22), (10.5,3.25,-.08), 'control_bezel')
        m.cube('controls', 'start_stop_button', (6,1.25,-.5), (10,2.95,-.22), 'button_side', {'north':'control_button'})
        m.cube('controls', 'engraved_operation_plate', (11,1.2,-.12), (14,2.9,-.08), 'machine_steel', {'north':'control_label'})
        m.cube('controls', 'status_lamp_mount', (2.2,1.2,-.25), (4.3,2.9,-.08), 'control_bezel')
        m.cube('controls', 'status_lamp', (2.65,1.65,-.32), (3.85,2.45,-.25), 'control_indicator')
        for x in (1,14.55):
            for y in (4,14.2):
                m.cube('fasteners',f'bezel_screw_{x}_{y}',(x,y,-.12),(x+.4,y+.4,-.05),
                       'machine_steel',{'north':'machine_screw'})
    else:
        # Keep the glass and telemetry plane clear; fittings stay outside its aperture.
        m.cube('display_frame','upper_visor',(1.25,14.65,-.5),(14.75,15.15,-.05),'control_bezel')
        m.cube('display_frame','lower_lip',(1.25,1,-.28),(14.75,1.5,-.05),'machine_steel')
        for x in (1,14.55):
            for y in (1.6,14.1):
                m.cube('fasteners',f'display_screw_{x}_{y}',(x,y,-.13),(x+.4,y+.4,-.05),
                       'machine_steel',{'north':'machine_screw'})
        for x in (.55,14.95):
            m.cube('display_frame',f'side_retainer_{x}',(x,6,-.22),(x+.5,10,-.05),'machine_steel')


def technical_details(m):
    c=m.cube
    c('service_panel','panel_gasket',(2.25,2.5,-.08),(13.75,13.5,.2),'machine_gasket')
    c('service_panel','vent_recess',(3.3,4,-.16),(12.7,12,-.08),'machine_gasket')
    for name,a,b in (
        ('left',(2.5,3,-.4),(3.3,13,.1)),
        ('right',(12.7,3,-.4),(13.5,13,.1)),
        ('header',(3.3,12,-.4),(12.7,13,.1)),
        ('sill',(3.3,3,-.4),(12.7,4,.1)),
    ):
        c('service_panel',name+'_rail',a,b,'control_bezel')
    # Actual stepped metal blades, not a printed vent graphic stretched onto a cube.
    for i,y in enumerate((4.6,6.1,7.6,9.1,10.6)):
        c('louvers',f'blade_{i}',(3.45,y,-.65),(12.55,y+.45,-.16),'machine_steel')
        c('louvers',f'folded_edge_{i}',(3.45,y,-.72),(12.55,y+.16,-.65),'control_bezel')
    for x in (2.65,12.85):
        for y in (3.2,12.3):
            c('fasteners',f'panel_screw_{x}_{y}',(x,y,-.48),(x+.5,y+.5,-.4),
              'machine_steel',{'north':'machine_screw'})


def equipment_atlas(base):
    """Purpose-made material patches; shared panel/trim/glass slots stay byte-identical."""
    atlas = base.copy()
    materials = {
        'ceramic': ('paper',(219,216,194)),
        'contact_copper': ('copper',(177,105,65)),
        'machine_steel': ('iron',(99,110,113)),
        'machine_gasket': ('dark',(35,40,41)),
        'control_bezel': ('brass',(74,86,87)),
        'machine_screw': ('gear',(145,155,154)),
        'gateway_shutter': ('port',(88,101,104)),
        'gateway_motor': ('vents',(64,77,80)),
        'control_label': ('plate',(137,149,140)),
        'control_button': ('green',(75,119,88)),
        'button_side': ('keys',(48,75,57)),
        'control_indicator': ('cyan',(183,147,76)),
        'gateway_rail': ('arrows',(132,130,109)),
    }
    for name,(slot,color) in materials.items():
        cargo.TILES[name] = cargo.TILES[slot]
        tile=Image.new('RGBA',(32,32),color+(255,)); d=ImageDraw.Draw(tile)
        hi=tuple(min(255,c+12) for c in color); lo=tuple(max(0,c-12) for c in color)
        d.line([(0,31),(0,0),(31,0)],fill=hi)
        d.line([(31,1),(31,31),(1,31)],fill=lo)
        if name == 'ceramic':
            # Plain glazed ceramic, no writing or printed cargo-form graphics.
            d.line((3,2,3,29),fill=(232,229,209))
            d.line((28,2,28,29),fill=(204,202,182))
        elif name == 'contact_copper':
            d.line((3,2,3,29),fill=(201,133,84))
            d.line((27,2,27,29),fill=(159,91,57))
        elif name == 'machine_screw':
            d.rectangle((5,5,26,26),fill=(122,134,134))
            d.line((9,16,22,16),fill=(66,77,78),width=2)
        elif name == 'gateway_shutter':
            for x in (6,25):
                d.line((x,3,x,28),fill=lo);d.line((x+1,3,x+1,28),fill=hi)
        elif name == 'gateway_motor':
            for y in (10,20):d.line((4,y,27,y),fill=lo)
        elif name == 'control_button':
            d.line((16,8,16,21),fill=(179,208,172),width=2)
        elif name == 'control_label':
            d.polygon([(6,9),(6,23),(13,16)],fill=(65,81,77))
            d.rectangle((19,10,26,22),fill=(65,81,77))
        elif name == 'control_indicator':
            d.rectangle((4,4,27,27),fill=(209,175,99))
            d.line((7,6,24,6),fill=(231,205,145))
        atlas.paste(tile,cargo.TILES[name])
    return atlas


def power_details(m):
    c=m.cube
    c('terminals','insulating_plate',(1.5,3,-.4),(14.5,13,.35),'machine_gasket')
    for x in (4,12):
        c('terminals',f'mount_{x}',(x-2.3,5.7,-.7),(x+2.3,10.3,-.4),'control_bezel')
        c('terminals',f'ceramic_core_{x}',(x-1.25,6.75,-5.7),(x+1.25,9.25,-.7),'ceramic')
        for i,z in enumerate((-1.6,-3,-4.4)):
            c('terminals',f'ceramic_fin_{x}_{i}',(x-2,6,z-.55),(x+2,10,z),'ceramic')
        c('terminals',f'copper_neck_{x}',(x-.85,7.15,-6.3),(x+.85,8.85,-5.7),'contact_copper')
        c('terminals',f'copper_clamp_{x}',(x-1.5,6.5,-8),(x+1.5,9.5,-6.3),'contact_copper')
        c('fasteners',f'clamp_screw_{x}',(x-.6,7.4,-8.08),(x+.6,8.6,-8),'machine_steel',{'north':'machine_screw'})
    for x in (2,13.3):
        for y in (3.6,11.7):
            c('fasteners',f'base_screw_{x}_{y}',(x,y,-.48),(x+.7,y+.7,-.4),'machine_steel',{'north':'machine_screw'})


def gateway_details(m):
    # Four functional transfer openings, authored independently of cargo sockets.
    for side in ('north','south','east','west'):
        pieces=[
            ('gasket',(2.25,2.5,-.08),(13.75,13.5,.15),'machine_gasket'),
            ('left_leaf',(3.2,3.4,-.22),(7.85,12.6,-.08),'gateway_shutter'),
            ('right_leaf',(8.15,3.4,-.22),(12.8,12.6,-.08),'gateway_shutter'),
            ('left_guide',(2.5,3,-.65),(3.2,13,.1),'gateway_rail'),
            ('right_guide',(12.8,3,-.65),(13.5,13,.1),'gateway_rail'),
            ('threshold',(3.2,2.8,-.7),(12.8,3.4,.1),'machine_steel'),
            ('header',(3.2,12.6,-.6),(12.8,13.2,.1),'machine_steel'),
            ('actuator',(5,13.2,-.85),(11,14.5,.1),'gateway_motor'),
            ('left_lock',(6.65,7,-.55),(7.85,9,-.22),'machine_steel'),
            ('right_lock',(8.15,7,-.55),(9.35,9,-.22),'machine_steel'),
        ]
        for name,a,b,mat in pieces:
            points=[{'north':(x,y,z),'south':(16-x,y,16-z),
                     'east':(16-z,y,x),'west':(z,y,16-x)}[side] for x,y,z in (a,b)]
            m.cube('transfer_gates',side+'_'+name,
                   [min(p[i] for p in points) for i in range(3)],
                   [max(p[i] for p in points) for i in range(3)],mat)
    m.cube('service_hatch','hatch_gasket',(3,16.01,3),(13,16.2,13),'machine_gasket')
    m.cube('service_hatch','steel_cover',(3.4,16.2,3.4),(12.6,16.45,12.6),'machine_steel')
    for x in (5,10.3):
        m.cube('service_hatch',f'handle_foot_{x}',(x,16.45,7),(x+.7,17.1,8),'control_bezel')
    m.cube('service_hatch','lifting_handle',(5,17.1,7),(11,17.6,8),'gateway_rail')


def panel_texture(color, size=32):
    # One repeating 3x3-block sheet, sampled in world space by ConnectedPanelModel.
    # Neutral trim keeps its plain finish; only painted sheets receive seams.
    if color == (54,61,65):
        tile = Image.new('RGBA', (size,size))
        d = ImageDraw.Draw(tile)
        for y in range(size):
            shade = (0, 1, 2, 1, 0, -1, -2, -1)[y % 8]
            d.line((0,y,size-1,y), fill=tuple(c + shade for c in color) + (255,))
        return tile
    tile = Image.new('RGBA', (96,96), color + (255,))
    d = ImageDraw.Draw(tile)
    seam = tuple(round(c * .87) for c in color) + (255,)
    rivet = tuple(round(c * .86) for c in color) + (255,)
    # Endpoints coincide on opposite borders; no artificial frame at tile boundaries.
    for points in (
        [(0,18),(96,18)],
        [(68,0),(68,18)],
        [(0,70),(42,70),(58,54),(58,18)],
        [(42,70),(42,82),(68,94),(68,96)],
        [(58,54),(96,70)],
    ):
        d.line(points, fill=seam, width=1)
    for x,y in ((8,14),(86,24),(52,49),(36,76)):
        d.rectangle((x,y,x+1,y+1), fill=rivet)
    # Exact border samples also avoid a one-pixel discontinuity when wrapping.
    for y in range(96): tile.putpixel((95,y), tile.getpixel((0,y)))
    for x in range(96): tile.putpixel((x,95), tile.getpixel((x,0)))
    return tile if size == 96 else tile.resize((size,size), Image.Resampling.NEAREST)


def export_connected(m, model):
    namespace = 'createcityexpansioncore:block/'
    model['credit'] = 'CCEC industrial sheet metal / art/mine_equipment'
    model['textures']['panel'] = namespace + 'machine_panel/orange'
    model['textures']['particle'] = '#panel'
    base = []
    replacements = []
    for element in model['elements']:
        name = element['name']
        if name == 'sheet_metal':
            for face, (axis, positive) in FACES.items():
                lower, upper = [1,1,1], [15,15,15]
                lower[axis], upper[axis] = (15,16) if positive else (0,1)
                base.append({'from':lower, 'to':upper,
                             'faces':{face:{'texture':'#panel','cullface':face}}})
            continue
        elif name.startswith('trim_'):
            face = name.split('_')[1]
            axis, positive = FACES[face]
            element['from'][axis], element['to'][axis] = (15,16) if positive else (0,1)
            trim = element['faces'][face]
            trim.update(cullface=face, tintindex=1)
            element['faces'] = {face:trim}
            replacements.append({
                'from':element['from'], 'to':element['to'],
                'faces':{face:{'texture':'#panel','cullface':face,'tintindex':2}}
            })
        base.append(element)
    model['elements'] = base + replacements
    cargo.save(ASSETS/'models/block'/(m.name+'.json'), model)
    item = {
        'parent':namespace+m.name, 'elements':base
    }
    if m.name == 'mine_power_port':
        first_person = {'rotation': [80, 0, 22], 'scale': [.4] * 3}
        # The third-person hand renderer applies its own -90° pitch and 180° yaw.
        third_person = {'rotation': [180, 0, 0], 'translation': [0, 2.5, 0], 'scale': [.375] * 3}
        item['display'] = {
            'gui': {'rotation': [120, 0, 135], 'translation': [0, -1.5, 0], 'scale': [.55] * 3},
            'firstperson_righthand': first_person,
            'firstperson_lefthand': first_person,
            'thirdperson_righthand': third_person,
            'thirdperson_lefthand': third_person,
        }
    cargo.save(ASSETS/'models/item'/(m.name+'.json'), item)
    colored = ASSETS/'models/block/machine_panel'
    colored.mkdir(parents=True, exist_ok=True)
    states = {}
    rotations = [('north',0),('east',90),('south',180),('west',270)] if m.name in (
        'mine_controller','mine_power_port','technical_screen','technical_block') else [(None,0)]
    for color in PALETTE:
        path = m.name
        if color != 'orange':
            path = f'machine_panel/{m.name}_{color}'
            cargo.save(ASSETS/'models/block'/(path+'.json'), {
                'parent':namespace+m.name, 'textures':{'panel':namespace+'machine_panel/'+color}
            })
        for facing, rotation in rotations:
            key = f'color={color}'
            if facing:
                key += f',facing={facing}'
            states[key] = {'model':namespace+path, **({'y':rotation} if rotation else {})}
        if m.name == 'mine_power_port':
            for face, pitch in [('up',270),('down',90)]:
                for rotation, yaw in rotations:
                    states[f'color={color},facing={face},vertical_rotation={rotation}'] = {
                        'model':namespace+path, 'x':pitch, **({'y':yaw} if yaw else {})
                    }
    cargo.save(ASSETS/'blockstates'/(m.name+'.json'), {'variants':states})


def painter_icon():
    # Keep equipment regeneration from replacing the authored 3D painter with a flat icon.
    import build_paintgun
    build_paintgun.main()


def symbols():
    image = Image.new('RGBA', (128,16))
    d = ImageDraw.Draw(image)
    # Crossed hammer, lightning, no power, ready, warning. Dedicated original pixel glyphs.
    # Upright T-shaped hammer: steel head and a distinct wooden handle.
    d.rectangle((7,5,9,14),fill='#704a2b')
    d.line((8,6,8,13),fill='#b0804c')
    d.rectangle((3,2,12,5),fill='#78888b')
    d.rectangle((4,2,11,4),fill='#b6c5c8')
    d.line((4,2,11,2),fill='#dde5e5')
    d.line((2,2,13,13),fill='#df6752',width=2)
    bolt = [(9,1),(3,9),(7,9),(6,15),(13,6),(9,6)]
    for origin in (16,32): d.polygon([(x+origin,y) for x,y in bolt],fill='#e6ba62')
    d.line((34,2,45,13),fill='#df6752',width=2)
    d.line((50,8,54,12,61,3),fill='#8bd36b',width=3)
    d.polygon([(72,1),(65,14),(79,14)],fill='#dd9352')
    d.rectangle((71,5,72,9),fill='#302c28');d.rectangle((71,11,72,12),fill='#302c28')
    d.rectangle((80,0,95,15),fill='white')
    image.save(ASSETS/'textures/block/machine_symbols.png')


def main():
    ART.mkdir(parents=True, exist_ok=True)
    cargo.OUT = ART
    atlas = cargo.ATLAS.copy(); draw = ImageDraw.Draw(atlas)
    atlas.paste(panel_texture(PALETTE['orange']), cargo.TILES['wood'])
    atlas.paste(panel_texture((54,61,65)), cargo.TILES['edge'])
    x,y = cargo.TILES['screen']; draw.rectangle((x,y,x+31,y+31),fill=(13,23,19,255))
    cargo.ATLAS = atlas
    detail_atlas = equipment_atlas(atlas)
    models=[]
    frame = shell('mine_frame'); models.append(frame)
    technical = shell('technical_block')
    technical_details(technical)
    models.append(technical)
    controller = shell('mine_controller');screen(controller,True);models.append(controller)
    power = shell('mine_power_port')
    power_details(power)
    models.append(power)
    gateway=shell('mine_gateway')
    gateway_details(gateway);models.append(gateway)
    display=shell('technical_screen');screen(display);models.append(display)
    sheet=Image.new('RGBA',(1200,850),(29,33,35,255)); d=ImageDraw.Draw(sheet)
    for i,m in enumerate(models):
        cargo.ATLAS = detail_atlas if m.name in (
            'mine_controller','mine_power_port','mine_gateway','technical_block','technical_screen') else atlas
        model=m.export()
        d.text(((i%3)*400+20,(i//3)*420+15),m.name,fill=(220,224,214),font_size=20)
        sheet.alpha_composite(cargo.render(model,390,380,yaw=-25,pitch=20),((i%3)*400,(i//3)*420+40))
        export_connected(m, model)
    sheet.convert('RGB').save(ART/'preview.png')
    panels = ASSETS/'textures/block/machine_panel'
    panels.mkdir(parents=True, exist_ok=True)
    for name, color in PALETTE.items():
        panel_texture(color, 96).save(panels/(name+'.png'))
    symbols()
    painter_icon()
    print('Generated six connected machine blocks, 16 panel colors and the painting tool.')


if __name__=='__main__':main()
