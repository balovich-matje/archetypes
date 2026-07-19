"""Generate the 32px node-icon set for the epic Nemesis-Shadow sub-tree.

House style, read off the nine shipped sets (shadow/, assassin/, priest/,
wizard/, elementalist/): every icon is really a *16px* drawing upscaled 2x
NEAREST onto the 32px canvas, so the chunky vanilla block is baked in and the
art survives the tree screen drawing it back down at 16. Vanilla sprites are
pulled straight from the client jar where one exists; the rest is hand-plotted
with a dark silhouette outline.

Backgrounds the icons have to hold up against: the unowned inset body
0xFF8B8B8B, the hovered 0xFFA3A3A3, and the owned plate, which for the Agility
archetype is a bright mint 0xFF7FCF9F.

Usage: python3 make_icons.py [--install]
"""
import os
import sys
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/nemesis_shadow"))
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

PUR = (140, 66, 186, 255)
PUR_D = (78, 30, 108, 255)
PUR_L = (180, 122, 226, 255)

GHOST = (214, 236, 250, 255)
GHOST_M = (150, 192, 222, 255)
GHOST_D = (86, 124, 158, 255)

AMBER = (255, 202, 74, 255)
AMBER_L = (255, 248, 198, 255)
AMBER_D = (198, 128, 20, 255)

CYAN = (150, 244, 240, 255)
TEAL = (44, 158, 158, 255)
TEAL_D = (22, 84, 92, 255)

NIGHT = (44, 32, 56, 255)
NIGHT_D = (24, 16, 32, 255)
NIGHT_L = (74, 56, 92, 255)

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


def save(im, name):
    big = im.resize((32, 32), Image.NEAREST)
    os.makedirs(OUT_DIR, exist_ok=True)
    big.save(os.path.join(OUT_DIR, f"{name}.png"))
    if "--install" in sys.argv:
        os.makedirs(DST, exist_ok=True)
        big.save(os.path.join(DST, f"{name}.png"))
    return name


# --- shared motifs -----------------------------------------------------------
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
    key = {"#": BONE, "E": eye}
    stamp(im, SKULL, ox, oy, key)
    # top-left light, bottom-right shade: vanilla's fixed key light.
    for x in range(8):
        for y in range(8):
            px = im.getpixel((ox + x, oy + y)) if 0 <= ox + x < 16 and 0 <= oy + y < 16 else CLEAR
            if px == BONE and (x + y) >= 9:
                put(im, ox + x, oy + y, BONE_MID)
            if px == BONE and (x + y) >= 12:
                put(im, ox + x, oy + y, BONE_DARK)
    # a hot pixel in each socket so the eyes glow rather than just sit there
    put(im, ox + 1, oy + 3, eye_hi)
    put(im, ox + 5, oy + 3, eye_hi)
    # nose notch
    put(im, ox + 3, oy + 5, OUT_SOFT)
    put(im, ox + 4, oy + 5, OUT_SOFT)


def drop(im, x, y, small=False):
    """A blood teardrop, point up, at (x, y)."""
    put(im, x, y, RED)
    put(im, x, y + 1, RED_L)
    if not small:
        put(im, x - 1, y + 1, RED)
        put(im, x + 1, y + 1, RED)
        put(im, x, y + 2, RED_D)


# --- the six icons -----------------------------------------------------------
GHOST_BODY = [
    ".######.",
    "########",
    "########",
    "########",
    "########",
    "########",
    "########",
    "########",
    "#.##.##.",
]

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


def circle(im, cx, cy, r, colour):
    import math
    for a in range(0, 720):
        x = round(cx + r * math.cos(math.radians(a / 2)))
        y = round(cy + r * math.sin(math.radians(a / 2)))
        put(im, x, y, colour)


def dark_ritual():
    """The gate: a skull with the eyes already lit, standing in the ring the
    ten-second channel draws — the one node that is not itself night form."""
    im = canvas()
    circle(im, 7, 7, 7.2, PUR_D)
    for x, y in ((7, 0), (14, 7), (7, 14), (0, 7)):
        put(im, x, y, RED)
    for x, y in ((12, 2), (2, 12), (12, 12), (2, 2)):
        put(im, x, y, PUR)
    body = canvas()
    skull(body, 4, 4)
    outline(body)
    im.alpha_composite(body)
    # embers coming off the circle
    put(im, 15, 3, RED_L)
    put(im, 1, 14, RED)
    return save(im, "dark_ritual")


def extra_sensory_perception():
    """What the node actually looks like in play: creatures wearing the violet
    sense-outline and players wearing the red one, straight through the dark."""
    im = canvas()
    for ox, oy, lit, shade, hi in ((1, 6, PUR, PUR_D, PUR_L), (9, 3, RED, RED_D, RED_L)):
        fig = canvas()
        stamp(fig, MOB, ox, oy, {"#": lit})
        px = fig.load()
        for x in range(16):
            for y in range(16):
                if px[x, y] == lit and (x - ox) + (y - oy) >= 9:
                    px[x, y] = shade
                elif px[x, y] == lit and (x - ox) + (y - oy) <= 2:
                    px[x, y] = hi
        outline(fig)
        im.alpha_composite(fig)
    # eyes, the one detail that turns a coloured shape into a creature
    for ex, ey in ((3, 8), (11, 5)):
        put(im, ex, ey, OUT)
        put(im, ex + 2, ey, OUT)
    return save(im, "extra_sensory_perception")


def night_eyes():
    """Eyes of the Night: the face gone dark under the hood, two lamps left
    burning in it, and the moon they are borrowing."""
    im = canvas()
    hood = [
        "...####...",
        "..######..",
        ".########.",
        "##########",
        "##########",
        "##########",
        "##########",
        ".########.",
        "..#.##.#..",
    ]
    stamp(im, hood, 3, 4, {"#": NIGHT})
    px = im.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == NIGHT and y >= 10:
                px[x, y] = NIGHT_D
            elif px[x, y] == NIGHT and y <= 5:
                px[x, y] = NIGHT_L
    outline(im, OUT)
    # two wide amber eyes, brows dark so they read as a stare and not lamps
    for ex in (4, 9):
        rect(im, ex, 9, ex + 2, 10, AMBER)
        put(im, ex if ex == 4 else ex + 2, 9, AMBER_L)
        put(im, ex + 1, 10, AMBER_D)
        rect(im, ex, 8, ex + 2, 8, OUT)
    # the moon it hunts by — dim, and tucked into the corner, so it stays a
    # detail instead of competing with the stare
    moon = [".##", "##.", "##.", ".##"]
    stamp(im, moon, 12, 0, {"#": BONE})
    put(im, 12, 1, BONE_MID)
    put(im, 12, 2, BONE_MID)
    put(im, 14, 3, BONE_MID)
    return save(im, "night_eyes")


def feast():
    """Fangs in, the heart running out of the bottom, and the same blood
    coming back up as health."""
    im = canvas()
    heart = vanilla("gui/sprites/hud/heart/full.png").resize((11, 11), Image.NEAREST)
    im.alpha_composite(heart, (2, 3))
    outline(im, OUT)
    # the bleed leaving the bottom of it
    drop(im, 5, 13, small=True)
    drop(im, 8, 14, small=True)
    # the fangs: wide at the gum, tips already inside the heart
    fangs = canvas()
    for fx in (4, 8):
        rect(fangs, fx, 1, fx + 2, 2, BONE)
        rect(fangs, fx, 3, fx + 1, 3, BONE)
        put(fangs, fx, 4, BONE)
        put(fangs, fx + 2, 2, BONE_MID)
        put(fangs, fx + 1, 3, BONE_MID)
    outline(fangs)
    im.alpha_composite(fangs)
    # the punctures the tips left
    put(im, 4, 5, RED_D)
    put(im, 8, 5, RED_D)
    # and what the wound gives back
    for x, y in ((13, 8), (13, 9), (13, 10), (12, 9), (14, 9)):
        put(im, x, y, RED_L)
    put(im, 13, 9, BONE)
    return save(im, "feast")


def ghost_figure(ox, oy, hem_dither=True):
    """The night-form body: the shape both left-line nodes are about, drawn
    once so Ghost Form and Incorporeal read as two rungs of one thing."""
    im = canvas()
    stamp(im, GHOST_BODY, ox, oy, {"#": GHOST})
    px = im.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == GHOST:
                if x >= ox + 5 or y >= oy + 6:
                    px[x, y] = GHOST_M
                if y >= oy + 8:
                    px[x, y] = GHOST_D
    if hem_dither:
        # only the hem dithers away — a whole dithered body is noise at 16px
        dither(im, ox, oy + 8, ox + 7, oy + 8, parity=1)
    outline(im, OUT)
    for ex in (ox + 2, ox + 5):
        rect(im, ex, oy + 3, ex, oy + 4, OUT)
    return im


def ghost_form():
    """The body half out of the world: a standing shape gone thin, with the
    dash it can now make trailing behind it."""
    im = ghost_figure(6, 3)
    # the dash it can now make, coming off its back
    rect(im, 0, 6, 4, 6, GHOST)
    rect(im, 1, 9, 4, 9, GHOST_M)
    rect(im, 2, 12, 4, 12, GHOST_D)
    return save(im, "ghost_form")


def incorporeal():
    """Nothing lands: the arrow is already out the far side of a body that is
    no longer there to stop it."""
    # The same body Ghost Form draws, solid — legibility first — but with the
    # channel the shot took punched out of it, which is the whole node: the
    # projectile did not stop, and the body did not notice.
    shot = 17                       # the diagonal x + y the arrow travels on
    im = ghost_figure(5, 2, hem_dither=False)
    px = im.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3] and abs(x + y - shot) <= 1:
                r, g, b, _ = px[x, y]
                px[x, y] = (r, g, b, 52)
    # the arrow, out the far side already — no outline of its own, so it stays
    # a shaft and not a black bar
    # Diagonal, like the vanilla arrow item and like every other shipped icon
    # that holds a shaft: a horizontal bar with a white blob on each end reads
    # at 16px as a bone, not as an arrow.
    arrow = canvas()
    for i in range(4, 13):
        put(arrow, i, shot - i, WOOD)
        put(arrow, i, shot + 1 - i, WOOD_D)
    for x, y in ((11, 6), (12, 5), (13, 4), (12, 6), (13, 5), (14, 3)):
        put(arrow, x, y, BONE)
    put(arrow, 12, 5, BONE_MID)
    put(arrow, 13, 5, BONE_MID)
    for x, y in ((2, 15), (3, 15), (2, 14), (3, 14), (4, 15), (2, 13)):
        put(arrow, x, y, BONE)
    put(arrow, 3, 14, BONE_MID)
    put(arrow, 2, 14, BONE_MID)
    outline(arrow, OUT_SOFT, diagonals=False)
    im.alpha_composite(arrow)
    return save(im, "incorporeal")


ICONS = [dark_ritual, extra_sensory_perception, night_eyes, feast, ghost_form, incorporeal]

if __name__ == "__main__":
    for fn in ICONS:
        print(fn())
