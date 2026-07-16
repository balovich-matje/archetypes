"""Generate the mana bar's 9x9 orb sprites (full + empty).

Blue orbs with the near-black outline vanilla's hearts and armor wear, so
the row reads as native HUD. Drawn by radius test at hunger-icon scale —
the first iteration's bottles are gone.

Usage: python3 make_hud_icons.py
"""
import os

from PIL import Image

DST = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "../../src/main/resources/assets/archetypes/textures/gui"))

OUTLINE = (22, 22, 28, 255)
BLUE = (45, 110, 230, 255)
BLUE_DARK = (24, 62, 160, 255)
BLUE_LIGHT = (140, 190, 255, 255)
EMPTY = (44, 44, 54, 255)
EMPTY_LIGHT = (66, 66, 80, 255)


def orb(filled):
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = im.load()
    center = 4.0

    for y in range(9):
        for x in range(9):
            d = ((x - center) ** 2 + (y - center) ** 2) ** 0.5

            if d > 4.4:
                continue

            if d > 3.3:
                px[x, y] = OUTLINE
            elif filled:
                # Lit from the upper left, like the heart's glint.
                if x + y <= 4:
                    px[x, y] = BLUE_LIGHT
                elif x + y >= 11:
                    px[x, y] = BLUE_DARK
                else:
                    px[x, y] = BLUE
            else:
                px[x, y] = EMPTY_LIGHT if x + y <= 4 else EMPTY

    return im


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)
    orb(True).save(os.path.join(DST, "mana_orb_full.png"))
    orb(False).save(os.path.join(DST, "mana_orb_empty.png"))
    print("mana_orb_full.png\nmana_orb_empty.png")
