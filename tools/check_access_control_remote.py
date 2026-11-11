# -*- coding: utf-8 -*-
"""Validates the Access Control Remote item model against the rules that make
JSON models look correct, and renders a top-view preview from the same cell
map used by build_access_control_remote.py.

Run:  python tools/check_access_control_remote.py
"""
import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(
    ROOT, "src", "main", "resources", "assets", "createcityexpansioncore",
    "models", "item", "access_control_remote.json")
TEXTURE = os.path.join(
    ROOT, "src", "main", "resources", "assets", "createcityexpansioncore",
    "textures", "item", "access_control_remote.png")
PREVIEW = os.path.join(ROOT, "art", "access_control_remote", "preview_top.png")

# Cell map, in atlas pixels (32x32, cells of 8x8). Mirrors build script.
CELLS = {
    (0, 0): "black_top", (8, 0): "black_side", (16, 0): "black_recess", (24, 0): "black_deep",
    (0, 8): "brass_light", (8, 8): "brass_mid", (16, 8): "brass_dark", (24, 8): "brass_under",
    (0, 16): "screen_glow", (8, 16): "screen_dark", (16, 16): "key_top", (24, 16): "key_side",
}
CELL_COLOR = {
    "black_top": (26, 26, 31), "black_side": (20, 20, 25),
    "black_recess": (12, 12, 16), "black_deep": (6, 6, 9),
    "brass_light": (216, 178, 90), "brass_mid": (184, 145, 45),
    "brass_dark": (125, 92, 30), "brass_under": (90, 67, 26),
    "screen_glow": (47, 143, 86), "screen_dark": (15, 46, 28),
    "key_top": (106, 106, 118), "key_side": (53, 53, 61),
}

errors = []


def require(ok, message):
    if not ok:
        errors.append(message)


# ---- display-transform math (matches vanilla quaternion rotationXYZ: R = Rx*Ry*Rz,
# rightmost factor applies to geometry first; right_rotation is applied BEFORE the
# main rotation, per PoseStack order translate -> rot -> scale -> right_rot).
def axis_rot(axis, deg):
    a = deg * math.pi / 180.0
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return ((1, 0, 0), (0, c, -s), (0, s, c))
    if axis == "y":
        return ((c, 0, s), (0, 1, 0), (-s, 0, c))
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def mat_mul(m, n):
    return tuple(
        tuple(sum(m[i][k] * n[k][j] for k in range(3)) for j in range(3))
        for i in range(3)
    )


def apply_mat(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def net_rotation(rotation, right_rotation, left_hand):
    def build(euler, flip):
        x, y, z = euler
        if flip:
            y, z = -y, -z
        return mat_mul(mat_mul(axis_rot("x", x), axis_rot("y", y)), axis_rot("z", z))

    main = build(rotation, left_hand)
    right = build(right_rotation, left_hand)
    return mat_mul(main, right)  # point: right_rotation applied first


EX = (1.0, 0.0, 0.0)
EY = (0.0, 1.0, 0.0)
EZ = (0.0, 0.0, 1.0)


def check_display(model):
    display = model.get("display")
    require(isinstance(display, dict) and display, "model must declare display transforms")
    if not display:
        return
    for context_name, transform in display.items():
        require(isinstance(transform, dict), f"display/{context_name}: must be an object")
        for key in ("rotation", "translation", "scale", "right_rotation"):
            value = transform.get(key)
            if value is None:
                continue
            require(isinstance(value, list) and len(value) == 3,
                    f"display/{context_name}: '{key}' needs 3 floats")
        for name in ("translation",):
            for component in (transform.get(name) or (0, 0, 0)):
                require(abs(component) <= 80,  # deserializer clamps to +/-5 after x0.0625
                        f"display/{context_name}/translation: {component} clamps past ±5 units")
        for component in (transform.get("scale") or (1, 1, 1)):
            require(0 < abs(component) <= 4,
                    f"display/{context_name}: scale {component} out of (-4,4)")
        rot = transform.get("rotation", [0, 0, 0])
        rrot = transform.get("right_rotation", [0, 0, 0])

        if context_name in ("firstperson_righthand", "firstperson_lefthand",
                            "thirdperson_righthand", "thirdperson_lefthand"):
            hand = "left" if "left" in context_name else "right"
            net_r = net_rotation(rot, rrot, hand == "left")
            want_normal = tuple(-c for c in EX) if hand == "right" else EX
            got = apply_mat(net_r, EY)
            require(all(abs(g - w) < 1e-6 for g, w in zip(got, want_normal)),
                    f"display/{context_name}: screen normal maps to {got}, expected {want_normal}")
            got_keys = apply_mat(net_r, EZ)
            require(all(abs(g - w) < 1e-6 for g, w in zip(got_keys, (0.0, -1.0, 0.0))),
                    f"display/{context_name}: keypad axis maps to {got_keys}, expected -Y (down)")
        elif context_name == "fixed":
            net = net_rotation(rot, rrot, False)
            got = apply_mat(net, EY)
            require(all(abs(g - w) < 1e-6 for g, w in zip(got, (0.0, 0.0, -1.0))),
                    f"display/fixed: screen normal maps to {got}, expected -Z (facing viewer)")
            got_keys = apply_mat(net, EZ)
            require(all(abs(g - w) < 1e-6 for g, w in zip(got_keys, (0.0, -1.0, 0.0))),
                    f"display/fixed: keypad axis maps to {got_keys}, expected -Y (down)")
        elif context_name == "ground":
            net = net_rotation(rot, rrot, False)
            got = apply_mat(net, EY)
            require(all(abs(g - w) < 1e-6 for g, w in zip(got, EY)),
                    f"display/ground: screen normal maps to {got}, expected +Y (screen up)")


def cell_name(u, v):
    """UV units are 0..16 over the full 32px texture: 2 UV units per atlas cell."""
    px, py = u * 2, v * 2
    key = (px, py)
    return CELLS.get(key)


FACE_NAME = {"x": ("west", "east"), "y": ("down", "up"), "z": ("north", "south")}
EPS = 1e-4


def check_topology(elements):
    """Every undeclared face must sit flush against a covering solid (no holes),
    and same-plane declared faces must never overlap in area (no z-fighting)."""
    n = len(elements)
    for a in range(3):
        b, c = [axis for axis in range(3) if axis != a]
        for i in range(n):
            from_i, to_i = elements[i]["from"], elements[i]["to"]
            name_i = elements[i].get("name", i)
            faces_i = elements[i].get("faces", {})
            rect_i = {b: (from_i[b], to_i[b]), c: (from_i[c], to_i[c])}
            for side in (0, 1):
                plane = from_i[a] if side == 0 else to_i[a]
                face = FACE_NAME["xyz"[a]][side]
                if face not in faces_i:
                    covered = False
                    for j in range(n):
                        if j == i:
                            continue
                        from_j, to_j = elements[j]["from"], elements[j]["to"]
                        if not (from_j[a] <= plane + EPS and to_j[a] >= plane - EPS):
                            continue
                        if (from_j[b] <= rect_i[b][0] + EPS and to_j[b] >= rect_i[b][1] - EPS
                                and from_j[c] <= rect_i[c][0] + EPS and to_j[c] >= rect_i[c][1] - EPS):
                            covered = True
                            break
                    require(covered, f"{name_i}: face '{face}' is undeclared and not flush-"
                            "covered by another element — a visible hole")
            # declared-vs-declared coplanar overlaps
            for j in range(i + 1, n):
                from_j, to_j = elements[j]["from"], elements[j]["to"]
                name_j = elements[j].get("name", j)
                faces_j = elements[j].get("faces", {})
                rect_j = {b: (from_j[b], to_j[b]), c: (from_j[c], to_j[c])}
                for side in (0, 1):
                    face = FACE_NAME["xyz"[a]][side]
                    if face not in faces_i or face not in faces_j:
                        continue
                    plane_i = from_i[a] if side == 0 else to_i[a]
                    plane_j = from_j[a] if side == 0 else to_j[a]
                    if abs(plane_i - plane_j) > EPS:
                        continue
                    lo_b = max(rect_i[b][0], rect_j[b][0])
                    hi_b = min(rect_i[b][1], rect_j[b][1])
                    lo_c = max(rect_i[c][0], rect_j[c][0])
                    hi_c = min(rect_i[c][1], rect_j[c][1])
                    area = max(0.0, hi_b - lo_b) * max(0.0, hi_c - lo_c)
                    if area <= EPS * EPS:
                        continue
                    cell_i = cell_name(faces_i[face]["uv"][0], faces_i[face]["uv"][1])
                    cell_j = cell_name(faces_j[face]["uv"][0], faces_j[face]["uv"][1])
                    require(cell_i == cell_j,
                            f"{name_i}/{name_j}('{face}') overlap coplanar by {area:.4f} "
                            f"with different cells {cell_i}/{cell_j} — z-fighting")


def main():
    with open(MODEL, encoding="utf-8") as fh:
        model = json.load(fh)

    texture = model.get("textures", {}).get("0")
    require(texture == "createcityexpansioncore:item/access_control_remote",
            "texture #0 must point to the item atlas")
    require(os.path.isfile(TEXTURE), "atlas texture file is missing")

    elements = model.get("elements")
    require(isinstance(elements, list) and elements, "model must declare elements")

    for element in elements:
        name = element.get("name", "?")
        frm, to = element.get("from"), element.get("to")
        require(isinstance(frm, list) and isinstance(to, list), f"{name}: from/to must be lists")
        for axis in range(3):
            require(frm[axis] < to[axis],
                    f"{name}: axis {axis} is degenerate ({frm[axis]}..{to[axis]})")
            require(-16 <= frm[axis] <= 32 and -16 <= to[axis] <= 32,
                    f"{name}: axis {axis} outside [-16,32]")
        # A GUI item should stay inside the vanilla 0..16 space; slight overflow on
        # a deliberate lip (rim) is fine, but flag anything wider than [-0.5,16.5].
        for coordinate in frm + to:
            require(-0.5 <= coordinate <= 16.5,
                    f"{name}: coordinate {coordinate} sticks out of the item volume")

        for face_name, face in element.get("faces", {}).items():
            uv = face.get("uv")
            require(isinstance(uv, list) and len(uv) == 4, f"{name}/{face_name}: bad uv")
            if not uv:
                continue
            u1, v1, u2, v2 = uv
            require(all(0 <= value <= 16 for value in uv),
                    f"{name}/{face_name}: uv {uv} outside 0..16 — JSON uv space is the "
                    "FULL texture regardless of atlas size")
            require(u1 < u2 and v1 < v2,
                    f"{name}/{face_name}: uv must follow [u1,v1,u2,v2] with u1<u2, v1<v2")
            require(f"#{'0'}" is not None and face.get("texture") == "#0",
                    f"{name}/{face_name}: unknown texture reference {face.get('texture')}")
            # Every face must show exactly ONE atlas cell: 4x4 uv units = 8x8 px,
            # starting on a defined cell origin (2 uv units per atlas pixel).
            require((u2 - u1) * 2 == 8 and (v2 - v1) * 2 == 8,
                    f"{name}/{face_name}: uv {uv} does not span a single 8x8 atlas cell")
            require(cell_name(u1, v1) is not None,
                    f"{name}/{face_name}: uv corner ({u1},{v1}) is not on a defined atlas cell")
            require(u1 * 2 + 8 <= 32 and v1 * 2 + 8 <= 32,
                    f"{name}/{face_name}: uv {uv} runs past the 32px atlas")

    # The PNG itself: parse and count solid pixels.
    if os.path.isfile(TEXTURE):
        verify_png()

    if not errors:
        check_topology(elements)
    if not errors:
        check_display(model)

    if errors:
        print("FAIL:")
        for error in errors:
            print(" -", error)
        sys.exit(1)

    render_preview()
    print("PASS: model geometry, uv ranges, cell alignment and texture are consistent")


def verify_png():
    with open(TEXTURE, "rb") as fh:
        data = fh.read()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", "texture is not a PNG")
    i = 8
    width = height = None
    while i < len(data):
        (length,) = struct.unpack(">I", data[i:i + 4])
        tag = data[i + 4:i + 8]
        body = data[i + 8:i + 8 + length]
        if tag == b"IHDR":
            width, height, depth, ctype = struct.unpack(">IIBB", body[:10])
            require(depth == 8 and ctype == 6, f"texture must be RGBA8, got {depth}/{ctype}")
        elif tag == b"IDAT":
            raw = zlib.decompress(body)
            stride = width * 4 + 1
            require(len(raw) == stride * height,
                    f"texture pixel data mismatch: {len(raw)} != {stride * height}")
        i += 8 + length + 4


def render_preview():
    """Top view: project the highest 'up' face covering every (x,z) px."""
    with open(MODEL, encoding="utf-8") as fh:
        model = json.load(fh)
    W = 16
    top = {}
    for element in model["elements"]:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        face = element["faces"].get("up")
        if not face:
            continue
        colour = CELL_COLOR.get(cell_name(face["uv"][0], face["uv"][1]))
        if colour is None:
            continue
        for z in range(z0.__floor__(), z1.__ceil__()):
            for x in range(x0.__floor__(), x1.__ceil__()):
                if x < 0 or z < 0 or x >= W or z >= W:
                    continue
                if (x, z) not in top or y1 > top[(x, z)][0]:
                    top[(x, z)] = (y1, colour)

    # Sides visible from south-east for height shading.
    pixels = [[(0, 0, 0, 0)] * W for _ in range(W)]
    for (x, z), (_, colour) in sorted(top.items()):
        pixels[z][x] = (colour + (255,))

    scale = 16
    out = [[(0, 0, 0, 0)] * (W * scale) for _ in range(W * scale)]
    for y in range(W):
        for x in range(W):
            base = pixels[y][x]
            for sy in range(scale):
                for sx in range(scale):
                    r, g, b, a = base
                    out[y * scale + sy][x * scale + sx] = (r, g, b, a)

    def chunk(tag, body):
        payload = tag + body
        return struct.pack(">I", len(body)) + payload + struct.pack(">I", zlib.crc32(payload))

    raw = b""
    for row in out:
        raw += b"\x00" + b"".join(struct.pack("4B", *p) for p in row)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", W * scale, W * scale, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    with open(PREVIEW, "wb") as fh:
        fh.write(png)
    print("preview:", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()
