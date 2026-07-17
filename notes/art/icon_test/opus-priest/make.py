"""Priest constellation skill-node icons (bake-off entry).

Every icon is a full standalone 32x32 PNG. The house rule: at a glance the
icon should tell the player what the node does. Vanilla sprites (pulled from
the client jar, so palettes match the game) are composed on a transparent
canvas with a few hand-plotted effect pixels.

The Priest's shared visual DNA is the Holy Light burst: a gold, white-cored
orb. Heals wear a red heart, harms wear a bone skull, protection wears a gold
absorption heart, mana math wears a blue orb. Secondary badges (a plus, an
arrow, rings, flame) carry the specific mechanic.

Usage: python3 make.py
"""
import math
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")
JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD_ASSETS = "/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/assets"

# ---------------------------------------------------------------- palettes
GOLD = dict(core=(255, 255, 238), hi=(255, 232, 150), mid=(255, 198, 70),
            dark=(216, 150, 38), edge=(140, 88, 18))
# Radiance leans to the cool sea-lantern white so it reads apart from the warm orbs.
RADI = dict(core=(240, 255, 255), hi=(198, 240, 246), mid=(150, 214, 226),
            dark=(96, 168, 190), edge=(44, 96, 120))
MANA = dict(core=(160, 206, 255), hi=(96, 160, 255), mid=(45, 110, 230),
            dark=(24, 62, 160), edge=(12, 30, 86))

BONE_HI = (240, 238, 224)
BONE = (206, 202, 178)
BONE_MID = (162, 158, 134)
BONE_DK = (108, 104, 86)
SOCKET = (34, 30, 40)

RED = (222, 40, 48)
RED_DK = (150, 20, 30)
WEAK = (150, 196, 120)
WEAK_DK = (86, 128, 66)
DARKLINE = (28, 26, 32, 255)
WHITE = (255, 255, 255, 255)

_zip = zipfile.ZipFile(JAR)


def vanilla(path):
    with _zip.open(f"assets/minecraft/textures/{path}") as f:
        return Image.open(f).convert("RGBA").copy()


def mod(path):
    return Image.open(os.path.join(MOD_ASSETS, path)).convert("RGBA").copy()


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def scaled(im, w, h=None):
    return im.resize((w, h or w), Image.NEAREST)


def put(im, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        if len(c) == 3:
            c = (c[0], c[1], c[2], 255)
        im.putpixel((x, y), c)


# ---------------------------------------------------------------- primitives
def orb(im, cx, cy, r, pal, outline=True):
    """A shaded holy/mana sphere: white-ish core lit from the upper-left,
    darkening to a rim, with a one-pixel edge line for punch on any bg."""
    lx, ly = cx - r * 0.42, cy - r * 0.42
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r - 1, cx + r + 2):
            d = math.hypot(x - cx, y - cy)
            if d <= r - 0.15:
                t = math.hypot(x - lx, y - ly) / (r * 1.65)
                if t < 0.26:
                    c = pal["core"]
                elif t < 0.52:
                    c = pal["hi"]
                elif t < 0.84:
                    c = pal["mid"]
                else:
                    c = pal["dark"]
                put(im, x, y, c)
            elif outline and d <= r + 0.75:
                put(im, x, y, pal["edge"])


def rays(im, cx, cy, r0, r1, color, n=8, phase=0.0, thick=False):
    for i in range(n):
        a = phase + i * 2 * math.pi / n
        for rr in [x * 0.5 for x in range(int(r0 * 2), int(r1 * 2))]:
            x = round(cx + math.cos(a) * rr)
            y = round(cy + math.sin(a) * rr)
            put(im, x, y, color)
            if thick:
                put(im, x + 1, y, color)


def ring(im, cx, cy, r, color, dash=False):
    steps = max(24, int(2 * math.pi * r))
    for i in range(steps):
        if dash and i % 3 == 0:
            continue
        a = 2 * math.pi * i / steps
        put(im, round(cx + math.cos(a) * r), round(cy + math.sin(a) * r), color)


def plus(im, cx, cy, arm, thick, color, outline=DARKLINE):
    def stamp(a, t, col):
        for d in range(-a, a + 1):
            for w in range(-t, t + 1):
                put(im, cx + d, cy + w, col)
                put(im, cx + w, cy + d, col)
    if outline:
        stamp(arm + 1, thick + 1, outline)
    stamp(arm, thick, color)


def minus(im, cx, cy, arm, thick, color, outline=DARKLINE):
    for d in range(-arm - 1, arm + 2):
        for w in range(-thick - 1, thick + 2):
            put(im, cx + d, cy + w, outline)
    for d in range(-arm, arm + 1):
        for w in range(-thick, thick + 1):
            put(im, cx + d, cy + w, color)


def vline(im, x, y0, y1, color):
    for y in range(y0, y1 + 1):
        put(im, x, y, color)


def arrow(im, cx, tail, head, color, outline=DARKLINE, shaft=1):
    """A crisp vertical arrow along column cx, tip at y=head (above or below
    tail). Thin shaft, clean triangular head, 1px dark outline."""
    down = head > tail
    core = set()
    stop = head - (shaft + 2) if down else head + (shaft + 2)
    lo, hi = sorted((tail, stop))
    for y in range(lo, hi + 1):
        for dx in range(-shaft, shaft + 1):
            core.add((cx + dx, y))
    hw = shaft + 2
    for i in range(hw + 1):
        yy = head - i if down else head + i
        for dx in range(-i, i + 1):
            core.add((cx + dx, yy))
    if outline:
        border = set()
        for (x, y) in core:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    p = (x + dx, y + dy)
                    if p not in core:
                        border.add(p)
        for (x, y) in border:
            put(im, x, y, outline)
    for (x, y) in core:
        put(im, x, y, color)


# ---------------------------------------------------------------- skull
_SKULL = [
    "................",
    "....MMMMMM......",
    "...MHHHHHHM.....",
    "..MHHHHHHHHM....",
    "..MHHHHHHHHM....",
    "..MHKKHHKKHM....",
    "..MHKKHHKKHM....",
    "..MHHHHHHHHM....",
    "..MHHHDDHHHM....",
    "...MHHHHHHM.....",
    "....MHHHHM......",
    "....MDHDHDM.....",
    "....MHDHDHM.....",
    ".....MMMM.......",
    "................",
    "................",
]
_SKULL_COL = {"M": BONE_MID, "H": BONE, "K": SOCKET, "D": BONE_DK}


def skull_img():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(_SKULL):
        assert len(row) == 16, f"skull row {y} len {len(row)}"
        for x, ch in enumerate(row):
            if ch != ".":
                c = _SKULL_COL[ch]
                im.putpixel((x, y), (c[0], c[1], c[2], 255))
    # a couple of bone highlights on the cranium
    for x, y in ((5, 3), (6, 3), (4, 4)):
        im.putpixel((x, y), (BONE_HI[0], BONE_HI[1], BONE_HI[2], 255))
    return im


def place(im, sprite, x, y):
    im.alpha_composite(sprite, (x, y))


# ---------------------------------------------------------------- icons
def holy_light():
    """The active: a lobbed burst of holy light. Gold white-cored orb with a
    full sunburst, and a faint arcing trail showing the lob."""
    im = canvas()
    cx, cy = 18, 15
    # lob trail: dots arcing up from lower-left into the orb
    for (x, y) in ((4, 28), (7, 25), (10, 22)):
        put(im, x, y, GOLD["mid"])
        put(im, x + 1, y, GOLD["hi"])
        put(im, x, y + 1, GOLD["dark"])
    rays(im, cx, cy, 9, 13, GOLD["hi"], n=8, phase=0.0)
    rays(im, cx, cy, 8, 11, GOLD["mid"], n=8, phase=math.pi / 8)
    orb(im, cx, cy, 8, GOLD)
    put(im, cx - 3, cy - 3, WHITE)
    save(im, "holy_light")


def lumen():
    """More light: the orb with a bold plus. Scales both heal and smite."""
    im = canvas()
    orb(im, 13, 18, 8, GOLD)
    put(im, 10, 15, WHITE)
    rays(im, 13, 18, 9, 12, GOLD["hi"], n=8)
    plus(im, 24, 8, 4, 1, GOLD["core"])
    save(im, "lumen")


def grace():
    """Cheaper cast: a mana orb marked with a big minus — costs less."""
    im = canvas()
    orb(im, 15, 17, 9, MANA)
    put(im, 11, 13, (210, 236, 255))
    minus(im, 15, 17, 5, 1, (235, 248, 255))
    # a down-chevron below to reinforce 'less'
    for i in range(3):
        put(im, 15 - (2 - i), 28 - abs(1 - i), (200, 232, 255))
        put(im, 15 + (2 - i), 28 - abs(1 - i), (200, 232, 255))
    save(im, "grace")


def devotion():
    """Mana regeneration: a mana orb with two green arrows flowing up — a
    steady per-second gain."""
    im = canvas()
    orb(im, 16, 21, 8, MANA)
    put(im, 13, 18, (210, 236, 255))
    arrow(im, 9, 13, 3, (140, 216, 150), outline=WEAK_DK + (255,), shaft=1)
    arrow(im, 23, 15, 5, (140, 216, 150), outline=WEAK_DK + (255,), shaft=1)
    save(im, "devotion")


def beacon():
    """+max mana: a big mana orb pressed to the brim of a container ring,
    with a plus. Capacity, not rate."""
    im = canvas()
    ring(im, 15, 16, 12, (150, 180, 230))
    ring(im, 15, 16, 11, (90, 130, 200))
    orb(im, 15, 16, 9, MANA)
    put(im, 12, 13, (210, 236, 255))
    plus(im, 25, 7, 3, 1, (210, 240, 255))
    save(im, "beacon")


def radiance():
    """Reaches further: a cool-white burst radiating outward in expanding
    rings — light spreading out."""
    im = canvas()
    cx, cy = 15, 16
    ring(im, cx, cy, 13, RADI["hi"], dash=True)
    ring(im, cx, cy, 10, RADI["mid"], dash=True)
    rays(im, cx, cy, 8, 14, RADI["hi"], n=4, phase=math.pi / 4)
    orb(im, cx, cy, 7, RADI)
    put(im, cx - 3, cy - 3, WHITE)
    save(im, "radiance")


def fervent_cast():
    """Flies faster and flatter: the orb streaking right on a flat line with
    hard speed-trails behind it."""
    im = canvas()
    cx, cy = 21, 16
    for (y, x0, x1, col) in ((13, 2, 12, GOLD["mid"]), (16, 1, 13, GOLD["hi"]),
                             (19, 3, 12, GOLD["mid"])):
        for x in range(x0, x1):
            put(im, x, y, col)
    orb(im, cx, cy, 7, GOLD)
    put(im, cx - 2, cy - 3, WHITE)
    # forward chevron
    for i in range(4):
        put(im, 29 - i, 16 - i, GOLD["core"])
        put(im, 29 - i, 16 + i, GOLD["core"])
    save(im, "fervent_cast")


def mercy():
    """Heal more: a full red heart with a bold plus."""
    im = canvas()
    heart = scaled(vanilla("gui/sprites/hud/heart/full.png"), 20)
    place(im, heart, 3, 7)
    plus(im, 25, 9, 4, 1, (255, 120, 130), outline=RED_DK + (255,))
    save(im, "mercy")


def wrath():
    """Harm undead more: a bone skull with a bold plus — the smite side."""
    im = canvas()
    place(im, scaled(skull_img(), 24), 1, 5)
    plus(im, 25, 8, 4, 1, (255, 96, 80), outline=RED_DK + (255,))
    save(im, "wrath")


def immolation():
    """Undead set ablaze: a skull with flame rising off the crown."""
    im = canvas()
    place(im, scaled(skull_img(), 24), 4, 8)
    flame = scaled(vanilla("particle/flame.png"), 16)
    place(im, flame, 8, 0)
    place(im, scaled(vanilla("particle/flame.png"), 10), 18, 2)
    save(im, "immolation")


def judgement():
    """Weakness on undead: a skull sapped by sickly-green down-arrows."""
    im = canvas()
    place(im, scaled(skull_img(), 22), 5, 5)
    # weakness sapping the skull: green down-arrows on each flank
    arrow(im, 6, 16, 29, WEAK, outline=WEAK_DK + (255,), shaft=1)
    arrow(im, 26, 16, 29, WEAK, outline=WEAK_DK + (255,), shaft=1)
    save(im, "judgement")


def aegis():
    """Absorb on self: a shield bearing a gold absorption heart."""
    im = canvas()
    # heater shield silhouette
    SH = (176, 182, 196)
    SH_DK = (108, 114, 130)
    SH_RIM = (230, 200, 96)
    shield = [
        "..MMMMMMMMMM..",
        ".MSSSSSSSSSSM.",
        ".MSSSSSSSSSSM.",
        ".MSSSSSSSSSSM.",
        ".MSSSSSSSSSSM.",
        ".MSSSSSSSSSSM.",
        ".MSSSSSSSSSSM.",
        "..MSSSSSSSSM..",
        "...MSSSSSSM...",
        "....MSSSSM....",
        ".....MSSM.....",
        "......MM......",
    ]
    base = Image.new("RGBA", (14, 12), (0, 0, 0, 0))
    for y, row in enumerate(shield):
        for x, ch in enumerate(row):
            if ch == "M":
                base.putpixel((x, y), SH_RIM + (255,))
            elif ch == "S":
                base.putpixel((x, y), (SH if y < 6 else SH_DK) + (255,))
    place(im, scaled(base, 28, 24), 2, 3)
    place(im, scaled(vanilla("gui/sprites/hud/heart/absorbing_full.png"), 14), 9, 7)
    save(im, "aegis")


def sanctuary():
    """Absorb on allies: a protective gold dome arcing over a pair of gold
    absorption hearts — the shell extended to friends."""
    im = canvas()
    h = scaled(vanilla("gui/sprites/hud/heart/absorbing_full.png"), 13)
    place(im, h, 3, 15)
    place(im, h, 16, 15)
    # dome arc over both
    for a_deg in range(200, 341, 3):
        a = math.radians(a_deg)
        x = round(16 + math.cos(a) * 14)
        y = round(17 + math.sin(a) * 14)
        put(im, x, y, (255, 226, 120))
        put(im, x, y - 1, (255, 244, 190))
    save(im, "sanctuary")


def renewal():
    """Capstone — Regeneration to allies: the vanilla regen heart haloed in
    gold, lifted on regen sparkles."""
    im = canvas()
    ring(im, 16, 15, 13, GOLD["hi"])
    ring(im, 16, 15, 12, GOLD["mid"])
    regen = scaled(vanilla("mob_effect/regeneration.png"), 18)
    place(im, regen, 7, 6)
    for (x, y) in ((6, 24), (16, 27), (25, 24)):
        plus(im, x, y, 1, 0, (255, 150, 200), outline=None)
    save(im, "renewal")


def benediction():
    """Capstone — a random blessing: a golden apple throwing off four buff
    sparks (speed, strength, resistance, fire-res)."""
    im = canvas()
    apple = scaled(vanilla("item/golden_apple.png"), 20)
    place(im, apple, 6, 8)
    pips = ((5, 6, (130, 190, 210)),    # speed - cyan
            (26, 6, (210, 70, 60)),     # strength - red
            (5, 25, (150, 156, 176)),   # resistance - slate
            (26, 25, (255, 150, 40)))   # fire res - orange
    for x, y, c in pips:
        plus(im, x, y, 2, 0, c)
    # a little sparkle crown
    put(im, 16, 4, (255, 250, 210))
    put(im, 16, 3, (255, 250, 210))
    save(im, "benediction")


def ascendant():
    """Top capstone — everything stronger: the Holy Light orb crowned by a
    full halo, long rays, ascending."""
    im = canvas()
    cx, cy = 16, 17
    ring(im, cx, cy - 1, 14, GOLD["hi"])
    ring(im, cx, cy - 1, 13, GOLD["mid"])
    rays(im, cx, cy, 10, 15, GOLD["hi"], n=8)
    rays(im, cx, cy, 9, 12, GOLD["core"], n=8, phase=math.pi / 8)
    orb(im, cx, cy, 8, GOLD)
    put(im, cx - 3, cy - 3, WHITE)
    # ascend: two stacked up-chevrons crowning the halo
    for cyy in (2, 5):
        for i in range(4):
            put(im, cx - i, cyy + i, (255, 250, 214))
            put(im, cx + i, cyy + i, (255, 250, 214))
    save(im, "ascendant")


# ---------------------------------------------------------------- output
def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


ORDER = [
    ("holy_light", holy_light), ("lumen", lumen), ("grace", grace),
    ("radiance", radiance), ("devotion", devotion), ("fervent_cast", fervent_cast),
    ("mercy", mercy), ("wrath", wrath), ("renewal", renewal),
    ("benediction", benediction), ("beacon", beacon), ("immolation", immolation),
    ("aegis", aegis), ("sanctuary", sanctuary), ("judgement", judgement),
    ("ascendant", ascendant),
]


def contact_sheet():
    from PIL import ImageDraw, ImageFont
    cols = 4
    rows = (len(ORDER) + cols - 1) // cols
    scale = 4
    cell = 32 * scale
    pad = 14
    labelh = 18
    cw = cell + pad * 2
    ch = cell + pad + labelh
    W, H = cols * cw, rows * ch
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None
    for i, (name, _) in enumerate(ORDER):
        r, c = divmod(i, cols)
        icon = Image.open(os.path.join(ICONS, f"{name}.png")).convert("RGBA")
        big = icon.resize((cell, cell), Image.NEAREST)
        x = c * cw + pad
        y = r * ch + pad
        sheet.alpha_composite(big, (x, y))
        tx = c * cw + cw // 2
        ty = y + cell + 3
        try:
            w = draw.textlength(name, font=font)
        except Exception:
            w = len(name) * 6
        draw.text((tx - w / 2, ty), name, fill=(230, 230, 230, 255), font=font)
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(ICONS, exist_ok=True)
    for _, fn in ORDER:
        fn()
    contact_sheet()


if __name__ == "__main__":
    main()
