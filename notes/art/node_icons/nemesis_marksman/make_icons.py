"""Generate the 32px node-icon set for the epic Nemesis-Marksman sub-tree.

House style, read off the shipped sets (marksman/, nemesis_shadow/, shadow/,
assassin/, oracle_priest/): every icon is a *16px* drawing upscaled 2x NEAREST
onto a 32px canvas, so the chunky vanilla block is baked in and the art still
reads when the tree screen draws it back down to 16. Dark silhouette outline on
everything; one dominant hue per icon; vanilla sprites pulled from the client
jar where one exists.

The tree's own signature: Deadeye's window is a cold cyan-white tracer, and
because Deadeye arrows "fly flat", every arrow in this set is drawn horizontal
— which is also what keeps it from colliding with the rare Marksman set, where
every arrow is the vanilla diagonal.

Backgrounds it must hold against: the unowned inset 0xFF8B8B8B, the hovered
0xFFA3A3A3, and the Agility owned plate 0xFF7FCF9F.

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
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/nemesis_marksman"))
OUT_DIR = os.path.join(HERE, "out")

# --- palette -----------------------------------------------------------------
CLEAR = (0, 0, 0, 0)
OUT = (18, 14, 22, 255)          # the universal dark outline
OUT_SOFT = (36, 30, 42, 255)

# the Deadeye tracer — this tree's signature cold light
TR_L = (226, 252, 255, 255)
TR = (126, 216, 240, 255)
TR_M = (74, 166, 200, 255)
TR_D = (36, 104, 138, 255)

BONE = (238, 238, 230, 255)
BONE_M = (196, 196, 188, 255)
BONE_D = (126, 126, 122, 255)

WOOD = (128, 88, 52, 255)
WOOD_L = (168, 122, 74, 255)
WOOD_D = (76, 50, 28, 255)

IRON = (206, 210, 214, 255)
IRON_M = (150, 156, 164, 255)
IRON_D = (92, 98, 108, 255)

RED = (200, 40, 46, 255)
RED_L = (255, 108, 104, 255)
RED_D = (114, 16, 22, 255)

LEATH = (150, 100, 56, 255)
LEATH_L = (188, 134, 80, 255)
LEATH_D = (94, 58, 32, 255)

PH = (216, 210, 228, 255)
PH_M = (166, 158, 190, 255)
PH_D = (108, 100, 136, 255)

WHITEISH = (244, 240, 236, 255)


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
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            put(im, x, y, c)


def stamp(im, rows, ox, oy, key):
    """Paint an ASCII sprite. `key` maps a char to a colour; '.' is skipped."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != "." and ch in key:
                put(im, ox + dx, oy + dy, key[ch])


def outline(im, colour=OUT, diagonals=True):
    """Ring every opaque cluster in a dark line — the single thing that makes a
    shape survive being drawn at 16px on a mid-grey plate."""
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
    """Knock out every other pixel — the tree's idiom for "not entirely there"."""
    px = im.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < im.width and 0 <= y < im.height and (x + y) % 2 == parity:
                if px[x, y][3]:
                    px[x, y] = CLEAR


def circle(im, cx, cy, r, colour):
    for a in range(0, 1440):
        put(im, round(cx + r * math.cos(math.radians(a / 4))),
            round(cy + r * math.sin(math.radians(a / 4))), colour)


def save(im, name):
    big = im.resize((32, 32), Image.NEAREST)
    os.makedirs(OUT_DIR, exist_ok=True)
    big.save(os.path.join(OUT_DIR, f"{name}.png"))
    if "--install" in sys.argv:
        os.makedirs(DST, exist_ok=True)
        big.save(os.path.join(DST, f"{name}.png"))
    return name


# --- shared motifs -----------------------------------------------------------
def flat_arrow(im, y, x0, x1, head="big", fletch=True, shaft=WOOD, hi=WOOD_L):
    """The tree's arrow: horizontal, because Deadeye's arrows fly flat. One
    pixel of shaft — the soft outline adds the other two, and anything fatter
    comes back from the tree screen as a plank."""
    rect(im, x0, y, x1, y, shaft)
    for x in range(x0 + 1, x1, 3):
        put(im, x, y, hi)
    if head == "big":
        stamp(im, ["#..",
                   "##.",
                   "###",
                   "##.",
                   "#.."], x1 + 1, y - 2, {"#": BONE})
        put(im, x1 + 1, y + 1, BONE_M)
        put(im, x1 + 1, y + 2, BONE_M)
        put(im, x1 + 2, y + 1, BONE_M)
    elif head == "small":
        stamp(im, ["#.",
                   "##",
                   "#."], x1 + 1, y - 1, {"#": BONE})
        put(im, x1 + 1, y + 1, BONE_M)
    if fletch:
        for i in (0, 1):
            put(im, x0 + i, y - 1, BONE)
            put(im, x0 + i, y + 1, BONE_M)


def feather(im, x0, y0, x1, y1, wid=2.2, light=BONE, mid=BONE_M, quill=BONE_D):
    """A feather along a quill: the vane swells in the middle and the trailing
    edge is notched, which is the only way the shape is not just a blob."""
    n = max(abs(x1 - x0), abs(y1 - y0))
    dx, dy = (x1 - x0) / n, (y1 - y0) / n
    px, py = -dy, dx                       # perpendicular
    for i in range(n + 1):
        u = i / n
        w = wid * math.sin(math.pi * min(1.0, u * 1.15)) ** 0.6
        cx, cy = x0 + dx * i, y0 + dy * i
        for t in range(0, int(round(w)) + 1):
            if t and (i % 4 == 3) and t == int(round(w)):
                continue               # the notch
            put(im, round(cx + px * t), round(cy + py * t), light if t < 2 else mid)
            put(im, round(cx - px * t), round(cy - py * t), mid if t else light)
        put(im, round(cx), round(cy), quill if u > 0.15 else light)


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


# --- the nine icons ----------------------------------------------------------
def deadeye():
    """The window itself: the eye that opens for fifteen seconds, held inside
    the reticle it is sighting through."""
    im = canvas()
    eye = canvas()
    lens = [
        "...#####...",
        ".#########.",
        "###########",
        "###########",
        "###########",
        ".#########.",
        "...#####...",
    ]
    stamp(eye, lens, 3, 5, {"#": BONE})
    px = eye.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == BONE and y >= 9:
                px[x, y] = BONE_M
            if px[x, y] == BONE_M and y >= 11:
                px[x, y] = BONE_D
    # iris and pupil: round, or the dark bar across it reads as a mouth
    rect(eye, 5, 7, 10, 10, TR)
    rect(eye, 5, 7, 10, 7, TR_L)
    rect(eye, 7, 8, 9, 9, OUT)
    put(eye, 8, 8, RED)
    put(eye, 8, 9, RED_L)
    put(eye, 6, 8, TR_L)
    outline(eye, OUT)
    im.alpha_composite(eye)

    br = canvas()
    for cx, cy, sx, sy in ((1, 2, 1, 1), (14, 2, -1, 1), (1, 13, 1, -1), (14, 13, -1, -1)):
        for i in range(4):
            put(br, cx + sx * i, cy, TR)
        for i in range(3):
            put(br, cx, cy + sy * i, TR)
        put(br, cx, cy, TR_L)
    outline(br, OUT_SOFT, diagonals=False)
    im.alpha_composite(br)
    put(im, 12, 4, TR_L)
    put(im, 4, 12, TR_L)
    return save(im, "deadeye")


def fleet():
    """Deadeye's Slowness comes off and Speed II goes on — so, vanilla's own
    ball-and-chain, snapped, with the window's light coming out of the break.

    Three fat links, not five thin ones: at 16px a chain drawn to scale is a
    dotted line, and a dotted line is noise.
    """
    im = canvas()
    ball = canvas()
    stamp(ball, ["..####..",
                 ".######.",
                 "########",
                 "########",
                 "########",
                 "########",
                 ".######.",
                 "..####.."], 0, 8, {"#": IRON_D})
    px = ball.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == IRON_D and x + y <= 13:
                px[x, y] = IRON_M
    rect(ball, 1, 10, 2, 11, IRON)
    outline(ball, OUT)
    im.alpha_composite(ball)

    chain = canvas()
    for cx, cy in ((5, 5), (11, 1)):
        rect(chain, cx, cy, cx + 2, cy + 2, IRON_M)
        rect(chain, cx + 1, cy + 1, cx + 1, cy + 1, IRON_D)
        put(chain, cx, cy, IRON)
    outline(chain, OUT)
    im.alpha_composite(chain)

    # the break, and the light coming through it
    for x, y in ((9, 4), (10, 3)):
        put(im, x, y, TR_L)
    for x, y in ((8, 3), (11, 4), (9, 2), (10, 5), (8, 5)):
        put(im, x, y, TR)

    # what it turns into: the ground going past
    streak = canvas()
    for sy, x0, x1 in ((9, 11, 15), (12, 9, 15)):
        rect(streak, x0, sy, x1, sy, TR)
        put(streak, x1, sy, TR_L)
    outline(streak, OUT_SOFT, diagonals=False)
    im.alpha_composite(streak)
    return save(im, "fleet")


def long_shot():
    """Two per cent a block: one shaft, and the light on it widening the whole
    way out — thin where it left the bow, a wedge by fifty blocks."""
    im = canvas()
    # cone and shaft on one canvas, outlined once: outline the shaft separately
    # and the dark ring cuts the glow off the arrow it belongs to
    for x in range(0, 13):
        r = round((x / 12) ** 0.8 * 3)
        for t in range(1, r + 1):
            put(im, x, 8 - t, TR_D if t == r else TR)
            put(im, x, 8 + t, TR_D if t == r else TR)
        if r:
            put(im, x, 8 - 1, TR_L)
    flat_arrow(im, 8, 0, 12, head="big")
    outline(im, OUT, diagonals=False)
    return save(im, "long_shot")


def vault():
    """Eight blocks of roll, out of the air as well, and back in three seconds:
    the body tucked inside the ring its own tumble draws, thrown forward.

    Every other shape for this — the leap arc, the boot on an arc, the bare
    loop — came back from 16px as an archway, a roof or the letter C. The tuck
    inside the ring is the only one that still reads as a person rolling.
    """
    im = canvas()
    ring = canvas()
    for a in range(0, 300):
        ang = math.radians(a + 50)
        for r, col in ((6.0, TR_M), (5.0, TR_L)):
            put(ring, round(7.5 + r * math.cos(ang)), round(7.5 + r * math.sin(ang)), col)
    outline(ring, OUT)
    im.alpha_composite(ring)

    tuck = canvas()
    stamp(tuck, [".####.",
                 "######",
                 "######",
                 "######",
                 "######",
                 ".####."], 5, 5, {"#": TR})
    rect(tuck, 5, 5, 8, 6, TR_L)
    rect(tuck, 6, 7, 7, 8, OUT)                # the head, tucked in
    put(tuck, 10, 9, TR_M)
    outline(tuck, OUT)
    im.alpha_composite(tuck)

    head = canvas()
    stamp(head, ["#..",
                 "##.",
                 "###",
                 "##.",
                 "#.."], 12, 9, {"#": BONE})
    put(head, 12, 12, BONE_M)
    outline(head, OUT)
    im.alpha_composite(head)

    dust = canvas()
    for x, y in ((0, 12), (1, 13), (0, 14), (2, 14), (0, 15), (2, 15)):
        put(dust, x, y, BONE)
    for x, y in ((1, 13), (2, 14)):
        put(dust, x, y, BONE_M)
    outline(dust, OUT_SOFT)
    im.alpha_composite(dust)
    return save(im, "vault")


def on_the_wing():
    """The ground stops mattering: the feather vanilla hangs on Slow Falling,
    and the drift beside it that is holding you up."""
    im = canvas()
    fea = canvas()
    quill = vanilla("item/feather.png").crop((0, 0, 16, 16)).resize((14, 14), Image.NEAREST)
    fea.alpha_composite(quill, (2, 0))
    # cool it toward the phantom violet so it is not a grey shape on a grey plate
    px = fea.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (min(255, r), min(255, int(g * 0.97)), min(255, int(b * 1.06) + 8), a)
    outline(fea, OUT)
    im.alpha_composite(fea)

    chev = canvas()
    for cx, cy, w in ((0, 3, 3), (0, 8, 3), (1, 13, 3)):
        rect(chev, cx, cy, cx + w, cy, TR)
        put(chev, cx + w, cy, TR_L)
        put(chev, cx + w, cy + 1, TR_M)
        put(chev, cx + w - 1, cy + 2, TR_M)
    outline(chev, OUT_SOFT, diagonals=False)
    im.alpha_composite(chev)
    return save(im, "on_the_wing")


def punch_through():
    """The plate is not in the conversation: armour ignored, and the shaft is
    already out the far side on its way to the next one."""
    im = canvas()
    plate = canvas()
    mail = vanilla("item/iron_chestplate.png").crop((1, 2, 15, 15)).resize((14, 13), Image.NEAREST)
    plate.alpha_composite(mail, (1, 2))
    outline(plate, OUT)
    im.alpha_composite(plate)

    # cracks running out of where it went in — read at 16 where a hole does not
    cracks = canvas()
    for x, y in ((6, 6), (5, 5), (4, 5), (10, 6), (11, 5), (12, 4),
                 (6, 10), (5, 11), (4, 11), (10, 10), (11, 11), (12, 12)):
        put(cracks, x, y, OUT)
    im.alpha_composite(cracks)

    shot = canvas()
    flat_arrow(shot, 8, 0, 12, head="big")
    outline(shot, OUT, diagonals=False)
    im.alpha_composite(shot)

    # plate shrapnel coming off the exit side
    for x, y in ((14, 4), (15, 5), (14, 12), (15, 11)):
        put(im, x, y, IRON_M)
    for x, y in ((15, 4), (15, 12)):
        put(im, x, y, IRON_D)
    return save(im, "punch_through")


def evasion():
    """It goes through you: the shot never slowed, and it is lit where it
    crossed the body that was not there to stop it."""
    im = canvas()
    body = canvas()
    # a plain front-on player: narrow head over full shoulders, arms down the
    # sides, legs split. Anything cleverer comes back from 16px as a lantern.
    fig = [
        "..######..",
        "..######..",
        "..######..",
        "..######..",
        "..######..",
        "##########",
        "##########",
        "##########",
        "##########",
        "##########",
        "..######..",
        "..##..##..",
        "..##..##..",
        "..##..##..",
    ]
    stamp(body, fig, 3, 1, {"#": TR})
    px = body.load()
    for x in range(16):
        for y in range(16):
            if px[x, y] == TR:
                if y >= 11:
                    px[x, y] = TR_M
                if y <= 5:
                    px[x, y] = TR_L
    rect(body, 4, 6, 4, 10, TR_D)              # the arms, told apart from the chest
    rect(body, 11, 6, 11, 10, TR_D)
    rect(body, 9, 2, 10, 5, TR)                # the head takes the light from
    rect(body, 5, 5, 10, 5, TR_M)              # the top-left like everything else
    put(body, 5, 1, TR_L)
    outline(body, OUT)
    dither(body, 3, 13, 12, 14, parity=1)      # only the hem frays
    im.alpha_composite(body)

    shot = canvas()
    flat_arrow(shot, 8, 0, 12, head="big")
    outline(shot, OUT, diagonals=False)
    im.alpha_composite(shot)
    for x, y in ((2, 8), (13, 8)):
        put(im, x, y, TR_L)
    return save(im, "evasion")


def long_watch():
    """Twenty-five seconds instead of fifteen: the glass with the window's own
    light still most of the way up it."""
    im = canvas()
    glass = canvas()
    bulbs = {
        2: (4, 11), 3: (5, 10), 4: (6, 9), 5: (7, 8),
        6: (7, 8),
        7: (7, 8), 8: (6, 9), 9: (5, 10), 10: (4, 11), 11: (4, 11),
    }
    for y, (x0, x1) in bulbs.items():
        rect(glass, x0, y, x1, y, BONE)
    for y in range(2, 6):
        x0, x1 = bulbs[y]
        rect(glass, x0 + 1, y, x1 - 1, y, TR)
    rect(glass, 5, 2, 5, 2, TR_L)
    rect(glass, 7, 5, 8, 7, TR_L)
    rect(glass, 5, 10, 10, 11, TR)
    rect(glass, 6, 9, 9, 9, TR_M)
    rect(glass, 3, 1, 12, 1, WOOD)
    rect(glass, 3, 0, 12, 0, WOOD_L)
    rect(glass, 3, 12, 12, 12, WOOD)
    rect(glass, 3, 13, 12, 13, WOOD_D)
    outline(glass, OUT)
    im.alpha_composite(glass)
    plus = canvas()
    rect(plus, 13, 5, 15, 5, TR)
    rect(plus, 14, 4, 14, 6, TR)
    put(plus, 14, 5, TR_L)
    outline(plus, OUT_SOFT, diagonals=False)
    im.alpha_composite(plus)
    return save(im, "long_watch")


def siege():
    """Stand still and the shot arrives twice: both of them dead centre of the
    block, over the braces that say you are not being moved."""
    im = canvas()
    tgt = canvas()
    ring = vanilla("block/target_side.png").crop((0, 0, 16, 16)).resize((12, 12), Image.NEAREST)
    tgt.alpha_composite(ring, (4, 0))
    outline(tgt, OUT)
    im.alpha_composite(tgt)

    shots = canvas()
    flat_arrow(shots, 4, 0, 8, head=None)
    flat_arrow(shots, 9, 0, 8, head=None)
    outline(shots, OUT, diagonals=False)
    im.alpha_composite(shots)
    for x, y in ((8, 4), (9, 4), (8, 9), (9, 9), (10, 4), (10, 9)):
        put(im, x, y, TR_L)
    for x, y in ((11, 3), (11, 10), (7, 3), (7, 10), (12, 6)):
        put(im, x, y, TR)

    braces = canvas()
    rect(braces, 2, 13, 5, 13, IRON_M)
    rect(braces, 10, 13, 13, 13, IRON_M)
    rect(braces, 1, 14, 6, 14, IRON_D)
    rect(braces, 9, 14, 14, 14, IRON_D)
    outline(braces, OUT, diagonals=False)
    im.alpha_composite(braces)
    return save(im, "siege")


ICONS = [deadeye, fleet, long_shot, vault, on_the_wing,
         punch_through, evasion, long_watch, siege]

if __name__ == "__main__":
    for fn in ICONS:
        print(fn())
