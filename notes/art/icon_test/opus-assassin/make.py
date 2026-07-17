"""Skill-node icons for the Assassin dagger tree (bake-off round two).

Every icon is composed FROM REAL VANILLA SPRITES pulled straight from the
client jar (so palettes match the game for free) plus the mod's own dagger
textures, with a small readable mechanic grammar hand-plotted on top:
  +  = more            ghost copy = a strike duplicated
  speed dashes = faster      colored motes/drip = an applied effect
  cracked/cut = defense undone   refresh loop = a cooldown reset

Compose at 16px (the vanilla grid), save at 32px (2x NEAREST) — the house norm.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
MODITEM = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/item"))
ICONS = os.path.join(HERE, "icons")

# ---- palette ---------------------------------------------------------------
POISON = (86, 196, 46, 255)
POISON_HI = (150, 232, 96, 255)
POISON_DK = (44, 120, 24, 255)
WITHER = (58, 54, 62, 255)
WITHER_DK = (28, 26, 32, 255)
WITHER_HI = (108, 104, 118, 255)
ARC = (236, 236, 240, 255)
ARC_DIM = (150, 150, 156, 210)
GOLD = (255, 214, 92, 255)
GOLD_DK = (176, 130, 30, 255)
PORTAL = (168, 96, 216, 255)
PORTAL_HI = (206, 150, 240, 255)
BONE = (198, 198, 204, 255)
BONE_DK = (118, 118, 126, 255)
BONE_SHADOW = (60, 60, 66, 255)
READY = (96, 214, 96, 255)
READY_HI = (168, 240, 150, 255)
STEEL_HI = (246, 246, 252, 255)


# ---- sprite access ---------------------------------------------------------
def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def moditem(name):
    return Image.open(os.path.join(MODITEM, f"{name}.png")).convert("RGBA")


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


def tint(im, colour, keep_alpha=True):
    """Recolour every opaque pixel to `colour`, preserving alpha."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            a = px[x, y][3]
            if a:
                px[x, y] = (colour[0], colour[1], colour[2], a if keep_alpha else 255)
    return out


def plot(im, pts, colour):
    for x, y in pts:
        if 0 <= x < im.width and 0 <= y < im.height:
            im.putpixel((x, y), colour)


def outline_plus(im, cx, cy, colour, dark=(20, 20, 24, 255)):
    """A crisp 3x3 plus with a dark skirt so it reads on any sprite."""
    arms = [(cx, cy - 1), (cx, cy + 1), (cx - 1, cy), (cx + 1, cy), (cx, cy)]
    skirt = [(cx, cy - 2), (cx, cy + 2), (cx - 2, cy), (cx + 2, cy),
             (cx - 1, cy - 1), (cx + 1, cy - 1), (cx - 1, cy + 1), (cx + 1, cy + 1)]
    plot(im, skirt, dark)
    plot(im, arms, colour)


def save(im16, name):
    big = im16.resize((im16.width * 2, im16.height * 2), Image.NEAREST)
    big.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# ---------------------------------------------------------------------------
# THE ACTIVE + ITS IMPROVERS
# ---------------------------------------------------------------------------
def shadow_step():
    """The active: an ender pearl (teleport) with the dagger already driven
    through it, portal sparks around — blink in, stab."""
    im = canvas()
    pearl = vanilla("item/ender_pearl.png").resize((12, 12), Image.NEAREST)
    im.alpha_composite(pearl, (1, 3))
    dagger = moditem("iron_dagger")
    im.alpha_composite(dagger, (2, -2))
    for x, y, c in ((0, 2, PORTAL_HI), (1, 1, PORTAL), (13, 6, PORTAL_HI),
                    (14, 8, PORTAL), (3, 14, PORTAL), (11, 13, PORTAL_HI)):
        im.putpixel((x, y), c)
    save(im, "shadow_step")


def adrenaline_rush():
    """Sugar (vanilla's speed reagent) kicking upward — a burst of speed
    chevrons above the pile, the rush after the blink."""
    im = canvas()
    sugar = vanilla("item/sugar.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(sugar, (2, 3))
    # two rising speed chevrons
    for oy, c in ((0, ARC), (3, ARC_DIM)):
        for dx, dy in ((-3, 2), (-2, 1), (-1, 0), (0, 1), (1, 0), (2, 1), (3, 2)):
            im.putpixel((8 + dx, 2 + oy + dy), c)
    # trailing dashes
    plot(im, [(0, 9), (1, 9), (14, 10), (15, 10)], ARC_DIM)
    # a portal spark: the speed comes off Shadow Step's blink
    im.putpixel((14, 6), PORTAL_HI)
    im.putpixel((15, 5), PORTAL)
    save(im, "adrenaline_rush")


def opportunist():
    """The clock, wound backward: a counter-clockwise rewind arrow riding its
    shoulder — the cooldown comes sooner."""
    im = canvas()
    clock = vanilla("item/clock_00.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(clock, (0, 2))
    # rewind arrow, top-right, sweeping anticlockwise
    ring = ((14, 2), (13, 1), (12, 1), (11, 2), (11, 3), (12, 4), (13, 5))
    plot(im, ring, GOLD)
    # arrowhead pointing back/down-left into the sweep
    plot(im, [(11, 1), (10, 2), (11, 3), (12, 2)], GOLD)
    plot(im, [(14, 3), (13, 6)], GOLD_DK)
    save(im, "opportunist")


# ---------------------------------------------------------------------------
# THE BODY WORK (footwork)
# ---------------------------------------------------------------------------
def lightfoot():
    """Leather boots with speed dashes streaming off the heel — faster on
    your feet while a dagger is out."""
    im = canvas()
    boots = vanilla("item/leather_boots.png").resize((15, 15), Image.NEAREST)
    im.alpha_composite(boots, (2, 1))
    for y in (8, 11, 14):
        for x in range(0, 6):
            im.putpixel((x, y), ARC if x >= 3 else ARC_DIM)
    save(im, "lightfoot")


def sidestep():
    """A feather (nimbleness) with a dodge swoosh curving under it and a red
    blow whiffing past behind — the hit that finds nothing."""
    im = canvas()
    # the whiffing strike: a faint red slash crossing empty space, top-left
    for i, (x, y) in enumerate(((2, 1), (3, 2), (4, 3), (5, 4))):
        im.putpixel((x, y), (196, 60, 60, 255) if i % 2 else (150, 40, 40, 255))
    feather = vanilla("item/feather.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(feather, (3, 1))
    # dodge swoosh: a curved motion arc sweeping to the lower-right
    arc = ((5, 14), (7, 15), (9, 15), (11, 14), (12, 12), (13, 10))
    for i, (x, y) in enumerate(arc):
        im.putpixel((x, y), ARC if i < 4 else ARC_DIM)
    im.putpixel((13, 9), ARC)   # arrowhead
    im.putpixel((14, 11), ARC)
    save(im, "sidestep")


# ---------------------------------------------------------------------------
# LEFT EDGE — raw steel
# ---------------------------------------------------------------------------
def razor_edge():
    """The iron dagger, honed: a bright edge-glint running the blade, a spark
    at the point and a small plus — simply more bite."""
    im = canvas()
    dagger = moditem("iron_dagger")
    im.alpha_composite(dagger, (0, 0))
    # edge glint along the blade's back (up-right diagonal)
    for x, y in ((8, 4), (9, 5), (10, 6), (7, 5), (8, 6)):
        im.putpixel((x, y), STEEL_HI)
    # point spark
    plot(im, [(12, 2), (11, 2), (12, 3), (13, 2), (12, 1)], STEEL_HI)
    outline_plus(im, 3, 4, GOLD)
    save(im, "razor_edge")


def expose():
    """A bullseye with a half-emptied heart in front — the bonus that lands
    once the target has bled below half."""
    im = canvas()
    tgt = vanilla("block/target_top.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(tgt, (0, 0))
    heart = vanilla("gui/sprites/hud/heart/half.png").resize((10, 10), Image.NEAREST)
    # dark skirt so the heart sits off the busy bullseye
    sk = tint(heart, (0, 0, 0), keep_alpha=False)
    im.alpha_composite(faded(sk, 200), (6, 6))
    im.alpha_composite(heart, (5, 5))
    save(im, "expose")


# ---------------------------------------------------------------------------
# RIGHT EDGE — what's on the steel
# ---------------------------------------------------------------------------
def venom():
    """A spider eye leaking venom: green poison bubbles rising off it and a
    bright drip below — the poison the dagger now carries."""
    im = canvas()
    eye = vanilla("item/spider_eye.png").resize((12, 12), Image.NEAREST)
    im.alpha_composite(eye, (2, 3))
    # rising poison bubbles, upper-right
    plot(im, [(12, 3), (13, 2)], POISON)
    plot(im, [(13, 1)], POISON_HI)
    plot(im, [(11, 5), (14, 4)], POISON_DK)
    im.putpixel((12, 4), POISON_HI)
    # venom drip below the eye
    plot(im, [(7, 15), (7, 14)], POISON)
    im.putpixel((7, 13), POISON_HI)
    im.putpixel((6, 15), POISON_DK)
    save(im, "venom")


def blight():
    """The wither rose beside the very Wither effect it inflicts (vanilla's
    own withered-heart icon), pale decay wisps rising between them — the
    wound that keeps taking."""
    im = canvas()
    rose = vanilla("block/wither_rose.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(rose, (-2, 3))
    # vanilla Wither mob-effect icon (a blackened heart) as the applied effect
    wfx = vanilla("mob_effect/wither.png").resize((9, 9), Image.NEAREST)
    im.alpha_composite(wfx, (7, 0))
    # pale decay wisps curling up off the bloom, light enough to read on dark
    plot(im, [(3, 2), (5, 1)], WITHER_HI)
    plot(im, [(2, 3), (4, 2)], WITHER)
    plot(im, [(1, 4)], WITHER_DK)
    save(im, "blight")


def flense():
    """Shears biting an iron chestplate, the plate split by the cut — armor
    that no longer counts."""
    im = canvas()
    chest = vanilla("item/iron_chestplate.png").resize((12, 12), Image.NEAREST)
    im.alpha_composite(chest, (2, 3))
    # the cut: a dark diagonal gash splitting the plate
    for x, y in ((5, 12), (6, 11), (7, 10), (8, 9), (9, 8), (10, 7)):
        im.putpixel((x, y), (26, 26, 30, 255))
        im.putpixel((x + 1, y), STEEL_HI)
    shears = vanilla("item/shears.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(shears, (2, 0))
    save(im, "flense")


# ---------------------------------------------------------------------------
# CAPSTONES + THE POINT
# ---------------------------------------------------------------------------
def shadow_flurry():
    """The netherite dagger struck three times at once: two ghost echoes
    fanned behind the real blade — three daggers' weight."""
    im = canvas()
    dagger = moditem("netherite_dagger")
    for angle, alpha, off in ((26, 150, (-3, 1)), (-26, 195, (3, 1))):
        echo = dagger.rotate(angle, resample=Image.NEAREST, expand=False)
        im.alpha_composite(faded(echo, alpha), off)
    im.alpha_composite(dagger, (0, 0))
    save(im, "shadow_flurry")


def momentum():
    """A skull crowned by a green refresh loop — every kill winds Shadow Step
    back to ready."""
    im = canvas()
    # skull built from the vanilla banner skull, recoloured wither-bone
    skull = _skull()
    im.alpha_composite(skull, (4, 5))
    # refresh loop arcing over the skull, green = ready again
    loop = ((3, 4), (3, 3), (4, 2), (6, 1), (8, 1), (10, 1), (12, 2), (13, 3), (13, 4))
    for i, (x, y) in enumerate(loop):
        im.putpixel((x, y), READY if 1 <= i <= 7 else READY_HI)
    # arrowhead closing the loop, lower-right
    plot(im, [(13, 5), (12, 5), (14, 5), (13, 6)], READY_HI)
    # tail feather lower-left
    plot(im, [(2, 5), (2, 6), (3, 5)], READY)
    save(im, "momentum")


def _skull():
    """A clean 8x8 wither-bone skull cropped from the vanilla banner skull,
    given eye sockets, a nose and a jaw."""
    banner = vanilla("entity/shield/skull.png").crop((3, 7, 11, 12))  # 8x5 head
    head = canvas(8)
    px = banner.load()
    for y in range(banner.height):
        for x in range(banner.width):
            if px[x, y][3] > 20:
                head.putpixel((x, y), BONE)
    hp = head.load()
    # shade + features
    for x in range(8):
        if hp[x, 4][3]:
            head.putpixel((x, 4), BONE_DK)
    for x, y in ((2, 2), (3, 2), (5, 2), (6, 2)):   # eye sockets
        head.putpixel((x, y), BONE_SHADOW)
    for x, y in ((2, 3), (5, 3), (6, 3)):
        head.putpixel((x, y), (0, 0, 0, 0))
    head.putpixel((4, 3), BONE_SHADOW)              # nose
    # jaw + teeth
    out = canvas(8)
    out.alpha_composite(head, (0, 0))
    for x, y, c in ((2, 4, BONE), (4, 4, BONE), (6, 4, BONE)):
        out.putpixel((x, y), c)
    return out


def deathblow():
    """A nether star with the dagger driven across it and a bright impact
    flare — the point of the tree, every Shadow Step strike hitting harder."""
    im = canvas()
    star = vanilla("item/nether_star.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(star, (1, 1))
    dagger = moditem("iron_dagger")
    im.alpha_composite(dagger, (1, 1))
    # impact flare where the point bites, upper-right
    plot(im, [(13, 3), (14, 2), (12, 3), (13, 4), (13, 2), (14, 3)], STEEL_HI)
    outline_plus(im, 4, 12, GOLD)
    save(im, "deathblow")


def main():
    os.makedirs(ICONS, exist_ok=True)
    shadow_step()
    lightfoot()
    sidestep()
    adrenaline_rush()
    opportunist()
    razor_edge()
    expose()
    venom()
    blight()
    flense()
    shadow_flurry()
    momentum()
    deathblow()
    contact_sheet()


# ---------------------------------------------------------------------------
def contact_sheet():
    from PIL import ImageDraw
    order = ["shadow_step", "lightfoot", "sidestep", "adrenaline_rush", "opportunist",
             "razor_edge", "expose", "venom", "blight", "flense",
             "shadow_flurry", "momentum", "deathblow"]
    scale = 4
    icon = 16 * scale               # 64px on screen? no -> 32*.. handled below
    cols = 5
    cell_w, cell_h = 150, 168
    rows = (len(order) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (43, 43, 43, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(order):
        im = Image.open(os.path.join(ICONS, f"{name}.png")).convert("RGBA")
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        cx = (i % cols) * cell_w
        cy = (i // cols) * cell_h
        ox = cx + (cell_w - big.width) // 2
        oy = cy + 12
        sheet.alpha_composite(big, (ox, oy))
        label = name.replace("_", " ")
        w = d.textlength(label)
        d.text((cx + (cell_w - w) / 2, oy + big.height + 8), label, fill=(224, 224, 224, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


if __name__ == "__main__":
    main()
