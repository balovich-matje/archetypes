"""Skill-node icons for the Crusher tree (maces and bare fists).

Every icon starts from a real vanilla sprite pulled from the client jar —
recolored, cropped, composed — with a small readable mechanic grammar plotted
on top (+ = more, speed dashes = faster, rings = area, chevrons = up/down,
cracks = sundered/shattered). Palettes come from the game for free. The one
hand-plotted object is the fist: vanilla has no fist sprite, so it is built
from the player skin's own skin-tone palette, item-outlined like iron_spikes
(a canon Brawler icon) — the mace path uses the real mace item throughout.

Composed at 16px (vanilla item resolution), saved 2x NEAREST -> 32px.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

# ---- palettes -------------------------------------------------------------
IRON = (200, 200, 208, 255)
IRON_DARK = (120, 120, 128, 255)
IRON_SHADOW = (84, 84, 92, 255)
IRON_LIGHT = (238, 238, 244, 255)
IRON_OUT = (44, 44, 50, 255)

SKIN_L = (200, 142, 112, 255)
SKIN_M = (170, 114, 89, 255)
SKIN_D = (150, 99, 73, 255)
SKIN_SH = (120, 78, 58, 255)
SKIN_OUT = (58, 36, 26, 255)
SKIN_NAIL = (224, 182, 152, 255)

WHITE = (238, 238, 238, 255)
DIM = (150, 150, 150, 220)
GOLD = (255, 208, 74, 255)
GOLD_D = (206, 150, 24, 255)
FLASH = (255, 240, 176, 255)
FIRE = (255, 150, 34, 255)
FIRE_L = (255, 214, 120, 255)
STONE_BRK = (60, 60, 66, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def canvas(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


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
        im.putpixel((x, y), c)


# ---- the fist -------------------------------------------------------------
# A raised clenched fist, front view, four knuckle grooves and a wrapped
# thumb. Vanilla has no fist sprite, so it is plotted from the player skin's
# own skin-tone palette (or iron, for Iron Skin) and item-outlined.
FIST_ROWS = {
    3: (4, 11), 4: (4, 11), 5: (4, 11),
    6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12),
    10: (4, 11), 11: (5, 10), 12: (5, 10),
}


def fist(pal="skin"):
    if pal == "iron":
        L, M, D, SH, OUT, NAIL = IRON_LIGHT, IRON, IRON_DARK, IRON_SHADOW, IRON_OUT, IRON_LIGHT
    else:
        L, M, D, SH, OUT, NAIL = SKIN_L, SKIN_M, SKIN_D, SKIN_SH, SKIN_OUT, SKIN_NAIL
    im = canvas()
    fill = {}
    for y, (x0, x1) in FIST_ROWS.items():
        for x in range(x0, x1 + 1):
            if y <= 4:
                c = L
            elif y >= 10:
                c = D
            elif x <= x0 + 1:
                c = SH
            elif x >= x1 - 1:
                c = L
            else:
                c = M
            fill[(x, y)] = c
    # knuckle grooves between the four fingers
    for gx in (5, 7, 9):
        for gy in (3, 4):
            fill[(gx, gy)] = SH
    # the fold where fingers meet the back of the hand
    for x in range(4, 12):
        fill[(x, 5)] = SH
    # knuckle-top highlights
    for kx in (4, 6, 8, 10):
        fill[(kx, 3)] = NAIL
    # the thumb, wrapped across the near side
    for x in range(3, 7):
        fill[(x, 7)] = L
    fill[(3, 7)] = M
    fill[(6, 7)] = SH
    # commit + auto-outline
    for (x, y), c in fill.items():
        im.putpixel((x, y), c)
    out = set()
    for (x, y) in fill:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = (x + dx, y + dy)
                if 0 <= n[0] < 16 and 0 <= n[1] < 16 and n not in fill:
                    out.add(n)
    for (x, y) in out:
        im.putpixel((x, y), OUT)
    return im


# ---- grammar marks --------------------------------------------------------
def plus(im, cx, cy, c, arm=2, edge=None):
    """A plus sign: +more."""
    if edge:
        for dx in range(-arm - 1, arm + 2):
            put(im, cx + dx, cy - 1, edge)
            put(im, cx + dx, cy + 1, edge)
        for dy in range(-arm - 1, arm + 2):
            put(im, cx - 1, cy + dy, edge)
            put(im, cx + 1, cy + dy, edge)
    for d in range(-arm, arm + 1):
        put(im, cx + d, cy, c)
        put(im, cx, cy + d, c)


def speed_dashes(im, rows, x0, length, c=WHITE, c_dim=DIM):
    for i, y in enumerate(rows):
        for x in range(x0, x0 + length):
            put(im, x, y, c if x >= x0 + length - 2 else c_dim)


def chevrons(im, cx, ys, c, down=True, span=2):
    """Stacked chevrons: up = launch, down = slow/fall."""
    for y in ys:
        for d in range(span + 1):
            dy = d if down else -d
            put(im, cx - d, y + dy, c)
            put(im, cx + d, y + dy, c)


def ring(im, cx, cy, r, c):
    """A thin circular ring by midpoint sampling."""
    steps = max(8, int(2 * 3.1416 * r))
    import math
    for i in range(steps):
        a = 2 * math.pi * i / steps
        x = round(cx + r * math.cos(a))
        y = round(cy + r * math.sin(a))
        put(im, x, y, c)


def impact_star(im, cx, cy):
    """A bright radiating burst — the hit landing."""
    put(im, cx, cy, FLASH)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        put(im, cx + dx, cy + dy, FLASH)
    for dx, dy in ((0, -3), (0, -2), (0, 2), (0, 3), (-3, 0), (-2, 0), (2, 0), (3, 0),
                   (-2, -2), (2, -2), (-2, 2), (2, 2)):
        put(im, cx + dx, cy + dy, WHITE)


def iso_block(texture):
    """Quarter-size isometric inventory cube from a block texture."""
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


def save(im, name):
    im.resize((32, 32), Image.NEAREST).save(os.path.join(ICONS, f"{name}.png"))
    return im


# ===========================================================================
#  the icons
# ===========================================================================
def adrenaline():
    """Attack speed on every hit: the mace, speed dashes streaming off the
    head — the same swing coming faster."""
    im = canvas()
    im.alpha_composite(vanilla("item/mace.png"))
    # trailing dashes behind the head (upper-left), motion = faster
    speed_dashes(im, (1, 3, 5), 0, 5)
    return save(im, "adrenaline")


def sunder():
    """Armor parted like Breach: the iron chestplate split by a breach crack,
    plate chips knocked loose."""
    im = canvas()
    im.alpha_composite(vanilla("item/iron_chestplate.png"))
    crack = (40, 40, 46, 255)
    edge = IRON_LIGHT
    for x, y in ((8, 3), (8, 4), (9, 5), (8, 6), (9, 7), (8, 8), (9, 9), (8, 10), (8, 11)):
        put(im, x, y, crack)
        put(im, x - 1, y, edge)
    # chips flying off to the right
    for x, y, c in ((12, 4, IRON), (13, 3, IRON_DARK), (12, 9, IRON), (14, 8, IRON_DARK),
                    (13, 11, IRON)):
        put(im, x, y, c)
    return save(im, "sunder")


def bare_knuckle():
    """Unarmed damage up: the bare fist, a gold plus — more punch."""
    im = fist("skin")
    plus(im, 12, 4, GOLD, arm=2, edge=GOLD_D)
    return save(im, "bare_knuckle")


def iron_skin():
    """Skin like plate: the same fist cast in iron, a defensive armor emblem
    tucked behind it — +armor, +toughness while unarmed."""
    im = canvas()
    emblem = vanilla("gui/sprites/hud/armor_full.png").resize((8, 8), Image.NEAREST)
    im.alpha_composite(emblem, (8, 0))
    im.alpha_composite(fist("iron"))
    plus(im, 13, 11, IRON_LIGHT, arm=1, edge=IRON_OUT)
    return save(im, "iron_skin")


def haymaker():
    """Capstone. One enormous punch: the fist driven into a bright impact
    star, knockback streaming off the blow."""
    im = canvas()
    f = fist("skin")
    im.alpha_composite(f, (-3, 1))
    impact_star(im, 12, 7)
    # knockback dashes flying off the strike
    for y in (5, 9):
        for x in (14, 15):
            put(im, x, y, WHITE)
    return save(im, "haymaker")


def meteor():
    """Mace smashes fall harder: the mace wreathed in fire on its head,
    downward motion — bonus damage per block fallen."""
    im = canvas()
    mace = vanilla("item/mace.png")
    im.alpha_composite(mace)
    # fire riding the head (upper-right)
    fc = vanilla("item/fire_charge.png").resize((9, 9), Image.NEAREST)
    im.alpha_composite(faded(fc, 235), (7, -1))
    # re-lay the head edge over the flame so the mace still reads
    put(im, 11, 6, FIRE_L)
    put(im, 12, 5, FIRE)
    # downward fall dashes under the head
    for i, y in enumerate((10, 12, 14)):
        put(im, 12 - i, y, FIRE_L if i == 2 else FIRE)
    return save(im, "meteor")


def shockwave():
    """A falling blow rings outward: the mace head, concentric shock rings
    spreading from the point of impact."""
    im = canvas()
    ring(im, 8, 9, 6.2, DIM)
    ring(im, 8, 9, 4.2, WHITE)
    mace = vanilla("item/mace.png")
    im.alpha_composite(mace)
    # bright impact where the head lands
    put(im, 11, 4, FLASH)
    return save(im, "shockwave")


def earth_shatter():
    """A Quake vented into the ground: the mace over a stone block bursting
    apart, chips flying, a clock for the cooldown paid back."""
    im = canvas()
    block = iso_block("stone")
    im.alpha_composite(block, (2, 3))
    # the mace, small, driving down onto it
    mace = vanilla("item/mace.png").resize((12, 12), Image.NEAREST)
    im.alpha_composite(mace, (2, -2))
    # shatter chips bursting off the block
    for x, y, c in ((2, 8, STONE_BRK), (1, 10, (150, 150, 156, 255)),
                    (14, 9, STONE_BRK), (15, 11, (150, 150, 156, 255)),
                    (7, 15, STONE_BRK)):
        put(im, x, y, c)
    # refund clock, lower-left corner
    clock = vanilla("item/clock_00.png").resize((7, 7), Image.NEAREST)
    im.alpha_composite(clock, (0, 9))
    return save(im, "earth_shatter")


def quake():
    """Capstone. Plant, raise, slam: the mace over cracked ground, hostiles
    launched skyward on rising chevrons."""
    im = canvas()
    # cracked ground line across the base
    ground = (110, 78, 46, 255)
    ground_d = (74, 52, 30, 255)
    for x in range(0, 16):
        put(im, x, 14, ground)
        put(im, x, 15, ground_d)
    for x in (3, 8, 12):
        put(im, x, 14, ground_d)
        put(im, x, 13, ground_d)
    # the mace, slamming straight down
    mace = vanilla("item/mace.png")
    im.alpha_composite(mace, (0, 0))
    # launch chevrons rising on both flanks — hostiles go skyward
    up = (150, 230, 255, 255)
    for cx in (2, 14):
        for j, y in enumerate((12, 9)):
            put(im, cx, y, up)
            put(im, cx - 1, y + 1, up)
            put(im, cx + 1, y + 1, up)
    return save(im, "quake")


def battle_trance():
    """Every hit banks temporary health: the gold absorption heart, a plus —
    a shield of health that grows as you fight."""
    im = canvas()
    heart = vanilla("gui/sprites/hud/heart/absorbing_full.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(heart, (1, 2))
    plus(im, 13, 4, GOLD, arm=2, edge=GOLD_D)
    return save(im, "battle_trance")


ICON_FNS = [
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


def contact_sheet(rendered):
    cols = 4
    cell = 64
    padx, pady = 22, 20
    labelh = 16
    rows = (len(rendered) + cols - 1) // cols
    W = cols * cell + (cols + 1) * padx
    H = rows * (cell + labelh) + (rows + 1) * pady
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/SFNSMono.ttf", 12)
    except Exception:
        font = ImageFont.load_default()
    for i, (name, im) in enumerate(rendered):
        r, c = divmod(i, cols)
        x = padx + c * (cell + padx)
        y = pady + r * (cell + labelh + pady)
        big = im.resize((cell, cell), Image.NEAREST)
        sheet.alpha_composite(big, (x, y))
        tb = draw.textbbox((0, 0), name, font=font)
        tw = tb[2] - tb[0]
        draw.text((x + (cell - tw) // 2, y + cell + 2), name, fill=(210, 210, 210, 255), font=font)
    sheet.save(os.path.join(HERE, "contact_sheet.png"))


def main():
    os.makedirs(ICONS, exist_ok=True)
    rendered = [(name, fn()) for name, fn in ICON_FNS]
    contact_sheet(rendered)
    print(f"{len(rendered)} icons + contact_sheet.png")


if __name__ == "__main__":
    main()
