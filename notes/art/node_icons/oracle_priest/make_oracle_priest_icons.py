"""Generate the 32x32 node icons for the epic Oracle-Priest constellation.

House style, matched from the nine shipped trees: a 16x16 vanilla-scale
drawing blown up 2x NEAREST onto a 32px canvas, so every "pixel" is a fat
2x2 block. One dominant, instantly-readable subject per icon (a vanilla
sprite wherever one fits), dark outlines, and at most one or two small
hand-plotted accents (sparkles, chevrons, motion lines) to say what the
node DOES.

Vanilla sprites live in ./vanilla (pulled from the 26.2 clientonly deobf
jar). Output goes to assets/archetypes/textures/node/oracle_priest/.

Usage: python3 make_oracle_priest_icons.py
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
VAN = os.path.join(HERE, "vanilla")
INSTALL = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/oracle_priest"))
# Renders land in ./out while iterating; --install also copies them into the
# assets tree.
DST = os.path.join(HERE, "out")

# --- the holy palette -------------------------------------------------------
OUT = (38, 26, 12, 255)          # warm dark outline
GOLD_D = (150, 96, 18, 255)
GOLD = (232, 168, 34, 255)
GOLD_L = (255, 210, 84, 255)
CREAM = (255, 246, 206, 255)
WHITE = (255, 255, 255, 255)
STEEL = (196, 198, 206, 255)
STEEL_D = (108, 110, 120, 255)
BONE_D = (86, 86, 86, 255)


def van(name):
    return Image.open(os.path.join(VAN, name + ".png")).convert("RGBA")


def c16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def up(im):
    """16px art -> the 32px canvas, chunky."""
    return im.resize((32, 32), Image.NEAREST)


def put(im, x, y, col):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((int(x), int(y)), col)


def blend(im, x, y, col):
    """Draw over whatever is there, respecting alpha."""
    if 0 <= x < im.width and 0 <= y < im.height:
        one = Image.new("RGBA", (1, 1), col)
        im.alpha_composite(one, (int(x), int(y)))


def fit(im, size):
    return im.resize((size, size), Image.NEAREST)


def outline(im, col=OUT):
    """Wrap every opaque run in a 1px dark border, vanilla-item style."""
    out = im.copy()
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][3]:
                continue
            near = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < im.width and 0 <= ny < im.height and px[nx, ny][3] > 128:
                    near = True
            if near:
                out.putpixel((x, y), col)
    return out


def sparkle(im, x, y, col=CREAM, arm=1):
    """A vanilla-ish plus-shaped twinkle centred on (x, y)."""
    put(im, x, y, col)
    for i in range(1, arm + 1):
        put(im, x + i, y, col)
        put(im, x - i, y, col)
        put(im, x, y + i, col)
        put(im, x, y - i, col)


def ring(im, cx, cy, r_in, r_out, col):
    for y in range(im.height):
        for x in range(im.width):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r_in <= d < r_out:
                put(im, x, y, col)


def disc(im, cx, cy, r, col):
    ring(im, cx, cy, 0, r, col)


def save(im32, name):
    os.makedirs(DST, exist_ok=True)
    im32.save(os.path.join(DST, name + ".png"))
    print(name + ".png")


# --- the icons --------------------------------------------------------------

def star(im, cx, cy, arm, core=WHITE, mid=GOLD_L, tip=GOLD):
    """A four-point holy burst: the shape vanilla uses for the nether star,
    fattened so it survives the 16px slot."""
    for i in range(1, arm + 1):
        col = mid if i <= arm - 2 else tip
        put(im, cx, cy - i, col)
        put(im, cx, cy + i, col)
        put(im, cx - i, cy, col)
        put(im, cx + i, cy, col)
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        put(im, cx + dx, cy + dy, mid)
    put(im, cx, cy, core)
    put(im, cx, cy - 1, core)
    put(im, cx, cy + 1, core)
    put(im, cx - 1, cy, core)
    put(im, cx + 1, cy, core)


def arrow_up(im, x, y, col=GOLD_L, edge=GOLD, h=7):
    """A stubby up-arrow, the same 'the number moved' accent the shipped
    Protector cooldown icon uses (theirs points down). Drawn on its own
    layer and outlined, so it stays an object and not a smear of gold."""
    rows = ["  aa  ",
            " aaaa ",
            "aaaaaa",
            "bbaabb",
            "  aa  ",
            "  aa  ",
            "  aa  "]
    layer = c16()
    for ry, row in enumerate(rows[:h]):
        for rx, ch in enumerate(row):
            if ch == "a":
                put(layer, x + rx, y + ry, col)
            elif ch == "b":
                put(layer, x + rx, y + ry, edge)
    im.alpha_composite(outline(layer, OUT))


def skull16(im, x, y):
    """A chunky hand-plotted skull — the vanilla skeleton head is a flat
    square of texture, and a square reads as a block at 16px."""
    o, b, d, s = OUT, (224, 222, 212, 255), (160, 157, 148, 255), (28, 24, 24, 255)
    rows = [
        "  oooooo  ",
        " obbbbbbo ",
        "obbbbbbbbo",
        "obssbbssbo",
        "obssbbssbo",
        "obbbbbbbbo",
        "obbbbbbbbo",
        " obbbbbbo ",
        " obdbbdbo ",
        " obdbbdbo ",
        "  o oo o  ",
    ]
    cmap = {"o": o, "b": b, "d": d, "s": s}
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch != " ":
                put(im, x + rx, y + ry, cmap[ch])


def aura_of_radiance():
    """The root: a ring of holy fire thrown out around the caster, burning
    hot at the middle. A hollow ring is a silhouette nothing else in the
    tree owns, so it stays the root even in the 16px slot."""
    im = c16()
    ring(im, 8, 8, 5.4, 7.4, GOLD_D)
    ring(im, 8, 8, 5.6, 7.0, GOLD)
    ring(im, 8, 8, 5.9, 6.7, GOLD_L)
    # the caster, white-hot at the middle
    disc(im, 8, 8, 3.2, GOLD)
    disc(im, 8, 8, 2.4, GOLD_L)
    disc(im, 8, 8, 1.6, CREAM)
    # light breaking out past the ring, so it radiates instead of just
    # sitting there being a gold hoop
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for k, col in ((5, GOLD_L), (6, GOLD)):
            put(im, 8 + (dx * k) - (1 if dx > 0 else 0),
                8 + (dy * k) - (1 if dy > 0 else 0), col)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        put(im, 8 + dx * 8 - (1 if dx > 0 else 0),
            8 + dy * 8 - (1 if dy > 0 else 0), CREAM)
    save(up(im), "aura_of_radiance")


def brilliance():
    """Both halves of the aura turned up at once: the heart it heals with,
    lit by the burst it burns with, and a plus for 'more of both'."""
    im = c16()
    star(im, 11, 4, 4)
    heart = fit(van("heart_full"), 11)
    im.alpha_composite(outline(heart, OUT), (1, 4))
    sparkle(im, 14, 12, CREAM)
    sparkle(im, 2, 1, GOLD_L)
    save(up(im), "brilliance")


def beacon_of_light():
    """Three times as long: the beacon block firing its beam clean off the
    top of the frame, with the up-arrow that says the number went up."""
    im = c16()
    for y in range(0, 11):
        put(im, 3, y, GOLD_D)
        put(im, 4, y, GOLD)
        put(im, 5, y, CREAM)
        put(im, 6, y, WHITE)
        put(im, 7, y, CREAM)
        put(im, 8, y, GOLD)
        put(im, 9, y, GOLD_D)
    # the beacon block: vanilla's glass core inside an obsidian frame, wider
    # than the beam so the beam reads as standing on it
    obsidian = (42, 30, 54, 255)
    obsidian_l = (78, 58, 96, 255)
    for y in range(5):
        for x in range(13):
            put(im, x, 11 + y, obsidian if (y in (0, 4) or x in (0, 12)) else (22, 16, 28, 255))
    for x in range(1, 12):
        put(im, x, 11, obsidian_l)
    core = van("beacon").crop((2, 2, 14, 14)).resize((11, 3), Image.NEAREST)
    im.alpha_composite(core, (1, 12))
    arrow_up(im, 10, 1)
    save(up(im), "beacon_of_light")


def blinding_light():
    """The undead caught in the aura: a skull with the light flaring white
    in its eye, sagging under weakness and slowness."""
    im = c16()
    skull16(im, 0, 3)
    # the flash, right on the eye — gold-edged so it separates from bone
    star(im, 10, 4, 4, core=WHITE, mid=CREAM, tip=GOLD)
    put(im, 10, 4, WHITE)
    # weakness and slowness: one steel arrow, pointed down
    layer = c16()
    rows = ["  aa  ",
            "  aa  ",
            "  aa  ",
            "bbaabb",
            "aaaaaa",
            " aaaa ",
            "  aa  "]
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch == "a":
                put(layer, 10 + rx, 9 + ry, STEEL)
            elif ch == "b":
                put(layer, 10 + rx, 9 + ry, STEEL_D)
    im.alpha_composite(outline(layer, OUT))
    save(up(im), "blinding_light")


def steadfast():
    """Nothing shifts you: a gold pavise braced in the aura's light, the
    shove coming in from both sides and stopping dead. Boots and ingots both
    turned to grey mush in the 16px slot; the shield keeps its silhouette.
    """
    im = c16()
    x0, y0, w = 2, 1, 11
    # body
    for y in range(0, 9):
        for x in range(1, w - 1):
            put(im, x0 + x, y0 + y, GOLD_L if x < 4 else (GOLD if x < 8 else GOLD_D))
    for y, (a, b) in enumerate(((2, 9), (3, 8), (4, 7)), start=9):
        for x in range(a, b):
            put(im, x0 + x, y0 + y, GOLD_L if x < 4 else (GOLD if x < 7 else GOLD_D))
    # outline
    for x in range(1, w - 1):
        put(im, x0 + x, y0 - 1, OUT)
    for y in range(0, 9):
        put(im, x0, y0 + y, OUT)
        put(im, x0 + w - 1, y0 + y, OUT)
    for y, (a, b) in enumerate(((1, 10), (2, 9), (3, 8)), start=9):
        put(im, x0 + a, y0 + y, OUT)
        put(im, x0 + b, y0 + y, OUT)
    for x in range(4, 7):
        put(im, x0 + x, y0 + 12, OUT)
    # the cross
    for y in range(1, 10):
        put(im, x0 + 5, y0 + y, CREAM)
        put(im, x0 + 6, y0 + y, WHITE if y < 6 else CREAM)
    for x in range(2, 10):
        put(im, x0 + x, y0 + 3, CREAM)
        put(im, x0 + x, y0 + 4, WHITE)
    # the shove, coming in and stopping dead
    for side, x in ((-1, 0), (1, 15)):
        for k in range(3):
            put(im, x + side * -k, 5 + abs(k - 1), STEEL)
            put(im, x + side * -k, 5 + abs(k - 1) + 1, STEEL_D)
    save(up(im), "steadfast")


def retribution():
    """The aura arms you: the golden sword lit up and moving, speed lines
    trailing off the hilt."""
    im = c16()
    for y, x0, ln in ((2, 1, 3), (5, 0, 4), (8, 0, 4)):
        for k in range(ln):
            put(im, x0 + k, y, CREAM)
            put(im, x0 + k, y + 1, GOLD_D)
    sword = van("golden_sword")
    im.alpha_composite(sword)
    star(im, 13, 2, 2, core=WHITE, mid=CREAM, tip=GOLD_L)
    save(up(im), "retribution")


ICONS = (aura_of_radiance, brilliance, beacon_of_light,
         blinding_light, steadfast, retribution)

if __name__ == "__main__":
    for fn in ICONS:
        fn()

    if "--install" in sys.argv:
        import shutil
        os.makedirs(INSTALL, exist_ok=True)
        for fn in ICONS:
            name = fn.__name__ + ".png"
            shutil.copyfile(os.path.join(DST, name), os.path.join(INSTALL, name))
        print("installed -> " + INSTALL)
