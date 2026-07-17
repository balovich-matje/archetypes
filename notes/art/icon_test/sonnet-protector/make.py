"""Generate 32x32 skill-node icons for the Protector tree (bake-off entry).

Same method as notes/art/make_node_icons.py (the Slayer set): every icon
starts from a real vanilla sprite pulled straight from the client jar, then
gets recolored / cropped / composed with a few hand-plotted effect pixels on
top. Built at a 16x16 working canvas (matches the vanilla sprites' native
scale) and saved upscaled 2x NEAREST to 32x32, which is the size the
Protector Family enum already declares for its icons.

Nothing here touches the mod's real assets - output lands in ./icons next to
this script for a side-by-side compare against the other model's pass.

Usage: python3 make.py
"""
import math
import os
import zipfile

from PIL import Image, ImageDraw

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "icons")

# ---------------------------------------------------------------- palette --
IRON = (196, 196, 204, 255)
IRON_DARK = (108, 108, 118, 255)
IRON_LIGHT = (238, 238, 244, 255)
WOOD = (146, 104, 56, 255)
WOOD_DARK = (98, 66, 32, 255)
WOOD_LIGHT = (178, 132, 78, 255)
GOLD = (255, 210, 64, 255)
GOLD_DARK = (168, 122, 20, 255)
ARC = (255, 255, 255, 255)
ARC_DIM = (180, 180, 180, 200)
LEATHER = (150, 82, 52, 255)
LEATHER_DARK = (104, 54, 32, 255)
BLOOD = (200, 32, 32, 255)


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


def line(im, x0, y0, x1, y1, c, c_shadow=None):
    """A hand-plotted straight stroke from (x0,y0) to (x1,y1) - used for the
    handful of effect marks (straps, speed streaks) that aren't sampled from
    a vanilla sprite."""
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        put(im, x, y, c)
        if c_shadow is not None:
            put(im, x, y + 1, c_shadow)


def save(im, name):
    im.resize((im.width * 2, im.height * 2), Image.NEAREST).save(
        os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# --------------------------------------------------------- shield builder --
# gui/sprites/container/slot/shield.png is vanilla's own greyed-out "empty
# shield slot" placeholder: a hollow 16x16 kite-shield OUTLINE. It is the one
# flat vanilla sprite that is actually shield-SHAPED (the real item icon is a
# 3D render, and the front-plate texture crop used elsewhere in this mod is
# just a rectangular plank UV island). Recoloring its rim and flood-filling
# the interior gives a small, instantly-readable, genuinely-vanilla shield
# glyph to build every shield-family icon on.
def _shield_outline():
    return vanilla("gui/sprites/container/slot/shield.png")


def shield_edges():
    """Per-row (leftmost, rightmost) rim column of the real outline sprite -
    lets effects (spikes) attach exactly to the shield's actual silhouette
    instead of guessed coordinates."""
    src = _shield_outline()
    px = src.load()
    edges = {}
    for y in range(16):
        xs = [x for x in range(16) if px[x, y][3]]
        if xs:
            edges[y] = (min(xs), max(xs))
    return edges


def shield_icon(fill=WOOD, fill_dark=WOOD_DARK, rim=IRON_DARK, rim_hi=IRON_LIGHT):
    src = _shield_outline()
    im = canvas()
    px = src.load()
    out = im.load()
    # Recolor the rim: top rows get the highlight, the rest the dark rim.
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a:
                out[x, y] = rim_hi if y <= 2 else rim
    # Scanline flood-fill: between the first and last rim pixel on each row,
    # any still-transparent pixel is interior.
    for y in range(16):
        xs = [x for x in range(16) if out[x, y][3]]
        if len(xs) < 2:
            continue
        lo, hi = min(xs), max(xs)
        for x in range(lo + 1, hi):
            if out[x, y][3] == 0:
                out[x, y] = fill
    # A one-pixel-narrower shading pass down the left/centre for volume, and
    # a seam so it doesn't read as a flat sticker.
    for y in range(3, 14):
        xs = [x for x in range(16) if out[x, y] == fill]
        if xs:
            put(im, xs[0], y, fill_dark)
            mid = xs[len(xs) // 2]
            put(im, mid, y, fill_dark)
    return im


def main():
    os.makedirs(DST, exist_ok=True)
    bash()
    slam()
    cooldown()
    knockback()
    wide()
    unbreaking()
    spikes()
    rush()
    braced()
    reflect()
    taunt()
    omni_block()
    ground_slam()
    contact_sheet()


ORDER = ["bash", "slam", "cooldown", "knockback", "wide", "unbreaking", "spikes",
         "rush", "braced", "reflect", "taunt", "omni_block", "ground_slam"]


def contact_sheet():
    """Every icon at 4x NEAREST, labeled, on the #2b2b2b background this
    bake-off compares against."""
    cols, cell = 4, 160
    rows = (len(ORDER) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(ORDER):
        icon = Image.open(os.path.join(DST, f"{name}.png")).convert("RGBA")
        big = icon.resize((icon.width * 4, icon.height * 4), Image.NEAREST)
        x, y = (i % cols) * cell, (i // cols) * cell
        sheet.alpha_composite(big, (x + (cell - big.width) // 2, y + 10))
        draw.text((x + 10, y + cell - 20), name, fill=(255, 255, 255, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


# ---------------------------------------------------------------- icons ---

def bash():
    """Shield Bash: the shove itself - shield up, a bright starburst
    landing square in front of it."""
    im = shield_icon()
    core = (255, 244, 200, 255)
    burst = ((13, 7, core), (12, 6, ARC), (12, 8, ARC), (14, 6, ARC), (14, 8, ARC),
             (11, 7, ARC_DIM), (15, 7, ARC_DIM), (13, 5, ARC_DIM), (13, 9, ARC_DIM))
    for x, y, c in burst:
        put(im, x, y, c)
    save(im, "bash")


def slam():
    """Shield Slam: the same bash burst, hotter and wider, with a Strength
    badge (vanilla's own effect icon) riding the corner - the mod's own
    '+damage' tell."""
    im = shield_icon()
    hot = (255, 90, 40, 255)
    core = (255, 220, 140, 255)
    burst = ((13, 8, core), (12, 7, hot), (12, 9, hot), (14, 7, hot), (14, 9, hot),
             (11, 8, hot), (15, 8, hot), (13, 6, hot), (13, 10, hot),
             (11, 6, ARC_DIM), (15, 10, ARC_DIM), (15, 6, ARC_DIM), (11, 10, ARC_DIM))
    for x, y, c in burst:
        put(im, x, y, c)
    badge = vanilla("mob_effect/strength.png").resize((7, 7), Image.NEAREST)
    im.alpha_composite(badge, (9, 0))
    save(im, "slam")


def cooldown():
    """Quick Recovery: vanilla's own resting clock, wound backwards - a
    bold counter-clockwise rewind ring around the face so it reads as time
    coming BACK, not forward."""
    clock = vanilla("item/clock_00.png")
    im = canvas()
    im.alpha_composite(clock, (0, 0))
    cx, cy, r = 8, 8, 8
    for deg in range(30, 300, 18):
        a = math.radians(deg)
        x = round(cx + r * math.cos(a))
        y = round(cy + r * math.sin(a))
        put(im, x, y, ARC)
    # Arrowhead at the low end of the sweep, barb pointing counter-clockwise.
    put(im, 1, 12, ARC)
    put(im, 2, 11, ARC)
    put(im, 1, 10, ARC)
    save(im, "cooldown")


def knockback():
    """Concussive Blow: the piston's own crate face as the housing, an iron
    ram shoved hard out of it - a bigger, blunter punch than Bash's spark,
    since damage is traded away for the extra shove."""
    housing = vanilla("block/piston_top.png").resize((11, 16), Image.NEAREST)
    im = canvas()
    im.alpha_composite(housing, (0, 0))
    # The ram: a stubby grey arm punched out to the right.
    for x in range(10, 14):
        for y in range(6, 10):
            put(im, x, y, IRON if 7 <= y <= 8 else IRON_DARK)
    for y in range(6, 10):
        put(im, 13, y, IRON_LIGHT)
    # Impact chevrons, bigger and blunter than Bash's starburst.
    chevrons = ((15, 5), (14, 4), (15, 9), (14, 11), (16, 7))
    for x, y in chevrons:
        put(im, x, y, ARC)
    for x, y in ((13, 3), (13, 12)):
        put(im, x, y, ARC_DIM)
    save(im, "knockback")


def wide():
    """Wide Swings: the shield under a bash arc thrown all the way from one
    side to the other, instead of a single-target burst."""
    im = shield_icon()
    arc = ((0, 6), (0, 5), (1, 3), (2, 2), (3, 1), (5, 0), (6, 0), (8, 0),
           (9, 0), (11, 1), (12, 2), (13, 3), (14, 5), (14, 6))
    for x, y in arc:
        put(im, x, y, ARC)
        put(im, x, y + 1, ARC_DIM)
    save(im, "wide")


def unbreaking():
    """Reinforced Straps: the shield with a real leather-brown strap belted
    across it corner to corner, iron rivets pinning the ends down - built-in
    Unbreaking, worn right on the sleeve."""
    im = shield_icon()
    line(im, 2, 2, 13, 11, LEATHER, LEATHER_DARK)
    line(im, 2, 3, 13, 12, (176, 104, 64, 255), LEATHER_DARK)
    for x, y in ((2, 2), (13, 12)):
        put(im, x - 1, y, IRON_DARK)
        put(im, x, y, IRON_LIGHT)
        put(im, x + 1, y, IRON_DARK)
    save(im, "unbreaking")


def spikes():
    """Iron Spikes: the same shield, now bristling with short iron spikes
    driven straight out from its real rim - Thorns worn on the shield
    itself instead of drawn as a stray caltrop off to one side."""
    edges = shield_edges()
    im = shield_icon()
    for y in (3, 6, 9, 12):
        lx, rx = edges[y]
        put(im, lx - 1, y, IRON)
        put(im, lx - 2, y, IRON_LIGHT)
        put(im, rx + 1, y, IRON)
        put(im, rx + 2, y, IRON_LIGHT)
    # The one spike that just caught someone.
    put(im, edges[6][1] + 3, 6, BLOOD)
    put(im, edges[6][1] + 2, 5, BLOOD)
    save(im, "spikes")


def rush():
    """Shield Rush: the shield leaning into a sprint, three bold speed
    streaks crossing its face and a wind-charge swirl (vanilla's own burst
    sprite, shrunk) kicked up at the heel."""
    im = shield_icon()
    for sx, sy, ex, ey in ((1, 3, 6, 2), (0, 7, 6, 6), (1, 12, 6, 10)):
        line(im, sx, sy, ex, ey, ARC)
        put(im, sx, sy, ARC_DIM)
    gust = vanilla("item/wind_charge.png").resize((8, 8), Image.NEAREST)
    im.alpha_composite(gust, (-3, 8))
    save(im, "rush")


def braced():
    """Braced: the shield with a small gold clock badge riding the corner,
    backed by a dark disc so it pops, and a downward tick beside it - every
    block shaves the second right off."""
    im = shield_icon()
    backdrop = canvas()
    for x in range(16):
        for y in range(16):
            if (x - 12) ** 2 + (y - 4) ** 2 <= 16:
                put(backdrop, x, y, IRON_DARK)
    im.alpha_composite(backdrop, (0, 0))
    clock = vanilla("item/clock_00.png").resize((7, 7), Image.NEAREST)
    im.alpha_composite(clock, (9, 1))
    for x, y in ((14, 8), (15, 9), (13, 9)):
        put(im, x, y, GOLD)
    put(im, 14, 10, GOLD_DARK)
    save(im, "braced")


def reflect():
    """Reflection: an arrow arriving from below-left, the same arrow
    already leaving above-right, flipped - the shield sends it straight
    back the way it came."""
    im = shield_icon()
    arrow = vanilla("item/arrow.png").resize((8, 8), Image.NEAREST)
    # Vanilla's arrow sprite points up-right, tail at bottom-left. Flipping
    # it end-for-end gives an arrow pointing down-left - the "incoming"
    # shot, tip aimed at the shield's face.
    incoming = arrow.transpose(Image.ROTATE_180)
    im.alpha_composite(incoming, (0, 8))
    # The real arrow (tip up-right, unflipped) is the "outgoing" shot,
    # already leaving the way it's now headed.
    im.alpha_composite(arrow, (8, 0))
    # The bounce itself, right where the two paths meet on the shield face.
    for x, y, c in ((7, 7, ARC), (8, 6, ARC), (6, 8, ARC_DIM), (9, 5, ARC_DIM)):
        put(im, x, y, c)
    save(im, "reflect")


def taunt():
    """Taunt: the goat horn, mid-call - sound rings arcing out from the
    bell, the outermost gone hostile-red to say the call is a challenge,
    not a greeting."""
    horn = vanilla("item/goat_horn.png")
    im = canvas()
    im.alpha_composite(horn, (0, 0))
    # The bell flares open toward the top-left; fan the call out into the
    # open top-right corner so it never overlaps the horn itself.
    near = ((10, 2), (11, 0), (9, 4))
    far = ((13, 1), (14, 3), (12, 5))
    red = ((15, 2), (14, 5))
    for x, y in near:
        put(im, x, y, ARC)
    for x, y in far:
        put(im, x, y, ARC_DIM)
    for x, y in red:
        put(im, x, y, (224, 64, 32, 255))
    save(im, "taunt")


def omni_block():
    """Bulwark (capstone): the real shield flanked by two faded ghost
    copies covering the sides - blocking every direction at once - framed
    with the small gold corner ticks this set uses for capstones."""
    im = canvas()
    ghost = faded(shield_icon(), 140).resize((10, 10), Image.NEAREST)
    im.alpha_composite(ghost, (-2, 4))
    im.alpha_composite(ghost.transpose(Image.FLIP_LEFT_RIGHT), (8, 4))
    im.alpha_composite(shield_icon(), (2, 1))
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(im, x, y, GOLD)
    save(im, "omni_block")


def ground_slam():
    """Ground Slam (capstone): the shield's bash, but the ground under it
    is already cracking and shockwaves are kicking out both sides - a small
    hand-drawn anvil badge (colour-matched to the real block texture) rides
    the corner for the capstone's own name."""
    # No bright rim highlight here - it would fight the anvil badge for the
    # same top row, and a duller rim reads a little heavier anyway, fitting
    # for a capstone.
    im = shield_icon(fill=WOOD_DARK, fill_dark=(70, 48, 24, 255), rim_hi=IRON_DARK)
    ground = ((110, 78, 46, 255), (78, 54, 30, 255))
    for x in range(16):
        put(im, x, 14, ground[0])
        put(im, x, 15, ground[1])
    crack = (46, 30, 14, 255)
    for x, y in ((3, 14), (2, 15), (12, 14), (13, 15), (7, 14), (8, 15)):
        put(im, x, y, crack)
    for x, y in ((0, 12), (15, 12), (2, 10), (13, 10)):
        put(im, x, y, ARC_DIM)
    # A small anvil in profile - flat top, pinched waist, wide foot - is the
    # one silhouette every player already recognises, so it's worth hand-
    # plotting even though the block texture itself is just grey noise.
    anvil_hi = (168, 168, 176, 255)
    anvil_c = (120, 120, 128, 255)
    anvil_d = (72, 72, 80, 255)
    rows = ((0, (8, 9, 10, 11, 12, 13, 14), anvil_hi),
            (1, (9, 10, 11, 12, 13), anvil_c),
            (2, (10, 11, 12), anvil_c),
            (3, (9, 10, 11, 12, 13), anvil_d))
    for y, xs, c in rows:
        for x in xs:
            put(im, x, y, c)
    for x, y in ((7, 0), (15, 0)):
        put(im, x, y, GOLD)
    save(im, "ground_slam")


if __name__ == "__main__":
    main()
