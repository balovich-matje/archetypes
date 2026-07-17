"""Generate the dagger and magic wand item textures.

Daggers are derived, not drawn: every vanilla sword shares one pixel
layout, so cutting a band out of the blade's diagonal and sliding the tip
toward the guard shortens any of them into a dagger — palette, shading and
outline stay authentically vanilla for all seven materials at once. The
wand runs the same trick in reverse on the stick, then gets a pale tip.

Usage: python3 make_weapon_textures.py [preview]
"""
import os
import sys
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
ASSETS = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/assets"))
DST = os.path.join(ASSETS, "archetypes/textures/item")

MATERIALS = ("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def dagger(material):
    """A sword shortened by four steps along the blade's ↗ diagonal: rows
    above the cut move (-4, +4), landing exactly on same-role pixels of the
    blade's middle (the blade is diagonal, so it maps onto itself), and the
    rows they vacate go empty. Guard and grip untouched."""
    sword = vanilla(f"item/{material}_sword.png")
    src = sword.load()
    out = Image.new("RGBA", sword.size, (0, 0, 0, 0))
    px = out.load()
    y_cut, k = 6, 4

    for y in range(sword.height):
        for x in range(sword.width):
            if y > y_cut and src[x, y][3]:
                px[x, y] = src[x, y]

    for y in range(y_cut + 1):
        for x in range(sword.width):
            if src[x, y][3] and 0 <= x - k < 16 and 0 <= y + k < 16:
                px[x - k, y + k] = src[x, y]

    return out


WAND_GLOW = (211, 160, 231, 255)
WAND_GLOW_BRIGHT = (243, 218, 251, 255)
# The specialist wands' sparks: amethyst, fire, frost, light.
APPRENTICE_GLOW = ((176, 116, 217, 255), (225, 194, 247, 255))
BLAZE_GLOW = ((255, 140, 0, 255), (255, 224, 120, 255))
BREEZE_GLOW = ((110, 190, 235, 255), (222, 248, 255, 255))
HOLY_GLOW = ((240, 200, 70, 255), (255, 250, 200, 255))


def wand(base="item/stick.png", glow=WAND_GLOW, glow_bright=WAND_GLOW_BRIGHT):
    """Two rods' worth of carved material: the base item plus a copy of
    itself one step up its own diagonal — sticks and blaze/breeze rods are
    pure diagonals, so the copy self-aligns everywhere except the tip it
    extends — with the school's spark continuing past the new point."""
    stick = vanilla(base)
    src = stick.load()
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()

    for y in range(16):
        for x in range(16):
            if src[x, y][3]:
                px[x, y] = src[x, y]
                if 0 <= x + 1 < 16 and 0 <= y - 1 < 16:
                    px[x + 1, y - 1] = src[x, y]

    # Find the tip — the highest opaque pixel — and spark just past it.
    tx, ty = next((x, y) for y in range(16) for x in range(15, -1, -1) if px[x, y][3])
    for dx, dy, c in ((1, -1, glow_bright), (1, 0, glow), (0, -1, glow)):
        x, y = tx + dx, ty + dy
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    return im


def save(im, name):
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


if __name__ == "__main__":
    images = [(f"{m}_dagger", dagger(m)) for m in MATERIALS] + [
        ("magic_wand", wand()),
        ("apprentice_wand", wand(glow=APPRENTICE_GLOW[0], glow_bright=APPRENTICE_GLOW[1])),
        ("blaze_wand", wand("item/blaze_rod.png", *BLAZE_GLOW)),
        ("breeze_wand", wand("item/breeze_rod.png", *BREEZE_GLOW)),
        ("holy_wand", wand(glow=HOLY_GLOW[0], glow_bright=HOLY_GLOW[1])),
    ]

    for name, im in images:
        save(im, name)

    if "preview" in sys.argv[1:]:
        sheet = Image.new("RGBA", (len(images) * 144, 144), (60, 60, 68, 255))
        for i, (name, im) in enumerate(images):
            sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (8 + i * 144, 8))
        sheet.save("weapon_preview.png")
