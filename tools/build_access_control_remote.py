# -*- coding: utf-8 -*-
"""Builds the 32x32 colour-cell atlas texture for the Access Control Remote item.

Run:  python tools/build_access_control_remote.py

The item model references flat colour cells by UV, so the atlas is just a
grid of solid blocks with subtle vertical shading. No PIL needed: the PNG is
written directly with zlib.
"""
import os
import struct
import zlib

OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..",
    "src",
    "main",
    "resources",
    "assets",
    "createcityexpansioncore",
    "textures",
    "item",
    "access_control_remote.png",
)

SIZE = 32
CELL = 8  # every colour cell is 8x8 pixels

# (x, y, colour) solid cells. Colour = (r, g, b), alpha always 255.
BLACKS = [
    # housing top / housing sides / housing bottom / deepest recess
    (0, 0, (26, 26, 31)),      # top shading
    (8, 0, (20, 20, 25)),      # side shading
    (16, 0, (12, 12, 16)),     # recess
    (24, 0, (6, 6, 9)),        # deepest
]
BRASS = [
    (0, 8, (216, 178, 90)),    # highlight
    (8, 8, (184, 145, 45)),    # mid
    (16, 8, (125, 92, 30)),    # dark rim
    (24, 8, (90, 67, 26)),     # under rim
]
FACE = [
    (0, 16, (47, 143, 86)),    # screen glow
    (8, 16, (15, 46, 28)),     # screen dark
    (16, 16, (106, 106, 118)), # key top
    (24, 16, (53, 53, 61)),    # key sides
]


def pixels():
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    for group in (BLACKS, BRASS, FACE):
        for cx, cy, colour in group:
            px0, py0 = cx, cy
            for y in range(py0, py0 + CELL):
                for x in range(px0, px0 + CELL):
                    r, g, b = colour
                    # subtle vertical shading so solid cells read as 3D in-game
                    shade = 1.0 - (y - py0) / (CELL * 2.5)
                    px[y][x] = (
                        int(max(0, min(255, r * shade))),
                        int(max(0, min(255, g * shade))),
                        int(max(0, min(255, b * shade))),
                        255,
                    )
    return px


def write_png(path, px):
    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    raw = b""
    for row in px:
        raw += b"\x00" + b"".join(struct.pack("4B", *p) for p in row)  # filter=0

    ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)  # RGBA8
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    write_png(OUT, pixels())
