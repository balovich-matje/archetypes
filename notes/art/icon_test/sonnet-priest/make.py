"""Generate 32x32 skill-node icons for the Priest constellation (bake-off test).

Same pipeline as notes/art/make_node_icons.py: vanilla sprites pulled straight
from the client jar (palettes match the game for free), composed on a
transparent 32x32 canvas at 2x NEAREST, with a few hand-plotted accent
pixels. One icon per family, MINOR skipped.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD_TEXTURES = ("/Users/german-mac-mini/repos/mc-modding/archetypes/"
                "src/main/resources/assets/archetypes/textures")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

SIZE = 32

# Gold light palette (the Priest's own light, not vanilla glowstone-yellow —
# a touch warmer/whiter so it reads as "holy" rather than "redstone lamp").
GOLD = (255, 214, 90, 255)
GOLD_LIGHT = (255, 245, 200, 255)
GOLD_DARK = (168, 120, 32, 255)
WHITE = (255, 255, 255, 255)

BONE = (228, 224, 205, 255)
BONE_DARK = (168, 160, 138, 255)
SOCKET = (35, 28, 22, 255)

MANA_BLUE = (60, 140, 235, 255)
MANA_DARK = (18, 40, 82, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def mod_tex(path):
    return Image.open(os.path.join(MOD_TEXTURES, path)).convert("RGBA").copy()


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def item2x(path):
    """A 16x16 vanilla item/block sprite upscaled 2x NEAREST to fill the
    32px canvas — see greatsword_item() in make_node_icons.py."""
    return vanilla(path).resize((SIZE, SIZE), Image.NEAREST)


def block2x(x0, y0, im16):
    """Paste a 16x16 sprite 2x-scaled at a given top-left in 32-space."""
    return im16.resize((im16.width * 2, im16.height * 2), Image.NEAREST), (x0, y0)


def blot(im, x, y, color, w=2, h=2):
    """A single hand-plotted 'pixel' drawn as a wxh block so it stays crisp
    against the 2x-scaled sprites around it."""
    for dx in range(w):
        for dy in range(h):
            px, py = x + dx, y + dy
            if 0 <= px < SIZE and 0 <= py < SIZE:
                im.putpixel((px, py), color)


def plus(im, cx, cy, color, arm=2, thick=2):
    """A '+' centred at (cx, cy), arms `arm` blocks long, each block
    `thick`px — the house-style "more" glyph."""
    blot(im, cx - thick // 2, cy - thick // 2, color, thick, thick)
    for i in range(1, arm + 1):
        blot(im, cx - thick // 2, cy - thick // 2 - i * thick, color, thick, thick)
        blot(im, cx - thick // 2, cy - thick // 2 + i * thick, color, thick, thick)
        blot(im, cx - thick // 2 - i * thick, cy - thick // 2, color, thick, thick)
        blot(im, cx - thick // 2 + i * thick, cy - thick // 2, color, thick, thick)


def minus(im, cx, cy, color, arm=2, thick=2):
    blot(im, cx - thick // 2, cy - thick // 2, color, thick, thick)
    for i in range(1, arm + 1):
        blot(im, cx - thick // 2 - i * thick, cy - thick // 2, color, thick, thick)
        blot(im, cx - thick // 2 + i * thick, cy - thick // 2, color, thick, thick)


def skull(im, x, y, scale=2):
    """A tiny bone-white skull glyph (8x7 logical px, drawn at `scale`) —
    the undead marker reused across Wrath/Immolation/Judgement."""
    rows = [
        "..BBBB..",
        ".BBBBBB.",
        "BBoBBoBB",
        "BBoBBoBB",
        "BBBBBBBB",
        ".B.BB.B.",
        "..B..B..",
    ]
    for ry, row in enumerate(rows):
        for rx, c in enumerate(row):
            if c == ".":
                continue
            colour = SOCKET if c == "o" else (BONE_DARK if (rx in (0, 7) or ry == 6) else BONE)
            blot(im, x + rx * scale, y + ry * scale, colour, scale, scale)


def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# ---------------------------------------------------------------------------
# HOLY_LIGHT — the base cast: a lob of light that shatters on impact.
def holy_light():
    im = canvas()
    im.alpha_composite(item2x("item/glowstone_dust.png"), (0, 0))
    # A sunburst of rays exploding off the dust — "shatters on impact."
    rays = ((16, 1), (16, 30), (1, 16), (30, 16),
            (5, 5), (26, 5), (5, 26), (26, 26))
    for x, y in rays:
        blot(im, x - 1, y - 1, GOLD_LIGHT)
    for x, y in ((16, 5), (16, 26), (5, 16), (26, 16)):
        blot(im, x - 1, y - 1, GOLD)
    save(im, "holy_light")


# ---------------------------------------------------------------------------
# LUMEN — +1 heal AND +1 undead damage per rank: the same light, stronger.
def lumen():
    im = canvas()
    block = vanilla("block/glowstone.png").resize((22, 22), Image.NEAREST)
    im.alpha_composite(block, (5, 5))
    plus(im, 27, 5, GOLD_LIGHT, arm=1, thick=2)
    save(im, "lumen")


# ---------------------------------------------------------------------------
# GRACE — Holy Light costs 10 less mana.
def grace():
    im = canvas()
    sugar = item2x("item/sugar.png")
    im.alpha_composite(faded(sugar, 255), (0, 0))
    orb = mod_tex("gui/mana_orb_full.png").resize((18, 18), Image.NEAREST)
    im.alpha_composite(orb, (0, 12))
    minus(im, 24, 21, GOLD_LIGHT, arm=2, thick=2)
    save(im, "grace")


# ---------------------------------------------------------------------------
# RADIANCE — the burst reaches 1.5 blocks further.
def radiance():
    im = canvas()
    block = vanilla("block/sea_lantern.png").crop((0, 0, 16, 16))
    block = block.resize((22, 22), Image.NEAREST)
    im.alpha_composite(block, (5, 5))
    # Rays punching straight out from the lantern to the frame's edge —
    # the light's reach stretching past where it used to stop.
    for x in range(0, 6):
        blot(im, x, 14, GOLD_LIGHT, 2, 2)
    for x in range(26, 32):
        blot(im, x, 14, GOLD_LIGHT, 2, 2)
    for y in range(0, 6):
        blot(im, 14, y, GOLD_LIGHT, 2, 2)
    for y in range(26, 32):
        blot(im, 14, y, GOLD_LIGHT, 2, 2)
    save(im, "radiance")


# ---------------------------------------------------------------------------
# DEVOTION — +0.5 mana regeneration per second.
def devotion():
    im = canvas()
    im.alpha_composite(item2x("item/lapis_lazuli.png"), (0, 0))
    orb = mod_tex("gui/mana_orb_full.png").resize((16, 16), Image.NEAREST)
    im.alpha_composite(orb, (16, 16))
    # A little regen tick climbing above the orb.
    for i, (x, y) in enumerate(((25, 14), (23, 11), (25, 8))):
        blot(im, x, y, GOLD_LIGHT if i == 2 else GOLD, 2, 2)
    save(im, "devotion")


# ---------------------------------------------------------------------------
# FERVENT_CAST — the lob flies half again faster and flatter.
def fervent_cast():
    im = canvas()
    feather = item2x("item/feather.png")
    im.alpha_composite(feather, (2, 0))
    for i, y in enumerate((10, 17, 24)):
        length = 3 - (i % 2)
        for j in range(length):
            x = 6 - j * 2
            if x >= 0:
                blot(im, x, y, WHITE if j == 0 else GOLD_LIGHT, 2, 2)
    save(im, "fervent_cast")


# ---------------------------------------------------------------------------
# MERCY — Holy Light healing improved, the heal side only.
def mercy():
    im = canvas()
    im.alpha_composite(item2x("item/glistering_melon_slice.png"), (0, 0))
    heart = vanilla("gui/sprites/hud/heart/full.png")
    im.alpha_composite(heart, (22, 0))
    save(im, "mercy")


# ---------------------------------------------------------------------------
# WRATH — Holy Light deals more damage to undead, the harm side only.
def wrath():
    im = canvas()
    im.alpha_composite(item2x("item/blaze_powder.png"), (0, 0))
    skull(im, 16, 15, scale=2)
    save(im, "wrath")


# ---------------------------------------------------------------------------
# RENEWAL — capstone: Holy Light also grants Regeneration.
def renewal():
    im = canvas()
    im.alpha_composite(item2x("item/ghast_tear.png"), (0, 0))
    im.alpha_composite(vanilla("mob_effect/regeneration.png"), (13, -1))
    save(im, "renewal")


# ---------------------------------------------------------------------------
# BENEDICTION — capstone: Holy Light grants a random blessing (one of four).
def benediction():
    im = canvas()
    im.alpha_composite(item2x("item/golden_apple.png"), (0, 0))
    # Four tiny pips sampled from the four vanilla buff icons — "one of these."
    pips = (
        ((255, 255, 255, 255)),  # Speed
        ((190, 60, 30, 255)),    # Strength
        ((150, 130, 200, 255)),  # Resistance
        ((235, 140, 40, 255)),   # Fire Resistance
    )
    coords = ((22, 20), (26, 20), (22, 24), (26, 24))
    for (x, y), c in zip(coords, pips):
        blot(im, x, y, c, 2, 2)
    blot(im, 20, 18, GOLD_DARK, 8, 8)
    for (x, y), c in zip(coords, pips):
        blot(im, x, y, c, 2, 2)
    save(im, "benediction")


# ---------------------------------------------------------------------------
# BEACON — halo junction: +25 max mana.
def beacon():
    im = canvas()
    orb = mod_tex("gui/mana_orb_full.png").resize((24, 24), Image.NEAREST)
    im.alpha_composite(orb, (2, 4))
    plus(im, 26, 8, GOLD_LIGHT, arm=2, thick=2)
    save(im, "beacon")


# ---------------------------------------------------------------------------
# IMMOLATION — Holy Light sets undead enemies ablaze.
def immolation():
    im = canvas()
    skull(im, 8, 15, scale=2)
    # Ordinary orange fire (not soul fire's blue) — unmistakably "burning."
    flame = vanilla("particle/flame.png").resize((16, 16), Image.NEAREST)
    im.alpha_composite(faded(flame, 210), (2, -3))
    im.alpha_composite(flame, (13, -5))
    im.alpha_composite(faded(flame, 200), (20, -1))
    save(im, "immolation")


# ---------------------------------------------------------------------------
# AEGIS — casting shells YOU with absorption.
def aegis():
    im = canvas()
    face = vanilla("entity/shield/shield_base_nopattern.png").crop((0, 0, 13, 23))
    face = face.resize((face.width * 2, face.height * 2), Image.NEAREST)
    im.alpha_composite(face, (4, 4))
    heart = vanilla("gui/sprites/hud/heart/absorbing_full.png")
    heart = heart.resize((14, 14), Image.NEAREST)
    im.alpha_composite(heart, (9, 9))
    save(im, "aegis")


# ---------------------------------------------------------------------------
# SANCTUARY — casting shells FRIENDLY TARGETS with absorption (plural).
def sanctuary():
    im = canvas()
    face = vanilla("entity/shield/shield_base_nopattern.png").crop((0, 0, 13, 23))
    face = face.resize((int(face.width * 1.3), int(face.height * 1.3)), Image.NEAREST)
    left = faded(face, 235)
    right = faded(face, 235)
    im.alpha_composite(left, (0, 6))
    im.alpha_composite(right, (15, 6))
    heart = vanilla("gui/sprites/hud/heart/absorbing_full.png")
    im.alpha_composite(heart, (2, 10))
    im.alpha_composite(heart, (21, 10))
    save(im, "sanctuary")


# ---------------------------------------------------------------------------
PURPLE = (137, 96, 178, 255)
PURPLE_DARK = (86, 58, 120, 255)


# JUDGEMENT — Holy Light applies Weakness to undead enemies.
def judgement():
    im = canvas()
    im.alpha_composite(item2x("item/fermented_spider_eye.png"), (0, 0))
    skull(im, 16, 14, scale=2)
    # A little debuff arrow under the skull — weakness pulling it down.
    for i, y in enumerate((26, 28, 30)):
        w = 6 - i * 2
        blot(im, 20 - w // 2, y, PURPLE if i < 2 else PURPLE_DARK, w, 2)
    save(im, "judgement")


# ---------------------------------------------------------------------------
# ASCENDANT — final capstone: healing and smiting both 25% stronger.
def ascendant():
    im = canvas()
    im.alpha_composite(item2x("item/end_crystal.png"), (0, 0))
    heart = vanilla("gui/sprites/hud/heart/full.png")
    im.alpha_composite(heart, (0, 22))
    skull(im, 22, 23, scale=1)
    for x, y in ((16, 2), (26, 6), (6, 8)):
        blot(im, x, y, GOLD_LIGHT, 1, 1)
    save(im, "ascendant")


ORDER = [
    ("holy_light", holy_light),
    ("lumen", lumen),
    ("grace", grace),
    ("radiance", radiance),
    ("devotion", devotion),
    ("fervent_cast", fervent_cast),
    ("mercy", mercy),
    ("wrath", wrath),
    ("renewal", renewal),
    ("benediction", benediction),
    ("beacon", beacon),
    ("immolation", immolation),
    ("aegis", aegis),
    ("sanctuary", sanctuary),
    ("judgement", judgement),
    ("ascendant", ascendant),
]


def contact_sheet():
    cols = 4
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
