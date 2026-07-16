"""Generate the mana bar's 9x9 bottle sprites (full + empty).

Hand-plotted at hunger-icon scale rather than shrunk from the 16px item
textures — a 16-to-9 resample turns pixel art to mush. Palette sampled from
vanilla's glass bottle and water so the pair reads as "water bottle" at a
glance.

Usage: python3 make_hud_icons.py
"""
import os

from PIL import Image

DST = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "../../src/main/resources/assets/archetypes/textures/gui"))

GLASS = (200, 220, 228, 255)
GLASS_DIM = (140, 160, 172, 255)
CORK = (146, 108, 62, 255)
WATER = (47, 108, 219, 255)
WATER_LIGHT = (86, 142, 234, 255)


def bottle(filled):
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = im.load()

    def put(x, y, c):
        px[x, y] = c

    # Cork and neck.
    put(4, 0, CORK)
    put(3, 1, GLASS_DIM)
    put(4, 1, GLASS)
    put(5, 1, GLASS_DIM)

    # Body outline.
    for y in range(2, 8):
        put(2, y, GLASS_DIM)
        put(6, y, GLASS_DIM)
    for x in range(3, 6):
        put(x, 8, GLASS_DIM)
    put(2, 8, GLASS_DIM)
    put(6, 8, GLASS_DIM)

    # Contents.
    for y in range(3, 8):
        for x in range(3, 6):
            if filled:
                put(x, y, WATER_LIGHT if y == 3 else WATER)
            elif y == 3:
                put(x, y, (255, 255, 255, 40))

    # A glass glint on the shoulder.
    put(3, 2, GLASS)

    return im


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)
    bottle(True).save(os.path.join(DST, "mana_bottle_full.png"))
    bottle(False).save(os.path.join(DST, "mana_bottle_empty.png"))
    print("mana_bottle_full.png\nmana_bottle_empty.png")
