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

# Tip effects as (dx, dy, color) relative to the wand's tip pixel. Negative
# dy is up-right along the diagonal; (0, 0) paints over the tip itself.
SPARK = ((1, -1, WAND_GLOW_BRIGHT), (1, 0, WAND_GLOW), (0, -1, WAND_GLOW))
AMETHYST_TIP = (
    (0, -1, (225, 194, 247, 255)),   # crystal core
    (-1, -1, (176, 116, 217, 255)),
    (1, -1, (176, 116, 217, 255)),
    (0, -2, (176, 116, 217, 255)),
    (0, 0, (140, 88, 180, 255)),     # socket over the stick's tip
)
# The big ornaments (the stick shifts down-left to make the room): a
# faceted amethyst crystal and a golden starburst, each ~4x the small tip.
BIG_AMETHYST_TIP = (
    # Mounted ON the stick's end — the gem's lower half swallows the tip,
    # so the stick keeps its full length and nothing floats.
    (0, -2, (200, 156, 235, 255)),   # the point
    (-1, -1, (150, 96, 190, 255)),   # upper facets
    (0, -1, (225, 194, 247, 255)),   # bright face
    (1, -1, (176, 116, 217, 255)),
    (-1, 0, (176, 116, 217, 255)),   # waist, over the tip pixel
    (0, 0, (225, 194, 247, 255)),
    (1, 0, (200, 156, 235, 255)),
    (-1, 1, (110, 70, 150, 255)),    # lower facets, onto the stick
    (0, 1, (176, 116, 217, 255)),
    (1, 1, (150, 96, 190, 255)),
    (0, 2, (110, 70, 150, 255)),     # socket
)
BIG_HOLY_TIP = (
    (0, 0, (200, 160, 60, 255)),     # mount, on the stick's very tip
    (1, -2, (255, 252, 220, 255)),   # blazing core
    (1, -1, (250, 215, 90, 255)),    # cross, long arms
    (1, -3, (250, 215, 90, 255)),
    (1, -4, (240, 190, 60, 255)),
    (0, -2, (250, 215, 90, 255)),
    (-1, -2, (240, 190, 60, 255)),
    (2, -2, (250, 215, 90, 255)),
    (3, -2, (240, 190, 60, 255)),
    (0, -3, (240, 190, 60, 180)),    # faint diagonals
    (2, -3, (240, 190, 60, 180)),
    (0, -1, (240, 190, 60, 180)),
    (2, -1, (240, 190, 60, 180)),
)
FLAME_TIP = (
    (1, -1, (255, 236, 130, 255)),   # white-hot core
    (1, -2, (255, 170, 40, 255)),    # rising flame
    (0, -1, (230, 80, 20, 255)),
    (2, -1, (255, 120, 30, 255)),
    (1, 0, (200, 60, 15, 255)),
)
SNOWFLAKE_TIP = (
    (1, -1, (255, 255, 255, 255)),   # core
    (0, -1, (190, 235, 255, 255)),   # plus arms
    (2, -1, (190, 235, 255, 255)),
    (1, 0, (190, 235, 255, 255)),
    (1, -2, (190, 235, 255, 255)),
    (0, -2, (140, 205, 240, 200)),   # diagonal arms, fainter
    (2, 0, (140, 205, 240, 200)),
    (0, 0, (140, 205, 240, 200)),
    (2, -2, (140, 205, 240, 200)),
)
HOLY_TIP = (
    (0, -1, (255, 252, 220, 255)),   # white-gold core
    (-1, -1, (250, 215, 90, 255)),   # golden cross
    (1, -1, (250, 215, 90, 255)),
    (0, -2, (250, 215, 90, 255)),
    (0, 0, (250, 215, 90, 255)),
    (1, -2, (240, 190, 60, 180)),    # faint rays
    (-1, 0, (240, 190, 60, 180)),
)


def wand(base="item/stick.png", tip=SPARK, double=True, shift=(0, 0)):
    """A wand from a vanilla rod. The basic wand doubles the stick along its
    own diagonal (rods are pure diagonals, so the copy self-aligns and only
    the tip extends); the specialists keep the single vanilla rod — blaze
    and breeze rods are already wand-length — optionally shifted down-left
    to clear room, and wear their school's effect at the tip."""
    stick = vanilla(base)
    src = stick.load()
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()

    for y in range(16):
        for x in range(16):
            if src[x, y][3]:
                tx, ty = x + shift[0], y + shift[1]
                if 0 <= tx < 16 and 0 <= ty < 16:
                    px[tx, ty] = src[x, y]
                if double:
                    tx, ty = x + shift[0] + 1, y + shift[1] - 1
                    if 0 <= tx < 16 and 0 <= ty < 16:
                        px[tx, ty] = src[x, y]

    # Find the tip — the highest opaque pixel — and dress it.
    tx, ty = next((x, y) for y in range(16) for x in range(15, -1, -1) if px[x, y][3])
    for dx, dy, c in tip:
        x, y = tx + dx, ty + dy
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    return im


def greatsword16(material):
    """A greatsword in the daggers' dialect: derived from the vanilla sword,
    not drawn. The blade is lengthened two steps up its own diagonal (the
    blade self-maps, so only the tip extends), widened one perpendicular
    step (highlights flattened to the core colour so the band reads as one
    face), internal seam outlines dissolved, the outer outline repaired,
    and the grip stretched a step toward the pommel. Replaces the 32px
    hi-res art — iron first, the rest if the user approves."""
    sword = vanilla(f"item/{material}_sword.png")
    src = sword.load()
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = out.load()

    def lum(c):
        return sum(c[:3]) / 3

    from collections import Counter
    counts = Counter(src[x, y] for y in range(9) for x in range(16) if src[x, y][3])
    core = max((c for c in counts if 90 < lum(c) < 235), key=counts.get)
    dark = max((c for c in counts if lum(c) <= 90), key=counts.get)

    def paint(dx, dy, rows, flatten):
        for y in rows:
            for x in range(16):
                if src[x, y][3]:
                    tx, ty = x + dx, y + dy
                    if 0 <= tx < 16 and 0 <= ty < 16:
                        c = src[x, y]
                        if flatten and lum(c) > 235:
                            c = core
                        px[tx, ty] = c

    paint(3, -1, range(9), True)    # widened copy of the extension
    paint(1, 1, range(11), True)    # widened copy of the base blade
    paint(2, -2, range(9), False)   # the extension itself
    paint(0, 0, range(16), False)   # the original sword on top

    def bright(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] and lum(px[x, y]) > 90

    # Outlines trapped inside the widened blade are seams, not edges.
    for _ in range(2):
        for y in range(11):
            for x in range(16):
                if px[x, y][3] and lum(px[x, y]) <= 90 and any(
                        bright(x + dx, y + dy) and bright(x - dx, y - dy)
                        for dx, dy in ((1, 0), (0, 1), (1, 1), (1, -1))):
                    px[x, y] = core

    # And where dissolution left bare metal against air, the outline returns.
    for y in range(16):
        for x in range(16):
            if not px[x, y][3] and any(bright(x + dx, y + dy)
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = dark

    # A step more grip toward the pommel.
    for y in range(15, 10, -1):
        for x in range(6):
            if src[x, y][3]:
                tx, ty = x - 1, y + 1
                if 0 <= tx < 16 and 0 <= ty < 16 and not px[tx, ty][3]:
                    px[tx, ty] = src[x, y]

    return out


def save(im, name):
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


if __name__ == "__main__":
    images = [(f"{m}_dagger", dagger(m)) for m in MATERIALS] + [
    ] + [(f"{m}_greatsword", greatsword16(m)) for m in MATERIALS] + [
        # The basic wand wears the small crystal now; apprentice and holy
        # carry the big ornaments on a shifted stick.
        ("magic_wand", wand(tip=AMETHYST_TIP, double=False)),
        ("apprentice_wand", wand(tip=BIG_AMETHYST_TIP, double=False)),
        ("blaze_wand", wand("item/blaze_rod.png", FLAME_TIP, double=False, shift=(-1, 1))),
        ("breeze_wand", wand("item/breeze_rod.png", SNOWFLAKE_TIP, double=False, shift=(-1, 1))),
        ("holy_wand", wand(tip=BIG_HOLY_TIP, double=False, shift=(-2, 2))),
    ]

    for name, im in images:
        save(im, name)

    if "preview" in sys.argv[1:]:
        sheet = Image.new("RGBA", (len(images) * 144, 144), (60, 60, 68, 255))
        for i, (name, im) in enumerate(images):
            sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (8 + i * 144, 8))
        sheet.save("weapon_preview.png")
