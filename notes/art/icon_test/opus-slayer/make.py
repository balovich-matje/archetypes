"""Slayer skill-node icons, round three of the bake-off.

Every icon starts from a real vanilla sprite pulled from the client jar
(recolored, cropped, composed) plus a small readable mechanic grammar hand-
plotted on top — the grammar the two earlier rounds settled on:

    + / burst  = more damage        speed dashes = faster / movement
    ghost copy = an extra strike     down-chevrons = slowed
    refresh loop = cooldown reset    blood drip = bleed / lifesteal

The Slayer is swords and two-handed greatswords, so almost every icon is built
on the actual weapon it modifies — the vanilla iron_sword or the mod's own
iron_greatsword — the way the mod author liked in rounds one and two ("fit very
well with minecraft's icon design", "match the weapon in hand").

Output: 32x32 PNGs (16px art upscaled 2x NEAREST is the norm; greatsword art is
already composed at its native 32px) plus a labeled contact sheet.

Usage: python3 make.py
"""
import math
import os
import zipfile

from PIL import Image, ImageDraw

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")
MOD_ITEM = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/item"))

# --- palette, lifted from the mod's own make_node_icons.py so it matches ------
BLOOD = (200, 32, 32, 255)
BLOOD_DARK = (122, 16, 16, 255)
BLOOD_LIGHT = (240, 96, 96, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (150, 150, 150, 255)
IRON = (200, 200, 208, 255)
IRON_DARK = (120, 120, 128, 255)
IRON_LIGHT = (238, 238, 244, 255)
# slow = a cold blue-grey chevron, the debuff read
SLOW = (128, 196, 224, 255)
SLOW_DARK = (58, 118, 152, 255)
# "more" gold and "refresh" green, the two effect colours the earlier sheets use
GOLD = (255, 214, 66, 255)
GOLD_DARK = (150, 104, 0, 255)
GREEN = (112, 210, 92, 255)
GREEN_DARK = (44, 128, 44, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def greatsword(size=32):
    im = Image.open(os.path.join(MOD_ITEM, "iron_greatsword.png")).convert("RGBA")
    return im.resize((size, size), Image.NEAREST)


def canvas(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def up2(im):
    return im.resize((32, 32), Image.NEAREST)


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def plot(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def drop(im, x, y, s=1):
    """A blood teardrop, point at (x, y). s=1 for 16px art, s=2 for 32px."""
    body = ((0, 0, BLOOD), (0, 1, BLOOD), (-1, 2, BLOOD_DARK), (0, 2, BLOOD_LIGHT),
            (1, 2, BLOOD), (-1, 3, BLOOD), (0, 3, BLOOD_DARK), (1, 3, BLOOD_DARK))
    for dx, dy, c in body:
        for ox in range(s):
            for oy in range(s):
                plot(im, x + dx * s + ox, y + dy * s + oy, c)


def down_chevron(im, cx, cy, c=SLOW, dark=SLOW_DARK):
    """A '\\/' chevron pointing down, tip at (cx, cy+2). The 'slowed' read."""
    for dx in range(-2, 3):
        y = cy + (2 - abs(dx))
        plot(im, cx + dx, y, c)
        plot(im, cx + dx, y + 1, dark)


def speed_dash(im, x, y, length, c=ARC, dim=ARC_DIM):
    """A horizontal motion streak, brightest at its leading (right) end."""
    for i in range(length):
        plot(im, x + i, y, c if i >= length - 2 else dim)


def sword16():
    return vanilla("item/iron_sword.png")


# --- the icons ----------------------------------------------------------------

def hamstring():
    """Hamstring — sword/greatsword hits apply Slowness. The blade, and the
    'slowed' down-chevrons stacked where it struck."""
    im = sword16().copy()
    for i, cy in enumerate((8, 11)):
        down_chevron(im, 11, cy)
    return up2(im)


def taste_of_blood():
    """Taste of Blood — kills restore hearts. A vanilla heart drinking a rising
    blood drip, a slim blade behind it to say the kill was yours."""
    im = canvas()
    sword = sword16().resize((13, 13), Image.NEAREST)
    im.alpha_composite(faded(sword, 150), (2, 1))
    heart = vanilla("gui/sprites/hud/heart/full.png").resize((11, 11), Image.NEAREST)
    im.alpha_composite(heart, (2, 1))
    plot(im, 7, 12, BLOOD_DARK)
    drop(im, 7, 12)
    plot(im, 11, 14, BLOOD)
    return up2(im)


def lunge():
    """Lunge — a sprinting sword-swing flings you forward and up. The blade
    thrusting up-right, speed dashes trailing from the grip."""
    im = sword16().copy()
    speed_dash(im, 0, 12, 6)
    speed_dash(im, 1, 14, 5)
    speed_dash(im, 0, 10, 4)
    return up2(im)


def immovable():
    """Immovable — a greatsword in hand gives knockback resistance. The blade
    over an obsidian block, Minecraft's own byword for 'won't be moved'."""
    im = canvas(32)
    im.alpha_composite(iso_block("obsidian"), (16, 15))
    im.alpha_composite(greatsword(), (0, 0))
    return im


def rend():
    """Rend — a sword-cut that bleeds over time. Two open gashes, blood running
    off the bottom of them."""
    im = canvas()
    for sx, sy, length in ((7, 2, 7), (10, 2, 8)):
        for j in range(length):
            x = sx - j
            y = sy + j
            plot(im, x, y, BLOOD_LIGHT if j == 0 else BLOOD)
            plot(im, x + 1, y, BLOOD_DARK)
    drop(im, 3, 10)
    drop(im, 8, 11)
    return up2(im)


def blade_dance():
    """Blade Dance — a strike that may lash a second enemy in any direction. The
    real blade up-right, a ghost copy of it striking off up-left: two hits, two
    ways at once."""
    im = canvas()
    real = sword16()
    ghost = real.resize((14, 14), Image.NEAREST).transpose(Image.FLIP_LEFT_RIGHT)
    im.alpha_composite(faded(ghost, 165), (0, 1))
    im.alpha_composite(real, (3, 0))
    # an arc off each tip, the two strikes landing
    for x, y in ((13, 1), (14, 3)):
        plot(im, x, y, ARC)
    for x, y in ((2, 1), (1, 3)):
        plot(im, x, y, ARC_DIM)
    return up2(im)


def heavy_blows():
    """Heavy Blows — the greatsword hits harder and swings slower. A gold '+'
    off the tip (more), slow down-chevrons off the grip (slower)."""
    im = greatsword()
    plus(im, 5, 6, GOLD, GOLD_DARK)
    d = canvas(16)
    down_chevron(d, 4, 8)
    down_chevron(d, 4, 11)
    im.alpha_composite(up2(d), (0, 0))
    return im


def plus(im, cx, cy, c, dark):
    """A chunky 5x5 plus with a dark edge, centered at (cx, cy) on a 32 canvas."""
    for d in range(-2, 3):
        plot(im, cx + d, cy, c)
        plot(im, cx, cy + d, c)
    for ox, oy in ((-3, 0), (3, 0), (0, -3), (0, 3),
                   (-2, -1), (-2, 1), (2, -1), (2, 1),
                   (-1, -2), (1, -2), (-1, 2), (1, 2)):
        plot(im, cx + ox, cy + oy, dark)


def first_blood():
    """First Blood — greatsword hits on an unhurt target deal more. A clean
    blade, its tip drawing the first drop from a full heart."""
    im = greatsword()
    heart = vanilla("gui/sprites/hud/heart/full.png").resize((9, 9), Image.NEAREST)
    im.alpha_composite(heart, (21, 2))
    for x, y, c in ((22, 10, BLOOD), (23, 11, BLOOD_LIGHT), (22, 12, BLOOD),
                    (23, 13, BLOOD_DARK)):
        plot(im, x, y, c)
    return im


def flurry():
    """Flurry — sword kills reset Lunge's cooldown. The blade wrapped in a green
    refresh loop, the 'cooldown reset' read."""
    im = sword16().copy()
    refresh_loop(im, 8, 8, 6, GREEN, GREEN_DARK)
    return up2(im)


def refresh_loop(im, cx, cy, r, c, dark):
    pts = []
    for a in range(0, 360, 6):
        if 300 <= a <= 350:  # the gap the arrowhead sits in
            continue
        x = round(cx + r * math.cos(math.radians(a)))
        y = round(cy + r * math.sin(math.radians(a)))
        if (x, y) not in pts:
            pts.append((x, y))
    for x, y in pts:
        plot(im, x, y, c)
    # arrowhead at the top of the gap, pointing clockwise (up-ish)
    hx = round(cx + r * math.cos(math.radians(295)))
    hy = round(cy + r * math.sin(math.radians(295)))
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, -1), (1, -1), (2, 0), (0, 1)):
        plot(im, hx + dx, hy + dy, c)
    plot(im, hx, hy, dark)


def executioner():
    """Executioner — a greatsword blow finishes any target below a health
    threshold. The blade's tip come down onto a bare skull."""
    im = canvas(32)
    skull = vanilla("entity/skeleton/skeleton.png").crop((8, 8, 16, 16))
    skull = skull.resize((13, 13), Image.NEAREST)
    # a dark seat so the pale skull reads on any node frame
    seat = Image.new("RGBA", (15, 15), (0, 0, 0, 0))
    sd = ImageDraw.Draw(seat)
    sd.ellipse((0, 1, 14, 14), fill=(24, 22, 20, 210))
    im.alpha_composite(seat, (15, 17))
    im.alpha_composite(skull, (16, 18))
    im.alpha_composite(greatsword(), (0, 0))
    return im


def bloodlust():
    """Bloodlust — kills grant Speed. A fat drop of blood driven fast: speed
    dashes streaking off it, splatter flung behind."""
    im = canvas()
    # a bold teardrop, point at top
    body = {
        (5, 2): BLOOD, (4, 3): BLOOD, (5, 3): BLOOD_LIGHT, (6, 3): BLOOD,
        (3, 4): BLOOD_DARK, (4, 4): BLOOD, (5, 4): BLOOD_LIGHT, (6, 4): BLOOD, (7, 4): BLOOD_DARK,
        (3, 5): BLOOD_DARK, (4, 5): BLOOD, (5, 5): BLOOD, (6, 5): BLOOD, (7, 5): BLOOD,
        (4, 6): BLOOD, (5, 6): BLOOD, (6, 6): BLOOD_DARK,
        (5, 7): BLOOD_DARK,
    }
    for (x, y), c in body.items():
        plot(im, x, y, c)
    # splatter flung to the left
    for x, y, c in ((1, 3, BLOOD_DARK), (2, 6, BLOOD), (1, 8, BLOOD_DARK), (3, 9, BLOOD)):
        plot(im, x, y, c)
    # speed dashes streaking right
    speed_dash(im, 9, 3, 5)
    speed_dash(im, 10, 6, 5)
    speed_dash(im, 9, 9, 4)
    return up2(im)


def relentless():
    """Relentless — your capstone comes back sooner. The vanilla clock, wound
    faster: speed dashes running off it."""
    im = canvas()
    clock = vanilla("item/clock_00.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(clock, (0, 1))
    speed_dash(im, 12, 4, 4)
    speed_dash(im, 13, 7, 3)
    speed_dash(im, 12, 11, 4)
    return up2(im)


def bladestorm():
    """Bladestorm — the sword capstone: whirl, hitting everything around you.
    A fan of the same blade sweeping the same way, mid-spin."""
    sword = sword16()
    im = canvas()
    for angle, alpha in ((-63, 95), (-42, 135), (-21, 185)):
        echo = sword.rotate(angle, resample=Image.NEAREST, expand=False)
        im.alpha_composite(faded(echo, alpha), (0, 0))
    im.alpha_composite(sword, (0, 0))
    # a couple of sweep sparks on the leading arc
    for x, y in ((14, 6), (13, 3), (11, 14)):
        plot(im, x, y, ARC)
    return up2(im)


def decimate():
    """Decimate — the greatsword capstone: one huge tilted cleave. Vanilla's own
    sweep flash, the greatsword driven through it."""
    im = canvas(32)
    sweep = vanilla("particle/sweep_2.png").resize((32, 32), Image.NEAREST)
    im.alpha_composite(faded(sweep, 235), (0, 0))
    im.alpha_composite(greatsword(), (0, 0))
    return im


def iso_block(texture):
    """A quarter-size isometric inventory cube from a block texture: diamond
    top, shaded left and right faces (from the mod's own make_node_icons.py)."""
    tex = vanilla(f"block/{texture}.png")
    cube = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top = tex.rotate(45, expand=True).resize((16, 8), Image.NEAREST)
    cube.alpha_composite(top, (0, 0))
    for face, x0, shade in ((0, 0, 150), (1, 8, 210)):
        for x in range(8):
            sx = x * 2
            drop_y = (x if face == 0 else 7 - x) // 2
            for y in range(8):
                r, g, b, a = tex.getpixel((sx, y * 2))
                if a:
                    py = 4 + drop_y + y
                    if py < 16:
                        cube.putpixel((x0 + x, py),
                                (r * shade // 255, g * shade // 255, b * shade // 255, 255))
    return cube


ICON_LIST = [
    ("slowness", "Hamstring", hamstring),
    ("taste_of_blood", "Taste of Blood", taste_of_blood),
    ("lunge", "Lunge", lunge),
    ("kbres", "Immovable", immovable),
    ("bleed", "Rend", rend),
    ("blade_dance", "Blade Dance", blade_dance),
    ("heavy", "Heavy Blows", heavy_blows),
    ("firstblood", "First Blood", first_blood),
    ("flurry", "Flurry", flurry),
    ("executioner", "Executioner", executioner),
    ("bloodlust", "Bloodlust", bloodlust),
    ("relentless", "Relentless", relentless),
    ("bladestorm", "Bladestorm", bladestorm),
    ("decimate", "Decimate", decimate),
]


def contact_sheet(rendered):
    scale = 4
    cols = 5
    cell = 16 * scale
    pad = 14
    labelh = 20
    rows = (len(rendered) + cols - 1) // cols
    w = cols * (cell + pad) + pad
    h = rows * (cell + labelh + pad) + pad
    sheet = Image.new("RGBA", (w, h), (43, 43, 43, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, im) in enumerate(rendered):
        c = i % cols
        r = i // cols
        big = im.resize((cell, cell), Image.NEAREST)
        x = pad + c * (cell + pad)
        y = pad + r * (cell + labelh + pad)
        sheet.alpha_composite(big, (x, y))
        tw = d.textlength(label)
        d.text((x + (cell - tw) / 2, y + cell + 5), label, fill=(224, 224, 224, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))


def main():
    os.makedirs(ICONS, exist_ok=True)
    rendered = []
    for fname, label, fn in ICON_LIST:
        im = fn()
        if im.size != (32, 32):
            im = im.resize((32, 32), Image.NEAREST)
        im.save(os.path.join(ICONS, f"{fname}.png"))
        rendered.append((label, im))
        print(f"{fname}.png")
    contact_sheet(rendered)
    print("contact_sheet.png")


if __name__ == "__main__":
    main()
