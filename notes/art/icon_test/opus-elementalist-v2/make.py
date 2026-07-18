"""Generate 32x32 skill-node icons for the Elementalist constellation
(bake-off round two — re-rendering the round-one winner to the settled style
standard).

Same pipeline as notes/art/make_node_icons.py and the neighbouring bake-off
sets (opus-marksman, opus-slayer, sonnet-wizard, sonnet-priest): every icon
STARTS FROM A REAL VANILLA SPRITE pulled straight from the client jar
(so palettes match the game for free), recoloured / cropped / composed on a
transparent 32x32 canvas, with a handful of hand-plotted accent pixels on top
carrying the grammar the earlier rounds settled on:

    +            = more (damage / duration / capacity)
    -            = less (mana cost)
    down-chevrons = slowed
    crack        = bonus damage vs a state (slowed / frozen)
    ghost copy   = a duplicate
    speed dashes = faster / in flight

The flame's two edges are the two elements — fire warm (fire_charge, blaze
powder/rod, flint, campfire, magma) up the left, ice cool (snowball, powder
snow, ice blocks, a frost-blue trident) up the right — so the set reads as one
constellation without any fully hand-plotted comets, orbs or arrows (the
round-one look the author flagged as "stands out from all the other 8 trees").

FOCUSED_MIND is not designed here: it is the unified mana-regen icon shared
across every Seeker tree, copied byte-for-byte from the Priest's devotion.png.

Usage: python3 make.py
"""
import os
import shutil
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD_ASSETS = ("/Users/german-mac-mini/repos/mc-modding/archetypes/"
              "src/main/resources/assets/archetypes")
MOD_TEXTURES = os.path.join(MOD_ASSETS, "textures")
# The shared mana-regen icon every Seeker tree uses for its +mana/s node.
DEVOTION_SRC = os.path.join(
    MOD_TEXTURES, "node/test/sonnet/priest/devotion.png")

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")
SIZE = 32

# --------------------------------------------------------------- palette ----
# Fire — sampled warm from blaze_powder / fire_charge.
FIRE = (255, 150, 30, 255)
FIRE_LIGHT = (255, 214, 90, 255)
FIRE_DARK = (176, 64, 16, 255)
EMBER = (255, 104, 24, 255)

# Ice / slowed — the cold blue-grey chevron the Slayer set uses for "slowed".
SLOW = (128, 196, 224, 255)
SLOW_DARK = (58, 118, 152, 255)
ICE_LIGHT = (206, 240, 255, 255)

# Mana — sampled from the mod's own mana_orb_full.png (matches devotion).
MANA_MID = (45, 110, 230, 255)
MANA_LIGHT = (140, 190, 255, 255)
MANA_DARK = (24, 62, 160, 255)

WHITE = (255, 255, 255, 255)
GOLD = (255, 214, 90, 255)
RED = (208, 40, 32, 255)
OUTLINE = (26, 24, 30, 255)


# ----------------------------------------------------------- sprite loading -
def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def mod_tex(path):
    return Image.open(os.path.join(MOD_TEXTURES, path)).convert("RGBA").copy()


def first_frame(im):
    """Vanilla animated block/particle textures are vertical strips; keep the
    top 16x16 frame."""
    return im.crop((0, 0, 16, 16))


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def item2x(im):
    """A 16px sprite upscaled 2x NEAREST to fill the 32px canvas."""
    return im.resize((im.width * 2, im.height * 2), Image.NEAREST)


def scale(im, factor):
    return im.resize((max(1, round(im.width * factor)),
                      max(1, round(im.height * factor))), Image.NEAREST)


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def tint(im, rf, gf, bf):
    """Multiply each channel and clamp — turns a grey/white vanilla sprite
    into an elemental recolour while keeping its exact shading and shape."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (min(255, int(r * rf)),
                            min(255, int(g * gf)),
                            min(255, int(b * bf)), a)
    return out


def paste(base, im, x, y):
    base.alpha_composite(im, (x, y))


def paste_centered(base, im, cx=16, cy=16):
    base.alpha_composite(im, (cx - im.width // 2, cy - im.height // 2))


# -------------------------------------------------------------- accents -----
def blot(im, x, y, color, w=2, h=2):
    for dx in range(w):
        for dy in range(h):
            px, py = x + dx, y + dy
            if 0 <= px < SIZE and 0 <= py < SIZE:
                im.putpixel((px, py), color)


def _cells_plus(cx, cy, arm):
    yield (cx, cy)
    for i in range(1, arm + 1):
        yield (cx, cy - i * 2)
        yield (cx, cy + i * 2)
        yield (cx - i * 2, cy)
        yield (cx + i * 2, cy)


def _cells_minus(cx, cy, arm):
    yield (cx, cy)
    for i in range(1, arm + 1):
        yield (cx - i * 2, cy)
        yield (cx + i * 2, cy)


def plus(im, cx, cy, color, arm=2, outline=OUTLINE):
    cells = list(_cells_plus(cx, cy, arm))
    if outline:
        for x, y in cells:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    blot(im, x + dx, y + dy, outline)
    for x, y in cells:
        blot(im, x, y, color)


def minus(im, cx, cy, color, arm=2, outline=OUTLINE):
    cells = list(_cells_minus(cx, cy, arm))
    if outline:
        for x, y in cells:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    blot(im, x + dx, y + dy, outline)
    for x, y in cells:
        blot(im, x, y, color)


def down_chevron(im, cx, cy, c=SLOW, dark=SLOW_DARK):
    """A '\\/' chevron pointing down, on the 32px grid (2px strokes). The
    'slowed' read, straight from the Slayer set."""
    for dx in (-2, 0, 2):
        y = cy + (2 - abs(dx))
        blot(im, cx + dx, y, c)
        blot(im, cx + dx, y + 2, dark)


def speed_dash(im, x, y, length, c=WHITE, dim=SLOW):
    """A horizontal motion streak, brightest at its leading (right) end."""
    for i in range(0, length, 2):
        blot(im, x + i, y, c if i >= length - 4 else dim)


def spark(im, x, y, color, light=WHITE):
    """A small 4-point impact spark: a bright core with four stubby arms."""
    blot(im, x, y, light)
    for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        blot(im, x + dx, y + dy, color)


def crack(im, verts, color=WHITE, outline=OUTLINE):
    """A clean branching fracture: a connected polyline stepped pixel by
    pixel, dark-outlined first so it cuts through a busy sprite, bright core
    on top. `verts` is a list of segments, each a list of (x, y) vertices."""
    pts = []
    for chain in verts:
        for (x0, y0), (x1, y1) in zip(chain, chain[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0)) or 1
            for i in range(steps + 1):
                x = round(x0 + (x1 - x0) * i / steps)
                y = round(y0 + (y1 - y0) * i / steps)
                pts.append((x, y))
    for x, y in pts:
        for dx in range(-1, 3):
            for dy in range(-1, 3):
                blot(im, x + dx, y + dy, outline, 1, 1)
    for x, y in pts:
        blot(im, x, y, color)


def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# =================================================================== FIRE ===
def fireball():
    """The base cast: vanilla's own thrown fire — the fire charge — with a
    short ember trail behind it so it reads as a shot in flight, not a held
    item."""
    im = canvas()
    paste(im, item2x(vanilla("item/fire_charge.png")), 0, 0)
    for x, y, c in ((6, 24, FIRE_LIGHT), (3, 27, EMBER), (8, 27, FIRE_DARK)):
        blot(im, x, y, c)
    save(im, "fireball")


def kindling():
    """Fire spells cost less: flint-and-steel, the game's fire-starter, over a
    small mana orb struck through with a minus — cheaper fire."""
    im = canvas()
    paste(im, item2x(vanilla("item/flint_and_steel.png")), 0, 0)
    # sparks struck off the steel, so the grey tool reads as fire-making
    for x, y, c in ((12, 12, FIRE_LIGHT), (14, 10, EMBER), (10, 15, FIRE)):
        blot(im, x, y, c)
    orb = scale(mod_tex("gui/mana_orb_full.png"), 0.9)
    paste(im, orb, 16, 15)
    minus(im, 23, 22, FIRE_LIGHT, arm=2)
    save(im, "kindling")


def scorch():
    """Fire spells hit harder: a fistful of blaze powder, a '+' pip and a hot
    impact spark for the extra hearts."""
    im = canvas()
    paste(im, item2x(vanilla("item/blaze_powder.png")), 0, 0)
    plus(im, 25, 7, FIRE_LIGHT, arm=2)
    spark(im, 7, 24, EMBER, FIRE_LIGHT)
    save(im, "scorch")


def ignition():
    """Your fire burns longer: a campfire — fire that keeps burning — with a
    clock tucked in the corner for the added seconds."""
    im = canvas()
    fire = scale(vanilla("item/campfire.png"), 1.55)
    paste(im, fire, 0, 1)
    clock = scale(vanilla("item/clock_00.png"), 0.85)
    paste(im, clock, 18, 17)
    save(im, "ignition")


def vaporize():
    """Fire projectiles boil away the water they cross: a fireball skimming a
    strip of water, steam bursting up in its wake."""
    im = canvas()
    water = tint(first_frame(vanilla("block/water_still.png")), 0.45, 0.85, 1.6)
    strip = item2x(water).crop((0, 22, 32, 32))
    paste(im, strip, 0, 22)
    # the waterline
    for x in range(0, 32, 2):
        blot(im, x, 22, MANA_LIGHT if (x // 2) % 2 else MANA_MID, 2, 1)
    ball = scale(vanilla("item/fire_charge.png"), 1.0)
    paste(im, ball, 9, 5)
    for x, y, c in ((8, 4, FIRE_LIGHT), (5, 8, EMBER), (24, 7, FIRE_LIGHT)):
        blot(im, x, y, c)
    # steam boiling up off the water in little rising puffs
    for x, y in ((3, 17), (6, 20), (26, 15), (23, 19)):
        blot(im, x, y, ICE_LIGHT)
        blot(im, x, y - 2, WHITE)
        blot(im, x + 2, y - 4, WHITE, 1, 1)
    save(im, "vaporize")


def meteorite():
    """Fireball becomes a falling meteor: the fire charge up top, a flame
    trail streaking down to an impact burst on the ground."""
    im = canvas()
    ball = scale(vanilla("item/fire_charge.png"), 0.85)
    paste(im, ball, 4, 1)
    flame = vanilla("particle/flame.png")
    for i, (x, y, s) in enumerate(((11, 12, 1.4), (16, 18, 1.1), (21, 22, 0.9))):
        f = scale(flame, s)
        paste(im, faded(f, 235 - i * 30), x, y)
    # impact on the ground, lower-right
    spark(im, 25, 28, EMBER, FIRE_LIGHT)
    for x, y, c in ((21, 30, FIRE_DARK), (29, 30, FIRE_DARK), (27, 26, FIRE_LIGHT)):
        blot(im, x, y, c)
    save(im, "meteorite")


def flamethrower():
    """Fireball becomes a channel: the blaze rod as the nozzle, a widening
    cone of flame spraying from its tip."""
    im = canvas()
    paste(im, item2x(vanilla("item/blaze_rod.png")), -2, 2)
    flame = vanilla("particle/flame.png")
    jets = ((18, 6, 1.5, 255), (23, 3, 1.2, 235),
            (24, 12, 1.4, 245), (28, 8, 1.0, 220), (27, 17, 1.1, 220))
    for x, y, s, a in jets:
        paste(im, faded(scale(flame, s), a), x, y)
    save(im, "flamethrower")


# ==================================================================== ICE ===
def ice_blast():
    """The base cast: a snowball recoloured to a cold ice-blue — the freezing
    bolt — a short flight trail behind it and a slowed-chevron for the
    Slowness it carries."""
    im = canvas()
    ball = tint(vanilla("item/snowball.png"), 0.72, 0.88, 1.1)
    paste(im, item2x(ball), 0, 0)
    for x, y, c in ((5, 24, ICE_LIGHT), (2, 27, SLOW), (8, 28, SLOW_DARK)):
        blot(im, x, y, c)
    # the Slowness it carries, chevrons dropping off the trailing corner
    down_chevron(im, 26, 22)
    down_chevron(im, 26, 27)
    save(im, "ice_blast")


def chill():
    """Ice spells cost less: a plain snowball over a small mana orb with a
    minus — the ice mirror of Kindling."""
    im = canvas()
    paste(im, item2x(vanilla("item/snowball.png")), 0, 0)
    orb = scale(mod_tex("gui/mana_orb_full.png"), 0.9)
    paste(im, orb, 16, 15)
    minus(im, 23, 22, ICE_LIGHT, arm=2)
    save(im, "chill")


def frostbite():
    """Ice Blast slows harder and longer: the powder-snow bucket, a stack of
    slowed-chevrons deepening below it and a '+' for the stronger effect."""
    im = canvas()
    paste(im, item2x(vanilla("item/powder_snow_bucket.png")), 0, 0)
    down_chevron(im, 8, 20)
    down_chevron(im, 8, 25)
    plus(im, 26, 8, SLOW, arm=1)
    save(im, "frostbite")


def shatter():
    """Slowed or frozen targets take bonus damage: a block of blue ice split
    by a bright crack, a '+' for the extra hurt it pays out."""
    im = canvas()
    paste(im, item2x(vanilla("block/blue_ice.png")), 0, 0)
    # one clean forked fracture, top to bottom, with two short branches
    crack(im, [
        [(15, 1), (17, 7), (13, 13), (18, 19), (14, 25), (16, 31)],
        [(13, 13), (5, 11)],
        [(18, 19), (26, 17)],
    ])
    plus(im, 25, 6, ICE_LIGHT, arm=1)
    save(im, "shatter")


def permafrost():
    """Ice projectiles glaze the water they cross into walkable frost: a strip
    of water on the left crusting over into solid frosted ice on the right,
    with a footprint on the new crust."""
    im = canvas()
    # The mirror of Vaporize: an ice bolt skims the water and freezes a solid
    # crust in its wake — the water it passed is now walkable ice, the water
    # ahead of it still liquid.
    water = tint(first_frame(vanilla("block/water_still.png")), 0.45, 0.85, 1.6)
    frost = tint(first_frame(vanilla("block/frosted_ice_0.png")), 1.14, 1.15, 1.16)
    paste(im, item2x(water).crop((0, 20, 32, 32)), 0, 20)
    # frozen crust over the left two-thirds (the wake), a touch proud of the
    # water and topped with a bright frozen surface
    paste(im, item2x(frost).crop((0, 14, 22, 26)), 0, 15)
    for x in range(0, 22, 2):
        blot(im, x, 14, WHITE if (x // 2) % 2 else ICE_LIGHT, 2, 1)
    for x in range(22, 32, 2):
        blot(im, x, 20, MANA_LIGHT if (x // 2) % 2 else MANA_DARK, 2, 1)
    # frost crystals spreading along the freezing front
    for x, y in ((22, 17), (23, 21), (21, 24)):
        blot(im, x, y, ICE_LIGHT)
        blot(im, x + 1, y - 1, WHITE, 1, 1)
    # the ice bolt that did it, out ahead over the open water, cold trail
    ball = tint(vanilla("item/snowball.png"), 0.72, 0.88, 1.1)
    paste(im, scale(ball, 0.6), 23, 3)
    for x, y in ((21, 8), (18, 11), (15, 13)):
        blot(im, x, y, SLOW)
    save(im, "permafrost")


def glacial_spike():
    """Ice Blast becomes a lance of winter: vanilla's trident — the one polearm
    drawn as a clean diagonal spear — recoloured to hard ice-blue, a frost
    sparkle at the point for the freeze."""
    im = canvas()
    spear = tint(vanilla("item/trident.png"), 0.55, 0.9, 1.25)
    paste(im, item2x(spear), 0, 0)
    spark(im, 24, 7, SLOW, ICE_LIGHT)
    for x, y in ((10, 22), (14, 18)):
        blot(im, x, y, ICE_LIGHT)
    save(im, "glacial_spike")


def blizzard():
    """Ice Blast becomes a called storm: a bank of snow across the ground with
    sleet raking down onto it at a slant."""
    im = canvas()
    snow = item2x(vanilla("block/snow.png")).crop((0, 20, 32, 32))
    paste(im, snow, 0, 20)
    # a low snow mound, brighter along its lip
    for x, y in ((10, 19), (12, 18), (14, 18), (16, 17), (18, 18), (20, 18), (22, 19)):
        blot(im, x, y, WHITE)
    for x in range(0, 32, 2):
        blot(im, x, 21, ICE_LIGHT if (x // 2) % 2 else SLOW, 2, 1)
    # sleet raking down at a slant, bright head then a cold streaking tail
    for sx, sy in ((7, 0), (14, 3), (21, 0), (28, 4)):
        blot(im, sx, sy, WHITE)
        blot(im, sx - 2, sy + 3, WHITE)
        blot(im, sx - 4, sy + 6, SLOW)
        blot(im, sx - 6, sy + 9, SLOW_DARK)
    save(im, "blizzard")


# ================================================================== CROWN ===
def spellweaver():
    """The crown of cheaper casting: an enchanted book over a mana orb with a
    minus — every spell, less mana."""
    im = canvas()
    paste(im, item2x(vanilla("item/enchanted_book.png")), 0, 0)
    orb = scale(mod_tex("gui/mana_orb_full.png"), 0.85)
    paste(im, orb, 17, 17)
    minus(im, 24, 23, MANA_LIGHT, arm=2)
    save(im, "spellweaver")


def arcane_power():
    """The crown of harder casting: the nether star — the tree's keystone — a
    '+' for the extra damage, one warm mote and one cold mote so it reads as
    both elements at once."""
    im = canvas()
    paste(im, item2x(vanilla("item/nether_star.png")), 0, 0)
    plus(im, 26, 6, WHITE, arm=1)
    spark(im, 6, 24, EMBER, FIRE_LIGHT)
    spark(im, 26, 25, SLOW, ICE_LIGHT)
    save(im, "arcane_power")


# =============================================================== FOCUSED ====
def focused_mind():
    """Not designed here: the unified +mana/s icon every Seeker tree shares,
    copied byte-for-byte from the Priest's devotion.png."""
    shutil.copyfile(DEVOTION_SRC, os.path.join(ICONS, "focused_mind.png"))
    print("focused_mind.png (copied from priest/devotion.png)")


# ---------------------------------------------------------------------------
ORDER = [
    ("fireball", fireball),
    ("ice_blast", ice_blast),
    ("focused_mind", focused_mind),
    ("kindling", kindling),
    ("scorch", scorch),
    ("ignition", ignition),
    ("vaporize", vaporize),
    ("chill", chill),
    ("frostbite", frostbite),
    ("shatter", shatter),
    ("permafrost", permafrost),
    ("meteorite", meteorite),
    ("flamethrower", flamethrower),
    ("glacial_spike", glacial_spike),
    ("blizzard", blizzard),
    ("spellweaver", spellweaver),
    ("arcane_power", arcane_power),
]


def contact_sheet():
    cols = 6
    rows = (len(ORDER) + cols - 1) // cols
    factor = 4
    icon_px = SIZE * factor
    pad_top, pad_mid, text_h, pad_bottom, pad_side = 10, 8, 18, 12, 10
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
        label = name.replace("_", " ")
        tw = draw.textlength(label, font=font)
        draw.text((cx + (cell_w - tw) / 2, oy + icon_px + pad_mid), label,
                  fill=(235, 235, 235, 255), font=font)
    sheet.convert("RGB").save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(ICONS, exist_ok=True)
    for _, fn in ORDER:
        fn()
    contact_sheet()


if __name__ == "__main__":
    main()
