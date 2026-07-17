"""Generate 32x32 skill-node icons for the Wizard constellation (bake-off
entry). Same pipeline as notes/art/make_node_icons.py: vanilla sprites pulled
straight from the client jar (so palettes match the game for free), composed
on a transparent 32x32 canvas, with a handful of hand-plotted effect pixels
on top. One icon per family, MINOR skipped.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD_ASSETS = "/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/assets/archetypes"
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "icons")

# ---------------------------------------------------------------- palettes
# Amethyst missile (sampled from item/amethyst_shard.png).
AME_DARK = (84, 57, 138, 255)
AME_MAIN = (141, 106, 204, 255)
AME_MID = (179, 142, 243, 255)
AME_LIGHT = (207, 160, 243, 255)
AME_PALE = (255, 253, 213, 255)

# Mana (sampled from textures/gui/mana_orb_full.png).
MANA_OUTLINE = (22, 22, 28, 255)
MANA_DARK = (24, 62, 160, 255)
MANA_MID = (45, 110, 230, 255)
MANA_LIGHT = (140, 190, 255, 255)

# Vanilla heart reds (gui/sprites/hud/heart/*.png).
HP_MAIN = (255, 19, 19, 255)
HP_DARK = (187, 19, 19, 255)
HP_LIGHT = (255, 200, 200, 255)

# Generic motion / callout accents, reused from the Slayer script for a
# consistent house style.
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)
GOLD = (255, 236, 160, 255)
RED = (200, 32, 32, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def mod_tex(path):
    return Image.open(os.path.join(MOD_ASSETS, "textures", path)).convert("RGBA")


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def item2x(im):
    """A 16px vanilla/mod sprite, upscaled 2x NEAREST to the 32px canvas."""
    return im.resize((im.width * 2, im.height * 2), Image.NEAREST)


def scale(im, factor):
    return im.resize((round(im.width * factor), round(im.height * factor)), Image.NEAREST)


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
    """Exact-match RGBA -> RGBA remap, everything else passes through."""
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


def save(im, name):
    os.makedirs(DST, exist_ok=True)
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# The four hp-heart quadrants we lean on for Mana Shield / Overwhelm: real
# vanilla sprites, not hand-drawn, so the shape/shading is exactly the HUD
# heart the player already knows.
def heart_container():
    return vanilla("gui/sprites/hud/heart/container.png")


def heart_half_red():
    return vanilla("gui/sprites/hud/heart/half.png")


def heart_half_blue():
    """The vanilla half-heart, mirrored and recoloured to mana blue —
    used to build a heart that reads 'half health, half mana'."""
    half = heart_half_red().transpose(Image.FLIP_LEFT_RIGHT)
    return recolor(half, {HP_MAIN: MANA_MID, HP_DARK: MANA_DARK, HP_LIGHT: MANA_LIGHT})


# --------------------------------------------------------------- families
def magic_missile():
    """The active: wand held low-left, an amethyst missile already clear of
    the tip and away up the top-right corner, a sparkle trail strung
    between them — the shot in flight, not just a sparkly stick."""
    im = canvas()
    wand = scale(mod_tex("item/magic_wand.png"), 1.375)
    im.alpha_composite(wand, (0, 7))

    shard = scale(vanilla("item/amethyst_shard.png"), 0.6)
    im.alpha_composite(shard, (23, 0))

    for x, y, c in ((21, 5, AME_PALE), (19, 7, AME_LIGHT), (17, 9, AME_MID)):
        dot(im, x, y, c)
    save(im, "magic_missile")


def mana_shield():
    """A heart split down the middle: the left half still the player's own
    blood-red, the right half turned to mana blue — half of what you take
    doesn't cost you health at all."""
    heart = heart_container().copy()
    heart.alpha_composite(heart_half_red(), (0, 0))
    heart.alpha_composite(heart_half_blue(), (0, 0))
    im = canvas()
    paste_centered(im, scale(heart, 3.0))
    save(im, "mana_shield")


def force():
    """The breeze rod, business end bursting hot orange on impact (not the
    missile's cool purple — this is raw hit-harder, not the cast itself),
    plus the '+' that marks every additive, stacking node in the tree."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/breeze_rod.png")), (0, 0))
    for x, y, c in ((28, 1, GOLD), (29, 2, RED), (27, 1, RED), (28, 3, GOLD), (30, 3, RED)):
        dot(im, x, y, c)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (0, 0)):
        dot(im, 6 + dx, 6 + dy, ARC if dx or dy else (255, 255, 255, 255))
    save(im, "force")


def clarity():
    """The mana orb, smaller than Arcane Orb's and marked with a red
    minus instead of a plus — same 'more/less' grammar as that node, but
    this one shrinks the cost, not the pool."""
    im = canvas()
    orb = scale(mod_tex("gui/mana_orb_full.png"), 2.6)
    paste_centered(im, orb, cx=15, cy=17)
    # Same cross footprint as Arcane Orb's '+', minus the vertical stroke —
    # same "more/less" grammar, opposite sign, right next to the orb.
    for dx in (-2, -1, 0, 1, 2):
        dot(im, 27 + dx, 6, RED)
        dot(im, 27 + dx, 7, RED)
    save(im, "clarity")


def siphon():
    """A glass bottle pooled deep blue at its base — the fill traced from
    the bottle's own silhouette so it never leaks past the glass — a
    kill-spark above with a bright drip strung down into the neck."""
    bottle = vanilla("item/glass_bottle.png")
    liquid = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(9, 14):
        xs = [x for x in range(16) if bottle.getpixel((x, y))[3] > 0]
        if len(xs) >= 2:
            for x in range(min(xs) + 1, max(xs)):
                liquid.putpixel((x, y), MANA_MID if (x + y) % 2 else MANA_DARK)

    im = canvas()
    im.alpha_composite(item2x(liquid), (0, 0))
    im.alpha_composite(item2x(bottle), (0, 0))
    for x, y, c in ((23, 2, RED), (24, 3, GOLD), (22, 3, GOLD), (23, 4, RED)):
        dot(im, x, y, c)
    for x, y in ((21, 6), (19, 10), (17, 14)):
        dot(im, x, y, MANA_LIGHT)
    save(im, "siphon")


def echo():
    """Two amethyst shards on the same line: a solid missile up front, a
    faint ghost twin trailing it — the free second cast, same motif the
    Slayer tree uses for its own echo effects."""
    im = canvas()
    shard = scale(vanilla("item/amethyst_shard.png"), 1.25)
    ghost = faded(shard, 130)
    im.alpha_composite(ghost, (0, 3))
    im.alpha_composite(shard, (10, 10))
    save(im, "echo")


def range_():
    """The spyglass, a dashed sightline stretching out past the lens —
    farther sight standing in for farther reach."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/spyglass.png")), (0, 0))
    for x, y, c in ((23, 3, ARC), (26, 4, ARC), (29, 5, ARC_DIM)):
        dot(im, x, y, c)
    save(im, "range")


def arcane_orb():
    """The orb in the head, as large as the frame allows, a '+' pip beside
    it — the plain capacity node, so it gets the plainest 'more' mark."""
    im = canvas()
    orb = scale(mod_tex("gui/mana_orb_full.png"), 3.2)
    paste_centered(im, orb, cx=15, cy=17)
    for dx, dy in ((0, -2), (0, -1), (0, 1), (0, 2), (-2, 0), (-1, 0), (1, 0), (2, 0)):
        dot(im, 27 + dx, 6 + dy, (255, 255, 255, 255))
    save(im, "arcane_orb")


def velocity():
    """The feather, motion lines streaming off its trailing edge."""
    im = canvas()
    feather = item2x(vanilla("item/feather.png"))
    im.alpha_composite(feather, (2, 0))
    for i, y in enumerate((9, 15, 21)):
        for x in range(0, 6 - i):
            dot(im, x, y, ARC if x > 2 else ARC_DIM)
    save(im, "velocity")


def overwhelm():
    """The vanilla half-heart: real health already spent before your
    missile lands. A small red spark at the broken edge for the bonus
    damage that punishes it."""
    heart = heart_container().copy()
    heart.alpha_composite(heart_half_red(), (0, 0))
    im = canvas()
    paste_centered(im, scale(heart, 3.0))
    for x, y, c in ((24, 13, GOLD), (26, 10, GOLD), (22, 16, RED)):
        dot(im, x, y, c)
    save(im, "overwhelm")


def concussion():
    """Vanilla's own Weakness icon, straight off the effects HUD — the
    exact debuff the missile leaves behind, instantly legible."""
    im = canvas()
    icon = scale(vanilla("mob_effect/weakness.png"), 1.8)
    paste_centered(im, icon, cx=15, cy=17)
    for x, y, c in ((26, 4, AME_MID), (28, 6, AME_LIGHT), (27, 8, AME_MID)):
        dot(im, x, y, c)
    save(im, "concussion")


def shatterpoint():
    """A pristine archery bullseye — full health, rings unbroken — with a
    bold white crack punched through the centre, dark-outlined so it cuts
    through the busy ring pattern instead of vanishing into it."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("block/target_top.png")), (0, 0))
    outline = (40, 20, 10, 255)
    crack = ((16, 16), (14, 13), (12, 11), (10, 9), (19, 19), (22, 21), (24, 23))
    for x, y in crack:
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            dot(im, x + dx, y + dy, outline)
    for x, y in crack:
        dot(im, x, y, (255, 255, 255, 255))
    for x, y, c in ((15, 15, AME_PALE), (16, 15, AME_LIGHT), (15, 16, AME_LIGHT),
                    (16, 16, AME_PALE), (14, 15, AME_MID), (17, 16, AME_MID)):
        dot(im, x, y, c)
    save(im, "shatterpoint")


def seeker_missile():
    """The Eye of Ender — vanilla's own homing item — with a bent dashed
    line showing the curve a missile takes chasing it down."""
    im = canvas()
    eye = scale(vanilla("item/ender_eye.png"), 1.7)
    paste_centered(im, eye, cx=13, cy=13)
    path = ((26, 6), (28, 10), (28, 15), (26, 19), (22, 22))
    for i, (x, y) in enumerate(path):
        dot(im, x, y, ARC if i < 3 else ARC_DIM)
    dot(im, 20, 24, ARC_DIM)
    dot(im, 24, 21, ARC_DIM)
    save(im, "seeker_missile")


def lance():
    """Vanilla's own trident — the one polearm in the game already drawn
    as a proper diagonal spear — with hit-pips down its shaft where it
    punches through more than one target."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/trident.png")), (0, 0))
    for x, y, c in ((9, 22, AME_LIGHT), (15, 16, AME_LIGHT), (21, 10, AME_PALE)):
        dot(im, x, y, c)
    save(im, "lance")


def mind_well():
    """Lapis for the mind, a tally of small charges climbing to a bright
    burst — the count building toward every 8th, then 4th, empowered
    missile."""
    im = canvas()
    lapis = scale(vanilla("item/lapis_lazuli.png"), 1.6)
    paste_centered(im, lapis, cx=14, cy=18)
    for x, y in ((22, 20), (24, 17), (25, 13)):
        dot(im, x, y, MANA_LIGHT)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (0, 0)):
        dot(im, 27 + dx, 8 + dy, GOLD if dx or dy else (255, 255, 255, 255))
    save(im, "mind_well")


def flow():
    """The heart of the sea, a rising column of small upward chevrons
    above it — a steady tide, not a one-off sparkle, feeding the pool
    back second by second."""
    im = canvas()
    hots = scale(vanilla("item/heart_of_the_sea.png"), 1.6)
    paste_centered(im, hots, cx=16, cy=20)
    chevrons = ((15, 10), (15, 6), (15, 2))
    for cx, cy in chevrons:
        dot(im, cx, cy, MANA_LIGHT)
        dot(im, cx - 1, cy + 1, MANA_MID)
        dot(im, cx + 1, cy + 1, MANA_MID)
    save(im, "flow")


def archmage():
    """The nether star as-is — the tree's own top keystone deserves the
    game's own 'you made it' sparkle — with one amethyst mote worked in so
    it still reads as this tree's capstone and not another one's."""
    im = canvas()
    im.alpha_composite(item2x(vanilla("item/nether_star.png")), (0, 0))
    for x, y, c in ((6, 24, AME_MID), (5, 26, AME_LIGHT), (24, 6, AME_MID)):
        dot(im, x, y, c)
    save(im, "archmage")


# Render order == tree order: haft, grip, diamond, crown — see WizardNodes.
FAMILIES = [
    "magic_missile", "mana_shield", "force", "clarity", "siphon", "echo",
    "range", "arcane_orb", "velocity", "overwhelm", "concussion",
    "shatterpoint", "seeker_missile", "lance", "mind_well", "flow", "archmage",
]


def contact_sheet():
    """Every icon at 4x NEAREST, labelled, on a dark background so pale
    pixels read the way they will on the tree screen."""
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
    magic_missile()
    mana_shield()
    force()
    clarity()
    siphon()
    echo()
    range_()
    arcane_orb()
    velocity()
    overwhelm()
    concussion()
    shatterpoint()
    seeker_missile()
    lance()
    mind_well()
    flow()
    archmage()
    contact_sheet()


if __name__ == "__main__":
    main()
