"""Author the 2x2x2 industrial auger, its eight cell models and portable Blockbench source.

Python + Pillow + numpy. Original pixel textures and cuboid geometry; no external artwork.
The assembled renderer uses the same geometry at its existing 8x asset scale.
"""
from pathlib import Path
import base64
import copy
import json
import math
import random
import uuid

import build_cargo_models as preview
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/industrial_auger'
ASSETS = ROOT / 'src/main/resources/assets/createcityexpansioncore'
MODELS = ASSETS / 'models/block/industrial_auger'
NS = 'createcityexpansioncore'
SIZE = 128
MATERIALS = {'steel': (87, 90, 89), 'edge': (157, 163, 158),
             'dark': (42, 45, 43), 'andesite': (107, 112, 102),
             'brass': (163, 131, 76), 'shaft': (117, 120, 112)}


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, NS + '/industrial_auger/' + name))


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def texture():
    image = Image.new('RGBA', (SIZE, SIZE), (40, 42, 39, 255))
    draw = ImageDraw.Draw(image)
    rng = random.Random(421)
    for index, (name, color) in enumerate(MATERIALS.items()):
        ox, oy = (index % 4) * 32, (index // 4) * 32
        draw.rectangle((ox, oy, ox+31, oy+31), fill=color)
        for y in range(0, 32, 2):
            for x in range(0, 32, 2):
                variation = rng.randrange(-11, 12)
                shade = tuple(max(0, min(255, c+variation)) for c in color)
                draw.rectangle((ox+x, oy+y, ox+x+1, oy+y+1), fill=shade)
        # Restrained edge highlights and machined seams, at Minecraft texel density.
        draw.line((ox, oy, ox+31, oy), fill=tuple(min(255, c+30) for c in color))
        draw.line((ox, oy+31, ox+31, oy+31), fill=tuple(max(0, c-30) for c in color))
        if name in ('shaft', 'steel'):
            for x in (7, 23):
                draw.line((ox+x, oy+2, ox+x, oy+29), fill=tuple(max(0, c-18) for c in color))
    return image


def geometry():
    elements = []

    def box(group, a, b, material):
        index = list(MATERIALS).index(material)
        u, v = (index % 4)*4, (index // 4)*4
        elements.append({'name': group + '_' + str(len(elements)), 'from': list(a), 'to': list(b),
                         'faces': {face: {'uv': [u, v, u+4, v+4], 'texture': '#atlas'}
                                   for face in ('north', 'east', 'south', 'west', 'up', 'down')}})

    # Downward point, tapering steel core, and the central drive spindle.
    for y, radius in ((0, 1.5), (2, 3), (4, 4), (7, 5), (11, 6), (16, 7)):
        # Recess joins by 1/8 texel so flight edges never share an exposed core plane.
        box('core', (16-radius-.125, y+.125, 16-radius-.125),
            (16+radius+.125, min(y+5, 23)-.125, 16+radius+.125), 'steel')
    # End below the coupling: a shaft top at y=32 overlaps the brass cap's top.
    box('shaft', (12, 20, 12), (20, 29, 20), 'shaft')
    # Continuous stepped spiral, tapered towards the tip. Two-pixel cuboids keep it
    # Java-model compatible and can be split exactly at every 16-pixel cell boundary.
    for x in range(2, 30, 2):
        for z in range(2, 30, 2):
            radius = math.hypot(x+1-16, z+1-16)
            angle = math.atan2(z+1-16, x+1-16) % math.tau
            for turn in range(3):
                y = round(2 + (turn + angle/math.tau)*8, 2)
                outer = min(14.5, 3.5 + y*.45)
                inner = 2.5 + y*.18
                if inner <= radius <= outer and y + 1.6 < 25:
                    box('helical_flight', (x, y, z), (x+2, y+1.6, z+2),
                        'edge' if radius > outer-2 else 'steel')
            # Heavy octagonal mounting collar; the centre remains visibly open.
            if 10.5 <= radius <= 14.5:
                box('collar', (x, 26, z), (x+2, 29, z+2), 'andesite')
                box('collar_lip', (x, 25, z), (x+2, 26, z+2), 'dark')
    for angle in range(0, 360, 45):
        x, z = 16+12*math.cos(math.radians(angle)), 16+12*math.sin(math.radians(angle))
        box('bolt', (x-1, 29, z-1), (x+1, 30, z+1), 'edge')
    box('coupling', (10, 29, 12), (22, 31, 20), 'dark')
    # Disjoint arms instead of two intersecting bars with coplanar top/bottom faces.
    box('coupling', (12, 29, 10), (20, 31, 12), 'dark')
    box('coupling', (12, 29, 20), (20, 31, 22), 'dark')
    box('bearing', (11, 31, 11), (21, 32, 21), 'brass')
    return elements


def model(elements):
    return {'parent': 'minecraft:block/block', 'ambientocclusion': True,
            'textures': {'atlas': NS+':block/industrial_auger', 'particle': NS+':block/industrial_auger'},
            'elements': elements}


def scaled(elements, scale, offset=(0, 0, 0)):
    result = copy.deepcopy(elements)
    for element in result:
        for key in ('from', 'to'):
            element[key] = [round((value-offset[i])*scale, 5) for i, value in enumerate(element[key])]
    return result


def split_cell(elements, x, y, z):
    """Clip every cuboid and its UVs so chunk boundaries and layer previews stay exact."""
    offset = [x*16, y*16, z*16]
    result = []
    for source in elements:
        a = [max(source['from'][i], offset[i]) for i in range(3)]
        b = [min(source['to'][i], offset[i]+16) for i in range(3)]
        if any(a[i] >= b[i] for i in range(3)):
            continue
        item = copy.deepcopy(source)
        item['from'] = [a[i]-offset[i] for i in range(3)]
        item['to'] = [b[i]-offset[i] for i in range(3)]
        # Match Java face UV directions (not all cube faces run in +axis order).
        for face, axes in {'north': (0, 1, True, True), 'south': (0, 1, False, True),
                           'east': (2, 1, True, True), 'west': (2, 1, False, True),
                           'up': (0, 2, False, False), 'down': (0, 2, False, True)}.items():
            u, v, U, V = source['faces'][face]['uv']
            cropped = []
            for axis, reverse, lo, hi in ((axes[0], axes[2], u, U), (axes[1], axes[3], v, V)):
                width = source['to'][axis]-source['from'][axis]
                start = (a[axis]-source['from'][axis])/width
                end = (b[axis]-source['from'][axis])/width
                if reverse: start, end = 1-end, 1-start
                cropped.append((lo+(hi-lo)*start, lo+(hi-lo)*end))
            item['faces'][face]['uv'] = [cropped[0][0], cropped[1][0], cropped[0][1], cropped[1][1]]
        result.append(item)
    return result


def oriented(elements, facing):
    """Rotate the whole 32-pixel assembly BEFORE clipping it into world-space cells."""
    def vector(p):
        x, y, z = p
        return {'down': (x, y, z), 'up': (x, -y, -z),
                'north': (x, -z, y), 'south': (x, z, -y),
                'east': (-y, x, z), 'west': (y, -x, z)}[facing]

    normals = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1),
               'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0)}
    names = {v: k for k, v in normals.items()}
    result = []
    for source in elements:
        element = copy.deepcopy(source)
        corners = [vector([p[i]-16 for i in range(3)]) for p in (source['from'], source['to'])]
        element['from'] = [round(min(p[i] for p in corners)+16, 5) for i in range(3)]
        element['to'] = [round(max(p[i] for p in corners)+16, 5) for i in range(3)]
        element['faces'] = {names[vector(normals[face])]: copy.deepcopy(data) for face, data in source['faces'].items()}
        result.append(element)
    return result


def validate_surfaces(elements):
    """Reject positive-area overlaps of outward faces in the same plane (z-fighting)."""
    planes = {}
    for element in elements:
        for axis in range(3):
            lateral = [i for i in range(3) if i != axis]
            rectangle = [element[key][i] for key in ('from', 'to') for i in lateral]
            for key in ('from', 'to'):
                plane = (axis, key, round(element[key][axis], 5))
                for old, name in planes.get(plane, []):
                    overlap = all(min(rectangle[i+2], old[i+2])-max(rectangle[i], old[i]) > 1e-5 for i in range(2))
                    assert not overlap, f'Coplanar faces: {element["name"]} / {name} at {plane}'
                planes.setdefault(plane, []).append((rectangle, element['name']))


def main():
    ART.mkdir(parents=True, exist_ok=True)
    atlas = texture()
    atlas.save(ART / 'industrial_auger.png')
    atlas.save(ASSETS / 'textures/block/industrial_auger.png')
    elements = geometry()
    validate_surfaces(elements)
    rotating = [e for e in elements if e['name'].startswith(('core_', 'shaft_', 'helical_flight_', 'coupling_'))]
    stationary = [e for e in elements if e not in rotating]
    save(MODELS / 'rotor.json', model(rotating))
    save(MODELS / 'housing.json', model(stationary))
    display = {'gui': {'rotation': [25, 225, 0], 'translation': [-4, -4, 0], 'scale': [.35, .35, .35]},
               'ground': {'translation': [-2, 2, -2], 'scale': [.2, .2, .2]},
               'fixed': {'translation': [-4, -4, -4], 'scale': [.5, .5, .5]},
               'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [-2, 1, -2], 'scale': [.25, .25, .25]},
               'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [-2, 0, -2], 'scale': [.3, .3, .3]}}
    full = model(elements)
    full['display'] = display
    save(MODELS / 'full.json', full)
    save(ASSETS / 'models/item/drill_unit.json', {'parent': NS+':block/drill_unit'})
    save(ASSETS / 'models/block/drill_unit.json', model(scaled(elements, .5)))
    variants = {}
    for facing in ('down', 'up', 'north', 'south', 'east', 'west'):
        rotated = oriented(elements, facing)
        validate_surfaces(rotated)
        legacy = NS+':block/drill_unit'
        if facing != 'down':
            save(MODELS / ('single_'+facing+'.json'), model(scaled(rotated, .5)))
            legacy = NS+':block/industrial_auger/single_'+facing
        variants[f'part=single,facing={facing}'] = {'model': legacy}
        for x, z, name in ((0, 0, 'north_west'), (1, 0, 'north_east'), (1, 1, 'south_east'), (0, 1, 'south_west')):
            for y in range(2):
                suffix = name + ('_upper' if y else '_lower') + ('' if facing == 'down' else '_'+facing)
                save(MODELS / (suffix+'.json'), model(split_cell(rotated, x, y, z)))
                variants[f'part={name},upper={str(bool(y)).lower()},facing={facing}'] = {'model': NS+':block/industrial_auger/'+suffix}
    save(ASSETS / 'blockstates/drill_unit.json', {'variants': variants})
    save(MODELS / 'assembled.json', model(scaled(elements, 1/8, (16, 0, 16))))
    bb_elements, groups = [], {}
    for element in elements:
        name = element['name']
        bb_elements.append({'name': name, 'uuid': uid(name), 'type': 'cube',
                            'from': element['from'], 'to': element['to'], 'origin': [16, 16, 16],
                            'rotation': [0, 0, 0], 'box_uv': False, 'autouv': 0,
                            'faces': {face: {'uv': [v*8 for v in data['uv']], 'texture': 0}
                                      for face, data in element['faces'].items()}})
        groups.setdefault(name.rsplit('_', 1)[0], []).append(uid(name))
    save(ART / 'industrial_auger.bbmodel', {
        'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
        'name': 'Industrial Drill Auger 2x2x2', 'resolution': {'width': SIZE, 'height': SIZE},
        'elements': bb_elements, 'display': display,
        'outliner': [{'name': name, 'uuid': uid('group/'+name), 'origin': [16, 16, 16],
                      'rotation': [0, 0, 0], 'children': children} for name, children in groups.items()],
        'textures': [{'name': 'industrial_auger.png', 'id': '0', 'uuid': uid('atlas'),
                      'mode': 'bitmap', 'width': SIZE, 'height': SIZE, 'uv_width': SIZE, 'uv_height': SIZE,
                      'source': 'data:image/png;base64,' + base64.b64encode((ART/'industrial_auger.png').read_bytes()).decode()}]})
    preview.ATLAS = atlas
    image = Image.new('RGBA', (1050, 650), (29, 33, 35, 255))
    small = model(scaled(elements, .5))
    image.alpha_composite(preview.render(small, 650, 600, yaw=-35, pitch=18), (0, 40))
    image.alpha_composite(preview.render(small, 360, 300, yaw=0, pitch=0), (670, 35))
    image.alpha_composite(preview.render(small, 360, 300, yaw=-25, pitch=-28), (670, 335))
    draw = ImageDraw.Draw(image)
    draw.text((25, 16), 'CCEC / INDUSTRIAL DRILL AUGER / 2 x 2 x 2 BLOCKS', fill=(220, 225, 218), font_size=22)
    image.convert('RGB').save(ART/'preview.png')
    print(f'Authored {len(elements)} cuboids, 48 cell models in six orientations, assembled model and Blockbench source; no coplanar face overlaps.')


if __name__ == '__main__':
    main()
