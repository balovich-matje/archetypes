"""Generate 32x32 skill-node icons for the Elementalist tree (round two).

Round one (opus-elementalist) leaned on hand-plotted polygons for its
comets, faceted shards and snowflakes -- readable, but the only tree in the
game that looked hand-drawn instead of Minecraft-drawn. This pass follows
the settled house style instead (see notes/art/make_node_icons.py and the
sonnet-wizard / sonnet-priest bake-off entries): every icon STARTS FROM A
REAL VANILLA SPRITE pulled straight from the client jar -- an item icon, a
block face, or a particle -- composed on a transparent 32x32 canvas at 2x
NEAREST, with a small number of hand-plotted ACCENT PIXELS on top (a "+", a
chevron, a crack, a sparkle, a speed dash). No hand-painted comets, no
polygon-filled shards, no vector snowflakes.

Design grammar for this tree:
  * WARM palette -> fire branch, COOL palette -> ice branch, mana-blue/white
    -> element-agnostic. The two edges of the flame read as two colours
    before you read a word.
  * The active spells mirror each other: fire_charge (round, real item) for
    Fireball, a rotated ice-block diamond (angular, real block, cropped by
    the rotation itself) for Ice Blast.
  * Repeated glyphs carry the mechanic, all built from a handful of accent
    pixels, never a filled shape:
      - a small pale "-" beside the mana orb = cheaper cast
        (Kindling, Chill, Spellweaver -- the tree's three cost-down nodes)
      - a small red up-chevron = more damage (Scorch, Arcane Power)
      - a small ice-blue down-chevron = slows harder/longer (Frostbite)
      - a gold vanilla clock = longer duration (Ignition)
      - a crack through a real ice block = bonus vs. slowed/frozen (Shatter)
      - a faded, offset echo of the same sprite = motion, reused from the
        Slayer tree's own bladestorm/heavy_blows language (Fireball,
        Ice Blast, Meteorite)
  * FOCUSED_MIND is not designed here -- it is a byte-for-byte copy of the
    shared Seeker mana-regen icon (sonnet/priest/devotion.png).

Usage: python3 make.py
"""
import os
import shutil
import zipfile

from PIL import Image, ImageDraw

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
REPO_ASSETS = "/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/assets/archetypes"
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "icons")

DEVOTION_SRC = os.path.join(REPO_ASSETS, "textures/node/test/sonnet/priest/devotion.png")

# ---------------------------------------------------------------- palettes
# Mana (sampled from the mod's own gui/mana_orb_full.png -- same values the
# Wizard and Priest bake-off entries used, so every tree's mana glyph
# matches).
MANA_DARK = (24, 62, 160, 255)
MANA_MID = (45, 110, 230, 255)
MANA_LIGHT = (140, 190, 255, 255)

# Generic accents, reused from the Slayer/Wizard house style.
RED = (200, 32, 32, 255)
RED_LIGHT = (240, 96, 96, 255)
GOLD = (255, 236, 160, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)
WHITE = (255, 255, 255, 255)

# Ice accents (frost sparkle, slow-chevron) -- cool, pale, distinct from mana.
ICE_LIGHT = (215, 241, 255, 255)
ICE_MID = (150, 210, 245, 255)

# Flat water strip (Vaporize / Permafrost mirror each other across it).
WATER = (52, 108, 196, 255)
WATER_DARK = (34, 78, 158, 255)


def vanilla(path):
    """A vanilla texture, animated vertical strips cropped to the first
    16x16 frame."""
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            im = Image.open(f).convert("RGBA").copy()
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    return im


def mod_tex(path):
    return Image.open(os.path.join(REPO_ASSETS, "textures", path)).convert("RGBA")


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def item2x(im):
    """A 16px vanilla/mod sprite, upscaled 2x NEAREST to fill the 32px
    canvas -- the house style's default composition."""
    return im.resize((im.width * 2, im.height * 2), Image.NEAREST)


def scale(im, factor):
    return im.resize((max(1, round(im.width * factor)), max(1, round(im.height * factor))), Image.NEAREST)


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def recolor(im, mapping):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            c = px[x, y]
            if c in mapping:
                px[x, y] = mapping[c]
    return out


def paste_centered(base, im, cx=16, cy=16):
    base.alpha_composite(im, (cx - im.width // 2, cy - im.height // 2))


def dot(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def dots(im, pts, c):
    for x, y in pts:
        dot(im, x, y, c)


def minus(im, cx, cy, c=MANA_LIGHT, r=3):
    """The tree's cheaper-cast glyph: a plain horizontal bar."""
    for dx in range(-r, r + 1):
        dot(im, cx + dx, cy, c)
        dot(im, cx + dx, cy + 1, MANA_DARK if abs(dx) == r else c)


def up_chevron(im, cx, cy, c=RED, hi=RED_LIGHT):
    """The tree's more-damage glyph: a small '^' of loose pixels."""
    dot(im, cx, cy - 2, hi)
    dots(im, ((cx - 1, cy - 1), (cx + 1, cy - 1)), c)
    dots(im, ((cx - 2, cy), (cx + 2, cy)), c)


def down_chevron(im, cx, cy, c=(30, 90, 150, 255), hi=ICE_MID):
    """The slowed glyph: a small 'v' of loose pixels, mirrored from
    up_chevron so the two read as opposites. Darker than the frost sparkle
    accent so it still reads against pale ice/snow backgrounds."""
    dot(im, cx, cy + 2, hi)
    dots(im, ((cx - 1, cy + 1), (cx + 1, cy + 1)), c)
    dots(im, ((cx - 2, cy), (cx + 2, cy)), c)


def mana_orb(factor=1.3):
    """The mod's 9x9 mana orb, scaled by a small multiplicative factor to a
    corner-accent size (~12px at the default) -- NOT an absolute pixel size,
    matching the sonnet-wizard script's own scale() convention."""
    return scale(mod_tex("gui/mana_orb_full.png"), factor)


def ice_diamond(block_path, canvas_fill=32):
    """A block face rotated 45 degrees and scaled to fill the canvas -- the
    same 'top face becomes a diamond' trick notes/art/make_node_icons.py's
    iso_block() uses, repurposed here as a whole-canvas angular shard. The
    rotation itself (not hand-plotting) is what makes it angular, and it
    leaves the four corners transparent for accent pixels."""
    tex = vanilla(block_path)
    rot = tex.rotate(45, expand=True, resample=Image.NEAREST)
    side = max(rot.width, rot.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(rot, ((side - rot.width) // 2, (side - rot.height) // 2))
    return square.resize((canvas_fill, canvas_fill), Image.NEAREST)


def water_strip(im, top, bottom):
    """A flat two-tone water band, the same flat-colour-bars technique the
    Slayer tree's ground_slam_overlay uses for ground."""
    for y in range(top, bottom):
        c = WATER if (y - top) % 2 == 0 else WATER_DARK
        for x in range(32):
            dot(im, x, y, c)


def save(im, name):
    os.makedirs(DST, exist_ok=True)
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# --------------------------------------------------------------- families
def fireball():
    """The active: fire_charge, full frame, real vanilla item -- with a
    small trail of ember-coloured accent pixels off its lower-left margin,
    where the sprite's own transparent padding leaves room. Just thrown,
    not sitting still."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/fire_charge.png")), (0, 0))
    dots(im, ((3, 27), (5, 29), (1, 30)), (255, 150, 40, 220))
    dots(im, ((6, 26),), (255, 214, 88, 200))
    save(im, "fireball")


def ice_blast():
    """The mirror active: the ice block's own face rotated into a diamond
    -- angular where Fireball is round, the two element-doors reading as
    opposites -- with the same small motion trail, in frost instead of
    ember."""
    im = canvas()
    im.alpha_composite(ice_diamond("block/ice.png"), (0, 0))
    dots(im, ((3, 28), (5, 30), (1, 31)), ICE_MID)
    dots(im, ((6, 27),), ICE_LIGHT)
    save(im, "ice_blast")


def focused_mind():
    """Not designed here -- the exact shared Seeker mana-regen icon, copied
    byte-for-byte per the hard constraint."""
    shutil.copyfile(DEVOTION_SRC, os.path.join(DST, "focused_mind.png"))
    print("focused_mind.png (copied from sonnet/priest/devotion.png)")


def kindling():
    """Fire costs less: flint and steel (the vanilla fire-lighting tool,
    the plainest 'this is about fire' item there is), a mana orb, and the
    tree's cheaper-cast minus."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/flint_and_steel.png")), (0, 0))
    im.alpha_composite(mana_orb(), (18, 1))
    minus(im, 25, 17)
    save(im, "kindling")


def scorch():
    """Fire hits harder: blaze powder (vanilla's fire-damage ingredient)
    with the tree's more-damage chevron in the free top corner."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/blaze_powder.png")), (0, 0))
    up_chevron(im, 27, 5)
    save(im, "scorch")


def ignition():
    """Fire burns longer: the campfire item (a real flat vanilla icon, not
    a rendered block) with vanilla's own clock pinned in the corner -- the
    house style's established time glyph, straight off
    make_node_icons.py's braced_overlay."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/campfire.png")), (0, 0))
    clock = scale(vanilla("item/clock_00.png"), 0.85)
    im.alpha_composite(clock, (32 - clock.width, 0))
    save(im, "ignition")


def vaporize():
    """Fire boils water away: the sponge block (vanilla's own water-eating
    block) sitting just above a flat water strip, a few pale steam pixels
    lifting off where the two meet."""
    im = canvas()
    water_strip(im, 24, 32)
    sponge = scale(vanilla("block/sponge.png"), 1.375)
    im.alpha_composite(sponge, (4, 2))
    dots(im, ((10, 20), (14, 17), (18, 21)), (240, 244, 248, 235))
    dots(im, ((12, 14), (17, 12)), (240, 244, 248, 170))
    save(im, "vaporize")


def chill():
    """Ice costs less: a snowball (the plain round icy item, mirroring
    Kindling's plain fire tool) with a mana orb and the same minus."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/snowball.png")), (0, 0))
    im.alpha_composite(mana_orb(), (18, 1))
    minus(im, 25, 17)
    save(im, "chill")


def frostbite():
    """Ice slows harder and longer: the powder snow bucket (vanilla's own
    freeze-you-solid material) with the tree's slowed chevron stacked
    twice beneath it."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/powder_snow_bucket.png")), (0, 0))
    down_chevron(im, 25, 24)
    down_chevron(im, 25, 29)
    save(im, "frostbite")


def shatter():
    """Bonus vs. slowed/frozen: packed ice (a real block face, full
    frame -- the same 'block fills the canvas, the accent overlays it'
    composition sonnet-wizard's shatterpoint uses) split by a bold crack,
    the classic cracked-thing-for-bonus."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("block/packed_ice.png")), (0, 0))
    outline = (14, 34, 64, 255)
    crack = ((16, 3), (13, 8), (17, 13), (12, 18), (16, 23), (11, 28))
    lyr = canvas()
    d = ImageDraw.Draw(lyr)
    d.line(crack, fill=outline, width=3)
    im.alpha_composite(lyr)
    lit = canvas()
    d2 = ImageDraw.Draw(lit)
    d2.line(crack, fill=WHITE, width=1)
    im.alpha_composite(lit)
    dots(im, ((16, 3), (11, 28)), ICE_LIGHT)
    save(im, "shatter")


def permafrost():
    """Ice glazes water into a walkable sheet: frosted ice (the literal
    vanilla block this node creates) over a flat water strip, a few frost
    sparkles on top."""
    im = canvas()
    water_strip(im, 24, 32)
    ice = scale(vanilla("block/frosted_ice_3.png"), 1.5)
    im.alpha_composite(ice.crop((0, 0, 24, 24)), (4, 1))
    dots(im, ((7, 4), (18, 3), (22, 9), (9, 12)), ICE_LIGHT)
    dots(im, ((12, 7), (16, 14)), ICE_MID)
    save(im, "permafrost")


def meteorite():
    """The crater capstone: a magma block (the exact real material this
    family maps to) as a thin strip of impact ground, a fire_charge --
    this tree's own established fireball glyph, enlarged -- falling onto it
    out of open space above so the drop actually reads against the block,
    with a small faded echo for the motion."""
    im = canvas()
    magma = item2x(vanilla("block/magma.png"))
    im.alpha_composite(magma.crop((0, 23, 32, 32)), (0, 23))
    ball = scale(vanilla("item/fire_charge.png"), 1.6)
    echo = faded(ball, 120)
    im.alpha_composite(echo, (8, -4))
    im.alpha_composite(ball, (4, 1))
    dots(im, ((3, 24), (27, 23), (7, 26)), (255, 214, 88, 230))
    save(im, "meteorite")


def flamethrower():
    """The channel capstone: the blaze rod (vanilla's actual fire-spray
    tool) with real flame-particle puffs -- not a painted cone -- spraying
    from its tip."""
    im = canvas()
    im.alpha_composite(scale(vanilla("item/blaze_rod.png"), 1.7), (0, 8))
    for x, y, s, a in ((17, 14, 10, 235), (22, 8, 12, 220), (26, 2, 9, 200)):
        puff = faded(scale(vanilla("particle/flame.png"), s / 8), a)
        im.alpha_composite(puff, (x, y))
    save(im, "flamethrower")


def glacial_spike():
    """The lance capstone: blue ice (a real block, the coldest, slickest
    vanilla ice there is) rotated into a much larger diamond than Ice
    Blast's, stretched tall to read as a spike rather than a bolt, with
    frost at its foot."""
    im = canvas()
    tex = vanilla("block/blue_ice.png")
    rot = tex.rotate(45, expand=True, resample=Image.NEAREST)
    side = max(rot.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(rot, ((side - rot.width) // 2, (side - rot.height) // 2))
    spike = square.resize((24, 36), Image.NEAREST)
    im.alpha_composite(spike, (4, -2))
    dots(im, ((3, 29), (7, 31), (11, 30), (1, 26)), ICE_LIGHT)
    dot(im, 16, 1, WHITE)
    save(im, "glacial_spike")


def blizzard():
    """The storm capstone: a snow block (real vanilla texture, full frame
    -- the same 'block fills the canvas' composition as Shatter) driven by
    a few wind-streak accent dashes, restrained rather than a dozen
    hand-drawn flakes."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("block/snow.png")), (0, 0))
    d = ImageDraw.Draw(im)
    streak = (94, 140, 186, 235)
    for x0, y0 in ((-4, 4), (8, -2), (20, 6), (2, 20), (18, 18)):
        d.line([(x0, y0), (x0 + 8, y0 + 8)], fill=streak, width=2)
    dots(im, ((5, 3), (24, 5), (14, 15), (27, 22), (7, 26)), ICE_MID)
    dots(im, ((6, 4), (25, 6), (15, 16)), (46, 82, 128, 255))
    save(im, "blizzard")


def spellweaver():
    """The cheap-cast crown: an enchanted book (all your magic, and it
    glints on its own) with a mana orb and the same minus every cost-down
    node in this tree shares."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/enchanted_book.png")), (0, 0))
    im.alpha_composite(mana_orb(), (18, 17))
    minus(im, 25, 29)
    save(im, "spellweaver")


def arcane_power():
    """The raw-power crown: the nether star, almost exactly as vanilla
    ships it -- the game's own capstone shine needs no help -- with the
    same more-damage chevron Scorch uses."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/nether_star.png")), (0, 0))
    up_chevron(im, 27, 5)
    save(im, "arcane_power")


FAMILIES = [
    "fireball", "ice_blast", "focused_mind",
    "kindling", "scorch", "ignition", "vaporize",
    "chill", "frostbite", "shatter", "permafrost",
    "meteorite", "flamethrower", "glacial_spike", "blizzard",
    "spellweaver", "arcane_power",
]

RENDER = {
    "fireball": fireball, "ice_blast": ice_blast, "focused_mind": focused_mind,
    "kindling": kindling, "scorch": scorch, "ignition": ignition, "vaporize": vaporize,
    "chill": chill, "frostbite": frostbite, "shatter": shatter, "permafrost": permafrost,
    "meteorite": meteorite, "flamethrower": flamethrower, "glacial_spike": glacial_spike,
    "blizzard": blizzard, "spellweaver": spellweaver, "arcane_power": arcane_power,
}


def contact_sheet():
    """Every icon at 4x NEAREST, labelled, on #2b2b2b -- matches the
    sonnet-wizard / sonnet-priest bake-off sheets so they compare 1:1."""
    scale_factor = 4
    cell_w = 32 * scale_factor + 16
    cell_h = 32 * scale_factor + 28
    cols = 6
    rows = -(-len(FAMILIES) // cols)
    sheet = Image.new("RGBA", (cell_w * cols, cell_h * rows), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(FAMILIES):
        im = Image.open(os.path.join(DST, f"{name}.png")).convert("RGBA")
        up = im.resize((im.width * scale_factor, im.height * scale_factor), Image.NEAREST)
        x = (i % cols) * cell_w + (cell_w - up.width) // 2
        y = (i // cols) * cell_h + 8
        sheet.alpha_composite(up, (x, y))
        label = name.replace("_", " ")
        bbox = draw.textbbox((0, 0), label)
        tw = bbox[2] - bbox[0]
        tx = (i % cols) * cell_w + (cell_w - tw) // 2
        ty = (i // cols) * cell_h + up.height + 12
        draw.text((tx, ty), label, fill=(235, 235, 235, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(DST, exist_ok=True)
    for name in FAMILIES:
        RENDER[name]()
    contact_sheet()


if __name__ == "__main__":
    main()
