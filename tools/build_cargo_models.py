"""Author the cargo equipment family: editable Blockbench projects and Java models.

Run with Python 3 + Pillow + numpy. No downloaded artwork or external textures.
UVs are authored in atlas pixels, then converted to Java's 0..16 coordinates.
The preview rasterizes the exported Java elements, including rotated faces.
"""
from pathlib import Path
import base64
import hashlib
import json
import math
import uuid
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/cargo_equipment'
ASSETS = ROOT / 'src/main/resources/assets/createcityexpansioncore'
NS = 'createcityexpansioncore'
SIZE = 128
OUT.mkdir(parents=True, exist_ok=True)


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, NS + '/cargo_equipment/' + name))


# 32px patches retain low-frequency Minecraft pixel clusters, with dedicated
# graphic patches for the controls rather than a single flat colour per face.
PALETTE = {
    'iron': (76, 76, 79), 'edge': (107, 107, 108),
    'dark': (30, 31, 33), 'brass': (191, 140, 65),
    'wood': (99, 63, 39), 'copper': (169, 86, 51),
    'port': (40, 35, 28), 'gear': (103, 64, 37),
    'arrows': (197, 147, 67), 'screen': (15, 45, 29),
    'paper': (220, 207, 174), 'keys': (181, 167, 135),
    'cyan': (24, 146, 148), 'green': (60, 130, 37),
    'vents': (58, 58, 59), 'plate': (119, 87, 45),
}
TILES = {name: ((i % 4) * 32, (i // 4) * 32) for i, name in enumerate(PALETTE)}


def atlas_image():
    im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    for index, (name, col) in enumerate(PALETTE.items()):
        ox, oy = TILES[name]

        def rect(box, color):
            x, y, X, Y = box
            d.rectangle((ox+x, oy+y, ox+X, oy+Y), fill=tuple(color)+(255,))

        def line(points, color, width=1):
            d.line([(ox+x, oy+y) for x, y in points], fill=tuple(color)+(255,), width=width)

        def poly(points, color):
            d.polygon([(ox+x, oy+y) for x, y in points], fill=tuple(color)+(255,))

        rect((0, 0, 31, 31), col)
        # Deterministic clustered wear. No per-pixel white noise.
        for y in range(2, 30, 3):
            for x in range(2, 30, 5):
                delta = ((x*7+y*3+index*11) % 5-2)*3
                rect((x, y, min(29, x+3), min(29, y+1)),
                     tuple(max(0, min(255, c+delta)) for c in col))
        hi = tuple(min(255, c+25) for c in col)
        lo = tuple(max(0, c-28) for c in col)
        line([(0, 31), (0, 0), (31, 0)], hi)
        line([(31, 1), (31, 31), (1, 31)], lo)
        if name in ('iron', 'edge', 'brass', 'copper'):
            line([(2, 3), (13, 3)], hi)
            line([(19, 26), (28, 26)], lo)
            rect((4, 14, 8, 14), hi)
            rect((23, 8, 25, 9), lo)
        if name == 'wood':
            for y in (7, 15, 23):
                rect((1, y, 30, y), (55, 35, 24))
                rect((1, y+1, 30, y+1), (126, 81, 47))
            for x, y in ((6, 4), (22, 11), (12, 20), (24, 28)):
                rect((x, y, x+5, y), (66, 40, 25))
        elif name == 'port':
            rect((2, 2, 29, 29), (130, 89, 40))
            rect((4, 4, 27, 27), (67, 51, 31))
            rect((6, 6, 25, 25), (20, 23, 22))
            rect((8, 8, 23, 9), (11, 14, 14))
            for x in (8, 13, 18, 23):
                rect((x, 22, x+1, 25), (161, 111, 49))
        elif name == 'gear':
            # Square-tooth silhouette, then stepped octagonal ring and dark hub.
            for x, y, X, Y in ((12, 3, 19, 28), (3, 12, 28, 19),
                               (6, 6, 11, 11), (20, 6, 25, 11),
                               (6, 20, 11, 25), (20, 20, 25, 25)):
                rect((x, y, X, Y), (202, 152, 66))
            poly([(10, 7), (21, 7), (25, 11), (25, 20), (21, 24),
                  (10, 24), (6, 20), (6, 11)], (212, 164, 79))
            rect((12, 12, 19, 19), (73, 52, 31))
            rect((13, 13, 18, 18), (98, 68, 37))
        elif name == 'arrows':
            rect((3, 3, 28, 28), (214, 170, 83))
            for turn in range(4):
                points = [(14, 13), (14, 8), (11, 8), (16, 3),
                          (21, 8), (18, 8), (18, 13)]
                for _ in range(turn):
                    points = [(31-y, x) for x, y in points]
                poly(points, (68, 46, 25))
            rect((14, 14, 17, 17), (100, 67, 31))
        elif name == 'screen':
            rect((1, 1, 30, 30), (10, 34, 23))
            for y in range(3, 29, 3):
                rect((2, y, 29, y), (14, 43, 27))
            ink = (147, 205, 111)
            line([(7, 11), (16, 6), (25, 11), (16, 16), (7, 11),
                  (7, 22), (16, 27), (25, 22), (25, 11)], ink, 2)
            line([(16, 16), (16, 27)], ink)
            line([(12, 8), (21, 13), (21, 17)], (97, 160, 77))
            rect((3, 3, 8, 3), (72, 112, 64))
            rect((24, 27, 28, 28), (89, 165, 69))
        elif name == 'paper':
            rect((3, 3, 28, 28), (233, 222, 191))
            for y, end in ((7, 24), (12, 23), (17, 20), (22, 25)):
                rect((7, y, end, y+1), (139, 119, 86))
            rect((23, 24, 27, 27), (161, 80, 48))
        elif name == 'keys':
            rect((2, 2, 29, 29), (211, 197, 161))
            rect((5, 7, 13, 9), (135, 122, 94))
            rect((4, 26, 28, 29), (141, 126, 96))
        elif name in ('cyan', 'green'):
            colors = ((11, 64, 69), (19, 145, 150), (56, 211, 207), (151, 246, 222)) if name == 'cyan' else (
                (29, 55, 22), (56, 122, 30), (100, 187, 44), (181, 227, 98))
            for inset, color in enumerate(colors):
                rect((2+inset*3, 2, 29-inset*3, 29), color)
            rect((10, 4, 14, 12), colors[-1])
        elif name == 'vents':
            for y in range(5, 28, 5):
                rect((4, y, 27, y+1), (19, 23, 23))
                rect((4, y+2, 27, y+2), (98, 96, 88))
        elif name == 'plate':
            rect((2, 2, 29, 29), (174, 132, 66))
            for y, end in ((9, 24), (14, 20), (19, 25), (24, 16)):
                rect((6, y, end, y+1), (76, 58, 33))
    return im


ATLAS = atlas_image()
DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.66]*3},
    'ground': {'translation': [0, 3, 0], 'scale': [.28]*3},
    'fixed': {'rotation': [0, 180, 0], 'scale': [.65]*3},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.375]*3},
    'thirdperson_lefthand': {'rotation': [75, 225, 0], 'translation': [0, 2.5, 0], 'scale': [.375]*3},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.4]*3},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [.4]*3},
}


class Model:
    def __init__(self, name):
        self.name, self.elements, self.groups = name, [], {}

    def cube(self, group, name, a, b, material, face_materials=None, rotation=None):
        faces = {}
        for face in ('north', 'east', 'south', 'west', 'up', 'down'):
            tile = (face_materials or {}).get(face, material)
            x, y = TILES[tile]
            faces[face] = {'uv': [x, y, x+32, y+32], 'texture': 0}
        el = {'name': name, 'uuid': uid(self.name+'/'+group+'/'+name),
              'type': 'cube', 'from': list(a), 'to': list(b),
              'origin': [8, 8, 8], 'rotation': [0, 0, 0],
              'box_uv': False, 'rescale': False, 'autouv': 0,
              'color': len(self.groups) % 8, 'faces': faces}
        if rotation:
            angle, origin = rotation
            el['rotation'] = [angle, 0, 0]
            el['origin'] = list(origin)
        self.elements.append(el)
        self.groups.setdefault(group, []).append(el['uuid'])

    def frame(self, height=14):
        c = self.cube
        c('housing', 'wooden_inset_body', (1.5, 1, 1.5), (14.5, height, 14.5), 'wood')
        c('frame', 'lower_iron_plinth', (.5, .5, .5), (15.5, 2, 15.5), 'iron')
        c('frame', 'upper_iron_band', (.5, height-1, .5), (15.5, height+.25, 15.5), 'iron')
        for x in (.5, 13.5):
            for z in (.5, 13.5):
                tag = f'{x}_{z}'
                c('frame', 'corner_'+tag, (x, 1, z), (x+2, height, z+2), 'iron')
                c('frame', 'foot_'+tag, (x-.25, 0, z-.25), (x+2.25, 2.5, z+2.25), 'dark')
                c('fasteners', 'top_rivet_'+tag, (x+.65, height+.25, z+.65), (x+1.35, height+.45, z+1.35), 'brass')
        for z in (.8, 14.85):
            c('trim', 'lower_rail_'+str(z), (2.5, 2.25, z), (13.5, 2.65, z+.35), 'brass')
        for x in (.8, 14.85):
            c('trim', 'side_rail_'+str(x), (x, 2.25, 2.5), (x+.35, 2.65, 13.5), 'brass')

    def port(self, side, center_y=7.5):
        # Shape-local socket on north, rotated by coordinate permutation for sides.
        pieces = [
            ('socket_plate', (5, center_y-3, 1.12), (11, center_y+3, 1.5), 'dark'),
            ('socket_inset', (5.7, center_y-2.3, 1.0), (10.3, center_y+2.3, 1.12), 'port'),
            ('top', (4.8, center_y+2.4, .65), (11.2, center_y+3.15, 1.6), 'brass'),
            ('bottom', (4.8, center_y-3.15, .65), (11.2, center_y-2.4, 1.6), 'brass'),
            ('left', (4.8, center_y-2.4, .65), (5.55, center_y+2.4, 1.6), 'brass'),
            ('right', (10.45, center_y-2.4, .65), (11.2, center_y+2.4, 1.6), 'brass')]
        for name, a, b, mat in pieces:
            pts = []
            for x, y, z in (a, b):
                pts.append({'north': (x, y, z), 'south': (16-x, y, 16-z),
                            'east': (16-z, y, x), 'west': (z, y, 16-x)}[side])
            aa = [min(pts[0][i], pts[1][i]) for i in range(3)]
            bb = [max(pts[0][i], pts[1][i]) for i in range(3)]
            self.cube('ports', side+'_'+name, aa, bb, mat)

    def gear_badge(self, y=7):
        self.cube('identity', 'brass_gear_badge', (5.5, y-2.5, 1.2), (10.5, y+2.5, 1.48), 'wood', {'north': 'gear'})

    def export(self):
        dest = OUT / self.name
        dest.mkdir(exist_ok=True)
        tex = dest / (self.name+'.png')
        ATLAS.save(tex)
        target_tex = ASSETS / 'textures/block' / (self.name+'.png')
        target_tex.parent.mkdir(parents=True, exist_ok=True)
        target_tex.write_bytes(tex.read_bytes())
        elements = []
        for e in self.elements:
            j = {'name': e['name'], 'from': e['from'], 'to': e['to'],
                 'faces': {f: {'uv': [v*16/SIZE for v in data['uv']], 'texture': '#atlas'}
                           for f, data in e['faces'].items()}}
            if e['rotation'][0]:
                j['rotation'] = {'origin': e['origin'], 'axis': 'x', 'angle': e['rotation'][0], 'rescale': False}
            elements.append(j)
        java = {'credit': 'CCEC cargo equipment / Blockbench source in art/cargo_equipment',
                'parent': 'minecraft:block/block', 'texture_size': [SIZE, SIZE],
                'ambientocclusion': True, 'gui_light': 'side',
                'textures': {'atlas': NS+':block/'+self.name, 'particle': NS+':block/'+self.name},
                'display': DISPLAY, 'elements': elements}
        save(ASSETS / 'models/block' / (self.name+'.json'), java)
        # format_version 4.10 uses the nested outliner representation supported
        # by Blockbench 4 and 5. Texture is embedded, so the project is portable.
        bb = {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
              'name': self.name, 'model_identifier': self.name,
              'resolution': {'width': SIZE, 'height': SIZE},
              'elements': self.elements,
              'outliner': [{'name': group, 'uuid': uid(self.name+'/group/'+group),
                            'origin': [8, 8, 8], 'rotation': [0, 0, 0],
                            'export': True, 'isOpen': True, 'children': children}
                           for group, children in self.groups.items()],
              'textures': [{'name': self.name+'.png', 'id': '0', 'uuid': uid(self.name+'/atlas'),
                            'namespace': NS, 'folder': 'block', 'mode': 'bitmap',
                            'uv_width': SIZE, 'uv_height': SIZE, 'width': SIZE, 'height': SIZE,
                            'particle': True, 'render_mode': 'default', 'render_sides': 'auto',
                            'source': 'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode()}],
              'display': DISPLAY}
        save(dest / (self.name+'.bbmodel'), bb)
        return java


def router():
    m = Model('cargo_router')
    m.frame(14)
    for side in ('east', 'west', 'south'):
        m.port(side)
    m.gear_badge(7)
    c = m.cube
    c('routing_table', 'table_gasket', (3, 14.25, 3), (13, 14.55, 13), 'dark')
    c('routing_table', 'direction_plate', (3.25, 14.55, 3.25), (12.75, 15.05, 12.75), 'brass', {'up': 'arrows'})
    for x in (3.6, 11.8):
        for z in (3.6, 11.8):
            c('fasteners', f'table_screw_{x}_{z}', (x, 15.05, z), (x+.6, 15.25, z+.6), 'edge')
    c('indicators', 'lamp_mount', (6.7, 10.4, .7), (9.3, 12.8, 1.5), 'dark')
    c('indicators', 'lamp_bezel', (7.05, 10.75, .48), (8.95, 12.45, .75), 'brass')
    c('indicators', 'green_status', (7.4, 11.05, .35), (8.6, 12.1, .5), 'green')
    for x in (3.1, 12.3):
        c('fasteners', 'front_bolt_'+str(x), (x, 10.7, 1.12), (x+.6, 11.3, 1.5), 'brass')
    c('identity', 'serial_plate', (6, 3.1, 1.15), (10, 3.9, 1.5), 'plate')
    return m


def terminal():
    m = Model('cargo_ticker')
    m.frame(8)
    c = m.cube
    c('housing', 'rear_monitor_cabinet', (2.3, 8, 8), (13.7, 14, 14), 'wood')
    c('housing', 'rear_top_cap', (2, 13.8, 9), (14, 14.5, 14.5), 'iron')
    m.gear_badge(5)
    m.port('south', 5)
    # All screen pieces share a pivot; Java's legal 22.5 degree rotation.
    # North-facing normal tilts upwards and the top edge leans towards the back.
    tilt = (-22.5, (8, 9, 7))
    c('monitor', 'monitor_body', (2.4, 8.9, 7), (13.6, 15, 9.8), 'dark', rotation=tilt)
    c('monitor', 'recessed_green_display', (3.9, 10.35, 6.84), (12.1, 13.65, 7.02), 'screen', rotation=tilt)
    # Taller UV aspect is preserved with an almost square central display.
    c('monitor', 'screen_glass', (4.4, 9.95, 6.76), (11.6, 14.15, 6.87), 'dark', {'north': 'screen'}, tilt)
    for name, a, b in (
        ('left_bezel', (3.3, 9.45, 6.55), (4.35, 14.65, 7.12)),
        ('right_bezel', (11.65, 9.45, 6.55), (12.7, 14.65, 7.12)),
        ('lower_bezel', (3.3, 9.45, 6.55), (12.7, 10, 7.12)),
        ('upper_bezel', (3.3, 14.1, 6.55), (12.7, 14.65, 7.12))):
        c('monitor', name, a, b, 'brass', rotation=tilt)
    for x in (2.1, 13.1):
        c('monitor', 'iron_cheek_'+str(x), (x, 9, 6.75), (x+.8, 15.15, 9.5), 'iron', rotation=tilt)
    for x in (3.45, 12.15):
        for y in (9.6, 14.2):
            c('fasteners', f'bezel_screw_{x}_{y}', (x, y, 6.4), (x+.4, y+.3, 6.58), 'edge', rotation=tilt)
    c('keyboard', 'desk_top', (1, 8.25, 1), (15, 8.65, 7.3), 'wood')
    c('keyboard', 'keyboard_recess', (2.7, 8.65, 2.3), (10.4, 8.8, 5.9), 'dark')
    for row in range(2):
        for column in range(5):
            x, z = 3+column*1.4, 2.6+row*1.55
            c('keyboard', f'key_{row}_{column}', (x, 8.8, z), (x+1.05, 9.2, z+1.1), 'keys')
    c('paper_tray', 'brass_document_tray', (11.1, 8.65, 1.5), (14.5, 8.95, 6), 'brass')
    c('paper_tray', 'request_form', (11.35, 8.95, 1.8), (14.2, 9.06, 5.7), 'paper')
    c('paper_tray', 'paper_clip', (12.2, 9.06, 5.25), (13.4, 9.25, 5.65), 'edge')
    for x in (2.15, 13.5):
        c('rear_details', 'rear_upright_'+str(x), (x, 8.4, 13.8), (x+.55, 14, 14.5), 'iron')
    c('rear_details', 'rear_cooling_grille', (4, 10, 14.02), (12, 13, 14.2), 'vents')
    c('rear_details', 'identification_tag', (6, 8.8, 14.02), (10, 9.6, 14.22), 'plate')
    return m


def loader():
    m = Model('chunk_loader')
    m.frame(11.5)
    m.gear_badge(6.5)
    c = m.cube
    for x in (.35, 14.75):
        for z in (.35, 14.75):
            c('anchor_feet', f'anchor_{x}_{z}', (max(0, x-.3), 0, max(0, z-.3)), (min(16, x+1.2), 1, min(16, z+1.2)), 'iron')
    for side in ('east', 'west'):
        m.port(side, 6.5)
    c('rear_details', 'cooling_grille', (4.5, 4, 14.5), (11.5, 9, 14.7), 'vents')
    c('core', 'square_dark_gasket', (4, 11.75, 4), (12, 12.15, 12), 'dark')
    c('core', 'lower_brass_pedestal', (4.25, 12.15, 4.25), (11.75, 12.65, 11.75), 'brass')
    c('core', 'copper_shoulder', (5.25, 12.65, 5.25), (10.75, 13.15, 10.75), 'copper')
    c('core', 'lower_collar', (5.5, 13.15, 5.5), (10.5, 13.6, 10.5), 'brass')
    c('core', 'copper_anchor_core', (6, 13.6, 6), (10, 15.35, 10), 'copper')
    c('core', 'crown_collar', (5.5, 15.35, 5.5), (10.5, 15.85, 10.5), 'brass')
    c('core', 'crown_inset', (6.5, 15.85, 6.5), (9.5, 15.95, 9.5), 'dark')
    c('core', 'crown_copper_cap', (7, 15.95, 7), (9, 16, 9), 'copper')
    # Guarded luminous-looking ceramic strips; regular texture shading in game.
    for x in (1.05, 14.15):
        for z in (.26, 15.44):
            name = f'{x}_{z}'
            c('stabilizers', 'strip_socket_'+name, (x-.25, 3, z), (x+1.05, 10, z+.3), 'dark')
            c('stabilizers', 'cyan_strip_'+name, (x, 3.5, z-.02), (x+.8, 9.5, z+.32), 'cyan')
            for y in (3, 9.5):
                c('stabilizers', f'guard_{name}_{y}', (x-.2, y, z-.07), (x+1, y+.5, z+.37), 'brass')
    for x in (3, 12.25):
        c('core', 'top_brace_'+str(x), (x, 11.75, 5), (x+.75, 12.1, 11), 'edge')
    return m


# Faces and UV corner order follow Java's FaceBakery (texture north is -Z).
def face_vertices(a, b):
    x, y, z = a
    X, Y, Z = b
    return {
        'north': [(X,Y,z), (X,y,z), (x,y,z), (x,Y,z)],
        'south': [(x,Y,Z), (x,y,Z), (X,y,Z), (X,Y,Z)],
        'west': [(x,Y,z), (x,y,z), (x,y,Z), (x,Y,Z)],
        'east': [(X,Y,Z), (X,y,Z), (X,y,z), (X,Y,z)],
        'up': [(x,Y,z), (x,Y,Z), (X,Y,Z), (X,Y,z)],
        'down': [(x,y,Z), (x,y,z), (X,y,z), (X,y,Z)],
    }


def render(java, width=500, height=490, yaw=-35, pitch=26):
    """Orthographic textured z-buffer preview, not a generative illustration."""
    target = np.zeros((height, width, 4), dtype=np.uint8)
    depth = np.full((height, width), -np.inf)
    tex = np.asarray(ATLAS)
    y, p = math.radians(yaw), math.radians(pitch)
    right = np.array([math.cos(y), 0, -math.sin(y)])
    view = np.array([math.sin(y)*math.cos(p), math.sin(p), math.cos(y)*math.cos(p)])
    # Looking from north: negate world Z for the view and horizontal projection.
    right[2] *= -1
    view[2] *= -1
    up = np.cross(right, view)
    scale = min(width, height)/26
    for e in java['elements']:
        for face, points in face_vertices(e['from'], e['to']).items():
            pts = np.array(points, dtype=float)
            rot = e.get('rotation')
            if rot:
                angle = math.radians(rot['angle'])
                matrix = np.array([[1,0,0], [0,math.cos(angle),-math.sin(angle)], [0,math.sin(angle),math.cos(angle)]])
                origin = np.array(rot['origin'])
                pts = (pts-origin) @ matrix.T+origin
            normal = np.cross(pts[1]-pts[0], pts[2]-pts[0])
            normal /= np.linalg.norm(normal)
            if np.dot(normal, view) <= 0:
                continue
            centered = pts-np.array([8, 7.5, 8])
            xy = np.column_stack((centered @ right*scale+width/2, -centered @ up*scale+height/2))
            zz = centered @ view
            u, v, U, V = np.array(e['faces'][face]['uv'])*SIZE/16
            uv = np.array([(u,v), (u,V), (U,V), (U,v)])
            light = .72+.28*max(0, np.dot(normal, np.array([-.4,.82,-.4])))
            for ids in ((0,1,2), (0,2,3)):
                t = xy[list(ids)]
                x0, x1 = max(0, int(t[:,0].min())), min(width-1, math.ceil(t[:,0].max()))
                y0, y1 = max(0, int(t[:,1].min())), min(height-1, math.ceil(t[:,1].max()))
                if x1 < x0 or y1 < y0:
                    continue
                xx, yy = np.meshgrid(np.arange(x0,x1+1)+.5, np.arange(y0,y1+1)+.5)
                den = (t[1,1]-t[2,1])*(t[0,0]-t[2,0])+(t[2,0]-t[1,0])*(t[0,1]-t[2,1])
                if abs(den) < 1e-8:
                    continue
                w0 = ((t[1,1]-t[2,1])*(xx-t[2,0])+(t[2,0]-t[1,0])*(yy-t[2,1]))/den
                w1 = ((t[2,1]-t[0,1])*(xx-t[2,0])+(t[0,0]-t[2,0])*(yy-t[2,1]))/den
                w2 = 1-w0-w1
                weights = np.stack((w0,w1,w2), axis=-1)
                zval = weights @ zz[list(ids)]
                mask = (weights.min(axis=-1) >= -1e-7) & (zval > depth[y0:y1+1,x0:x1+1])
                uvs = weights @ uv[list(ids)]
                tx = np.clip(uvs[:,:,0].astype(int), 0, SIZE-1)
                ty = np.clip(uvs[:,:,1].astype(int), 0, SIZE-1)
                colors = tex[ty, tx].copy()
                colors[:,:,:3] = (colors[:,:,:3]*light).astype(np.uint8)
                target[y0:y1+1,x0:x1+1][mask] = colors[mask]
                depth[y0:y1+1,x0:x1+1][mask] = zval[mask]
    return Image.fromarray(target)


def main():
    models = [router(), terminal(), loader()]
    manifest = {'format': 'java_block', 'atlas_size': [SIZE, SIZE], 'front': 'north (-Z)', 'models': {}}
    sheet = Image.new('RGBA', (1536, 900), (239, 233, 220, 255))
    draw = ImageDraw.Draw(sheet)
    font_path = Path('C:/Windows/Fonts/arial.ttf')
    title = ImageFont.truetype(str(font_path), 27)
    small = ImageFont.truetype(str(font_path), 18)
    labels = ['Грузовой маршрутизатор', 'Терминал запросов', 'Загрузчик чанка']
    for i, model in enumerate(models):
        java = model.export()
        hero = render(java)
        hero.save(OUT / model.name / 'preview.png')
        sheet.alpha_composite(hero, (i*512+6, 62))
        rear = render(java, 250, 260, yaw=145)
        front = render(java, 250, 260, yaw=0, pitch=0)
        sheet.alpha_composite(front, (i*512+3, 552))
        sheet.alpha_composite(rear, (i*512+259, 552))
        draw.text((i*512+256, 33), labels[i], font=title, anchor='mm', fill='#352b24')
        draw.text((i*512+128, 827), 'Спереди', font=small, anchor='mm', fill='#65594b')
        draw.text((i*512+384, 827), 'Сзади', font=small, anchor='mm', fill='#65594b')
        draw.text((i*512+256, 866), f'{len(model.elements)} элементов • 128×128 • Java Block', font=small, anchor='mm', fill='#65594b')
        if i:
            draw.line((i*512, 20, i*512, 880), fill='#c8bda9', width=2)
        manifest['models'][model.name] = {'elements': len(model.elements), 'groups': list(model.groups),
            'source': f'{model.name}/{model.name}.bbmodel',
            'runtime': f'assets/{NS}/models/block/{model.name}.json',
            'texture_sha256': hashlib.sha256((OUT/model.name/(model.name+'.png')).read_bytes()).hexdigest()}
    sheet.convert('RGB').save(OUT / 'cargo_equipment_preview.png')
    ATLAS.resize((768,768), Image.Resampling.NEAREST).save(OUT / 'atlas_preview.png')
    save(OUT / 'manifest.json', manifest)
    with zipfile.ZipFile(OUT / 'CCEC-cargo-equipment-Blockbench.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for path in sorted(OUT.rglob('*')):
            if path.is_file() and path.suffix != '.zip':
                z.write(path, path.relative_to(OUT))
        for model in models:
            path = ASSETS / 'models/block' / (model.name+'.json')
            z.write(path, 'runtime/assets/'+NS+'/models/block/'+path.name)
            path = ASSETS / 'textures/block' / (model.name+'.png')
            z.write(path, 'runtime/assets/'+NS+'/textures/block/'+path.name)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
