"""Generate 32x32 skill-node icons for the Crusher constellation (bake-off
test, round three).

Same pipeline as notes/art/make_node_icons.py: vanilla sprites pulled
straight from the client jar (palettes match the game for free), composed on
a transparent 32x32 canvas at 2x NEAREST, with a small readable mechanic
grammar laid on top in a few hand-plotted pixels. Every icon starts from a
real sprite; nothing is built out of pure hand-plotted pixels alone.

The tree is a mace (see CrusherNodes.java's own comment): the handle (shared
bruiser passives, either weapon) runs mace-only accents, the left flange
(bare fists) runs fist-only accents, the right flange (mace) runs mace +
corner-overlay accents matching the game's own existing Family-enum pattern,
and the crown is vanilla's own absorption heart.

One icon per family, MINOR skipped.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

SIZE = 32

WHITE = (255, 255, 255, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)

IRON = (200, 200, 208, 255)
IRON_DARK = (120, 120, 128, 255)
IRON_LIGHT = (238, 238, 244, 255)

# Sampled straight off the vanilla player skin's forearm (steve.png, the
# short-sleeve default) -- a real in-game skin tone, not an invented one.
SKIN = (170, 114, 89, 255)
SKIN_MID = (155, 99, 73, 255)
SKIN_DARK = (144, 89, 63, 255)
SKIN_OUTLINE = (64, 40, 28, 255)

STONE = (130, 130, 130, 255)
STONE_DARK = (80, 80, 80, 255)
STONE_LIGHT = (170, 170, 170, 255)
CRACK = (40, 38, 40, 255)

GOLD = (222, 171, 63, 255)
GOLD_LIGHT = (250, 216, 120, 255)

SLOW = (150, 130, 170, 255)
SLOW_DIM = (110, 96, 128, 200)

PLUS = (255, 244, 190, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def tint(im, colour):
    """Recolour every opaque pixel to `colour`, keeping the source alpha --
    turns a sprite into a flat silhouette for ghost echoes."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (colour[0], colour[1], colour[2], a)
    return out


def canvas(size=SIZE):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def mace_2x():
    return vanilla("item/mace.png").resize((32, 32), Image.NEAREST)


def chestplate_2x():
    return vanilla("item/iron_chestplate.png").resize((32, 32), Image.NEAREST)


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# --------------------------------------------------------------------------
# Shared grammar helpers
# --------------------------------------------------------------------------

def speed_dashes(im, x0, y0, dim_first=True):
    """Three motion dashes trailing left-to-right, bash_overlay's own
    grammar: 'faster'."""
    rows = ((0, ARC_DIM if dim_first else ARC), (5, ARC), (10, ARC_DIM))
    for dy, colour in rows:
        length = 5 if colour == ARC else 7
        for i in range(length):
            put(im, x0 + i, y0 + dy, colour)


def down_chevrons(im, x0, y0):
    """Two small downward chevrons -- 'slowed'."""
    for cx, cy in ((x0, y0), (x0 + 6, y0 + 2)):
        for dx, dy in ((-2, 0), (-1, 1), (0, 2), (1, 1), (2, 0)):
            put(im, cx + dx, cy + dy, SLOW if dy < 2 else SLOW_DIM)


def plus(im, x, y, colour=PLUS):
    for dx, dy in ((0, -2), (0, -1), (0, 0), (0, 1), (0, 2),
                   (-2, 0), (-1, 0), (1, 0), (2, 0)):
        put(im, x + dx, y + dy, colour)


def fist(im, x0, y0, scale=1):
    """A clenched fist, built from real vanilla skin-tone values (sampled
    off the player skin's bare forearm) laid out as a knuckle silhouette --
    dark outline, mid fill, top-left highlight, two knuckle-ridge shadows."""
    outline = [
        (3, 0), (4, 0), (5, 0), (6, 0),
        (2, 1), (7, 1),
        (1, 2), (8, 2),
        (1, 3), (8, 3),
        (1, 4), (8, 4),
        (1, 5), (8, 5),
        (2, 6), (7, 6),
        (2, 7), (3, 7), (4, 7), (5, 7), (6, 7), (7, 7),
        (2, 8), (7, 8),
        (2, 9), (7, 9),
    ]
    fill = [
        (3, 1), (4, 1), (5, 1), (6, 1),
        (2, 2), (3, 2), (4, 2), (5, 2), (6, 2), (7, 2),
        (2, 3), (3, 3), (4, 3), (5, 3), (6, 3), (7, 3),
        (2, 4), (3, 4), (4, 4), (5, 4), (6, 4), (7, 4),
        (2, 5), (3, 5), (4, 5), (5, 5), (6, 5), (7, 5),
        (3, 6), (4, 6), (5, 6), (6, 6),
        (3, 8), (4, 8), (5, 8), (6, 8),
    ]
    dark = [(3, 3), (4, 4), (6, 3), (7, 4), (3, 5), (6, 5)]
    light = [(3, 1), (2, 2), (2, 3)]
    for dx, dy in outline:
        for sx in range(scale):
            for sy in range(scale):
                put(im, x0 + dx * scale + sx, y0 + dy * scale + sy, SKIN_OUTLINE)
    for dx, dy in fill:
        for sx in range(scale):
            for sy in range(scale):
                put(im, x0 + dx * scale + sx, y0 + dy * scale + sy, SKIN_MID)
    for dx, dy in dark:
        for sx in range(scale):
            for sy in range(scale):
                put(im, x0 + dx * scale + sx, y0 + dy * scale + sy, SKIN_DARK)
    for dx, dy in light:
        for sx in range(scale):
            for sy in range(scale):
                put(im, x0 + dx * scale + sx, y0 + dy * scale + sy, SKIN)


def ring(im, cx, cy, r, colour):
    """A thin pixel ring (not filled) at radius r."""
    import math
    seen = set()
    steps = max(24, r * 8)
    for i in range(steps):
        a = 2 * math.pi * i / steps
        x = round(cx + r * math.cos(a))
        y = round(cy + r * math.sin(a) * 0.55)  # squashed for a "ground" ring
        if (x, y) not in seen:
            seen.add((x, y))
            put(im, x, y, colour)


# --------------------------------------------------------------------------
# HANDLE -- shared passives, mace as the constellation's own symbol
# --------------------------------------------------------------------------

def adrenaline():
    """Landing a hit grants attack speed. The mace, with the house's own
    'faster' grammar -- three speed dashes -- trailing off its swing."""
    im = canvas()
    speed_dashes(im, 0, 6)
    im.alpha_composite(mace_2x(), (4, 0))
    save(im, "adrenaline")


def sunder():
    """Blows part armor like Breach. Iron chestplate with the mace's head
    punched clean through a jagged hole -- the house's 'armor ignored'
    grammar (a punched hole), same as the Marksman sheet's piercing_tips."""
    im = canvas()
    plate = chestplate_2x()
    px = plate.load()
    hole = [(14, 14), (15, 14), (16, 14), (17, 14), (18, 14),
            (14, 15), (15, 15), (16, 15), (17, 15), (18, 15), (19, 15),
            (13, 16), (14, 16), (15, 16), (16, 16), (17, 16), (18, 16), (19, 16),
            (14, 17), (15, 17), (16, 17), (17, 17), (18, 17),
            (15, 18), (16, 18), (17, 18)]
    for x, y in hole:
        px[x, y] = (0, 0, 0, 0)
    im.alpha_composite(plate, (0, 0))
    # Crack lines radiating from the hole.
    for x, y in ((12, 12), (11, 10), (20, 12), (21, 10), (12, 20), (11, 22),
                 (20, 20), (21, 22)):
        put(im, x, y, CRACK)
    mace_head = vanilla("item/mace.png").crop((3, 0, 13, 8)).resize((20, 16), Image.NEAREST)
    im.alpha_composite(mace_head, (6, 9))
    save(im, "sunder")


# --------------------------------------------------------------------------
# LEFT FLANGE -- bare fists
# --------------------------------------------------------------------------

def bare_knuckle():
    """Unarmed damage up per rank. The fist alone, with the house's own
    'more' grammar -- a plus -- at the corner."""
    im = canvas()
    fist(im, 7, 8, scale=2)
    plus(im, 27, 6)
    save(im, "bare_knuckle")


def iron_skin():
    """+armor/+toughness while bare-handed -- skin like plate. The intact
    iron chestplate (no hole this time -- contrast with Sunder), a fist
    peeking out of its collar to keep the 'bare-handed' reading honest, plus
    the same 'more' grammar for the per-rank stacking."""
    im = canvas()
    im.alpha_composite(chestplate_2x(), (0, 0))
    fist(im, 11, 3, scale=1)
    plus(im, 27, 24)
    save(im, "iron_skin")


def haymaker():
    """Capstone: one enormous punch -- 4x damage, Slowness VI, Knockback II
    send-off. A bigger fist at impact, a starburst on contact, the house's
    'slowed' chevrons on the target side, and a knockback streak pushing
    away from the blow."""
    im = canvas()
    fist(im, 9, 8, scale=2)
    # Impact starburst at the knuckles.
    cx, cy = 25, 14
    for dx, dy, c in ((0, -3, WHITE), (0, 3, WHITE), (-3, 0, WHITE), (3, 0, WHITE),
                      (-2, -2, ARC), (2, -2, ARC), (-2, 2, ARC), (2, 2, ARC),
                      (0, 0, WHITE)):
        put(im, cx + dx, cy + dy, c)
    down_chevrons(im, 23, 21)
    # Knockback streak, opposite the punch.
    for i, x in enumerate(range(2, 8)):
        put(im, x, 25, ARC_DIM if i % 2 else ARC)
    save(im, "haymaker")


# --------------------------------------------------------------------------
# RIGHT FLANGE -- the mace
# --------------------------------------------------------------------------

def meteor():
    """Bonus damage per block fallen, per rank, like Density -- the falling
    star pun from the game's own canon overlay. Mace plus a fire charge
    riding its shoulder, with a falling motion streak above it."""
    im = canvas()
    im.alpha_composite(mace_2x(), (0, 0))
    fc = vanilla("item/fire_charge.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(fc, (17, 0))
    for x, y, c in ((22, 15, ARC), (23, 16, ARC), (24, 17, ARC_DIM),
                    (25, 18, ARC), (26, 19, ARC_DIM)):
        put(im, x, y, c)
    save(im, "meteor")


def shockwave():
    """A falling mace blow rings outward -- full damage within 2/4 blocks.
    The mace with two concentric ground-rings under its head."""
    im = canvas()
    im.alpha_composite(mace_2x(), (0, 0))
    ring(im, 16, 27, 6, ARC)
    ring(im, 16, 27, 10, ARC_DIM)
    save(im, "shockwave")


def earth_shatter():
    """A missed Quake vents into the ground: cooldown refund, blocks up to
    stone hardness shatter around you. The mace grounded in a burst of
    shattered stone rubble."""
    im = canvas()
    im.alpha_composite(mace_2x(), (0, 0))
    rubble = [
        (2, 27, STONE_LIGHT), (3, 28, STONE), (4, 27, STONE_DARK),
        (24, 26, STONE), (26, 28, STONE_LIGHT), (27, 26, STONE_DARK),
        (6, 30, STONE_DARK), (22, 30, STONE), (28, 30, STONE_DARK),
        (9, 29, STONE_LIGHT), (19, 29, STONE),
    ]
    for x, y, c in rubble:
        put(im, x, y, c)
    for x, y in ((14, 29), (16, 30), (18, 29), (12, 30)):
        put(im, x, y, CRACK)
    save(im, "earth_shatter")


def quake():
    """Capstone: plant, raise, slam -- hostiles go skyward. The mace raised
    high, a wide crack splitting the ground beneath it, rubble thrown
    upward -- the house's launched-airborne read."""
    im = canvas()
    raised = vanilla("item/mace.png").rotate(-25, resample=Image.NEAREST, expand=False)
    im.alpha_composite(raised.resize((32, 32), Image.NEAREST), (0, -4))
    for x in range(2, 30):
        put(im, x, 29, STONE_DARK)
    for x, y in ((5, 28), (10, 30), (16, 28), (22, 30), (27, 28)):
        put(im, x, y, CRACK)
    # Rubble launched skyward.
    for x, y, c in ((4, 22, STONE_LIGHT), (7, 18, STONE), (25, 20, STONE),
                    (28, 24, STONE_LIGHT), (3, 15, STONE_DARK)):
        put(im, x, y, c)
    save(im, "quake")


# --------------------------------------------------------------------------
# CROWN -- either weapon
# --------------------------------------------------------------------------

def battle_trance():
    """Every hit banks temporary health, up to a heart per rank. Vanilla's
    own absorption heart, upscaled, with a couple of gold heart-shard
    sparkles -- the game's own particle for gaining absorption -- to read
    as 'banking', not just 'full'."""
    im = canvas()
    heart = vanilla("gui/sprites/hud/heart/absorbing_full.png").resize((26, 26), Image.NEAREST)
    im.alpha_composite(heart, (3, 3))
    shard = vanilla("particle/goldheart_1.png").resize((8, 7), Image.NEAREST)
    im.alpha_composite(shard, (23, 22))
    shard2 = vanilla("particle/goldheart_0.png").resize((6, 5), Image.NEAREST)
    im.alpha_composite(shard2, (1, 24))
    save(im, "battle_trance")


# --------------------------------------------------------------------------

ORDER = [
    ("adrenaline", adrenaline),
    ("sunder", sunder),
    ("bare_knuckle", bare_knuckle),
    ("iron_skin", iron_skin),
    ("haymaker", haymaker),
    ("meteor", meteor),
    ("shockwave", shockwave),
    ("earth_shatter", earth_shatter),
    ("quake", quake),
    ("battle_trance", battle_trance),
]


def contact_sheet():
    cols = 5
    rows = (len(ORDER) + cols - 1) // cols
    scale = 4
    icon_px = SIZE * scale
    pad_top, pad_mid, text_h, pad_bottom, pad_side = 10, 8, 18, 10, 10
    cell_w = icon_px + pad_side * 2
    cell_h = pad_top + icon_px + pad_mid + text_h + pad_bottom
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype(
            "/System/Library/Fonts/Supplemental/Arial.ttf", 16)
    except OSError:
        font = ImageFont.load_default()
    for i, (name, _) in enumerate(ORDER):
        im = Image.open(os.path.join(ICONS, f"{name}.png")).convert("RGBA")
        big = im.resize((icon_px, icon_px), Image.NEAREST)
        cx = (i % cols) * cell_w
        cy = (i // cols) * cell_h
        ox = cx + (cell_w - icon_px) // 2
        oy = cy + pad_top
        sheet.alpha_composite(big, (ox, oy))
        tw = draw.textlength(name, font=font)
        draw.text((cx + (cell_w - tw) / 2, oy + icon_px + pad_mid), name,
                   fill=(255, 255, 255, 255), font=font)
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(ICONS, exist_ok=True)
    for _, fn in ORDER:
        fn()
    contact_sheet()


if __name__ == "__main__":
    main()
