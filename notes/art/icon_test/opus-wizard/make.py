"""Wizard skill-tree node icons — magic-missile artillery, mana economy, wand in hand.

Every icon is a full standalone 32x32 PNG. The house rule: at a glance it should
tell the player what the node DOES. Built from vanilla sprites pulled straight
from the client jar (palettes match the game for free) plus restrained
hand-plotted effect pixels, in the style of make_node_icons.py.

Signature vocabulary, reused across the set so the tree reads as one hand:
  * the MISSILE  — an amethyst-white comet bolt (palette lifted from amethyst_shard)
  * MANA        — the mod's own blue mana orb (textures/gui/mana_orb_full.png)
  * the WAND    — the mod's magic_wand sprite, amethyst tip and all
  * "+" more · "-" less · arrows for speed/reach · a heart for health-state

Usage: python3 make.py
"""
import math
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures"))
DST = os.path.join(HERE, "icons")

# --- palette, sampled straight from the sprites so hand pixels match the game ---
# amethyst missile
AME_DK = (84, 57, 138, 255)
AME = (111, 79, 171, 255)
AME_MID = (141, 106, 204, 255)
AME_LT = (179, 142, 243, 255)
AME_PALE = (207, 160, 243, 255)
AME_GLOW = (254, 203, 230, 255)   # pink-white shard highlight
AME_CORE = (255, 253, 213, 255)   # cream-white hot core
# mana
MANA_DK = (24, 62, 160, 255)
MANA = (45, 110, 230, 255)
MANA_LT = (140, 190, 255, 255)
# health
RED = (255, 19, 19, 255)
RED_DK = (187, 19, 19, 255)
RED_LT = (255, 200, 200, 255)
# arcane effect white / debuff
ARC = (238, 236, 250, 255)
ARC_DIM = (175, 165, 205, 220)
BONE = (232, 230, 214, 255)
BONE_DK = (150, 148, 132, 255)
WEAK = (120, 150, 120, 255)       # sickly Weakness green-grey
WEAK_DK = (74, 96, 74, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            im = Image.open(f).convert("RGBA").copy()
    w, h = im.size
    if h > w:                       # animated vertical strip -> first frame
        im = im.crop((0, 0, w, w))
    return im


def mod(path):
    return Image.open(os.path.join(MOD, path)).convert("RGBA")


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def up(im, factor=2):
    return im.resize((im.width * factor, im.height * factor), Image.NEAREST)


def scale_to(im, size):
    return im.resize((size, size), Image.NEAREST)


def brighten(im, k):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (min(255, int(r * k)), min(255, int(g * k)),
                            min(255, int(b * k)), a)
    return out


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        if len(c) == 3:
            c = c + (255,)
        if c[3] == 255:
            im.putpixel((x, y), c)
        else:
            base = Image.new("RGBA", (1, 1), c)
            im.alpha_composite(base, (x, y))


def disc(im, cx, cy, r, c):
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                put(im, x, y, c)


def line(im, x0, y0, x1, y1, c, thick=1):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        for dx in range(thick):
            for dy in range(thick):
                put(im, x + dx, y + dy, c)


def plus(im, cx, cy, arm, c, thick=2):
    for i in range(-arm, arm + 1):
        for t in range(thick):
            put(im, cx + i, cy + t - thick // 2, c)
            put(im, cx + t - thick // 2, cy + i, c)


def minus(im, cx, cy, arm, c, thick=2):
    for i in range(-arm, arm + 1):
        for t in range(thick):
            put(im, cx + i, cy + t - thick // 2, c)


def comet(im, hx, hy, tdx, tdy, length, glow=True):
    """A little amethyst-white missile bolt: hot core head, amethyst tail
    streaming in the (tdx,tdy) direction. Reads as a projectile in flight."""
    n = math.hypot(tdx, tdy)
    ux, uy = tdx / n, tdy / n
    if glow:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            put(im, hx + dx, hy + dy, AME_GLOW)
    # tail
    for i in range(1, length + 1):
        x = round(hx + ux * i)
        y = round(hy + uy * i)
        frac = i / (length + 1)
        if frac < 0.4:
            c = AME_LT
        elif frac < 0.7:
            c = AME_MID
        else:
            c = AME_DK
        c = (c[0], c[1], c[2], int(255 * (1 - frac * 0.6)))
        put(im, x, y, c)
        # a touch of width near the head
        if i <= 2:
            put(im, x - round(uy), y + round(ux), (c[0], c[1], c[2], c[3] // 2))
    # bright head on top of the glow
    put(im, hx, hy, AME_CORE)
    put(im, hx - round(ux), hy - round(uy), AME_CORE)   # nose, one ahead
    put(im, hx + round(uy * 0), hy, AME_CORE)


def mana_orb(size):
    return scale_to(mod("gui/mana_orb_full.png"), size)


def shard_missile(size, bright=1.0):
    im = scale_to(vanilla("item/amethyst_shard.png"), size)
    if bright != 1.0:
        im = brighten(im, bright)
    return im


def save(im, name):
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# ----------------------------------------------------------------------------
# THE ICONS
# ----------------------------------------------------------------------------

def magic_missile():
    """The active. Wand in hand, an amethyst bolt just loosed from its tip,
    streaking up and away — this is the cast that names the whole tree."""
    im = canvas()
    wand = up(mod("item/magic_wand.png"))          # tip sits ~ (22,9)
    im.alpha_composite(wand, (-5, 4))
    tipx, tipy = 21, 10
    # a bright fat bolt springing off the tip toward the upper-right corner
    hx, hy = 27, 4
    disc(im, hx, hy, 2, AME_GLOW)
    disc(im, hx, hy, 1, AME_CORE)
    put(im, hx + 1, hy - 1, AME_CORE)
    # streaming tail back toward the wand tip
    for i, (x, y) in enumerate(((25, 6), (24, 7), (23, 8), (tipx, tipy))):
        c = (AME_LT, AME_MID, AME_MID, AME_PALE)[i]
        put(im, x, y, c)
    put(im, 22, 9, AME_GLOW)
    # muzzle sparks at the tip
    for dx, dy in ((-1, -1), (1, 1), (-2, 0)):
        put(im, tipx + dx, tipy + dy, AME_GLOW)
    save(im, "magic_missile")


def mana_shield():
    """A heart split down the middle — red health on the left, mana blue on
    the right: half the damage you take is paid in mana, not blood."""
    im = canvas()
    heart = up(vanilla("gui/sprites/hud/heart/full.png"), 3)   # 27x27
    ox, oy = 3, 3
    cut = ox + heart.width // 2
    px = heart.load()
    for y in range(heart.height):
        for x in range(heart.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            gx = ox + x
            if gx >= cut:                       # recolor right half to mana
                if (r, g, b, a) == RED:
                    c = MANA
                elif (r, g, b, a) == RED_DK:
                    c = MANA_DK
                else:
                    c = MANA_LT
            else:
                c = (r, g, b, a)
            put(im, gx, oy + y, c)
    # seam glint down the divide
    for y in range(6, 24, 2):
        put(im, cut, y, ARC)
    save(im, "mana_shield")


def force():
    """A big amethyst missile with a bold plus and hit-spikes at the nose:
    the same bolt, landing harder."""
    im = canvas()
    im.alpha_composite(shard_missile(22, 1.05), (2, 6))
    # impact spikes off the bright nose (upper-right of the shard)
    for dx, dy in ((1, -1), (2, -2), (1, 0), (2, 0), (0, -2)):
        put(im, 20 + dx, 8 + dy, AME_GLOW)
    plus(im, 25, 7, 3, AME_CORE, thick=2)
    plus(im, 25, 7, 3, AME_GLOW, thick=1)
    save(im, "force")


def clarity():
    """A mana orb with a minus — the missile still flies (bolt, top-right)
    but it costs less to throw."""
    im = canvas()
    im.alpha_composite(mana_orb(20), (2, 8))
    minus(im, 22, 18, 4, ARC, thick=3)
    minus(im, 22, 18, 4, MANA_LT, thick=1)
    comet(im, 25, 6, -1.0, 0.6, 4)
    save(im, "clarity")


def siphon():
    """A kill pays mana back: a skull up top, a curved arrow sweeping its
    life down into the mana orb."""
    im = canvas()
    im.alpha_composite(mana_orb(15), (2, 15))     # orb, lower-left, centre ~(9,22)
    # a legible skull, upper-right
    sx, sy = 19, 2
    skull = [
        " ###### ",
        "########",
        "########",
        "#OO##OO#",   # eye sockets
        "########",
        "###NN###",   # nose
        " ###### ",
        " #.##.# ",   # teeth gaps
    ]
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            if ch == "#":
                put(im, sx + i, sy + j, BONE)
            elif ch == "O":
                put(im, sx + i, sy + j, (28, 26, 32, 255))
            elif ch == "N":
                put(im, sx + i, sy + j, (60, 56, 62, 255))
    # bold curved refund arrow sweeping from the skull down into the orb
    arc = [(17, 11), (15, 13), (13, 15), (12, 18), (11, 20)]
    for x, y in arc:
        put(im, x, y, MANA_LT)
        put(im, x + 1, y, MANA)
    # fat arrowhead landing in the orb
    for dx, dy in ((0, 0), (2, -1), (2, 0), (2, 1), (1, 2), (0, 2), (-1, 1)):
        put(im, 11 + dx, 20 + dy, ARC)
    save(im, "siphon")


def echo():
    """Two bolts where there was one — a solid missile and its ghost twin
    flying alongside: sometimes the cast comes doubled."""
    im = canvas()
    # the ghost twin, clearly a second bolt running parallel and just behind
    ghost = canvas()
    comet(ghost, 14, 22, -1.0, 0.7, 7)
    im.alpha_composite(faded(ghost, 150), (0, 0))
    # the solid bolt
    comet(im, 24, 11, -1.0, 0.7, 8)
    save(im, "echo")


def range_icon():
    """The bolt at the end of a long measured trail — reach ticks along the
    way, a target mark ahead: the same missile, thrown far."""
    im = canvas()
    y = 17
    # dashed measure line left -> right
    for x in range(2, 25, 2):
        put(im, x, y, ARC_DIM)
    # reach ticks
    for x in (5, 12, 19):
        put(im, x, y - 2, AME_MID)
        put(im, x, y + 2, AME_MID)
    # bolt flying right
    comet(im, 26, y - 1, -1.0, 0.15, 6)
    # far target mark
    for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0)):
        put(im, 29 + dx, y - 1 + dy, RED_LT)
    save(im, "range")


def arcane_orb():
    """A fat mana orb with a plus and an expansion ring: the pool itself
    grows — more mana to spend."""
    im = canvas()
    # faint growth ring
    for a in range(0, 360, 20):
        x = 15 + round(13 * math.cos(math.radians(a)))
        y = 16 + round(13 * math.sin(math.radians(a)))
        put(im, x, y, (MANA_LT[0], MANA_LT[1], MANA_LT[2], 90))
    im.alpha_composite(mana_orb(24), (2, 5))
    plus(im, 25, 8, 3, ARC, thick=2)
    plus(im, 25, 8, 3, MANA_LT, thick=1)
    save(im, "arcane_orb")


def velocity():
    """One bolt, hard speed-streaks stacked behind it: the missile flies
    faster."""
    im = canvas()
    # speed streaks
    for i, y in enumerate((9, 14, 19)):
        x0 = 2 + i % 2
        for x in range(x0, x0 + 11):
            c = AME_LT if x > x0 + 5 else ARC_DIM
            put(im, x, y, c)
    comet(im, 26, 13, -1.0, 0.25, 5)
    comet(im, 24, 14, -1.0, 0.25, 0)   # extra bright nose dot
    save(im, "velocity")


def overwhelm():
    """A comet slamming a wounded heart — half of it already gone — for the
    +damage against anything hurt."""
    im = canvas()
    heart = up(vanilla("gui/sprites/hud/heart/half.png"), 3)
    im.alpha_composite(heart, (1, 8))
    # broken/chipped right edge to sell 'wounded'
    for x, y in ((15, 12), (16, 14), (15, 16), (16, 18)):
        put(im, x, y, RED_DK)
    comet(im, 22, 6, -0.9, 0.8, 5)
    # impact burst where it bites
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
        put(im, 15 + dx, 13 + dy, AME_GLOW)
    plus(im, 26, 21, 2, AME_CORE, thick=2)
    save(im, "overwhelm")


def concussion():
    """A struck target and the debuff it leaves: a bolt hitting, weakness
    arrows sinking down — the enemy hits softer now."""
    im = canvas()
    # a target ring, struck
    disc(im, 11, 17, 8, (40, 40, 48, 255))
    disc(im, 11, 17, 6, (190, 190, 200, 255))
    disc(im, 11, 17, 4, (40, 40, 48, 255))
    disc(im, 11, 17, 2, (215, 70, 70, 255))
    # bolt in from upper-right
    comet(im, 20, 6, -0.9, 0.9, 5)
    # weakness: bold descending chevrons on the right — the target hits softer
    for y in (6, 12, 18):
        for x, yy, c in ((23, y, WEAK), (24, y + 1, WEAK), (25, y + 2, WEAK),
                         (27, y + 1, WEAK), (28, y, WEAK),
                         (24, y + 2, WEAK_DK), (26, y + 2, WEAK_DK)):
            put(im, x, yy, c)
    save(im, "concussion")


def shatterpoint():
    """A comet cracking a full, bright heart — the whole thing splintering:
    +damage against a target at full health."""
    im = canvas()
    heart = up(vanilla("gui/sprites/hud/heart/full.png"), 3)
    im.alpha_composite(heart, (2, 7))
    # white shatter cracks radiating from the hit
    cracks = [(15, 13), (17, 15), (19, 17), (14, 16), (12, 19),
              (16, 18), (18, 21), (13, 12), (11, 15)]
    for x, y in cracks:
        put(im, x, y, ARC)
    line(im, 15, 13, 19, 17, ARC)
    line(im, 15, 13, 12, 19, ARC)
    line(im, 15, 13, 20, 12, ARC)
    comet(im, 23, 5, -0.9, 0.85, 5)
    save(im, "shatterpoint")


def seeker_missile():
    """Capstone. The bolt bends: a curved homing trail hooking around into a
    locked target reticle — the missile chases the enemy."""
    im = canvas()
    # target reticle, upper-right
    tx, ty = 24, 8
    for dx, dy in ((0, -4), (0, 4), (-4, 0), (4, 0),
                   (0, -3), (0, 3), (-3, 0), (3, 0)):
        put(im, tx + dx, ty + dy, RED)
    disc(im, tx, ty, 1, RED_LT)
    # curved homing trail sweeping up from lower-left into the reticle
    path = [(4, 27), (5, 24), (7, 21), (10, 18), (13, 15),
            (16, 12), (19, 10), (21, 9)]
    for i, (x, y) in enumerate(path):
        frac = i / len(path)
        c = AME_DK if frac < 0.4 else (AME_MID if frac < 0.75 else AME_LT)
        put(im, x, y, c)
        if i % 2 == 0:
            put(im, x, y + 1, (c[0], c[1], c[2], 120))
    comet(im, 22, 9, -0.6, 0.9, 3)   # bolt head arriving at the reticle
    save(im, "seeker_missile")


def lance():
    """Capstone. A blazing straight lance driven clean through a row of foes
    — it pierces everything in its path."""
    im = canvas()
    y = 16
    # bright beam across the whole width
    for x in range(1, 31):
        put(im, x, y, ARC)
        put(im, x, y - 1, AME_GLOW)
        put(im, x, y + 1, AME_LT)
    for x in range(3, 29, 1):
        put(im, x, y, (255, 255, 255, 255))
    # amethyst spearhead at the right tip
    for dx, dy in ((0, 0), (-1, -1), (-1, 1), (-2, -2), (-2, 2), (-2, 0), (-3, 0)):
        put(im, 29 + dx, y + dy, AME_CORE)
    put(im, 30, y, AME_CORE)
    # three pierced foes strung along the line
    for fx in (8, 15, 22):
        for dy in (-3, -2, 2, 3):
            put(im, fx, y + dy, (70, 70, 80, 255))
        put(im, fx - 1, y - 3, (70, 70, 80, 255))
        put(im, fx + 1, y + 3, (70, 70, 80, 255))
        # spark where it punches through
        put(im, fx, y - 2, AME_GLOW)
        put(im, fx, y + 2, AME_GLOW)
    save(im, "lance")


def mind_well():
    """A run of ordinary bolts and then one charged fat with power: every so
    many missiles, one comes out empowered."""
    im = canvas()
    # a clean counted series of small plain bolts, evenly spaced, marching right
    for x in (4, 11, 18):
        y = 23
        put(im, x + 1, y - 1, AME_GLOW)
        put(im, x, y, AME_LT)
        put(im, x + 1, y, AME_MID)
        put(im, x - 1, y + 1, AME_DK)
        put(im, x, y + 1, AME_DK)       # little tail, all identical
    # then the one that comes out empowered: a big glowing missile
    disc(im, 23, 12, 7, (AME_LT[0], AME_LT[1], AME_LT[2], 70))
    disc(im, 23, 12, 4, (AME_MID[0], AME_MID[1], AME_MID[2], 110))
    im.alpha_composite(shard_missile(16, 1.18), (15, 4))
    for a in range(0, 360, 45):         # radiating charge sparks
        x = 23 + round(9 * math.cos(math.radians(a)))
        y = 12 + round(9 * math.sin(math.radians(a)))
        put(im, x, y, AME_GLOW)
    put(im, 23, 12, AME_CORE)
    put(im, 24, 11, AME_CORE)
    save(im, "mind_well")


def flow():
    """A mana orb refilling itself — droplets and up-arrows rising into it:
    mana regenerates over time."""
    im = canvas()
    im.alpha_composite(mana_orb(18), (7, 10))
    # rising flow arrows beneath / around the orb
    for bx in (10, 22):
        line(im, bx, 30, bx, 22, MANA_LT)
        put(im, bx - 1, 24, MANA_LT)      # arrowhead up
        put(im, bx + 1, 24, MANA_LT)
        put(im, bx, 23, ARC)
    # a couple of rising droplets
    for dx, dy, c in ((16, 28, MANA), (16, 27, MANA_LT), (16, 26, ARC)):
        put(im, dx, dy, c)
    plus(im, 25, 8, 2, ARC, thick=2)
    plus(im, 25, 8, 2, MANA_LT, thick=1)
    save(im, "flow")


def archmage():
    """The crown. A nether-star ablaze, amethyst missiles streaming out of it
    in every direction — the master whose every missile bites harder."""
    im = canvas()
    star = up(vanilla("item/nether_star.png"))
    # radiating bolts first, so the star sits on top
    for a in (25, 90, 155, 215, 300):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        hx = round(16 + dx * 13)
        hy = round(16 + dy * 13)
        comet(im, hx, hy, -dx, -dy, 4, glow=False)
    im.alpha_composite(star, (0, 0))
    # a hot amethyst core over the star's centre + a boost plus
    put(im, 16, 16, AME_GLOW)
    put(im, 15, 16, AME_PALE)
    put(im, 16, 15, AME_PALE)
    save(im, "archmage")


ORDER = [
    "magic_missile", "mana_shield", "force", "clarity", "siphon", "echo",
    "range", "arcane_orb", "velocity", "overwhelm", "concussion",
    "shatterpoint", "seeker_missile", "lance", "mind_well", "flow", "archmage",
]


def build_contact():
    """Every icon at 4x NEAREST in a labelled grid on a dark ground, so pale
    amethyst-white pixels still read."""
    from PIL import ImageDraw
    scale = 4
    cell = 32 * scale                     # 128
    pad = 10
    label_h = 16
    cols = 6
    rows = math.ceil(len(ORDER) / cols)
    cw = cell + pad
    ch = cell + label_h + pad
    W = cols * cw + pad
    H = rows * ch + pad
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(ORDER):
        icon = Image.open(os.path.join(DST, f"{name}.png")).convert("RGBA")
        big = icon.resize((cell, cell), Image.NEAREST)
        cx = pad + (i % cols) * cw
        cy = pad + (i // cols) * ch
        # subtle slot backing so transparent-heavy icons still show a frame
        d.rectangle([cx - 1, cy - 1, cx + cell, cy + cell], outline=(70, 70, 78, 255))
        sheet.alpha_composite(big, (cx, cy))
        d.text((cx + 2, cy + cell + 3), name, fill=(228, 228, 232, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png", sheet.size)


def main():
    os.makedirs(DST, exist_ok=True)
    magic_missile()
    mana_shield()
    force()
    clarity()
    siphon()
    echo()
    range_icon()
    arcane_orb()
    velocity()
    overwhelm()
    concussion()
    shatterpoint()
    seeker_missile()
    lance()
    mind_well()
    flow()
    archmage()
    build_contact()


if __name__ == "__main__":
    main()
