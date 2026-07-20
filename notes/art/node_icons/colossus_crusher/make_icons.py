"""Generate the 32px node-icon set for the epic Colossus-Crusher sub-tree.

House style, read off the shipped sets (crusher/, shadow/, assassin/,
oracle_priest/, nemesis_shadow/): every icon is a *16px* drawing upscaled 2x
NEAREST onto the 32px canvas, so the chunky vanilla block is baked in and the
art survives the tree screen drawing it back down at 16. Vanilla sprites are
pulled straight from the client jar where one exists (the mace is the tree's
weapon and already anchors crusher/quake + crusher/meteor); the rest is
hand-plotted with a dark silhouette outline.

Theme: gravity given a sky. Leap, aftershock, a well, immovability, an
unblockable blow.

Usage: python3 make_icons.py [--install]
"""
import math
import os
import sys
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/colossus_crusher"))
OUT_DIR = os.path.join(HERE, "out")

# --- palette -----------------------------------------------------------------
CLEAR = (0, 0, 0, 0)
OUT = (18, 14, 22, 255)          # the universal dark outline
OUT_SOFT = (34, 30, 38, 255)

STONE_L = (186, 186, 182, 255)
STONE = (138, 138, 136, 255)
STONE_D = (92, 92, 94, 255)
STONE_XD = (58, 58, 62, 255)

IRON_L = (232, 232, 238, 255)
IRON = (196, 196, 204, 255)
IRON_M = (146, 146, 156, 255)
IRON_D = (94, 94, 104, 255)

ANV_L = (116, 116, 122, 255)
ANV = (78, 78, 84, 255)
ANV_D = (48, 48, 54, 255)

AMBER = (255, 202, 74, 255)
AMBER_L = (255, 248, 198, 255)
AMBER_D = (198, 128, 20, 255)

GOLD = (255, 196, 56, 255)
GOLD_L = (255, 242, 170, 255)
GOLD_D = (186, 122, 16, 255)

PUR = (128, 62, 186, 255)
PUR_D = (66, 28, 100, 255)
PUR_L = (182, 126, 228, 255)
VOID = (26, 12, 40, 255)

TEAL = (62, 204, 172, 255)
TEAL_L = (156, 244, 220, 255)
TEAL_D = (24, 108, 96, 255)

WIND = (222, 244, 255, 255)
WIND_M = (156, 196, 224, 255)
WIND_D = (98, 134, 166, 255)

RED = (206, 34, 42, 255)
RED_D = (118, 14, 20, 255)

WOOD = (124, 86, 52, 255)
WOOD_D = (78, 52, 30, 255)


# --- helpers -----------------------------------------------------------------
def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def canvas(size=16):
    return Image.new("RGBA", (size, size), CLEAR)


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((int(x), int(y)), c)


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(im, x, y, c)


def stamp(im, rows, ox, oy, key):
    """Paint an ASCII sprite. `key` maps a char to a colour; '.' is skipped."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != "." and ch in key:
                put(im, ox + dx, oy + dy, key[ch])


def outline(im, colour=OUT, diagonals=True):
    """Ring every opaque cluster in a dark silhouette line — the single thing
    that makes a shape survive being drawn at 16px on a mid-grey plate."""
    src = im.copy()
    sp = src.load()
    offs = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    if diagonals:
        offs += [(-1, -1), (1, -1), (-1, 1), (1, 1)]
    for y in range(im.height):
        for x in range(im.width):
            if sp[x, y][3]:
                continue
            if any(0 <= x + dx < im.width and 0 <= y + dy < im.height
                   and sp[x + dx, y + dy][3] > 128 for dx, dy in offs):
                put(im, x, y, colour)


def ring(im, cx, cy, rx, ry, colour):
    for a in range(0, 1440):
        x = round(cx + rx * math.cos(math.radians(a / 4)))
        y = round(cy + ry * math.sin(math.radians(a / 4)))
        put(im, x, y, colour)


def save(im, name):
    big = im.resize((32, 32), Image.NEAREST)
    os.makedirs(OUT_DIR, exist_ok=True)
    big.save(os.path.join(OUT_DIR, f"{name}.png"))
    if "--install" in sys.argv:
        os.makedirs(DST, exist_ok=True)
        big.save(os.path.join(DST, f"{name}.png"))
    return name


# --- shared motifs -----------------------------------------------------------
def mace(scale=14, flip=True):
    """The tree's weapon, straight from the jar. Flipped so the head sits
    top-right, which leaves the bottom-left free for ground and dust."""
    im = vanilla("item/mace.png")
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if scale != 16:
        im = im.resize((scale, scale), Image.NEAREST)
    return im


def chevron_up(im, cx, y, half, colour):
    """A chunky up-pointing chevron: the tree's motion mark."""
    for i in range(half + 1):
        put(im, cx - i, y + i, colour)
        put(im, cx + i, y + i, colour)


def chevron_down(im, cx, y, half, colour):
    for i in range(half + 1):
        put(im, cx - i, y - i, colour)
        put(im, cx + i, y - i, colour)


def burst(im, cx, cy, r, hot=AMBER_L, warm=AMBER, cool=AMBER_D):
    """An impact star: a fat plus with short diagonals and a white core."""
    for i in range(1, r + 1):
        c = warm if i <= r - 1 else cool
        put(im, cx - i, cy, c)
        put(im, cx + i, cy, c)
        put(im, cx, cy - i, c)
        put(im, cx, cy + i, c)
    for i in range(1, max(1, r - 1)):
        put(im, cx - i, cy - i, cool)
        put(im, cx + i, cy - i, cool)
        put(im, cx - i, cy + i, cool)
        put(im, cx + i, cy + i, cool)
    put(im, cx, cy, hot)
    put(im, cx - 1, cy, hot)
    put(im, cx + 1, cy, hot)
    put(im, cx, cy - 1, hot)


ARROW_HEAD = [
    "...#...",
    "..###..",
    ".#####.",
    "#######",
]


def arrow(im, cx, tip, tail, body, edge):
    """A chunky arrow — the tree's one motion mark, drawn up for the leap and
    down for the landing so the two read as ends of one mechanic. `tip` is the
    point, `tail` the blunt end; direction follows from which is higher."""
    step = 1 if tail > tip else -1
    for i in range(4, abs(tail - tip) + 1):
        y = tip + step * i
        put(im, cx, y, body)
        put(im, cx + 1, y, edge)
    rows = ARROW_HEAD if step > 0 else ARROW_HEAD[::-1]
    for dy, row in enumerate(rows):
        y = tip + step * (dy if step > 0 else dy - 3)
        for dx, ch in enumerate(row):
            if ch == "#":
                put(im, cx - 2 + dx, y, body if dx < 4 else edge)
    return im


def rim(im, edge, inner=None):
    """Turn the outermost ring of an opaque shape into a border colour — how
    the shipped sets keep wood and iron apart at this size."""
    src = im.copy().load()
    for y in range(im.height):
        for x in range(im.width):
            if not src[x, y][3]:
                continue
            if any(not (0 <= x + dx < im.width and 0 <= y + dy < im.height)
                   or not src[x + dx, y + dy][3]
                   for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                put(im, x, y, edge)
            elif inner:
                put(im, x, y, inner)


# --- the six icons -----------------------------------------------------------
def titan_leap():
    """The gate and the tree's one ability key: the mace is already off the
    ground, the dust it kicked is still hanging where the feet were."""
    im = canvas()

    # the ground it left, and the dust still standing on the take-off point
    rect(im, 0, 15, 15, 15, STONE_D)
    for x, y in ((2, 14), (3, 14), (6, 14), (7, 14), (1, 13), (7, 13)):
        put(im, x, y, STONE)
    for x, y in ((2, 13), (3, 13), (6, 13), (4, 14), (5, 14), (8, 14)):
        put(im, x, y, STONE_L)

    # the weapon, mid-rise: head top-left the way the vanilla item draws it,
    # so the handle sweeps down-right and the right edge stays free
    body = canvas()
    body.alpha_composite(mace(12, flip=False), (0, 0))
    outline(body, OUT)
    im.alpha_composite(body)

    # the leap: one bold arrow up the right edge. At 16px this is the read.
    up = canvas()
    arrow(up, 11, 1, 13, AMBER, AMBER_D)
    outline(up, OUT, diagonals=False)
    for y in (2, 3, 6, 7, 8):
        put(up, 11, y, AMBER_L)
    im.alpha_composite(up)
    return save(im, "titan_leap")


def aftershock():
    """What the landing costs the ground: the slab breaks open and lights up
    from inside, and what was standing on it is already in the air."""
    im = canvas()

    # the ground itself, a real vanilla block rather than a drawn slab — the
    # same trick the shipped crusher/earth_shatter uses
    body = canvas()
    body.alpha_composite(vanilla("block/magma.png").crop((0, 0, 16, 16))
                         .resize((12, 12), Image.NEAREST), (2, 4))
    # the bite the weight took out of the top
    for x, y in ((6, 4), (7, 4), (8, 4), (7, 5), (2, 4), (13, 4)):
        body.putpixel((x, y), CLEAR)
    bp = body.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = bp[x, y]
            if not a:
                continue
            if y >= 12:                       # vanilla's key light: dark below
                bp[x, y] = (r * 3 // 5, g * 3 // 5, b * 3 // 5, a)
            elif y <= 5:
                bp[x, y] = (min(255, r * 5 // 4), min(255, g * 5 // 4),
                            min(255, b * 5 // 4), a)
    outline(body, OUT)
    im.alpha_composite(body)

    # the light coming up out of the break
    rect(im, 6, 3, 8, 3, AMBER_L)
    put(im, 5, 4, AMBER)
    put(im, 9, 4, AMBER)
    put(im, 7, 4, AMBER_L)
    put(im, 7, 2, AMBER)

    # and what the slam throws: chunks already off the slab
    chunks = canvas()
    for cx, cy, w in ((1, 1, 2), (11, 1, 2), (14, 6, 1), (4, 0, 1)):
        rect(chunks, cx, cy, cx + w - 1, cy + w - 1, STONE_L)
        put(chunks, cx + w - 1, cy + w - 1, STONE_D)
    outline(chunks, OUT, diagonals=False)
    im.alpha_composite(chunks)
    return save(im, "aftershock")


def gravity_well():
    """No damage, no launch — everything inside twelve blocks comes in and
    stops. An ender core with four heads dragging the world onto it."""
    im = canvas()

    # the well: a solid dark body, not a ring, so it never reads as an eye
    well = canvas()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) / 4.6) ** 2 + ((y - 7.5) / 4.6) ** 2
            if d <= 1.0:
                put(well, x, y, PUR_D if d > 0.45 else VOID)
    wp = well.load()
    for x in range(16):
        for y in range(16):
            if wp[x, y] == PUR_D and (x - 7.5) + (y - 7.5) < -5:
                wp[x, y] = PUR_L
            elif wp[x, y] == PUR_D and (x - 7.5) + (y - 7.5) < -2:
                wp[x, y] = PUR
    outline(well, OUT)
    im.alpha_composite(well)

    # the throat, and the pearl sitting at the bottom of it
    rect(im, 5, 6, 10, 10, VOID)
    rect(im, 6, 5, 9, 5, VOID)
    rect(im, 6, 11, 9, 11, VOID)
    pearl = vanilla("item/ender_pearl.png").crop((2, 2, 15, 15)).resize((7, 7), Image.NEAREST)
    im.alpha_composite(pearl, (5, 5))
    for x, y in ((5, 5), (6, 4), (9, 4), (10, 5)):
        put(im, x, y, PUR_L)

    # four arrows pointing in — the one mark that says "pulled", not "glowing".
    # Drawn as arrows and not barbs, or the set reads as a snowflake.
    tile = Image.new("RGBA", (5, 5), CLEAR)
    for dy, row in enumerate(("..#..", "...#.", "#####", "...#.", "..#..")):
        for dx, ch in enumerate(row):
            if ch == "#":
                tile.putpixel((dx, dy), TEAL)
    tile.putpixel((4, 2), TEAL_L)
    tile.putpixel((3, 1), TEAL_L)
    tile.putpixel((3, 3), TEAL_L)

    heads = canvas()
    for k, (px, py) in enumerate(((0, 6), (6, 0), (11, 6), (6, 11))):
        t = tile
        for _ in range(k):
            t = t.transpose(Image.ROTATE_270)
        heads.alpha_composite(t, (px, py))
    outline(heads, OUT, diagonals=False)
    im.alpha_composite(heads)
    return save(im, "gravity_well")


ANVIL = [
    "###########",
    "###########",
    "###########",
    ".#########.",
    "...#####...",
    "...#####...",
    "..#######..",
    ".#########.",
    "###########",
    "###########",
]


def immovable():
    """Nothing shoves you. The anvil is the shape; the gusts die on it."""
    im = canvas()
    body = canvas()
    stamp(body, ANVIL, 3, 3, {"#": IRON_M})
    rect(body, 3, 3, 13, 3, IRON)
    rect(body, 4, 4, 12, 4, IRON_M)
    rect(body, 3, 5, 13, 5, IRON_D)
    rect(body, 6, 7, 10, 8, IRON_M)
    rect(body, 6, 7, 6, 8, IRON)
    rect(body, 4, 11, 12, 11, IRON)
    rect(body, 3, 12, 13, 12, IRON_M)
    rect(body, 3, 13, 13, 13, IRON_D)
    outline(body, OUT)
    im.alpha_composite(body)

    # the knockback arriving and dying: streaks that stop dead at the iron
    for y, n in ((6, 3), (9, 3), (12, 2)):
        for i in range(n):
            c = WIND if i == 0 else WIND_M
            put(im, i, y, c)
            put(im, 15 - i, y, c)
    for x, y in ((0, 4), (1, 10), (15, 4), (14, 10)):
        put(im, x, y, WIND_D)
    return save(im, "immovable")


BIG_HEART = [
    "..###.###..",
    ".#########.",
    "###########",
    "###########",
    "###########",
    "###########",
    ".#########.",
    "..#######..",
    "...#####...",
    "....###....",
    ".....#.....",
]


def bulwark():
    """Battle Trance's banked health, this time wearing plate: the same gold
    heart the base tree draws, strapped into an iron band."""
    im = canvas()
    heart = canvas()
    stamp(heart, BIG_HEART, 2, 2, {"#": GOLD})
    hp = heart.load()
    for x in range(16):
        for y in range(16):
            if hp[x, y] == GOLD and (x - 2) + (y - 2) >= 13:
                hp[x, y] = GOLD_D
    rect(heart, 4, 3, 6, 4, GOLD_L)
    put(heart, 4, 5, GOLD_L)
    outline(heart, OUT)
    im.alpha_composite(heart)

    # the strap that makes it armour and not just banked health. Masked to the
    # heart, or a full-width bar turns the icon into a shelf with a blob on it.
    band = canvas()
    hp2 = heart.load()
    for y in (6, 7, 8):
        for x in range(16):
            if hp2[x, y][3] and hp2[x, y] != OUT:
                put(band, x, y, IRON_M if y > 6 else IRON)
    for x in range(16):
        if band.getpixel((x, 8))[3]:
            put(band, x, 8, IRON_D)
    for x in (4, 11):
        put(band, x, 7, IRON_L)
    outline(band, OUT, diagonals=False)
    im.alpha_composite(band)

    # the hit that slides off it, kept to a spark so it stays a detail
    put(im, 13, 3, WIND)
    put(im, 14, 2, WIND_M)
    put(im, 13, 2, WIND_M)
    return save(im, "bulwark")


SHIELD = [
    "############",
    "############",
    "############",
    "############",
    "############",
    "############",
    "############",
    ".##########.",
    ".##########.",
    "..########..",
    "...######...",
    "....####....",
    ".....##.....",
]


def siegebreaker():
    """Unstoppable Force: the guard is up and it does not matter. The blow
    goes through the middle and the shield leaves in two pieces."""
    whole = canvas()
    stamp(whole, SHIELD, 2, 2, {"#": WOOD})
    wp = whole.load()
    for x in range(16):
        for y in range(16):
            if wp[x, y] == WOOD and y >= 9:
                wp[x, y] = WOOD_D
    rim(whole, IRON_M)
    rect(whole, 3, 2, 12, 2, IRON)
    # the boss, so each half still reads as shield and not as a board
    rect(whole, 6, 6, 9, 8, IRON_M)
    rect(whole, 6, 6, 9, 6, IRON)
    rect(whole, 6, 8, 9, 8, IRON_D)

    # split it: everything left of the seam steps left, everything right steps
    # right, and the two seam columns are simply gone
    im = canvas()
    for y in range(16):
        for x in range(16):
            c = whole.getpixel((x, y))
            if not c[3]:
                continue
            if x <= 6:
                put(im, x - 1, y, c)
            elif x >= 9:
                put(im, x + 1, y, c)
    left = canvas()
    right = canvas()
    for y in range(16):
        for x in range(16):
            c = im.getpixel((x, y))
            if c[3]:
                (left if x < 7 else right).putpixel((x, y), c)
    outline(left, OUT)
    outline(right, OUT)
    out = canvas()
    out.alpha_composite(left)
    out.alpha_composite(right)

    # the blow that went through: a jagged bolt down the seam, white at the top
    seam = [7, 7, 7, 8, 8, 7, 7, 8, 8, 8, 7, 7, 8, 8, 8, 7]
    for y in range(16):
        put(out, seam[y], y, AMBER_L if y < 8 else AMBER)
        put(out, seam[y] - 1, y, AMBER if y < 8 else AMBER_D)
    for x, y in ((4, 3), (11, 5), (3, 9), (12, 10), (5, 13), (10, 14)):
        put(out, x, y, AMBER)
    return save(out, "siegebreaker")


ICONS = [titan_leap, aftershock, gravity_well, immovable, bulwark, siegebreaker]

if __name__ == "__main__":
    for fn in ICONS:
        print(fn())
