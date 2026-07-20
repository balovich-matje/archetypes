"""Generate the 32x32 node icons for the epic Colossus-Slayer constellation.

House style, matched from the shipped trees (slayer, shadow, assassin,
protector, nemesis_shadow, oracle_priest): a 16x16 vanilla-scale drawing
blown up 2x NEAREST onto a 32px canvas, so every "pixel" is a fat 2x2
block. One dominant, instantly-readable subject per icon — a vanilla item
sprite wherever one fits — dark outlines, and one or two small hand-plotted
accents (sparks, motion lines, chevrons) that say what the node DOES.

The tree is Parry: a timed deflection, what earns it, and what it pays.
Colour keys keep the six apart in the 16px slot:
  parry         white flash, two blades
  barbarian     violet potion, red slash
  blade_master  black netherite, gold arrow
  spell_reflect purple bolt turned around
  stalwart      gold hearts
  riposte       orange flare, mirrored blade

Vanilla sprites live in ./vanilla (pulled from the 26.2 clientonly deobf
jar). Output goes to ./out, and with --install into
assets/archetypes/textures/node/colossus_slayer/.

Usage: python3 make_colossus_slayer_icons.py [--install]
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
VAN = os.path.join(HERE, "vanilla")
DST = os.path.join(HERE, "out")
INSTALL = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/colossus_slayer"))

# --- palette ----------------------------------------------------------------
OUT = (20, 18, 22, 255)             # near-black outline, same weight as vanilla
STEEL_D = (92, 96, 106, 255)
STEEL = (176, 180, 190, 255)
STEEL_L = (228, 232, 240, 255)
WHITE = (255, 255, 255, 255)
FOE_D = (46, 44, 52, 255)           # the incoming enemy blade: dull, cold
FOE = (104, 100, 112, 255)
FOE_L = (150, 146, 158, 255)
GOLD_D = (150, 96, 18, 255)
GOLD = (232, 168, 34, 255)
GOLD_L = (255, 210, 84, 255)
CREAM = (255, 246, 206, 255)
RED_D = (118, 20, 26, 255)
RED = (206, 40, 44, 255)
RED_L = (255, 92, 88, 255)
PUR_D = (74, 40, 128, 255)
PUR = (146, 88, 216, 255)
PUR_L = (208, 168, 255, 255)
FIRE_D = (154, 54, 10, 255)
FIRE = (244, 140, 24, 255)
FIRE_L = (255, 208, 92, 255)


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
    if 0 <= x < im.width and 0 <= y < im.height:
        im.alpha_composite(Image.new("RGBA", (1, 1), col), (int(x), int(y)))


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
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < im.width and 0 <= ny < im.height and px[nx, ny][3] > 128:
                    out.putpixel((x, y), col)
                    break
    return out


def ring(im, cx, cy, r_in, r_out, col):
    for y in range(im.height):
        for x in range(im.width):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r_in <= d < r_out:
                put(im, x, y, col)


def disc(im, cx, cy, r, col):
    ring(im, cx, cy, 0, r, col)


def sparkle(im, x, y, col=CREAM, arm=1):
    put(im, x, y, col)
    for i in range(1, arm + 1):
        put(im, x + i, y, col)
        put(im, x - i, y, col)
        put(im, x, y + i, col)
        put(im, x, y - i, col)


def star(im, cx, cy, arm, core=WHITE, mid=CREAM, tip=GOLD):
    """A four-point burst — the vanilla nether-star shape, fattened so it
    survives the 16px slot."""
    for i in range(1, arm + 1):
        col = mid if i <= arm - 2 else tip
        put(im, cx, cy - i, col)
        put(im, cx, cy + i, col)
        put(im, cx - i, cy, col)
        put(im, cx + i, cy, col)
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        put(im, cx + dx, cy + dy, mid)
    put(im, cx, cy, core)


def arrow_up(im, x, y, col=GOLD_L, edge=GOLD):
    """The stubby up-arrow the shipped Protector/Priest icons use for 'the
    number went up'. Own layer, then outlined, so it stays an object."""
    rows = ["   a   ",
            "  aaa  ",
            " aaaaa ",
            "aaaaaaa",
            "b aaa b",
            "  aaa  ",
            "  aaa  "]
    layer = c16()
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch == "a":
                put(layer, x + rx, y + ry, col)
            elif ch == "b":
                put(layer, x + rx, y + ry, edge)
    im.alpha_composite(outline(layer, OUT))


def streaks(im, spans, col=STEEL_L, shade=STEEL_D):
    """Vanilla-ish speed lines: (x, y, len) runs with a darker underline."""
    for x, y, ln in spans:
        for k in range(ln):
            put(im, x + k, y, col)
            put(im, x + k, y + 1, shade)


def diag_blade(layer, x0, y0, n, dx, dy, body, edge, tip=None):
    """A hand-plotted 45-degree blade: 2px thick, lit on one side, with a
    pointed tip at the far end. Draw onto its own layer, then outline."""
    for i in range(n):
        x, y = x0 + dx * i, y0 + dy * i
        put(layer, x, y, edge)
        put(layer, x + dx, y, body)
        if i < n - 2:
            put(layer, x, y + dy, body)
    ex, ey = x0 + dx * (n - 1), y0 + dy * (n - 1)
    put(layer, ex, ey, tip or edge)
    put(layer, ex + dx, ey, tip or body)


def edge_light(sprite, cols, col=STEEL_L):
    """Run a bright edge down the top-left side of a sword sprite's blade so a
    dark blade still reads against the tree screen's dark node plate."""
    out = sprite.copy()
    px = out.load()
    for x in cols:
        for y in range(out.height):
            if px[x, y][3] > 128:
                out.putpixel((x, y), col)
                break
    return out


def save(im32, name):
    os.makedirs(DST, exist_ok=True)
    im32.save(os.path.join(DST, name + ".png"))
    print(name + ".png")


# --- the icons --------------------------------------------------------------

def parry():
    """The pinch of the hourglass: attack and block at once. The enemy's
    blade comes down from the top-left, the player's iron sword catches it
    dead, and the whole icon is built around the white flash where they
    meet — that flash is the only thing that has to survive 16px."""
    im = c16()

    # the incoming attack — heavy, cold, cut off by the frame so it reads as
    # something arriving rather than a second sword lying in an X
    foe = c16()
    diag_blade(foe, 0, 0, 7, 1, 1, FOE, FOE_D, FOE_L)
    for i in range(6):
        put(foe, 1 + i, 2 + i, FOE_D)
    im.alpha_composite(outline(foe, OUT))

    # the player's sword, catching it
    im.alpha_composite(van("iron_sword"))

    # the parry itself: a fat white burst on the contact, sparks thrown off
    # square to both blades
    star(im, 7, 7, 4, core=WHITE, mid=WHITE, tip=CREAM)
    for k in (2, 3):
        put(im, 7 - k, 7 + k, CREAM)
        put(im, 7 + k, 7 - k, CREAM)
    put(im, 4, 11, GOLD_L)
    put(im, 11, 4, GOLD_L)
    save(up(im), "parry")


def barbarian():
    """Magic slides off you: a potion of every colour struck through, the
    spell fizzling out at the edges. Damage and healing both — so the thing
    crossed out is the bottle itself, not a wand."""
    im = c16()
    overlay = van("potion_overlay")
    tint = Image.new("RGBA", overlay.size, (150, 92, 226, 255))
    liquid = Image.new("RGBA", overlay.size, (0, 0, 0, 0))
    op, tp = overlay.load(), tint.load()
    for y in range(overlay.height):
        for x in range(overlay.width):
            r, g, b, a = op[x, y]
            if a:
                tr, tg, tb, _ = tp[x, y]
                liquid.putpixel((x, y), (r * tr // 255, g * tg // 255, b * tb // 255, a))
    im.alpha_composite(liquid)
    im.alpha_composite(van("potion"))

    # the magic fizzling off it
    put(im, 12, 3, PUR_L)
    put(im, 13, 2, PUR)
    put(im, 2, 7, PUR)
    put(im, 1, 6, PUR_L)

    # struck through
    slash = c16()
    for i in range(13):
        x, y = 1 + i, 14 - i
        put(slash, x, y, RED_L if i % 3 else RED)
        put(slash, x, y - 1, RED_D)
    im.alpha_composite(outline(slash, OUT))
    save(up(im), "barbarian")


def blade_master():
    """Two ranks of pure weapon skill: the greatsword swings faster and the
    sword hits harder. Netherite blade with its edge honed white, the swing
    trailing behind it, and the up-arrow for the damage."""
    im = c16()
    sword = van("netherite_sword")
    im.alpha_composite(edge_light(sword, range(5, 14), STEEL_L))

    # the swing: two short dashes square to the blade, in clear air above it,
    # only where nothing has been drawn — they must not read as a second sword
    for x0, y0 in ((0, 7), (2, 2)):
        for k in range(3):
            for x, y, col in ((x0 + k, y0 - k, STEEL_L), (x0 + k, y0 - k + 1, STEEL_D)):
                if im.getpixel((x, y))[3] == 0:
                    put(im, x, y, col)

    arrow_up(im, 8, 8)
    put(im, 14, 0, CREAM)
    save(up(im), "blade_master")


def spell_reflect():
    """A directed spell parried straight back at whoever cast it. The bolt
    comes in along the top edge, the blade turns it, and the fat purple ball
    is on its way home — the flash marks where it bounced."""
    im = c16()
    # the blade, cropped to a bare edge across the corner — this icon belongs
    # to the bolt, not to another sword
    edge = c16()
    diag_blade(edge, 5, 15, 9, 1, -1, STEEL_D, STEEL, STEEL_L)
    for i in range(9):
        put(edge, 6 + i, 15 - i, STEEL_L)
    im.alpha_composite(outline(edge, OUT))

    # incoming: thin, dim, dying into the blade
    for (x, y), col in (((9, 0), PUR_D), ((9, 2), PUR_D), ((10, 4), PUR), ((10, 6), PUR)):
        put(im, x, y, col)

    # the turn
    star(im, 11, 8, 3, core=WHITE, mid=WHITE, tip=PUR_L)

    # outgoing: fat, bright, on its way back to whoever cast it
    for (x, y), col in (((9, 6), PUR_L), ((8, 5), PUR), ((8, 6), PUR_D),
                        ((7, 4), PUR), ((7, 5), PUR_D)):
        put(im, x, y, col)
    bolt = c16()
    rows = ["  dd  ",
            " LLPP ",
            "dLLPPd",
            "dLPPPd",
            " PPPP ",
            "  dd  "]
    cmap = {"d": PUR_D, "P": PUR, "L": PUR_L}
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch != " ":
                put(bolt, 1 + rx, ry, cmap[ch])
    im.alpha_composite(outline(bolt, OUT))
    put(im, 0, 0, PUR_L)
    save(up(im), "spell_reflect")


def stalwart():
    """A clean parry banks temporary hearts: vanilla's gold absorption heart,
    a second one arriving behind it, on the white spark of the parry that
    paid for them."""
    im = c16()
    # the parry that paid for them, low and behind
    star(im, 3, 13, 2, core=WHITE, mid=WHITE, tip=CREAM)

    # the second heart, arriving — kept clear of the first so the pair does
    # not fuse into one gold lump at 16px
    small = fit(van("heart_absorb"), 7)
    im.alpha_composite(outline(small, OUT), (9, 0))
    put(im, 9, 8, CREAM)
    put(im, 11, 9, GOLD_L)

    big = fit(van("heart_absorb"), 11)
    im.alpha_composite(outline(big, OUT), (0, 3))
    save(up(im), "stalwart")


def riposte():
    """The counter-thrust: the answering blade goes the other way to every
    other sword in this tree, wrapped in the orange of Strength, with the
    thrust lines behind it."""
    im = c16()
    # the Strength: vanilla's blaze powder, the game's own picture of raw
    # damage, banked low so the blade sits on top of it
    fire = van("blaze_powder").crop((1, 2, 15, 15)).resize((11, 10), Image.NEAREST)
    im.alpha_composite(fire, (0, 6))

    sword = van("iron_sword").transpose(Image.FLIP_LEFT_RIGHT)
    im.alpha_composite(outline(sword, OUT))
    im.alpha_composite(sword)

    # the counter, driven back the other way — thin vanilla speed lines, not
    # bars: three of them, tapering
    for x, y, ln, col in ((11, 1, 4, FIRE_L), (12, 3, 3, FIRE)):
        for k in range(ln):
            put(im, x + k, y, col)
    put(im, 2, 4, FIRE_L)
    save(up(im), "riposte")


ICONS = (parry, barbarian, blade_master, spell_reflect, stalwart, riposte)

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
