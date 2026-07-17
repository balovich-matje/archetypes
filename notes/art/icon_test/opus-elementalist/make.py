"""Generate 32x32 skill-node icons for the Elementalist tree.

House style (see ../../make_node_icons.py): vanilla sprites pulled straight
from the client jar so palettes match the game, composed on a transparent
canvas, upscaled 2x NEAREST for the crisp blocky look, with a few hand-plotted
effect pixels. One idea per icon; the silhouette should telegraph the mechanic.

Design grammar for this tree:
  * WARM palette  -> fire branch,  COOL palette -> ice branch,
    mana-blue/white -> element-agnostic nodes. The tree's two edges read as
    two colours before you read a single word.
  * A round fiery orb (fire_charge) is the fire ACTIVE; an angular ice shard is
    the ice ACTIVE. Passive element markers are a flame-lick and a snowflake.
  * red up-arrow  = more damage
    mana-orb + down-arrow = cheaper cast
    clock = longer duration
    cracked frozen cube = bonus vs slowed/frozen

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD_ASSETS = ("/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/"
              "assets/archetypes/textures")
OUT = os.path.join(HERE, "icons")

# ---------------------------------------------------------------- palettes ---
# Fire: white-hot -> yellow -> orange -> red -> deep, dark outline.
FW = (255, 249, 214, 255)
FY = (255, 214, 88, 255)
FO = (255, 148, 40, 255)
FR = (230, 84, 24, 255)
FD = (150, 44, 14, 255)
FOUT = (58, 18, 6, 255)

# Ice: white -> light -> mid -> blue -> dark, dark-blue outline.
IW = (236, 251, 255, 255)
IL = (180, 232, 251, 255)
IM = (120, 200, 242, 255)
IB = (70, 156, 222, 255)
ID = (38, 106, 182, 255)
IOUT = (16, 54, 104, 255)

# Mana (matches the mod's orb) and neutral whites/darks.
MB = (58, 120, 214, 255)
MBL = (128, 182, 242, 255)
MBD = (30, 74, 166, 255)
WHITE = (245, 247, 252, 255)
DARK = (26, 28, 36, 255)

# Water and gold (clock).
WA = (58, 118, 206, 255)
WAD = (36, 86, 168, 255)
WAL = (110, 168, 232, 255)
GOLD = (244, 210, 96, 255)
GOLDD = (168, 126, 40, 255)

# Damage arrow (red) and cost/less arrow (pale).
DMG = (236, 66, 40, 255)
DMGL = (255, 148, 120, 255)


# --------------------------------------------------------------- utilities ---
def vanilla(path):
    """A vanilla texture, animated strips cropped to the first square frame."""
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            im = Image.open(f).convert("RGBA").copy()
    if im.height > im.width:            # animated vertical strip
        im = im.crop((0, 0, im.width, im.width))
    return im


def mod(path):
    return Image.open(os.path.join(MOD_ASSETS, path)).convert("RGBA").copy()


def scale(im, s):
    return im.resize((s, s), Image.NEAREST)


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def layer():
    im = canvas()
    return im, ImageDraw.Draw(im)


def outlined(lyr, col=DARK):
    """Return a copy of a hand-drawn layer with an 8-neighbour dark border, so
    the glyph reads on light OR dark ground the way vanilla sprites do."""
    out = lyr.copy()
    src = lyr.load()
    dst = out.load()
    w, h = lyr.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            hit = False
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 128:
                        hit = True
            if hit:
                dst[x, y] = col
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
    if 0 <= x < 32 and 0 <= y < 32:
        im.putpixel((x, y), c)


# ------------------------------------------------------------------ glyphs ---
def flame_stamp(size):
    """A lick of fire from the vanilla flame particle, scaled crisp."""
    return scale(vanilla("particle/flame.png"), size)


def snowflake(cx, cy, r, col=IL, hot=IW):
    """A tidy six-fork snowflake, returned as an outlined layer."""
    lyr, d = layer()
    # straight axes
    for a in range(-r, r + 1):
        put(lyr, cx + a, cy, col)
        put(lyr, cx, cy + a, col)
    # diagonal axes, a touch shorter
    dr = r - 1
    for a in range(-dr, dr + 1):
        put(lyr, cx + a, cy + a, col)
        put(lyr, cx + a, cy - a, col)
    # little forks near the tips of the straight axes
    for f in (r - 2, r):
        put(lyr, cx - 1, cy - f + 1, col); put(lyr, cx + 1, cy - f + 1, col)
        put(lyr, cx - 1, cy + f - 1, col); put(lyr, cx + 1, cy + f - 1, col)
        put(lyr, cx - f + 1, cy - 1, col); put(lyr, cx - f + 1, cy + 1, col)
        put(lyr, cx + f - 1, cy - 1, col); put(lyr, cx + f - 1, cy + 1, col)
    # hot core
    put(lyr, cx, cy, hot)
    put(lyr, cx + 1, cy, hot); put(lyr, cx - 1, cy, hot)
    put(lyr, cx, cy + 1, hot); put(lyr, cx, cy - 1, hot)
    return lyr


def arrow(cx, tipy, h, w, col, light, up=True):
    """A fat vertical arrow whose tip is at (cx, tipy); the body extends AWAY
    from the tip (down for an up-arrow, up for a down-arrow) so it never runs
    off the canvas past its tip. Returned as an outlined layer."""
    lyr, d = layer()
    sw = max(1, w // 2)
    if up:
        base = tipy + (h // 2)                 # head base sits below the tip
        d.polygon([(cx, tipy), (cx - w, base), (cx + w, base)], fill=col)
        d.rectangle([cx - sw, base, cx + sw, tipy + h], fill=col)
        far = tipy + h
    else:
        base = tipy - (h // 2)                 # head base sits above the tip
        d.polygon([(cx, tipy), (cx - w, base), (cx + w, base)], fill=col)
        d.rectangle([cx - sw, tipy - h, cx + sw, base], fill=col)
        far = tipy - h
    for y in range(min(tipy, far), max(tipy, far) + 1):
        put(lyr, cx, y, light)                 # lit spine
    put(lyr, cx, tipy, light)
    return lyr


def plus(cx, cy, r, col=WHITE):
    lyr, d = layer()
    d.rectangle([cx - r, cy - 1, cx + r, cy + 1], fill=col)
    d.rectangle([cx - 1, cy - r, cx + 1, cy + r], fill=col)
    return lyr


def mana_orb(size=15):
    return scale(mod("gui/mana_orb_full.png"), size)


def down_cost(cx, cy):
    """A small pale down-arrow — the 'less' half of the cost glyph."""
    return arrow(cx, cy, 8, 3, WHITE, MBL, up=False)


def impact(im, cx, cy, col, big=1):
    """A radiating impact star of loose pixels."""
    for r in range(1, 3 + big):
        put(im, cx + r + 1, cy, col); put(im, cx - r - 1, cy, col)
        put(im, cx, cy + r + 1, col); put(im, cx, cy - r - 1, col)
    for r in range(1, 2 + big):
        put(im, cx + r, cy + r, col); put(im, cx - r, cy - r, col)
        put(im, cx + r, cy - r, col); put(im, cx - r, cy + r, col)


def iso_ice_cube(tex="block/blue_ice.png"):
    """A quarter-size isometric ice block, inventory-cube style."""
    t = vanilla(tex)
    cube = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top = t.rotate(45, expand=True).resize((16, 8), Image.NEAREST)
    cube.alpha_composite(top, (0, 0))
    for face, x0, shade in ((0, 0, 150), (1, 8, 205)):
        for x in range(8):
            sx = x * 2
            dy = (x if face == 0 else 7 - x) // 2
            for y in range(8):
                r, g, b, a = t.getpixel((sx, y * 2))
                if a:
                    py = 4 + dy + y
                    if py < 16:
                        cube.putpixel((x0 + x, py),
                                      (r * shade // 255, g * shade // 255, b * shade // 255, 255))
    return cube


def water_band(im, top, bottom, col=WA, dark=WAD, light=WAL):
    """Fill rows [top, bottom] with water, a lit wave line along the top."""
    for y in range(top, bottom + 1):
        for x in range(32):
            put(im, x, y, col if (y - top) % 3 else dark)
    for x in range(0, 32, 2):
        put(im, x, top, light)
        put(im, x + 1, top - 1 if (x // 2) % 2 else top, light)


def save(im, name):
    im.save(os.path.join(OUT, f"{name}.png"))
    print(f"{name}.png")


# ================================================================= ICONS ====
def fireball():
    """The signature hurl: fire_charge orb with a shrinking flame trail behind
    it — a fireball in flight."""
    im = canvas()
    for (x, y, s) in ((2, 2, 9), (7, 6, 12)):
        im.alpha_composite(faded(flame_stamp(s), 200), (x, y))
    im.alpha_composite(scale(vanilla("item/fire_charge.png"), 22), (9, 9))
    save(im, "fireball")


def ice_blast():
    """The mirror hurl: a sharp ice shard in flight, frost sparkling behind.
    Round fire vs angular ice, so the two doors read as opposites."""
    im = canvas()
    lyr, d = layer()
    # a four-point crystal bolt travelling down-right
    cx, cy = 20, 20
    d.polygon([(28, 28), (cx + 4, cy - 2), (cx - 6, cy - 6), (cx - 2, cy + 4)], fill=IM)
    d.line([(cx - 6, cy - 6), (28, 28)], fill=IW)          # lit spine
    d.line([(cx - 2, cy + 4), (cx + 4, cy - 2)], fill=ID)  # shadow side
    im.alpha_composite(outlined(lyr, IOUT))
    for (x, y) in ((7, 7), (11, 10), (10, 13)):            # frost trail
        put(im, x, y, IL)
    put(im, 8, 9, IW)
    save(im, "ice_blast")


def focused_mind():
    """Mana orb welling up: a '+' and rising sparkles. Neutral blue — it feeds
    either element."""
    im = canvas()
    im.alpha_composite(mana_orb(17), (7, 9))
    im.alpha_composite(outlined(plus(24, 8, 3, WHITE)))
    for (x, y) in ((6, 6), (13, 3), (26, 18)):             # rising motes
        put(im, x, y, MBL)
    save(im, "focused_mind")


def kindling():
    """Fire costs less: a flame beside a mana orb with a down-arrow."""
    im = canvas()
    im.alpha_composite(scale(vanilla("particle/flame.png"), 16), (1, 6))
    im.alpha_composite(mana_orb(13), (17, 8))
    im.alpha_composite(outlined(down_cost(23, 22)))
    save(im, "kindling")


def scorch():
    """Fire hits harder: a flame under a bold red damage-arrow."""
    im = canvas()
    im.alpha_composite(scale(vanilla("particle/flame.png"), 18), (7, 13))
    im.alpha_composite(outlined(arrow(16, 1, 12, 5, DMG, DMGL, up=True)))
    save(im, "scorch")


def ignition():
    """Fire burns longer: a flame beside a clock."""
    im = canvas()
    im.alpha_composite(scale(vanilla("particle/flame.png"), 17), (1, 8))
    lyr, d = layer()
    cx, cy, r = 22, 20, 7
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=GOLD, width=2)
    d.line([(cx, cy), (cx, cy - r + 2)], fill=GOLD)        # hour hand
    d.line([(cx, cy), (cx + r - 3, cy)], fill=GOLD)        # minute hand
    put(lyr, cx, cy, WHITE)
    im.alpha_composite(outlined(lyr))
    save(im, "ignition")


def vaporize():
    """Fire boils water to steam: a fireball skims a water strip and white
    steam plumes rise where it passed."""
    im = canvas()
    water_band(im, 25, 31)
    # steam plumes: vanilla smoke recoloured to bright white, rising tall
    for (x, y, s, a) in ((13, 0, 12, 235), (19, 3, 13, 255), (8, 5, 11, 210), (24, 6, 10, 200)):
        steam = scale(vanilla("particle/big_smoke_0.png"), s)
        px = steam.load()
        for yy in range(steam.height):
            for xx in range(steam.width):
                r, g, b, al = px[xx, yy]
                if al:
                    px[xx, yy] = (242, 246, 250, al * a // 255)
        im.alpha_composite(steam, (x, y))
    # the fireball skimming the surface, with a short flame lick trailing it
    im.alpha_composite(faded(scale(vanilla("particle/flame.png"), 12), 220), (2, 15))
    im.alpha_composite(scale(vanilla("item/fire_charge.png"), 15), (10, 16))
    save(im, "vaporize")


def chill():
    """Ice costs less: a snowflake beside a mana orb with a down-arrow.
    Mirrors Kindling."""
    im = canvas()
    im.alpha_composite(outlined(snowflake(8, 15, 7), IOUT))
    im.alpha_composite(mana_orb(13), (17, 8))
    im.alpha_composite(outlined(down_cost(23, 22)))
    save(im, "chill")


def frostbite():
    """Ice slows harder and longer: a snowflake gripping a target that sinks
    under stacked down-chevrons."""
    im = canvas()
    im.alpha_composite(outlined(snowflake(11, 12, 8), IOUT))
    lyr, d = layer()
    for i, y in enumerate((19, 23, 27)):                   # slow chevrons
        c = IM if i else IL
        d.line([(16, y), (21, y + 4), (26, y)], fill=c, width=2)
    im.alpha_composite(outlined(lyr, IOUT))
    save(im, "frostbite")


def shatter():
    """Bonus vs frozen: a frozen ice cube split by a crack and an impact
    burst — the classic cracked-thing-for-bonus."""
    im = canvas()
    im.alpha_composite(scale(iso_ice_cube(), 26), (3, 4))
    # a solid jagged crack splitting the cube, with a lit right lip
    crack = ((15, 5), (17, 9), (14, 12), (17, 16), (15, 19), (18, 23), (16, 26))
    clyr, cd = layer()
    cd.line(crack, fill=DARK, width=1)
    im.alpha_composite(clyr)
    for (x, y) in crack:
        put(im, x + 1, y, IW)                  # frosted lip along the split
    impact(im, 16, 13, IW, big=2)
    put(im, 16, 13, WHITE)
    save(im, "shatter")


def permafrost():
    """Ice glazes water to a walkable sheet: water frozen over into a slab of
    frosted ice, frost crystals on top."""
    im = canvas()
    water_band(im, 22, 31)
    ice = scale(vanilla("block/blue_ice.png"), 32)
    # keep only the top slab so water shows at the base
    im.alpha_composite(ice.crop((0, 0, 32, 20)), (0, 4))
    for (x, y) in ((6, 3), (14, 6), (22, 3), (26, 8), (10, 9)):   # frost sparkle
        put(im, x, y, IW); put(im, x + 1, y, IL)
    for x in range(0, 32, 3):                                     # frozen rim
        put(im, x, 23, IL)
    save(im, "permafrost")


def meteorite():
    """The crater capstone: a flaming ball plunging on a fire trail into a
    burst impact ring below."""
    im = canvas()
    # trail streaking down from top-right
    for (x, y, s, a) in ((25, 0, 8, 150), (21, 4, 11, 190), (16, 9, 14, 230)):
        im.alpha_composite(faded(flame_stamp(s), a), (x, y))
    im.alpha_composite(scale(vanilla("block/magma.png"), 15), (11, 12))
    im.alpha_composite(scale(vanilla("item/fire_charge.png"), 15), (11, 12))
    # impact at the bottom: dark crater lip + flung debris + shock arc
    for x in range(6, 26):
        put(im, x, 30, FD)
        put(im, x, 31, FOUT)
    for (x, y) in ((7, 28), (11, 27), (16, 27), (21, 27), (25, 28)):
        put(im, x, y, FO)
    for (x, y) in ((4, 29), (27, 29), (9, 26), (23, 26)):
        put(im, x, y, FY)
    save(im, "meteorite")


def flamethrower():
    """The channel capstone: a blaze-rod nozzle spraying an expanding cone of
    fire up-right."""
    im = canvas()
    im.alpha_composite(scale(vanilla("item/blaze_rod.png"), 20), (-2, 12))
    # cone of flame widening toward the top-right corner
    cone = ((11, 15, 8, 220), (16, 10, 11, 235), (21, 5, 13, 255), (24, 14, 10, 220))
    for (x, y, s, a) in cone:
        im.alpha_composite(faded(flame_stamp(s), a), (x, y))
    save(im, "flamethrower")


def glacial_spike():
    """The lance capstone: one great faceted spear of ice, far bigger than the
    Ice Blast bolt, frost at its foot."""
    im = canvas()
    lyr, d = layer()
    # a long lance from lower-left to upper-right
    d.polygon([(29, 2), (20, 9), (3, 27), (9, 29), (26, 11)], fill=IM)
    d.line([(29, 2), (6, 28)], fill=IW)                   # lit spine
    d.line([(20, 9), (3, 27)], fill=IL)
    d.line([(26, 11), (9, 29)], fill=ID)                  # shadow edge
    im.alpha_composite(outlined(lyr, IOUT))
    for (x, y) in ((4, 30), (8, 30), (12, 29), (2, 27)):  # frozen foot
        put(im, x, y, IW)
    put(im, 28, 3, WHITE)                                 # tip glint
    save(im, "glacial_spike")


def blizzard():
    """The storm capstone: a swarm of snowflakes driven on slanted wind
    streaks — many flakes, so it reads as a storm not a single spell."""
    im = canvas()
    d = ImageDraw.Draw(im)
    for x in range(-6, 34, 5):                            # driving wind
        d.line([(x, 3), (x + 9, 12)], fill=(188, 222, 244, 150))
        d.line([(x + 2, 14), (x + 11, 23)], fill=(188, 222, 244, 150))
        d.line([(x, 24), (x + 9, 33)], fill=(188, 222, 244, 150))
    for (cx, cy, r) in ((7, 8, 5), (22, 6, 4), (25, 20, 5), (12, 23, 4), (16, 14, 6)):
        im.alpha_composite(outlined(snowflake(cx, cy, r), IOUT))
    save(im, "blizzard")


def glacial_spike_bg():
    pass


def spellweaver():
    """The cheap-cast crown: an enchanted book (all your magic) with a mana orb
    and a down-arrow — every spell costs less."""
    im = canvas()
    im.alpha_composite(scale(vanilla("item/enchanted_book.png"), 22), (2, 4))
    im.alpha_composite(mana_orb(12), (18, 17))
    im.alpha_composite(outlined(down_cost(24, 30)))
    save(im, "spellweaver")


def arcane_power():
    """The raw-power crown: a nether star with a bold red damage-arrow — every
    spell hits harder."""
    im = canvas()
    im.alpha_composite(scale(vanilla("item/nether_star.png"), 22), (1, 6))
    im.alpha_composite(outlined(arrow(24, 2, 12, 5, DMG, DMGL, up=True)))
    save(im, "arcane_power")


ICONS = [
    ("fireball", fireball), ("ice_blast", ice_blast), ("focused_mind", focused_mind),
    ("kindling", kindling), ("scorch", scorch), ("ignition", ignition),
    ("vaporize", vaporize), ("chill", chill), ("frostbite", frostbite),
    ("shatter", shatter), ("permafrost", permafrost), ("meteorite", meteorite),
    ("flamethrower", flamethrower), ("glacial_spike", glacial_spike),
    ("blizzard", blizzard), ("spellweaver", spellweaver), ("arcane_power", arcane_power),
]


def contact_sheet():
    from PIL import ImageDraw as D
    S = 32 * 4
    pad = 14
    labelh = 16
    cols = 5
    rows = (len(ICONS) + cols - 1) // cols
    W = cols * (S + pad) + pad
    H = rows * (S + labelh + pad) + pad
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    d = D.Draw(sheet)
    for i, (name, _) in enumerate(ICONS):
        r, c = divmod(i, cols)
        x = pad + c * (S + pad)
        y = pad + r * (S + labelh + pad)
        icon = Image.open(os.path.join(OUT, f"{name}.png")).convert("RGBA")
        sheet.alpha_composite(scale(icon, S), (x, y))
        d.text((x, y + S + 3), name, fill=(226, 226, 226, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(OUT, exist_ok=True)
    for _, fn in ICONS:
        fn()
    contact_sheet()


if __name__ == "__main__":
    main()
