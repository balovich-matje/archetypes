"""Dump 16x16 textures as an indexed pixel grid + palette, for reading."""
import sys
from PIL import Image

for path in sys.argv[1:]:
    im = Image.open(path).convert("RGBA")
    px = im.load()
    pal, order = {}, []
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            if c[3] and c not in pal:
                pal[c] = "0123456789abcdefghijklmnopqrstuvwxyz"[len(pal)]
                order.append(c)
    print(f"== {path}  {im.size}")
    print("    " + "".join(f"{x%10}" for x in range(im.width)))
    for y in range(im.height):
        print(f"{y:3} " + "".join(pal.get(px[x, y], ".") if px[x, y][3] else "."
                                 for x in range(im.width)))
    for c in order:
        print(f"  {pal[c]} = {c}  #{c[0]:02x}{c[1]:02x}{c[2]:02x}")
    print()
