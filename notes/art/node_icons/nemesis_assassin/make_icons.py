"""Generate the 32px node-icon set for the epic Nemesis-Assassin sub-tree.

House style, read off the shipped sets (shadow/, assassin/, priest/, wizard/,
elementalist/, nemesis_shadow/): every icon is really a *16px* drawing upscaled
2x NEAREST onto the 32px canvas, so the chunky vanilla block is baked in and the
art survives the tree screen drawing it back down at 16. Vanilla sprites (and
the mod's own dagger sprites) are pulled straight from the jar / assets where
one exists; the rest is hand-plotted with a dark silhouette outline.

The tree's one running motif is THE MARK: a crimson diamond rune with a hot
core. Every node either does something to the creature wearing it (left column)
or is something the mark does to the crowd around it (right column), so the
rune has to appear, unmistakably the same rune, in almost every icon.

Backgrounds the icons have to hold up against: the unowned inset body
0xFF8B8B8B, the hovered 0xFFA3A3A3, and the owned plate.

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
ASSETS = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes"))
DST = os.path.join(ASSETS, "textures/node/nemesis_assassin")
OUT_DIR = os.path.join(HERE, "out")

# --- palette -----------------------------------------------------------------
CLEAR = (0, 0, 0, 0)
OUT = (18, 14, 22, 255)          # the universal dark outline
OUT_SOFT = (34, 26, 40, 255)

BONE = (236, 236, 228, 255)
BONE_MID = (196, 196, 188, 255)
BONE_DARK = (128, 128, 124, 255)

RED = (206, 34, 42, 255)
RED_D = (118, 14, 20, 255)
RED_L = (255, 104, 104, 255)
RED_HOT = (255, 214, 200, 255)

NIGHT = (44, 32, 56, 255)
NIGHT_D = (24, 16, 32, 255)
NIGHT_L = (74, 56, 92, 255)

STONE = (152, 152, 152, 255)
STONE_D = (108, 108, 108, 255)
STONE_L = (186, 186, 186, 255)

VEN = (108, 196, 60, 255)          # vanilla poison green
VEN_D = (58, 122, 30, 255)
VEN_L = (176, 240, 128, 255)

GHOST = (222, 238, 250, 255)
GHOST_M = (156, 194, 222, 255)
GHOST_D = (92, 128, 160, 255)

CYAN = (150, 244, 240, 255)


# --- helpers -----------------------------------------------------------------
def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def mod_item(name):
    return Image.open(os.path.join(ASSETS, "textures/item", name)).convert("RGBA")


def canvas(size=16):
    return Image.new("RGBA", (size, size), CLEAR)


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


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


def dither(im, x0, y0, x1, y1, parity=0):
    """Knock out every other pixel of a region — the tree's existing idiom for
    "not entirely there" (see shadow/ghost_armor)."""
    px = im.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < im.width and 0 <= y < im.height and (x + y) % 2 == parity:
                if px[x, y][3]:
                    px[x, y] = CLEAR


def shade(im, ox, oy, base, mid, dark, span=9):
    """Vanilla's fixed top-left key light, applied to one stamped cluster."""
    px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            if px[x, y] == base and (x - ox) + (y - oy) >= span:
                px[x, y] = mid
            elif px[x, y] == base and (x - ox) + (y - oy) >= span + 3:
                px[x, y] = dark


def save(im, name):
    big = im.resize((32, 32), Image.NEAREST)
    os.makedirs(OUT_DIR, exist_ok=True)
    big.save(os.path.join(OUT_DIR, f"{name}.png"))
    if "--install" in sys.argv:
        os.makedirs(DST, exist_ok=True)
        big.save(os.path.join(DST, f"{name}.png"))
    return name


# --- shared motifs -----------------------------------------------------------
# Seven wide, not five: a head only one pixel narrower than the shoulders is a
# head the outline eats, and the figure comes back as a slab.
MOB = [
    "..###..",
    "..###..",
    "..###..",
    "#######",
    "#######",
    "#######",
    ".#####.",
    ".##.##.",
    ".##.##.",
]

SKULL = [
    "..####..",
    ".######.",
    "########",
    "#EE##EE#",
    "#EE##EE#",
    "########",
    ".######.",
    "..#.#.#.",
]


def skull(im, ox, oy, eye=RED, eye_hi=RED_L):
    stamp(im, SKULL, ox, oy, {"#": BONE, "E": eye})
    shade(im, ox, oy, BONE, BONE_MID, BONE_DARK, span=9)
    put(im, ox + 1, oy + 3, eye_hi)
    put(im, ox + 5, oy + 3, eye_hi)
    put(im, ox + 3, oy + 5, OUT_SOFT)
    put(im, ox + 4, oy + 5, OUT_SOFT)


def brand(im, cx, cy, r=2, ring=RED, core=RED_HOT, glow=RED_L):
    """THE MARK: a crimson diamond rune with a hot core. r=2 is the badge that
    sits on a creature; bigger radii are the rune itself."""
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            d = abs(dx) + abs(dy)
            if d == r:
                put(im, cx + dx, cy + dy, ring)
            elif r >= 4 and d == r - 1:
                put(im, cx + dx, cy + dy, glow)
    if r >= 4:
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if abs(dx) + abs(dy) <= 1:
                    put(im, cx + dx, cy + dy, core)
    else:
        put(im, cx, cy, core)


def figure(ox, oy, body=NIGHT, lo=NIGHT_D, hi=NIGHT_L, eyes=RED_L, shape=MOB):
    """A hostile silhouette on its own canvas, lit and outlined.

    The head is banded lighter than the torso and the legs darker than both:
    a figure toned flat comes back at 16px as a slab with a notch in it, and
    the three bands are what keep it a creature.
    """
    im = canvas()
    stamp(im, shape, ox, oy, {"#": body})
    px = im.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == body:
                if y >= oy + 6:
                    px[x, y] = lo
                elif y <= oy + 2:
                    px[x, y] = hi
    outline(im, OUT)
    # the shoulder line, so head and torso do not fuse into one block
    rect(im, ox + 1, oy + 3, ox + 5, oy + 3, OUT_SOFT)
    if eyes:
        put(im, ox + 2, oy + 1, eyes)
        put(im, ox + 4, oy + 1, eyes)
    return im


def brackets(im, colour=RED, hot=RED_L):
    """Target-lock corner brackets — the node screen's shorthand for "this one,
    at range, through everything"."""
    for cx, cy, sx, sy in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        put(im, cx, cy, hot)
        put(im, cx + sx, cy, colour)
        put(im, cx + 2 * sx, cy, colour)
        put(im, cx, cy + sy, colour)
        put(im, cx, cy + 2 * sy, colour)


def spark(im, x, y, c=BONE):
    put(im, x, y, c)
    put(im, x - 1, y, c)
    put(im, x + 1, y, c)
    put(im, x, y - 1, c)
    put(im, x, y + 1, c)


# --- the eight icons ---------------------------------------------------------
def death_mark():
    """The gate, and the rune every other node in the tree is about: the mark
    itself, burning, locked in the brackets that say you put it there from
    thirty-two blocks off with nothing but a look."""
    im = canvas()
    # a solid diamond field so the rune has mass at 16px, then the ring, then
    # the core — a thin outline diamond alone comes back as a red smudge
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            d = abs(dx) + abs(dy)
            if d <= 6:
                put(im, 7 + dx, 7 + dy, NIGHT_D if d <= 3 else RED_D)
            if d == 6:
                put(im, 7 + dx, 7 + dy, RED)
            if d == 5:
                put(im, 7 + dx, 7 + dy, RED_D)
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            if abs(dx) + abs(dy) == 6 and (dx > 0) == (dy < 0):
                put(im, 7 + dx, 7 + dy, RED_L)
    outline(im, OUT)
    # the core: a hot four-point star, which is what a *mark* looks like and
    # what a plain filled diamond does not
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        put(im, 7 + dx, 7 + dy, RED_HOT)
    for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        put(im, 7 + dx, 7 + dy, RED_L)
    for dx, dy in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        put(im, 7 + dx, 7 + dy, RED)
    brackets(im)
    return save(im, "death_mark")


def stalk():
    """Outlined through walls: a solid crimson creature with the stone-brick
    coursing running *across* it. Drawing the mark as a thin glowing line and
    letting the bricks show through it was honest to the game and unreadable
    at 16px; a filled body with the wall passing in front of it is neither."""
    im = canvas()
    rect(im, 0, 0, 15, 15, STONE)
    rect(im, 0, 0, 15, 0, STONE_L)
    for y in (4, 9, 14):
        rect(im, 0, y, 15, y, STONE_D)
        if y < 14:
            rect(im, 0, y + 1, 15, y + 1, STONE_L)
    for x, y0, y1 in ((4, 1, 3), (11, 1, 3), (7, 5, 8), (14, 5, 8), (2, 10, 13),
                      (10, 10, 13)):
        rect(im, x, y0, x, y1, STONE_D)
    rect(im, 0, 15, 15, 15, STONE_D)
    fig = canvas()
    stamp(fig, MOB, 5, 3, {"#": RED})
    px = fig.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == RED:
                if y >= 9:
                    px[x, y] = RED_D
                elif y <= 5:
                    px[x, y] = RED_L
    outline(fig, OUT)
    rect(fig, 6, 6, 10, 6, (92, 10, 16, 255))       # the shoulder line
    rect(fig, 8, 11, 8, 12, (92, 10, 16, 255))      # and the gap between legs
    put(fig, 7, 4, RED_HOT)
    put(fig, 9, 4, RED_HOT)
    im.alpha_composite(fig)
    # the wall, back in front: the joints re-laid over the body is what says
    # the creature is behind eleven inches of brick and lit up anyway
    joints = canvas()
    for y in (4, 9):
        rect(joints, 0, y, 15, y, (58, 58, 58, 130))
    rect(joints, 7, 5, 7, 8, (58, 58, 58, 130))
    rect(joints, 10, 10, 10, 13, (58, 58, 58, 130))
    im.alpha_composite(joints)
    brand(im, 8, 8, r=2)
    return save(im, "stalk")


def contagion():
    """It dies and the mark does not: the rune lifts off the skull it has
    finished with and jumps to whatever is standing nearest."""
    im = canvas()
    dead = canvas()
    skull(dead, 0, 8, eye=OUT_SOFT, eye_hi=OUT_SOFT)
    outline(dead, OUT)
    im.alpha_composite(dead)
    im.alpha_composite(figure(9, 5))
    # the jump, drawn as three beads getting hotter towards the new host
    for x, y, c in ((3, 6, RED_D), (5, 4, RED), (7, 2, RED_L)):
        put(im, x, y, c)
    brand(im, 12, 9, r=2)
    return save(im, "contagion")


def headhunter():
    """Two ranks of dagger damage on the mark: the mod's own netherite dagger,
    its point already in the rune."""
    im = canvas()
    body = canvas()
    body.alpha_composite(mod_item("netherite_dagger.png"), (-1, 1))
    outline(body, OUT)
    im.alpha_composite(body)
    # netherite on a mid-grey plate is very nearly the plate: a bone edge down
    # the whole blade is what keeps this a dagger and not a brown smear
    for x, y in ((4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5)):
        put(im, x, y, BONE_MID)
    for x, y in ((5, 10), (6, 9), (7, 8), (8, 7), (9, 6)):
        put(im, x, y, BONE_DARK)
    brand(im, 11, 3, r=2)
    put(im, 11, 3, RED_HOT)
    put(im, 14, 6, RED_L)
    put(im, 8, 1, RED)
    return save(im, "headhunter")


def coup_de_grace():
    """A wounded mark does not get to walk away: the dagger comes down into the
    skull, and this is the one node where the mark is already out."""
    im = canvas()
    head = canvas()
    skull(head, 1, 7, eye=RED_D, eye_hi=RED)
    outline(head, OUT)
    im.alpha_composite(head)
    # the mod's diamond dagger, turned point-down-left: vertical and centred
    # reads at 16px as a flask with a stem, and the diagonal is the only
    # orientation that still says *struck*
    knife = mod_item("diamond_dagger.png").transpose(Image.ROTATE_180)
    body = canvas()
    body.alpha_composite(knife, (0, -1))
    outline(body, OUT)
    im.alpha_composite(body)
    # entry wound: the bone splits and the strike flashes
    for x, y in ((5, 10), (4, 11), (6, 11)):
        put(im, x, y, OUT_SOFT)
    put(im, 5, 9, RED_L)
    put(im, 6, 10, RED)
    put(im, 3, 15, RED)
    put(im, 7, 15, RED_D)
    return save(im, "coup_de_grace")


def carrier():
    """What is on the mark goes on everything beside it: the branded creature
    at the centre of a ring of poison already on its way out."""
    im = canvas()
    # a *ring*, laid down first: at 16px a scatter of green is dirt on the
    # plate, and a ring is a thing leaving the middle
    for a in range(0, 360, 45):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        bx, by = round(7.5 + 6.6 * dx), round(7.5 + 6.6 * dy)
        rect(im, bx, by, bx + 1, by + 1, VEN)
        put(im, bx, by, VEN_L)
        put(im, bx + 1, by + 1, VEN_D)
    for a in range(22, 360, 45):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        put(im, round(7.5 + 7.4 * dx), round(7.5 + 7.4 * dy), VEN_D)
    im.alpha_composite(figure(5, 3, eyes=VEN_L))
    brand(im, 8, 8, r=2)
    return save(im, "carrier")


def vanishing_act():
    """Kill it and you are not there: the bottle is drawn, its contents and
    half its glass are not, the speed is already on you and the rune you just
    broke is still coming apart behind it.

    Three passes were spent trying to draw the vanishing *man*. At 16px a
    figure that is half gone is a dark blob, and a figure that is only an
    outline is a light blob; the bottle is the read the game itself gives
    Invisibility, and it is the item the node's own table names."""
    im = canvas()
    glass = vanilla("item/potion.png")
    liquid = vanilla("item/potion_overlay.png")
    lp = liquid.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = lp[x, y]
            if a:
                lp[x, y] = (150 * r // 255, 162 * g // 255, 184 * b // 255, a)
    body = canvas()
    body.alpha_composite(liquid)
    body.alpha_composite(glass)
    # the outline is taken from the whole bottle before anything is removed,
    # so the shape stays a bottle after the bottom of it stops existing
    ring = canvas()
    ring.alpha_composite(glass)
    outline(ring, OUT)
    rp = ring.load()
    for x in range(16):
        for y in range(16):
            if rp[x, y] != OUT:
                rp[x, y] = CLEAR
    dither(body, 0, 9, 15, 12, parity=1)
    dither(body, 0, 13, 15, 15, parity=0)
    im.alpha_composite(body)
    im.alpha_composite(ring)
    # Speed II, off the side it went
    rect(im, 0, 3, 3, 3, CYAN)
    rect(im, 0, 6, 2, 6, CYAN)
    rect(im, 1, 9, 2, 9, GHOST_D)
    # the rune it broke, in two pieces
    for x, y in ((13, 1), (14, 2), (12, 2), (14, 5), (15, 6), (13, 6)):
        put(im, x, y, RED)
    put(im, 13, 1, RED_L)
    put(im, 15, 6, RED_D)
    return save(im, "vanishing_act")


def deaths_head():
    """The mark goes off like a charge: five hearts to everything left standing
    around the corpse. The blast is drawn as spikes rather than the tidy ring
    the neighbouring Nemesis-Shadow gate uses — two skulls in one archetype
    have to be told apart at 16px by what is around them."""
    im = canvas()
    for a in range(0, 360, 45):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        for r, c in ((6.4, RED_L), (7.4, RED), (8.4, RED_D)):
            put(im, round(7.5 + r * dx), round(7.5 + r * dy), c)
    for a in range(22, 360, 45):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        put(im, round(7.5 + 6.6 * dx), round(7.5 + 6.6 * dy), RED)
        put(im, round(7.5 + 7.6 * dx), round(7.5 + 7.6 * dy), RED_D)
    head = canvas()
    skull(head, 4, 4, eye=RED, eye_hi=RED_HOT)
    outline(head, OUT)
    im.alpha_composite(head)
    # the crack it is going out through — kept up on the crown, because a
    # fracture run down the centre line just reads as a nose
    for x, y in ((6, 4), (7, 5)):
        put(im, x, y, OUT_SOFT)
    put(im, 7, 4, RED_L)
    put(im, 3, 2, BONE_MID)
    put(im, 12, 2, BONE_MID)
    put(im, 13, 12, BONE_DARK)
    return save(im, "deaths_head")


ICONS = [death_mark, stalk, contagion, headhunter, coup_de_grace,
         carrier, vanishing_act, deaths_head]

if __name__ == "__main__":
    for fn in ICONS:
        print(fn())
