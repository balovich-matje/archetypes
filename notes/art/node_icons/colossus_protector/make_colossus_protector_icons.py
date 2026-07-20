"""Generate the 32x32 node icons for the epic Colossus-Protector constellation.

House style, matched from the shipped sets (oracle_priest, nemesis_shadow,
assassin): the drawing is authored at 16x16 -- exactly what the tree screen
shows -- and blown up 2x NEAREST onto the 32px canvas, so every "pixel" is a
fat 2x2 block. One dominant subject per icon, a vanilla sprite wherever one
fits, dark outlines, and one or two small hand-plotted accents to say what
the node DOES.

The three shield nodes share one hand-drawn shield sprite in the shipped
Protector palette (grey rim, plank wood, iron boss), so this tree reads as
the same shield line one tier up; they are pulled apart by what stands
beside the shield, not by the shield itself.

Vanilla sprites live in ./vanilla (pulled from the 26.2 clientonly deobf
jar). Renders land in ./out; --install copies them into
assets/archetypes/textures/node/colossus_protector/.

Usage: python3 make_colossus_protector_icons.py [--install]
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
VAN = os.path.join(HERE, "vanilla")
DST = os.path.join(HERE, "out")
INSTALL = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/colossus_protector"))

# --- palette ---------------------------------------------------------------
OUT = (26, 20, 14, 255)           # dark outline, warm
RIM = (145, 147, 155, 255)        # shipped Protector shield rim
RIM_L = (183, 185, 193, 255)
RIM_D = (104, 106, 114, 255)
WOOD_L = (133, 105, 62, 255)
WOOD = (102, 77, 39, 255)
WOOD_D = (90, 69, 35, 255)
IRON_L = (226, 228, 236, 255)
IRON = (176, 178, 188, 255)
IRON_D = (119, 121, 130, 255)
STEEL = (196, 198, 206, 255)
STEEL_D = (108, 110, 120, 255)
WHITE = (255, 255, 255, 255)
CREAM = (255, 246, 206, 255)
CYAN = (108, 222, 226, 255)
CYAN_L = (188, 246, 248, 255)
RED = (214, 60, 54, 255)
PINK = (226, 122, 196, 255)
GOLD = (232, 168, 34, 255)
GOLD_L = (255, 210, 84, 255)


def van(name):
    return Image.open(os.path.join(VAN, name + ".png")).convert("RGBA")


def c16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def up(im):
    return im.resize((32, 32), Image.NEAREST)


def put(im, x, y, col):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((int(x), int(y)), col)


def fit(im, w, h=None):
    return im.resize((w, h if h else w), Image.NEAREST)


def trim(im):
    box = im.getbbox()
    return im.crop(box) if box else im


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


def stamp(im, x, y, rows, cmap):
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch != " " and ch != ".":
                put(im, x + rx, y + ry, cmap[ch])


def sparkle(im, x, y, col=CREAM, arm=1):
    put(im, x, y, col)
    for i in range(1, arm + 1):
        put(im, x + i, y, col)
        put(im, x - i, y, col)
        put(im, x, y + i, col)
        put(im, x, y - i, col)


def save(im16, name):
    os.makedirs(DST, exist_ok=True)
    up(im16).save(os.path.join(DST, name + ".png"))
    print(name + ".png")


# --- the shield ------------------------------------------------------------
# How far each row is pulled in from both sides. Half the height is taper:
# a rectangle reads as a tankard at 16px, a hard V does not. The narrow cut
# is for the icon that has to leave room beside the shield.
SHIELDS = {
    11: ((0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 4, 5), (3, 2), 5),
    9: ((0, 0, 0, 0, 1, 1, 2, 2, 3, 4), (3, 2), 3),
}
PLANKS = (WOOD_L, WOOD, WOOD, WOOD_D, WOOD, WOOD, WOOD_D, WOOD_D)
BOSS5 = ["..i..",
         ".iIi.",
         "iIKIi",
         ".iIi.",
         "..i.."]
BOSS3 = [".i.",
         "iKi",
         ".i."]


def shield_layer(w=11):
    """The house shield, in the shipped Protector palette: grey rim, plank
    wood, iron boss."""
    im = c16()
    insets, (bx, by), boss_w = SHIELDS[w]
    rows = [range(i, w - i) for i in insets]
    for y, span in enumerate(rows):
        lo, hi = span[0], span[-1]
        above = rows[y - 1] if y else None
        below = rows[y + 1] if y + 1 < len(rows) else None
        for x in span:
            edge = (x == lo or x == hi
                    or above is None
                    or x not in above or (below is not None and x not in below)
                    or below is None)
            if edge:
                col = RIM_L if (x == lo or above is None) else RIM_D
            else:
                col = PLANKS[(x - 1) % len(PLANKS)]
            put(im, x, y, col)
    stamp(im, bx, by, BOSS5 if boss_w == 5 else BOSS3,
          {"i": IRON_D, "I": IRON, "K": IRON_L})
    return outline(im, OUT)


def shield(im, x, y, w=11):
    """Stamp the shield with its top-left at (x, y); negative x runs it off
    the frame edge, which is how the two 'while blocking' icons brace it."""
    layer = shield_layer(w)
    px = layer.load()
    for sy in range(16):
        for sx in range(16):
            if px[sx, sy][3]:
                put(im, x + sx, y + sy, px[sx, sy])


def arrow_up(im, x, y, col=CYAN_L, edge=CYAN):
    rows = ["  aa  ",
            " aaaa ",
            "aaaaaa",
            "bbaabb",
            "  aa  ",
            "  aa  ",
            "  aa  "]
    layer = c16()
    stamp(layer, x, y, rows, {"a": col, "b": edge})
    im.alpha_composite(outline(layer, OUT))


def tint(im, col):
    """Recolour a greyscale vanilla overlay, keeping its alpha."""
    out = im.copy()
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                k = max(r, g, b) / 255.0
                out.putpixel((x, y), (int(col[0] * k), int(col[1] * k),
                                      int(col[2] * k), a))
    return out


def burst(im, x, y, small=False):
    """A hit dying on the shield face."""
    put(im, x, y, WHITE)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(im, x + dx, y + dy, WHITE if not small else CREAM)
    if not small:
        for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
            put(im, x + dx, y + dy, CREAM)


# --- the icons -------------------------------------------------------------

def ironclad():
    """The root: every point of armour and toughness you own, worth half
    again. The diamond chestplate is the one silhouette in the game that
    means 'armour', with the arrow that says the number went up."""
    im = c16()
    plate = trim(van("diamond_chestplate"))
    plate = fit(plate, 13, 12)
    im.alpha_composite(outline(plate, OUT), (0, 3))
    arrow_up(im, 10, 1, GOLD_L, GOLD)
    sparkle(im, 3, 6, WHITE)
    save(im, "ironclad")


def well_fed():
    """Eating goes fast and the bar holds more: the hunger haunch coming in
    at speed, over the bar it banks into -- drawn full, and segmented so it
    reads as a bar and not as a floor."""
    im = c16()
    haunch = trim(van("food_full"))
    haunch = fit(haunch, 11, 11)
    im.alpha_composite(outline(haunch, OUT), (4, 0))
    for y, ln in ((2, 3), (5, 4), (8, 3)):
        for k in range(ln):
            put(im, k, y, CREAM)
            put(im, k, y + 1, OUT)
    # the bar it banks into: an outlined gauge, segmented so it reads as a
    # bar and not as a floor, running off the right of the frame because the
    # thing the node does is make it longer
    layer = c16()
    x0, x1, y0 = 0, 11, 12
    for x in range(x0, x1 + 1):
        put(layer, x, y0 + 1, GOLD_L)
        put(layer, x, y0 + 2, GOLD)
    for k in range(3):
        for y in range(y0 + k - 1, y0 + 4 - k):
            put(layer, x1 + 1 + k, y, GOLD_L if y < y0 + 2 else GOLD)
    im.alpha_composite(outline(layer, OUT))
    for x in (3, 7):
        put(im, x, y0 + 1, GOLD)
        put(im, x, y0 + 2, (150, 96, 18, 255))
    save(im, "well_fed")


def hearty_meal():
    """A meal is worth a potion now: the stew bowl with the three buffs it
    can hand you rising off it -- red strength, cyan speed, pink regen."""
    im = c16()
    bowl = trim(van("rabbit_stew"))
    bowl = fit(bowl, 14, 8)
    im.alpha_composite(outline(bowl, OUT), (1, 8))
    sparkle(im, 3, 4, RED)
    sparkle(im, 8, 2, CYAN_L)
    sparkle(im, 13, 5, PINK)
    save(im, "hearty_meal")


def instinctive_guard():
    """The guard you never raise: nobody is holding this shield and the
    hits still die on it. Chevrons read as incoming at 16px where a whole
    arrow just turns into a dark diagonal smear."""
    im = c16()
    shield(im, 1, 2)
    burst(im, 10, 4)
    burst(im, 8, 11, small=True)
    for x, y in ((13, 1), (14, 6), (12, 13)):
        put(im, x, y, WHITE)
    for x, y in ((14, 2), (13, 7)):
        put(im, x, y, CREAM)
    save(im, "instinctive_guard")


def free_hand():
    """Both hands still work: the shield stays braced at the frame edge and
    the other hand drinks anyway."""
    im = c16()
    shield(im, -4, 2)
    bottle = trim(van("potion"))
    brew = trim(van("potion_overlay").convert("RGBA"))
    brew = tint(brew, (232, 60, 82, 255))
    bottle = Image.alpha_composite(
        fit(brew, bottle.width, bottle.height), bottle) if brew.size else bottle
    bottle = fit(bottle, 9, 12)
    im.alpha_composite(outline(bottle, OUT), (7, 2))
    sparkle(im, 14, 1, CREAM)
    save(im, "free_hand")


def immovable_object():
    """Block and swing at once: the shield never drops and the sword swings
    over the top of it anyway, arc and all."""
    im = c16()
    shield(im, -4, 2)
    sword = trim(van("iron_sword"))
    sword = fit(sword, 11, 11)
    im.alpha_composite(outline(sword, OUT), (5, 0))
    # the swing, sweeping off the blade
    arc = ((15, 8), (15, 9), (14, 11), (13, 12), (11, 13), (9, 14))
    layer = c16()
    for x, y in arc:
        put(layer, x, y, WHITE)
    for x, y in arc[1:]:
        put(layer, x - 1, y + 1, CREAM)
    im.alpha_composite(outline(layer, OUT))
    save(im, "immovable_object")


ICONS = (ironclad, well_fed, hearty_meal, instinctive_guard, free_hand,
         immovable_object)

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
