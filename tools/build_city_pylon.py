"""Deterministic City Pylon authoring/export. Requires Python 3, Pillow and numpy.

The bbmodel is the editable deliverable; JSON parts are renderer inputs, not a
replacement for a BlockEntityRenderer. Run from any directory.
"""
from pathlib import Path
import base64
import json
import math
import uuid
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/city_pylon'
OUT.mkdir(parents=True, exist_ok=True)
NS = 'createcityexpansioncore'
ASSETS = OUT / 'runtime/assets' / NS
PARTS = ASSETS / 'models/block/city_pylon'
TEX = ASSETS / 'textures/block'
PARTS.mkdir(parents=True, exist_ok=True)
TEX.mkdir(parents=True, exist_ok=True)

def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'createcityexpansioncore/city_pylon/' + name))

def save(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

# Original pixel patterns using palettes studied in Create 6.0.10's brass_block,
# railway_casing, industrial_iron_block and copper_casing. No copied texture pixels.
MATERIALS = {
    'steel': (63, 62, 66), 'brass': (206, 160, 90),
    'edge': (103, 105, 101), 'dark': (30, 33, 38),
    'copper': (178, 98, 71), 'ceramic': (170, 168, 140),
    'screen': (10, 23, 25), 'green': (28, 63, 51),
    'amber': (84, 49, 16), 'red': (67, 24, 23),
    'indicator': (38, 54, 47), 'rubber': (23, 26, 25),
    'panel': (48, 54, 57), 'gear': (191, 142, 85),
    'shaft': (106, 115, 111), 'wood': (59, 43, 31),
}
GLOW = {'screen': (39, 106, 112), 'green': (78, 161, 115),
        'amber': (219, 145, 48), 'red': (188, 57, 42),
        'indicator': (104, 170, 147)}
atlas = Image.new('RGBA', (64, 64))
emissive = Image.new('RGBA', (64, 64))
tiles = {}
for i, (name, col) in enumerate(MATERIALS.items()):
    tx, ty = (i % 4)*16, (i // 4)*16
    tiles[name] = (tx, ty)
    d = ImageDraw.Draw(atlas)
    d.rectangle((tx, ty, tx+15, ty+15), fill=(*col, 255))
    # Discrete colour clusters, stepped edge wear and tool marks; no random noise.
    hi = tuple(min(255, c+15) for c in col)
    lo = tuple(max(0, c-12) for c in col)
    def rect(box, color):
        x,y,X,Y=box
        d.rectangle((tx+x,ty+y,tx+X,ty+Y),fill=(*color,255))
    if name in ('brass','gear','copper'):
        palette = ([(114,71,49),(158,105,71),(191,142,85),(215,170,94),(247,203,108),(255,235,140)]
                   if name != 'copper' else
                   [(99,54,44),(132,65,48),(154,80,56),(178,98,71),(214,123,91),(227,150,113)])
        rect((0,0,15,0),palette[4]); rect((0,1,0,15),palette[3])
        rect((1,1,15,1),palette[3]); rect((1,2,15,3),palette[2])
        rect((2,5,8,6),palette[3]); rect((7,4,13,4),palette[2])
        rect((5,8,14,9),palette[3]); rect((1,11,6,12),palette[2])
        rect((9,12,15,13),palette[3]); rect((0,15,15,15),palette[0])
        rect((15,2,15,14),palette[1])
        for box in ((1,0,3,0),(8,0,10,0),(0,8,0,9)):
            rect(box,palette[5])
        rect((3,7,4,7),palette[1]); rect((4,6,5,6),palette[4])
        rect((11,11,12,11),palette[1])
    elif name in ('steel','edge','shaft','dark','rubber','wood'):
        # Low-frequency rectangular clusters preserve the Minecraft texel scale.
        shift = 8 if name not in ('rubber','dark') else 3
        for row in range(0,16,3):
            for column in range(0,16,4):
                delta = (((row//3+column//4*2)%4)-1)*shift
                c = tuple(max(0,min(255,v+delta)) for v in col)
                rect((column,row,min(15,column+3),min(15,row+2)),c)
        rect((0,0,15,0),hi); rect((0,1,0,15),hi)
        rect((0,15,15,15),lo); rect((15,1,15,14),lo)
        if name not in ('dark','rubber','wood'):
            rect((3,4,5,4),hi);rect((10,10,12,10),lo)
            rect((11,9,12,9),hi)
    elif name == 'panel':
        # Four reusable 8x8 panel patches; UVs select the entire framed patch.
        for ox,oy in ((0,0),(8,0),(0,8),(8,8)):
            rect((ox,oy,ox+7,oy+7),(30,33,38))
            rect((ox+1,oy+1,ox+6,oy+6),(53,59,62))
            rect((ox+2,oy+2,ox+5,oy+5),(48,54,57))
            rect((ox+2,oy+4,ox+4,oy+5),(43,49,53))
            rect((ox+1,oy+1,ox+6,oy+1),(77,81,80))
            for px,py in ((1,1),(6,1),(1,6),(6,6)):
                rect((ox+px,oy+py,ox+px,oy+py),(151,145,120))
    elif name == 'ceramic':
        rect((0,0,15,0),hi);rect((0,1,0,15),hi)
        for y in (4,9,14): rect((1,y,15,y),lo)
    else:
        e = ImageDraw.Draw(emissive)
        e.rectangle((tx, ty, tx+15, ty+15), fill=(*GLOW[name], 255))
        if name == 'screen':
            # Low contrast phosphor bands, not wide bright stripes.
            for y in range(1, 16, 2):
                d.line((tx, ty+y, tx+15, ty+y), fill=(13, 29, 31, 255))
                e.line((tx, ty+y, tx+15, ty+y), fill=(34, 94, 99, 255))
            rect((0,0,0,3),(23,40,41))
        else:
            # Reusable 4x4 glass lens, with a dark edge and a two-pixel reflection.
            rect((0,0,3,3),tuple(max(0,c-14) for c in col))
            rect((1,1,2,2),col);rect((1,0,2,0),hi);rect((0,1,0,1),hi)
            lit=GLOW[name]
            e.rectangle((tx,ty,tx+3,ty+3),fill=(*(int(c*.65) for c in lit),255))
            e.rectangle((tx+1,ty+1,tx+2,ty+2),fill=(*lit,255))
            e.point((tx+1,ty),fill=(*(min(255,c+35) for c in lit),255))
atlas.save(OUT / 'city_pylon.png')
emissive.save(OUT / 'city_pylon_emissive.png')
atlas.save(TEX / 'city_pylon.png')
emissive.save(TEX / 'city_pylon_emissive.png')

groups, nodes, elements, materials, membership = {}, {}, [], {}, {}
def group(name, parent=None, pivot=(8, 0, 8)):
    groups[name] = {'name': name, 'origin': list(pivot), 'rotation': [0, 0, 0],
                    'uuid': uid(name), 'export': True, 'isOpen': True,
                    'visibility': True, 'color': len(groups) % 8}
    nodes[name] = {'uuid': uid(name), 'isOpen': True, 'children': []}
    membership[name] = []
    if parent:
        nodes[parent]['children'].append(nodes[name])

group('city_pylon_root')
for name in ('base', 'main_body', 'front_panel', 'top_module', 'mechanical_section', 'side_details', 'back_panel'):
    group(name, 'city_pylon_root')
for name in ('screen_frame', 'screen_surface', 'status_lamp_green', 'status_lamp_amber', 'status_lamp_red', 'selector_knob'):
    group(name, 'front_panel', (8, 9.5, 2.5) if name != 'selector_knob' else (6.1, 7.25, 2.6))
group('top_ring', 'top_module', (8, 14.3, 9))
group('top_indicator', 'top_module', (8, 14.3, 9))
group('small_gear', 'mechanical_section', (10.6, 4.8, 3.6))

FACES = ('north', 'east', 'south', 'west', 'up', 'down')
def cube(g, name, a, b, mat, rotation=None, pivot=None):
    dx, dy, dz = [b[i]-a[i] for i in range(3)]
    tx, ty = tiles[mat]
    faces = {}
    for face in FACES:
        w, h = (dx, dy) if face in ('north', 'south') else (dz, dy) if face in ('east', 'west') else (dx, dz)
        uv = [tx, ty, tx+max(w, 1), ty+max(h, 1)]
        if mat == 'panel' and min(w,h)>3:
            uv = [tx,ty,tx+8,ty+8]
        if mat in ('green','amber','red','indicator'):
            uv = [tx,ty,tx+4,ty+4]
        faces[face] = {'uv': uv, 'texture': 0}
    el = {'name': name, 'type': 'cube', 'uuid': uid(name), 'from': list(a), 'to': list(b),
          'autouv': 0, 'box_uv': False, 'rescale': False, 'locked': False,
          'rotation': rotation or [0, 0, 0], 'origin': list(pivot or groups[g]['origin']),
          'faces': faces, 'color': list(groups).index(g) % 8, 'visibility': True, 'export': True}
    elements.append(el)
    materials[el['uuid']] = mat
    nodes[g]['children'].append(el['uuid'])
    membership[g].append(el)

cube('base','foundation',(1,0,1),(15,1,15),'dark')
cube('base','cast_base',(1,1,1),(15,2,15),'steel')
cube('base','plinth_lip',(1.5,2,1.5),(14.5,2.5,14.5),'edge')
for x in (1.5, 12.5):
    for z in (1.5, 12.5):
        cube('base',f'anchor_{x}_{z}',(x,2.5,z),(x+2,2.85,z+2),'brass')
cube('main_body','central_casting',(3,2.5,5),(13,12.1,13),'steel')
cube('main_body','upper_shoulder',(2.8,11.8,4.5),(13.2,12.6,13.5),'edge')
cube('main_body','top_cap',(3.5,12.6,5),(12.5,13,13),'steel')
for x in (2.25,12.5):
    for z in (4.3,12.3):
        cube('main_body',f'upright_{x}_{z}',(x,2.5,z),(x+1.25,11.9,z+1.2),'brass')
        cube('main_body',f'collar_{x}_{z}',(x-.15,3,z-.1),(x+1.4,4,z+1.3),'edge')

cube('front_panel','console_body',(3,7,3.1),(13,12,5.2),'steel')
cube('front_panel','console_face',(3.35,7.2,2.8),(12.65,11.75,3.1),'panel')
cube('front_panel','console_lower_lip',(3,6.7,2.5),(13,7.1,4.8),'brass')
cube('front_panel','console_rain_hood',(2.8,11.95,2.6),(13.2,12.4,5.2),'brass')
for x in (3.15,12.35):
    cube('front_panel',f'console_cheek_{x}',(x,7,2.7),(x+.5,11.95,3.6),'edge')
cube('screen_frame','screen_socket',(4,8.15,2.45),(10.45,11.6,2.85),'dark')
for name,a,b in (
    ('top',(3.9,11.45,2.2),(10.55,11.8,2.8)),
    ('bottom',(3.9,8.05,2.2),(10.55,8.4,2.8)),
    ('left',(3.9,8.4,2.2),(4.25,11.45,2.8)),
    ('right',(10.2,8.4,2.2),(10.55,11.45,2.8))):
    cube('screen_frame','bezel_'+name,a,b,'brass')
cube('screen_surface','recessed_dark_glass',(4.25,8.4,2.39),(10.2,11.45,2.44),'screen')
for name,y in (('green',10.75),('amber',9.5),('red',8.25)):
    cube('front_panel','lamp_socket_'+name,(11, y-.1,2.45),(12.2,y+1,2.85),'dark')
    cube('status_lamp_'+name,'lamp_lens_'+name,(11.15,y+.05,2.15),(12.05,y+.85,2.5),name)
cube('front_panel','switch_plate',(5.2,7.05,2.35),(7.1,7.95,2.85),'dark')
cube('selector_knob','selector_grip',(5.5,7.18,1.95),(6.7,7.6,2.5),'brass')
cube('front_panel','gauge_backplate',(8.2,7.05,2.4),(9.9,7.95,2.8),'brass')
cube('front_panel','gauge_face',(8.4,7.2,2.3),(9.7,7.8,2.45),'ceramic')
cube('front_panel','gauge_pointer',(9.0,7.25,2.23),(9.2,7.7,2.32),'dark')

cube('mechanical_section','service_recess',(3.4,2.8,3.95),(12.6,6.8,5.1),'dark')
for y in (3.1,4.05,5,5.95):
    cube('mechanical_section',f'intake_louver_{y}',(3.8,y,3.45),(7.9,y+.45,4.5),'edge')
cube('mechanical_section','gear_spindle',(10.35,4.55,3.05),(10.85,5.05,5.1),'shaft')
cube('small_gear','gear_body',(9.65,3.85,3.4),(11.55,5.75,3.9),'gear')
for i in range(4):
    angle = i*45
    # Two perpendicular beams, plus diagonals, make an eight-tooth cog.
    if angle in (0,45):
        # Slight recess differences prevent coplanar textured tooth faces fighting.
        front_z = 3.35 if angle == 0 else 3.33
        cube('small_gear',f'gear_bar_h_{angle}',(8.95,4.4,front_z),(12.25,5.2,3.85),'gear', [0,0,angle],(10.6,4.8,3.6))
        cube('small_gear',f'gear_bar_v_{angle}',(10.2,3.15,front_z-.01),(11,6.45,3.85),'gear', [0,0,angle],(10.6,4.8,3.6))
cube('small_gear','gear_hub',(10.15,4.35,3.15),(11.05,5.25,3.4),'steel')
cube('mechanical_section','service_crossbar',(3.3,2.9,3.1),(12.7,3.35,4.5),'brass')

for x in (2.65,13):
    cube('side_details',f'service_hatch_{x}',(x,5,6),(x+.35,10.7,11.7),'panel')
    for z in (6.1,10.7):
        cube('side_details',f'hatch_rib_{x}_{z}',(x-.15,5.3,z),(x+.5,10.4,z+.5),'edge')
    cube('side_details',f'conduit_vertical_{x}',(x-.25,3.3,11.65),(x+.5,9.8,12.35),'copper')
    cube('side_details',f'conduit_elbow_{x}',(x-.25,3.3,9.5),(x+.5,4,12.35),'copper')
    for y in (4.4,8.7):
        cube('side_details',f'conduit_clamp_{x}_{y}',(x-.35,y,11.5),(x+.6,y+.45,12.5),'dark')
cube('back_panel','maintenance_hatch',(4,4,13),(12,10.9,13.35),'panel')
for y in (7.5,8.5,9.5):
    cube('back_panel',f'exhaust_{y}',(5,y,13.35),(11,y+.35,13.65),'dark')
cube('back_panel','socket_plate',(6,4.5,13.35),(10,6.8,13.65),'brass')
cube('back_panel','ceramic_connector',(7,5,13.65),(9,6.4,14.05),'ceramic')
cube('back_panel','cable_entry',(7.4,5.25,14.05),(8.6,6.15,14.4),'rubber')

cube('top_module','beacon_mount',(5.7,13,6.7),(10.3,13.4,11.3),'brass')
cube('top_module','beacon_bearing',(6.6,13.4,7.6),(9.4,13.8,10.4),'dark')
cube('top_indicator','protected_lens',(7.25,13.8,8.25),(8.75,15.5,9.75),'indicator')
cube('top_module','beacon_cap',(6.85,15.5,7.85),(9.15,15.9,10.15),'steel')
for x in (6.65,9):
    cube('top_module',f'lens_guard_{x}',(x,13.3,8.65),(x+.35,15.55,9.35),'brass')
# An open annular mechanical crown with four chamfered corners.
for name,a,b in (
    ('north',(6,14,5.7),(10,14.65,6.3)),('south',(6,14,11.7),(10,14.65,12.3)),
    ('west',(4.7,14,7),(5.3,14.65,11)),('east',(10.7,14,7),(11.3,14.65,11))):
    cube('top_ring','crown_'+name,a,b,'brass')
for x,z,angle in ((5.7,6.7,45),(10.3,6.7,-45),(5.7,11.3,-45),(10.3,11.3,45)):
    cube('top_ring',f'crown_corner_{x}_{z}',(x-.3,14,z-1),(x+.3,14.65,z+1),'brass',[0,angle,0],(x,14.3,z))
cube('top_ring','crown_index',(7.65,14.65,5.65),(8.35,14.95,6.35),'edge')

texture = {'path':'city_pylon.png','name':'city_pylon.png','folder':'block','namespace':NS,
           'id':'0','uuid':uid('atlas'),'render_mode':'default','render_sides':'auto',
           'mode':'bitmap','saved':True,'relative_path':'city_pylon.png',
           'uv_width':64,'uv_height':64,'width':64,'height':64,
           'source':'data:image/png;base64,'+base64.b64encode((OUT/'city_pylon.png').read_bytes()).decode()}

def animation(name, length, loop, tracks):
    animators = {}
    for g, keys in tracks.items():
        frames = []
        for t, r in keys:
            frames.append({'channel':'rotation','data_points':[dict(zip(('x','y','z'),map(str,r)))],
                           'uuid':uid(f'{name}/{g}/{t}'),'time':t,'color':-1,'interpolation':'linear'})
        animators[uid(g)] = {'name':g,'type':'bone','keyframes':frames}
    return {'uuid':uid(name),'name':name,'loop':loop,'length':length,'snapping':20,
            'override':False,'animators':animators}

model = {'meta':{'format_version':'5.0','model_format':'free','box_uv':False},
         'name':'city_pylon','model_identifier':'city_pylon','visible_box':[1,1,0],
         'resolution':{'width':64,'height':64},'elements':elements,'groups':list(groups.values()),
         'outliner':[nodes['city_pylon_root']], 'textures':[texture],
         'animations':[
             animation('idle_mechanical_reference',60,'loop',{
                 'top_ring':[(0,[0,0,0]),(60,[0,360,0])],
                 'small_gear':[(0,[0,0,0]),(60,[0,0,-720])]}),
             animation('open_mechanical_reference',1.5,'once',{
                 'selector_knob':[(0,[0,0,0]),(.4,[0,0,22.5]),(1.5,[0,0,0])],
                 'top_ring':[(0,[0,0,0]),(1.5,[0,24,0])]}),
             animation('level_up_mechanical_reference',1.6,'once',{
                 'selector_knob':[(0,[0,0,0]),(.4,[0,0,-22.5]),(.8,[0,0,0]),(1.6,[0,0,0])]})]}
save(OUT/'city_pylon.bbmodel', model)

ANIMATED = ['screen_surface','status_lamp_green','status_lamp_amber','status_lamp_red',
            'top_ring','top_indicator','small_gear','selector_knob']
def java_element(el):
    result = {'from':el['from'],'to':el['to'],'faces':{
        face:{'uv':[v/4 for v in data['uv']],'texture':'#atlas'} for face,data in el['faces'].items()}}
    for axis, angle in zip('xyz',el['rotation']):
        if angle:
            result['rotation'] = {'origin':el['origin'],'axis':axis,'angle':angle,'rescale':False}
    return result

def java_model(els, emissive_texture=False):
    return {'ambientocclusion':not emissive_texture,
            'textures':{'atlas':NS+':block/city_pylon'+('_emissive' if emissive_texture else ''),
                        'particle':NS+':block/city_pylon'},
            'elements':[java_element(e) for e in els]}

static = [e for g, els in membership.items() if g not in ANIMATED for e in els]
save(PARTS/'static_body.json',java_model(static))
for g in ANIMATED:
    save(PARTS/(g+'.json'),java_model(membership[g]))
    if g in GLOW or g.startswith('status_lamp') or g in ('screen_surface','top_indicator'):
        if g != 'top_ring':
            save(PARTS/(g+'_emissive.json'),java_model(membership[g],True))
# Complete inventory/off-state fallback; runtime world rendering must use the parts.
inventory = java_model(elements)
inventory['gui_light'] = 'side'
inventory['display'] = {
    'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.68,.68,.68]},
    'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.3,.3,.3]},
    'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.65,.65,.65]},
    'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375]*3},
    'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4]*3}}
save(PARTS/'inventory.json',inventory)
manifest = {'units':'1/16 block', 'front':'north (-Z)', 'bounds':[[1,0,1],[15,15.9,15]],
            'atlas_size':[64,64],'animated_groups':ANIMATED,
            'groups':{g:{'pivot':v['origin'],'cube_count':len(membership[g])} for g,v in groups.items()},
            'static_model':NS+':block/city_pylon/static_body',
            'runtime_parts':{g:NS+':block/city_pylon/'+g for g in ANIMATED},
            'emissive_groups':['screen_surface','status_lamp_green','status_lamp_amber','status_lamp_red','top_indicator']}
save(OUT/'integration_manifest.json',manifest)

# Orthographic, textured, depth-buffered render of the actual cube geometry.
# These are previews, not fabricated screenshots of Blockbench/Minecraft.
FACE_CORNERS = {'north':[0,1,2,3],'south':[5,4,7,6],'west':[4,0,3,7],
                'east':[1,5,6,2],'up':[3,2,6,7],'down':[4,5,1,0]}
def rotate(points, pivot, angles):
    points = np.array(points,float)-pivot
    for axis, degrees in enumerate(angles):
        t = math.radians(degrees)
        c,s = math.cos(t),math.sin(t)
        m = np.eye(3)
        j,k = [(1,2),(2,0),(0,1)][axis]
        m[j,j]=m[k,k]=c; m[j,k]=-s; m[k,j]=s
        points = points @ m.T
    return points+pivot

def render(filename, azimuth, elevation, active=True):
    W,H = 800,850
    color = np.zeros((H,W,3),dtype=np.uint8); color[:]=(27,33,37)
    depth = np.full((H,W),-np.inf)
    a,e = math.radians(azimuth),math.radians(elevation)
    right=np.array([math.cos(a),0,math.sin(a)])
    toward=np.array([math.sin(a)*math.cos(e),math.sin(e),-math.cos(a)*math.cos(e)])
    up=np.cross(right,toward)
    center=np.array([8,7.5,8]); scale=37
    base_tex=np.array(atlas)[:,:,:3]; glow_tex=np.array(emissive)[:,:,:3]
    light=np.array([-.4,.85,-.6]); light/=np.linalg.norm(light)
    for el in elements:
        x,y,z=el['from']; X,Y,Z=el['to']
        verts=rotate([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],np.array(el['origin']),el['rotation'])
        for face, ids in FACE_CORNERS.items():
            vs=verts[ids]
            normal=np.cross(vs[1]-vs[0],vs[2]-vs[0]); normal/=np.linalg.norm(normal)
            # FACE_CORNERS have inward winding; invert for front-face culling.
            normal=-normal
            if normal@toward <= 0: continue
            shade=.63+.37*max(0,normal@light)
            mat=materials[el['uuid']]
            tex=glow_tex if active and mat in ('screen','green','indicator') else base_tex
            if active and mat in ('screen','green','indicator'): shade=1
            p=vs-center
            proj=np.stack([W/2+p@right*scale,H/2-p@up*scale,p@toward],axis=1)
            u,v,U,V=el['faces'][face]['uv']
            uv=np.array([[u,V],[U,V],[U,v],[u,v]])
            for inds in ([0,1,2],[0,2,3]):
                tri=proj[inds]; tuv=uv[inds]
                xmin=max(0,int(np.floor(min(tri[:,0])))); xmax=min(W-1,int(np.ceil(max(tri[:,0]))))
                ymin=max(0,int(np.floor(min(tri[:,1])))); ymax=min(H-1,int(np.ceil(max(tri[:,1]))))
                if xmin>xmax or ymin>ymax: continue
                xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
                x0,y0=tri[0,:2]; x1,y1=tri[1,:2]; x2,y2=tri[2,:2]
                den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
                if abs(den)<1e-8:continue
                w0=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den
                w1=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den; w2=1-w0-w1
                zz=w0*tri[0,2]+w1*tri[1,2]+w2*tri[2,2]
                section=depth[ymin:ymax+1,xmin:xmax+1]
                mask=(w0>=-1e-7)&(w1>=-1e-7)&(w2>=-1e-7)&(zz>section)
                uu=np.clip((w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0]).astype(int),0,63)
                vv=np.clip((w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]).astype(int),0,63)
                section[mask]=zz[mask]
                color[ymin:ymax+1,xmin:xmax+1][mask]=(tex[vv,uu]*shade).astype(np.uint8)[mask]
    im=Image.fromarray(color)
    d=ImageDraw.Draw(im)
    try: font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',16)
    except OSError: font=ImageFont.load_default()
    d.text((28,25),'CITY PYLON / GEOMETRY PREVIEW',font=font,fill=(193,176,139))
    d.text((28,H-48),'64px atlas | '+('idle lighting reference' if active else 'unpowered')+' | 1 block',font=font,fill=(133,151,155))
    im.save(OUT/filename)

render('preview_front.png',0,0)
render('preview_isometric.png',35,25)
render('preview_back.png',-145,23,False)
render('preview_off.png',35,25,False)

# Validate partition, UVs, pivots and geometric envelope at all ring angles.
ids=[e['uuid'] for e in elements]
assert len(ids)==len(set(ids))
assert len(static)+sum(len(membership[g]) for g in ANIMATED)==len(elements)
assert not set(e['uuid'] for e in static)&set(e['uuid'] for g in ANIMATED for e in membership[g])
assert all(0<=v<=64 for el in elements for f in el['faces'].values() for v in f['uv'])
for el in elements:
    a,b=el['from'],el['to']
    assert all(a[i]<b[i] for i in range(3))
    vs=rotate([[x,y,z] for x in (a[0],b[0]) for y in (a[1],b[1]) for z in (a[2],b[2])], np.array(el['origin']),el['rotation'])
    assert np.min(vs)>=0 and np.max(vs)<=16, el['name']
for el in membership['top_ring']:
    a,b=el['from'],el['to']
    vs=rotate([[x,y,z] for x in (a[0],b[0]) for y in (a[1],b[1]) for z in (a[2],b[2])],np.array(el['origin']),el['rotation'])
    for angle in range(0,360,5):
        points=rotate(vs,np.array(groups['top_ring']['origin']),[0,angle,0])
        assert points.min()>=0 and points.max()<=16
report={'cube_count':len(elements),'groups':len(groups),'animated_groups':len(ANIMATED),
        'checks':['unique UUIDs','nondegenerate cuboids','all UVs inside 64x64 atlas',
                  'static/dynamic partition without duplicates','rotated geometry inside block',
                  'ring rotation remains inside block (5 degree samples)'],
        'limitations':['Not verified in Minecraft','Runtime renderer and city event binding are not implemented']}
save(OUT/'validation.json',report)
print(json.dumps(report,ensure_ascii=False,indent=2))
