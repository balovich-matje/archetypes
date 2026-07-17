"""Generate 32x32 skill-node icons for the Elementalist tree (fire/ice mage).

Same house style as notes/art/make_node_icons.py: vanilla sprites pulled
straight from the client jar (palettes match the game for free), composed on
a transparent canvas, plus a few hand-plotted effect pixels. Every node here
gets a FULL standalone 32x32 icon (this test has no overlay-on-live-item
nodes), landing in icons/ next to this script -- not the real texture pack.

Also builds contact_sheet.png: every icon at 4x NEAREST, labeled, on the
dark background the tree screen actually uses.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

# ---------------------------------------------------------------- palette --
MANA = (45, 110, 230, 255)          # from the mod's own mana_orb_full.png
MANA_DARK = (24, 62, 160, 255)
MANA_LIGHT = (140, 190, 255, 255)
MINUS = (225, 235, 255, 255)

FLAME = (255, 150, 30, 255)
FLAME_DARK = (200, 70, 10, 255)
FLAME_LIGHT = (255, 224, 130, 255)

ICE = (150, 205, 245, 255)
ICE_DARK = (70, 120, 190, 255)
ICE_LIGHT = (255, 255, 255, 255)

WATER = (70, 140, 210, 255)
WATER_DARK = (35, 90, 160, 255)

STEAM = (225, 225, 225, 180)

CRACK = (255, 255, 255, 230)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def canvas(size=32):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def up2(im):
    """The house move: vanilla 16px sprites, upscaled 2x NEAREST onto the
    32px icon canvas -- crisp and blocky, never smoothed."""
    return im.resize((im.width * 2, im.height * 2), Image.NEAREST)


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def save(im, name):
    os.makedirs(ICONS, exist_ok=True)
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


def dot(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def mana_cost_mark(im, x, y, badge=True):
    """A small mana teardrop with a '-' beside it, on a dark badge so it
    reads against any background: this node spends less. Shared idiom across
    every cost-reduction node (Kindling, Chill, Spellweaver) so the player
    learns it once."""
    if badge:
        d = ImageDraw.Draw(im)
        d.ellipse((x - 5, y - 4, x + 8, y + 8), fill=(20, 24, 34, 225))
    for dx, dy, c in ((0, 0, MANA), (0, 1, MANA), (-1, 2, MANA_DARK), (0, 2, MANA_LIGHT),
                      (1, 2, MANA), (-1, 3, MANA_DARK), (0, 3, MANA_DARK), (1, 3, MANA)):
        dot(im, x + dx, y + dy, c)
    for dx in range(3):
        dot(im, x + 3 + dx, y + 2, MINUS)


def circle_from_texture(tex, size, r, cx=None, cy=None):
    """A filled disc of `size`, radius `r`, coloured by `tex`'s own RGB
    (resampled to size) but forced fully opaque -- turns a flat vanilla
    block/item texture into a clean, readable orb silhouette, fireball-style."""
    cx = size // 2 if cx is None else cx
    cy = size // 2 if cy is None else cy
    tex2 = tex.resize((size, size), Image.NEAREST)
    out = canvas(size)
    for y in range(size):
        for x in range(size):
            if (x - cx + 0.5) ** 2 + (y - cy + 0.5) ** 2 <= r * r:
                rr, gg, bb, _ = tex2.getpixel((x, y))
                out.putpixel((x, y), (rr, gg, bb, 255))
    return out


def ring(im, cx, cy, r, color):
    for a in range(0, 360, 6):
        import math
        x = round(cx + r * math.cos(math.radians(a)))
        y = round(cy + r * math.sin(math.radians(a)))
        dot(im, x, y, color)


def motion_trail(im, color, corner="bl"):
    """Three fading streak pixels suggesting a just-thrown projectile."""
    pts = [(2, 29), (4, 27), (6, 25)] if corner == "bl" else [(29, 2), (27, 4), (25, 6)]
    for i, (x, y) in enumerate(pts):
        a = 130 + i * 45
        dot(im, x, y, (color[0], color[1], color[2], min(a, 255)))


def recolor_to_ice(im):
    """Hue-shift an orange/gold sprite (the blaze rod) to icy silver-blue,
    preserving its own light/dark structure -- Blizzard mirrors Flamethrower
    in shape, ice palette instead of fire."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                k = (r + g + b) / 3 / 255
                px[x, y] = (int(140 + 70 * k), int(180 + 60 * k), 255, a)
    return out


# --------------------------------------------------------------- the base --

def fireball():
    """The vanilla fire charge, thrown -- a short flame trail behind it."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/fire_charge.png")), (0, 0))
    motion_trail(im, (255, 170, 60), "bl")
    save(im, "fireball")


def ice_blast():
    """A compact orb of the vanilla ice block's own crackled pale-blue --
    Fireball's sibling: same silhouette, opposite element, opposite-tinted
    trail. A bolt, not a block, is what the tooltip promises, so it's built
    to read as a thrown orb rather than a wall texture."""
    im = canvas(32)
    orb = circle_from_texture(vanilla("block/ice.png"), 32, 14)
    im.alpha_composite(orb, (0, 0))
    ring(im, 16, 16, 13.5, ICE_DARK)
    for x, y in ((10, 9), (11, 9), (9, 10)):
        dot(im, x, y, ICE_LIGHT)
    motion_trail(im, (170, 215, 255), "bl")
    save(im, "ice_blast")


def focused_mind():
    """The lapis mana gem, ticking upward -- a small '+' and rising sparks
    for regeneration over time."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/lapis_lazuli.png")), (0, 0))
    # a small plus, top-centre
    for dx, dy in ((0, -1), (0, 0), (0, 1), (-1, 0), (1, 0)):
        dot(im, 16 + dx, 4 + dy, MANA_LIGHT)
    for x, y, a in ((10, 8, 220), (22, 6, 180), (14, 2, 140)):
        dot(im, x, y, (140, 190, 255, a))
    save(im, "focused_mind")


def kindling():
    """Flint and steel -- grey on its own, so a struck spark is added at the
    point they meet, the fire-branch cue this sprite otherwise lacks -- plus
    the shared cost-down mark: fire spells cheaper."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/flint_and_steel.png")), (0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(15, 15), (20, 17), (16, 20), (13, 18)], fill=FLAME)
    for x, y in ((16, 14), (20, 15), (12, 19), (17, 21)):
        dot(im, x, y, FLAME_LIGHT)
    mana_cost_mark(im, 22, 24)
    save(im, "kindling")


def scorch():
    """A heart wreathed in flame -- fire hits it harder. Heart-plus-damage is
    already this pack's idiom (see the Slayer tree's Taste of Blood); this
    reuses it with fire instead of blood."""
    im = canvas(32)
    heart = vanilla("gui/sprites/hud/heart/full.png").resize((16, 16), Image.NEAREST)
    d = ImageDraw.Draw(im)
    # flame licks behind the heart
    d.polygon([(6, 26), (9, 12), (12, 20), (14, 8), (17, 20), (20, 10), (23, 26)],
              fill=FLAME_DARK)
    d.polygon([(8, 26), (10, 15), (13, 21), (15, 10), (17, 21), (19, 14), (21, 26)],
              fill=FLAME)
    im.alpha_composite(heart, (8, 9))
    for x, y in ((9, 10), (22, 11), (16, 24)):
        dot(im, x, y, FLAME_LIGHT)
    save(im, "scorch")


def ignition():
    """The campfire, with the clock this pack already uses for 'longer' (see
    Braced) tucked in the corner -- the burn lasts more seconds."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/campfire.png")), (0, 0))
    im.alpha_composite(vanilla("item/clock_00.png"), (16, 0))
    save(im, "ignition")


def vaporize():
    """The sponge, with a water drop bottom-left and a rising chain of steam
    puffs boiling up and away from it, top-right -- fire's line evaporates
    the water it crosses."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("block/sponge.png")), (0, 0))
    d = ImageDraw.Draw(im)
    for dx, dy, c in ((0, 0, WATER), (0, 1, WATER), (-1, 2, WATER_DARK), (0, 2, WATER),
                      (1, 2, WATER), (-1, 3, WATER_DARK), (0, 3, WATER_DARK), (1, 3, WATER)):
        dot(im, 6 + dx, 24 + dy, c)
    d.ellipse((10, 16, 17, 22), fill=(235, 235, 235, 140))
    d.ellipse((15, 9, 23, 16), fill=(238, 238, 238, 175))
    d.ellipse((20, 2, 28, 9), fill=(242, 242, 242, 210))
    save(im, "vaporize")


def chill():
    """Snowball, plus the shared cost-down mark: ice spells cheaper."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/snowball.png")), (0, 0))
    mana_cost_mark(im, 22, 24)
    save(im, "chill")


def frostbite():
    """The powder snow bucket, with vanilla's own Slowness icon riding the
    corner -- the exact status this node piles on harder and longer."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/powder_snow_bucket.png")), (0, 0))
    slow = vanilla("mob_effect/slowness.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(slow, (17, 0))
    save(im, "frostbite")


def shatter():
    """The amethyst shard breaking apart: two smaller echoes flying off the
    empty corners, crack lines at the point of failure -- bonus damage to
    something already cracking."""
    im = canvas(32)
    shard = up2(vanilla("item/amethyst_shard.png"))
    im.alpha_composite(shard, (0, 0))
    echo = shard.resize((14, 14), Image.NEAREST)
    im.alpha_composite(faded(echo, 170), (0, 1))
    im.alpha_composite(faded(echo, 130), (19, 19))
    for x, y in ((11, 15), (14, 13), (17, 17), (20, 15)):
        dot(im, x, y, CRACK)
    save(im, "shatter")


def permafrost():
    """Packed ice, with a water drop crossed by a snowflake on a dark badge
    in the corner -- packed ice is nearly the same pale blue as a plain
    water drop, so the badge (and a drop big enough to fill it) is what
    keeps this readable instead of vanishing into the block. The water this
    node's ice crosses glazes solid behind it."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("block/packed_ice.png")), (0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((18, 0, 31, 13), fill=(16, 34, 68, 235))
    d.ellipse((20, 2, 29, 11), fill=WATER)
    d.ellipse((22, 4, 27, 9), fill=WATER_DARK)
    for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0), (-3, -3), (3, 3), (-3, 3), (3, -3)):
        dot(im, 24 + dx, 6 + dy, ICE_LIGHT)
    dot(im, 24, 6, ICE_LIGHT)
    save(im, "permafrost")


def meteorite():
    """A fireball streaking in on a diagonal comet tail from the top corner,
    onto a cracked magma floor -- the whole-mana-pool, sky-called crater
    capstone. Bigger and more violent than Fireball's own little thrown
    orb, and diagonal (not a straight drop) so the tail reads as a trail
    of motion instead of a torch standing on the ground."""
    im = canvas(32)
    magma = vanilla("block/magma.png").crop((0, 0, 16, 16))
    floor = magma.resize((32, 9), Image.NEAREST)
    im.alpha_composite(floor, (0, 23))
    d = ImageDraw.Draw(im)
    for x0, y0, x1, y1 in ((14, 23, 7, 31), (14, 24, 21, 31), (14, 25, 14, 31)):
        d.line((x0, y0, x1, y1), fill=(28, 10, 4, 255), width=1)
    # a chain of shrinking, fading puffs -- a trail, not a rigid handle --
    # streaking in from the top-right corner
    for x, y, r, a in ((30, 1, 5, 235), (27, 4, 5, 205), (24, 7, 4, 175),
                       (21, 10, 4, 145), (18, 12, 3, 115)):
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 150, 30, a))
    # the meteor head at the base of the tail, about to land
    d.ellipse((8, 13, 20, 23), fill=FLAME_DARK)
    d.ellipse((10, 14, 18, 22), fill=FLAME)
    for x, y in ((13, 16), (16, 18), (14, 20)):
        dot(im, x, y, FLAME_LIGHT)
    # impact glow where it is about to land
    for x, y, a in ((7, 22, 210), (21, 22, 210), (14, 21, 235), (3, 24, 130), (25, 24, 130)):
        dot(im, x, y, (255, 210, 120, a))
    save(im, "meteorite")


def flamethrower():
    """The blaze rod as a nozzle, spraying a straight cone of flame from its
    tip -- hold the key, not throw the ball."""
    im = canvas(32)
    rod = up2(vanilla("item/blaze_rod.png"))
    im.alpha_composite(rod, (0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(24, 8), (31, 2), (31, 12)], fill=FLAME_DARK)
    d.polygon([(25, 8), (30, 4), (30, 10)], fill=FLAME)
    for x, y in ((28, 5), (27, 8), (29, 8)):
        dot(im, x, y, FLAME_LIGHT)
    save(im, "flamethrower")


def glacial_spike():
    """A single hand-built icicle thrust diagonally through the frame, an
    icy burst where it lands -- one huge piercing lance, not a spray."""
    im = canvas(32)
    d = ImageDraw.Draw(im)
    d.polygon([(27, 2), (13, 22), (9, 30), (17, 24), (30, 6)], fill=ICE_DARK)
    d.polygon([(26, 3), (14, 21), (12, 26), (18, 22), (28, 6)], fill=ICE)
    d.line((26, 3, 15, 20), fill=ICE_LIGHT, width=1)
    # the freeze burst at the tip
    for dx, dy in ((0, -4), (0, 4), (-4, 0), (4, 0), (-3, -3), (3, 3)):
        dot(im, 9 + dx, 30 + dy, ICE_LIGHT)
    dot(im, 9, 30, ICE_LIGHT)
    save(im, "glacial_spike")


def blizzard():
    """The blaze rod re-tinted icy, a widening flurry off its tip instead of
    a tight jet -- Flamethrower's mirror: same rod, same hold-the-key idea,
    a storm instead of a straight flame."""
    im = canvas(32)
    rod = recolor_to_ice(up2(vanilla("item/blaze_rod.png")))
    im.alpha_composite(rod, (0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(24, 8), (31, 3), (31, 15)], fill=(160, 200, 255, 90))
    flakes = ((26, 4, 255), (29, 2, 230), (24, 9, 255), (30, 9, 220),
              (27, 12, 200), (31, 15, 180), (23, 3, 210), (30, 5, 250),
              (29, 12, 200), (25, 6, 240))
    for x, y, a in flakes:
        dot(im, x, y, (245, 250, 255, a))
    save(im, "blizzard")


def spellweaver():
    """The enchanted book, plus the shared cost-down mark on a dark badge
    (the book's own warm reds and browns swallow a plain blue drop) and a
    four-point magic twinkle -- every spell, cheaper, not just one element."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/enchanted_book.png")), (0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((17, 19, 31, 31), fill=(20, 24, 34, 230))
    for dx, dy, c in ((0, 0, (200, 200, 220, 255)), (0, 1, (200, 200, 220, 255)),
                      (-1, 2, (120, 120, 150, 255)), (0, 2, (235, 235, 250, 255)),
                      (1, 2, (200, 200, 220, 255)), (-1, 3, (120, 120, 150, 255)),
                      (0, 3, (120, 120, 150, 255)), (1, 3, (200, 200, 220, 255))):
        dot(im, 22 + dx, 22 + dy, c)
    for dx in range(3):
        dot(im, 25 + dx, 24, MINUS)
    # a small magic twinkle, top-left, clear of the book's own art
    for x, y, c in ((4, 3, ICE_LIGHT), (4, 1, MANA_LIGHT), (4, 5, MANA_LIGHT),
                    (2, 3, MANA_LIGHT), (6, 3, MANA_LIGHT)):
        dot(im, x, y, c)
    save(im, "spellweaver")


def arcane_power():
    """The nether star, radiating an extra burst of rays -- every spell hits
    harder, full stop."""
    im = canvas(32)
    im.alpha_composite(up2(vanilla("item/nether_star.png")), (0, 0))
    rays = ((16, 1), (16, 30), (1, 16), (30, 16), (4, 4), (27, 4), (4, 27), (27, 27))
    for x, y in rays:
        dot(im, x, y, (255, 250, 210, 255))
    save(im, "arcane_power")


ORDER = [
    "fireball", "ice_blast", "focused_mind",
    "kindling", "scorch", "ignition", "vaporize",
    "chill", "frostbite", "shatter", "permafrost",
    "meteorite", "flamethrower", "glacial_spike", "blizzard",
    "spellweaver", "arcane_power",
]


def contact_sheet():
    """Every icon at 4x NEAREST, labeled, dark background -- the look-and-
    judge pass this whole set gets checked against."""
    scale = 4
    icon_px = 32 * scale
    cell_w, cell_h = icon_px + 24, icon_px + 40
    cols = 6
    rows = (len(ORDER) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()

    for i, name in enumerate(ORDER):
        im = Image.open(os.path.join(ICONS, f"{name}.png")).convert("RGBA")
        im = im.resize((icon_px, icon_px), Image.NEAREST)
        col, row = i % cols, i // cols
        x = col * cell_w + (cell_w - icon_px) // 2
        y = row * cell_h + 12
        sheet.alpha_composite(im, (x, y))
        label = name.upper()
        bbox = draw.textbbox((0, 0), label, font=font)
        tw = bbox[2] - bbox[0]
        tx = col * cell_w + (cell_w - tw) // 2
        draw.text((tx, y + icon_px + 8), label, fill=(230, 230, 230, 255), font=font)

    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png", sheet.size)


def main():
    fireball()
    ice_blast()
    focused_mind()
    kindling()
    scorch()
    ignition()
    vaporize()
    chill()
    frostbite()
    shatter()
    permafrost()
    meteorite()
    flamethrower()
    glacial_spike()
    blizzard()
    spellweaver()
    arcane_power()
    contact_sheet()


if __name__ == "__main__":
    main()
