"""Generate the 32x32 node icons for the epic Oracle-Wizard sub-tree.

House style (matched from wizard/, elementalist/, priest/, shadow/, assassin/):
one big vanilla-grained object filling the frame, built on a 16px grid and
upscaled 2x NEAREST so every form is chunky and outlined at 16px grain, then a
few 1px accents (sparkles, drops, arcs) plotted at the 32px grain. Saturated,
dark-outlined, must survive being drawn at 16px in the tree screen.

The tree's subject is conjured armaments, so the sword and bow come from the
mod's own magic_sword/magic_bow item textures — the icons match the weapon that
actually appears in hand. Everything else is tinted into that same violet ramp.

Usage: python3 gen.py [--install]
"""
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "../../../../src/main/resources/assets"))
ITEM = os.path.join(ASSETS, "archetypes/textures/item")
DST = os.path.join(ASSETS, "archetypes/textures/node/oracle_wizard")
OUT_DIR = os.path.join(HERE, "out")
JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")

# The conjured-armament ramp, sampled straight off magic_sword.png.
OUTL = (24, 18, 36, 255)
V_DK = (74, 32, 140, 255)
V_MD = (118, 60, 196, 255)
V_LT = (168, 112, 246, 255)
V_PL = (214, 190, 255, 255)

# The wizard trees' shared accents.
CREAM = (255, 245, 200, 255)
WHITE = (255, 255, 255, 255)
BLUE = (45, 110, 230, 255)
BLUE_D = (24, 62, 160, 255)
BLUE_L = (140, 190, 255, 255)
GOLD = (255, 188, 94, 255)
GOLD_D = (180, 113, 64, 255)
GREEN = (86, 196, 62, 255)
GREEN_D = (40, 110, 32, 255)
TEAL = (72, 214, 186, 255)
TEAL_D = (26, 108, 96, 255)

RAMP = (OUTL, V_DK, V_MD, V_LT, V_PL)
# The same ramp a step brighter, for the thin or backdrop shapes.
LIGHT = (OUTL, V_MD, V_LT, V_PL, (250, 244, 255, 255))


# --------------------------------------------------------------------------- io

def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def mod_item(name, frame=0):
    """A frame of one of the mod's own (animated, 16x256) item textures."""
    im = Image.open(os.path.join(ITEM, f"{name}.png")).convert("RGBA")
    return im.crop((0, frame * 16, 16, frame * 16 + 16))


# ---------------------------------------------------------------- pixel helpers

def canvas(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def solid(im, cut=40):
    """Kill the conjured translucency: the tree screen draws on grey, and the
    house style is opaque and saturated."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= cut else (0, 0, 0, 0)
    return out


def outline(im, colour=OUTL):
    """A one-pixel dark border around the silhouette, drawn outward."""
    out = im.copy()
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), colour)
    return out


def up2(im):
    return im.resize((im.width * 2, im.height * 2), Image.NEAREST)


def lum(c):
    return (c[0] * 299 + c[1] * 587 + c[2] * 114) // 1000


def tint(im, ramp=RAMP):
    """Push a grey vanilla sprite onto the conjured ramp by luminance."""
    out = im.copy()
    px = out.load()
    edges = (46, 92, 150, 205)
    for y in range(out.height):
        for x in range(out.width):
            c = px[x, y]
            if not c[3]:
                continue
            l = lum(c)
            i = sum(1 for e in edges if l > e)
            px[x, y] = ramp[i]
    return out


def plot(im, pixels, colour):
    for x, y in pixels:
        if 0 <= x < im.width and 0 <= y < im.height:
            im.putpixel((x, y), colour)


def sparkle(im, x, y, arm=2, colour=CREAM, core=WHITE):
    """The four-point twinkle these trees use everywhere, at 32px grain."""
    for i in range(1, arm + 1):
        plot(im, ((x + i, y), (x - i, y), (x, y + i), (x, y - i)), colour)
    plot(im, ((x, y),), core)


def disc(im, cx, cy, r, colour):
    for y in range(im.height):
        for x in range(im.width):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                im.putpixel((x, y), colour)


def ring(im, cx, cy, r, thick, colour):
    for y in range(im.height):
        for x in range(im.width):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if (r - thick) ** 2 < d2 <= r * r:
                im.putpixel((x, y), colour)


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


# ------------------------------------------------------------------ the sprites

def sword16():
    """The conjured sword, opaque and dark-outlined, on the 16px grid."""
    return outline(solid(mod_item("magic_sword", 8)))


def bow16():
    """The conjured bow, opaque and dark-outlined, on the 16px grid — lifted a
    ramp step, because a limb this thin loses the tree's grey otherwise."""
    return outline(tint(solid(mod_item("magic_bow", 8)), LIGHT))


def hollow(im, colour):
    """The silhouette's rim only — an after-image that still reads at 16px,
    where a half-transparent copy just turns to fog."""
    out = canvas(im.width)
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if not px[x, y][3]:
                continue
            if any(not (0 <= x + dx < w and 0 <= y + dy < h) or not px[x + dx, y + dy][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), colour)
    return out


def base(build):
    """Run a 16px composition and hand back the 32px canvas it lives on."""
    small = canvas()
    build(small)
    return up2(small)


# ------------------------------------------------------------------- the icons

SILVER = ((24, 18, 36, 255), (176, 174, 194, 255), (216, 216, 230, 255),
          (244, 244, 252, 255), (255, 250, 190, 255))


def magic_armaments():
    """The root active: the conjured sword bursting into being across the wand
    it replaces. The wand goes pale silver so it still reads under the violet."""
    def build(im):
        wand = Image.open(os.path.join(ITEM, "oracle_wand.png")).convert("RGBA")
        wand = tint(solid(wand), SILVER).transpose(Image.FLIP_LEFT_RIGHT)
        im.alpha_composite(outline(wand))
        im.alpha_composite(sword16())

    im = base(build)
    sparkle(im, 25, 4, 3)
    sparkle(im, 5, 23, 2)
    sparkle(im, 28, 16, 2, V_PL, WHITE)
    return im


def magic_armor():
    """Mana spent turns into absorption: the conjured breastplate with a golden
    absorption heart swelling on it, mana pouring in."""
    def build(im):
        plate = tint(vanilla("item/diamond_chestplate.png"), LIGHT)
        im.alpha_composite(outline(plate))
        heart = solid(vanilla("gui/sprites/hud/heart/absorbing_full.png"))
        heart = outline(heart.resize((9, 9), Image.NEAREST))
        im.alpha_composite(heart, (6, 7))

    im = base(build)
    # Mana running into the plate from the upper left.
    plot(im, ((1, 12), (2, 12), (1, 13), (2, 13)), BLUE_L)
    plot(im, ((3, 16), (4, 16), (3, 17), (4, 17)), BLUE)
    plot(im, ((0, 9), (1, 9), (5, 20), (6, 20)), BLUE_D)
    sparkle(im, 27, 6, 2)
    return im


# The left wing, row by row on the 16px grid; the right one is its mirror.
# Drawn rather than tinted from the elytra item, whose folded shape turns to
# two lumps once it is down at 16px.
WING_ROWS = ((2, 5, 6), (3, 3, 6), (4, 2, 6), (5, 1, 6), (6, 0, 6), (7, 0, 6),
             (8, 0, 5), (9, 1, 5), (10, 2, 5), (11, 3, 4), (12, 4, 4))


def levitation():
    """Gliding: conjured wings spread wide, the air rising past them."""
    def build(im):
        for y, x0, x1 in WING_ROWS:
            for x in range(x0, x1 + 1):
                for wx in (x, 15 - x):
                    im.putpixel((wx, y), V_LT if y < 8 else V_MD)
        # The harness between them.
        for y in range(2, 9):
            for x in (7, 8):
                im.putpixel((x, y), V_DK if y > 3 else V_MD)
        # A dark trailing edge, then a lit leading edge: wings, not lumps.
        for x in range(16):
            col = [y for y in range(16) if im.getpixel((x, y))[3]]
            if not col:
                continue
            im.putpixel((x, col[-1]), V_DK)
            im.putpixel((x, col[0]), V_PL)
        im.alpha_composite(outline(im.copy()))

    im = base(build)
    # The lift: air streaming past, under the glide.
    for x0, y0, c in ((7, 28, CREAM), (19, 28, CREAM), (3, 31, V_PL), (13, 31, V_PL),
                      (23, 31, V_PL)):
        plot(im, tuple((x0 + i, y0) for i in range(6)), c)
    sparkle(im, 16, 1, 2)
    return im


def ward():
    """Warding: a conjured barrier ring, a gob of poison bursting apart on it."""
    def build(im):
        ring(im, 8, 7, 7.6, 2, V_MD)
        ring(im, 8, 7, 7.6, 1, V_LT)
        ring(im, 8, 7, 5.8, 1, V_DK)
        for y in range(16):
            for x in range(16):
                if im.getpixel((x, y))[3] and (x + y) > 17:
                    im.putpixel((x, y), V_DK)
        im.alpha_composite(outline(im.copy()))

    im = base(build)
    # The harm that never lands: a fat green blob shattering on the ward.
    blob = [(x, y) for y in range(18, 28) for x in range(0, 10)
            if (x - 4) ** 2 + (y - 23) ** 2 <= 21]
    for x, y in blob:
        im.putpixel((x, y), GREEN)
    for x, y in ((2, 21), (3, 21), (2, 22), (4, 20)):
        im.putpixel((x, y), (150, 240, 120, 255))
    for x, y in [(x, y) for y in range(17, 30) for x in range(0, 11)
                 if 21 < (x - 4) ** 2 + (y - 23) ** 2 <= 32]:
        im.putpixel((x, y), GREEN_D)
    # Splash-back off the barrier.
    plot(im, ((8, 19), (10, 26), (9, 28), (5, 18)), GREEN)
    sparkle(im, 8, 22, 3, WHITE, WHITE)
    return im


def mind_over_matter():
    """Sharper for costlier: the same conjured blade, but the edge is burning
    gold-hot above the hilt, and there is a fat drop of mana going into it."""
    HOT = (OUTL, (168, 92, 30, 255), (240, 158, 48, 255), (255, 214, 96, 255),
           (255, 248, 196, 255))

    def build(im):
        sw = solid(mod_item("magic_sword", 8))
        blade = canvas()
        hilt = canvas()
        px = sw.load()
        for y in range(16):
            for x in range(16):
                if not px[x, y][3]:
                    continue
                # Everything above the crossguard is blade; the guard sits on
                # the sprite's lower-left diagonal.
                (blade if x - y > -3 else hilt).putpixel((x, y), px[x, y])
        im.alpha_composite(outline(hilt))
        im.alpha_composite(outline(tint(blade, HOT)))

    im = base(build)
    # The mana it drinks, as a proper teardrop below the hilt.
    for x, y, w in ((22, 21, 1), (22, 22, 2), (21, 23, 3), (21, 24, 4),
                    (21, 25, 4), (21, 26, 4), (22, 27, 3)):
        for i in range(w):
            im.putpixel((x + i, y), BLUE)
    plot(im, ((22, 23), (22, 24), (23, 22)), BLUE_L)
    for x, y in ((21, 21), (23, 21), (22, 20), (20, 23), (20, 24), (20, 25),
                 (20, 26), (25, 24), (25, 25), (25, 26), (21, 27), (25, 23),
                 (22, 28), (23, 28), (24, 28), (24, 27), (21, 22)):
        im.putpixel((x, y), BLUE_D)
    sparkle(im, 6, 6, 2)
    return im


def blink():
    """The swing that moves you: the blade already landed forward while its
    after-image still hangs where you stood, ender sparks strung between."""
    def build(im):
        sil = solid(mod_item("magic_sword", 8))
        ghost = sil.copy()
        px = ghost.load()
        for y in range(16):
            for x in range(16):
                if px[x, y][3]:
                    px[x, y] = TEAL
        # A flat, ender-teal body with its own rim: a half-faded copy just goes
        # to fog once the icon is down at 16px, a flat one still reads.
        im.alpha_composite(outline(ghost, TEAL_D), (-5, 3))
        im.alpha_composite(sword16(), (3, -3))

    im = base(build)
    for x, y in ((8, 19), (9, 19), (8, 20), (9, 20), (12, 14), (13, 14), (12, 15), (13, 15)):
        im.putpixel((x, y), TEAL)
    plot(im, ((7, 21), (10, 18), (11, 16), (14, 13), (5, 23), (6, 23)), TEAL_D)
    sparkle(im, 5, 25, 2, TEAL, WHITE)
    sparkle(im, 26, 5, 2)
    return im


def spellbow():
    """The capstone: the armament comes out a bow instead — conjured limb,
    arrow on the string."""
    def build(im):
        shaft = canvas()
        # An arrow up the string's perpendicular, thick enough to survive 16px.
        for i in range(11):
            x, y = 2 + i, 13 - i
            plot(shaft, ((x, y), (x + 1, y), (x, y - 1)), CREAM)
        for x, y in ((11, 2), (12, 2), (13, 2), (13, 3), (13, 4),
                     (12, 1), (11, 1), (10, 3), (11, 3)):
            shaft.putpixel((x, y), CREAM)
        for x, y in ((2, 14), (3, 14), (1, 13), (10, 4), (12, 5)):
            shaft.putpixel((x, y), (206, 190, 130, 255))
        im.alpha_composite(outline(shaft))
        im.alpha_composite(bow16())

    im = base(build)
    sparkle(im, 5, 5, 3)
    sparkle(im, 26, 24, 2, V_PL, WHITE)
    return im


def mana_siphon():
    """Hits pay the channel back: a conjured arrow buried in a fat mana orb,
    which is coming apart into motes streaming back the way the shot came."""
    def build(im):
        orb = canvas()
        disc(orb, 9, 6, 5.4, BLUE)
        disc(orb, 8, 5, 3.2, BLUE_L)
        # A dark underside so the orb reads as a sphere, not a dot.
        for y in range(16):
            for x in range(16):
                if orb.getpixel((x, y))[3] and (x + y) > 17:
                    orb.putpixel((x, y), BLUE_D)
        im.alpha_composite(outline(orb))

        # The arrow, driven up into it from the lower left.
        shaft = canvas()
        for i in range(9):
            x, y = 1 + i, 14 - i
            plot(shaft, ((x, y), (x + 1, y), (x, y - 1)), CREAM)
        for x, y in ((1, 15), (2, 15), (0, 14)):
            shaft.putpixel((x, y), (206, 190, 130, 255))
        im.alpha_composite(outline(shaft))

    im = base(build)
    # The mana coming back out, trailing down the shaft toward the archer.
    plot(im, ((6, 20), (7, 20), (6, 21), (7, 21)), BLUE_L)
    plot(im, ((3, 25), (4, 25), (3, 26), (4, 26)), BLUE)
    plot(im, ((1, 29), (2, 29), (8, 18), (9, 18)), BLUE_D)
    sparkle(im, 24, 4, 3, BLUE_L, WHITE)
    sparkle(im, 27, 15, 2, V_PL, WHITE)
    return im


ICONS = {
    "magic_armaments": magic_armaments,
    "magic_armor": magic_armor,
    "levitation": levitation,
    "ward": ward,
    "mind_over_matter": mind_over_matter,
    "blink": blink,
    "spellbow": spellbow,
    "mana_siphon": mana_siphon,
}

ORDER = ["magic_armaments", "magic_armor", "levitation", "ward",
         "mind_over_matter", "blink", "spellbow", "mana_siphon"]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    install = "--install" in sys.argv
    if install:
        os.makedirs(DST, exist_ok=True)
    for name in ORDER:
        im = ICONS[name]()
        assert im.size == (32, 32), (name, im.size)
        im.save(os.path.join(OUT_DIR, f"{name}.png"))
        if install:
            im.save(os.path.join(DST, f"{name}.png"))
        print(name)


if __name__ == "__main__":
    main()
