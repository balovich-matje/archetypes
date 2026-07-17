"""Generate 32x32 skill-node icons for the Marksman tree.

House rule from round one: every icon starts FROM a real vanilla sprite pulled
straight out of the client jar (so the palette matches the game for free), then
a small readable mechanic element is composed on top — a speed-dash for faster,
a ghost copy for a spare, a stuck web for slow, a clock for cooldown, a curving
trail for homing. Nothing is a floating hand-drawn diagram.

Each icon is composed on a 16x16 canvas at the sprites' native resolution, then
upscaled 2x NEAREST to the 32x32 file — the house norm, keeping every effect
pixel the same chunky size as vanilla's own art.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "icons")
MODASSETS = os.path.normpath(os.path.join(HERE, "../../../src/main/resources/assets"))

# --- effect palette (kept tiny, borrowed straight from vanilla item ranges) ---
WHITE = (236, 236, 236, 255)      # bright motion / arc
DIM = (150, 150, 150, 215)        # trailing motion
GOLD = (255, 226, 120, 255)       # spectral power
GOLD_HI = (255, 248, 205, 255)
GREEN = (118, 214, 96, 255)       # refund / kept
GREEN_D = (66, 148, 54, 255)
RED = (214, 44, 40, 255)          # redstone charge
RED_HI = (255, 116, 96, 255)
FIRE_Y = (255, 224, 116, 255)
FIRE_O = (255, 148, 40, 255)
FIRE_R = (226, 66, 20, 255)
IMPACT = (255, 247, 205, 255)     # muzzle flash core
DARK = (28, 28, 32, 255)          # punched hole / outline


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            im = Image.open(f).convert("RGBA").copy()
    # Animated block/particle strips are tall — keep the first 16x16 frame.
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    return im


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def tint(im, color, keep=0.35):
    """Recolour a sprite toward `color`, keeping some of its own luminance so it
    still reads as the original object (used for the green 'spare' ghost)."""
    out = im.copy()
    px = out.load()
    cr, cg, cb, _ = color
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                lum = (r + g + b) / 3 / 255
                nr = int(cr * (keep + (1 - keep) * lum))
                ng = int(cg * (keep + (1 - keep) * lum))
                nb = int(cb * (keep + (1 - keep) * lum))
                px[x, y] = (nr, ng, nb, a)
    return out


def plot(im, pts, color):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), color)


def save(im, name):
    big = im.resize((32, 32), Image.NEAREST)
    big.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# ---------------------------------------------------------------------------

def true_shot():
    """Active. The spectral arrow itself, flying dead straight — a bright
    straight guide-trail behind it (no gravity bend) and a power-spark bursting
    at the tip for the x2 hit."""
    arrow = vanilla("item/spectral_arrow.png")
    im = canvas()
    # Straight trail behind the fletch, on the arrow's own axis (down-left).
    for i, (x, y) in enumerate(((3, 12), (2, 13), (1, 14))):
        plot(im, [(x, y)], GOLD if i == 0 else DIM)
    im.alpha_composite(arrow, (0, 0))
    # x2 power-spark at the tip (top-right).
    plot(im, [(13, 2), (14, 2), (13, 1), (12, 2), (14, 3)], GOLD_HI)
    plot(im, [(15, 2), (13, 0), (11, 2), (13, 4)], GOLD)
    save(im, "true_shot")


def conservation():
    """A fired arrow, and a green ghost of the same arrow kept behind it — the
    spare you didn't spend — with a couple of refund sparkles."""
    arrow = vanilla("item/arrow.png")
    im = canvas()
    ghost = faded(tint(arrow, GREEN, keep=0.30), 150)
    im.alpha_composite(ghost, (-3, 3))       # the kept spare, offset back
    im.alpha_composite(arrow, (1, -1))       # the one you fired
    plot(im, [(2, 6), (1, 5), (3, 5), (2, 4)], GREEN)   # refund sparkle
    plot(im, [(2, 5)], GREEN_D)
    plot(im, [(6, 11), (7, 12)], GREEN)
    save(im, "conservation")


def pinning():
    """The real cobweb block with an arrow driven through it — the target
    caught fast and slowed."""
    web = faded(vanilla("block/cobweb.png"), 205)
    arrow = vanilla("item/arrow.png")
    im = canvas()
    im.alpha_composite(web, (0, 0))
    # A dark seat so the arrow reads as embedded, not laid on top.
    plot(im, [(7, 8), (8, 8), (8, 9), (9, 7)], DARK)
    im.alpha_composite(arrow, (0, 0))
    save(im, "pinning")


def swift_flight():
    """An arrow laid flat and flying fast — three speed-dashes streaming off
    the fletch. Same arrow, just quicker."""
    arrow = vanilla("item/arrow.png").rotate(-45, resample=Image.NEAREST, expand=False)
    im = canvas()
    im.alpha_composite(arrow, (1, 0))
    # Speed dashes trailing the fletch (left), staggered.
    for y, x0, x1 in ((6, 0, 3), (8, 1, 5), (10, 0, 3)):
        for x in range(x0, x1 + 1):
            plot(im, [(x, y)], WHITE if x >= x1 - 1 else DIM)
    save(im, "swift_flight")


def acrobatics():
    """The rabbit's foot — agility — mid tumble: a dashed roll-arc sweeping
    forward over it into an arrowhead. The dodge roll."""
    foot = vanilla("item/rabbit_foot.png")
    im = canvas()
    im.alpha_composite(foot, (1, 2))
    # Forward roll arc, up over the top and down to the right.
    arc = ((1, 6), (2, 4), (4, 2), (6, 1), (9, 1), (11, 2), (13, 4), (14, 6))
    for i, (x, y) in enumerate(arc):
        plot(im, [(x, y)], WHITE if 2 <= i <= 5 else DIM)
    # Arrowhead on the leading (right) end, pointing forward-down.
    plot(im, [(14, 6), (15, 5), (15, 7), (13, 7), (14, 8)], WHITE)
    save(im, "acrobatics")


def nimble_draw():
    """A fully-drawn bow with the arrow nocked (the real pulling sprite) and
    speed-dashes streaming under it — moving at pace while still at full draw."""
    bow = vanilla("item/bow_pulling_2.png")
    im = canvas()
    im.alpha_composite(bow, (0, 0))
    # Movement dashes low, streaming back to the left.
    for y, x1 in ((13, 4), (15, 6)):
        for x in range(0, x1 + 1):
            plot(im, [(x, y)], WHITE if x >= x1 - 1 else DIM)
    save(im, "nimble_draw")


def rapid_reload():
    """A loaded crossbow, snapping back to ready in a redstone-charged instant
    — speed-dashes behind it, a redstone spark at the string."""
    xbow = vanilla("item/crossbow_arrow.png")
    im = canvas()
    # Speed dashes behind (left), showing the fast re-cock.
    for y, x1 in ((4, 3), (6, 4)):
        for x in range(0, x1 + 1):
            plot(im, [(x, y)], WHITE if x >= x1 - 1 else DIM)
    im.alpha_composite(xbow, (0, 0))
    # Redstone charge spark on the drawn string / stock.
    plot(im, [(11, 11), (12, 11), (11, 12), (12, 13), (13, 12)], RED)
    plot(im, [(12, 12)], RED_HI)
    plot(im, [(10, 13), (14, 11)], RED)
    save(im, "rapid_reload")


def combustion():
    """A fire charge with an arrow buried in it, blowing outward — the arrow
    that finds a burning target and detonates the whole space around it."""
    fball = vanilla("item/fire_charge.png")
    arrow = vanilla("item/arrow.png")
    im = canvas()
    # Detonation rays radiating past the fireball.
    rays = ((1, 8, FIRE_Y), (0, 9, FIRE_O), (14, 5, FIRE_Y), (15, 4, FIRE_O),
            (8, 0, FIRE_Y), (9, 0, FIRE_O), (2, 2, FIRE_O), (13, 13, FIRE_O),
            (3, 14, FIRE_R), (14, 12, FIRE_R))
    for x, y, c in rays:
        plot(im, [(x, y)], c)
    im.alpha_composite(fball, (1, 1))
    # Arrow stabbing in from the upper-left, tip into the blaze.
    im.alpha_composite(arrow.rotate(-90, resample=Image.NEAREST, expand=False), (-4, -4))
    # A couple of hot flecks thrown off.
    plot(im, [(2, 5), (13, 3), (12, 14)], FIRE_Y)
    save(im, "combustion")


def focus():
    """The spyglass — take aim — with a small clock in the corner ticking down:
    every hit shaves True Shot's cooldown."""
    glass = vanilla("item/spyglass.png")
    clock = vanilla("item/clock_00.png").resize((9, 9), Image.NEAREST)
    im = canvas()
    im.alpha_composite(glass, (0, 0))
    im.alpha_composite(clock, (7, 7))
    # Down-arrow over the clock: cooldown falling.
    plot(im, [(11, 8), (11, 9), (11, 10), (11, 11), (11, 12)], DARK)
    plot(im, [(10, 11), (12, 11), (11, 13)], DARK)
    plot(im, [(11, 9), (11, 10), (11, 11)], WHITE)
    plot(im, [(10, 11), (12, 11)], WHITE)
    save(im, "focus")


def piercing_tips():
    """An iron chestplate with an arrow punched clean through it — a dark hole
    where the shot ignored the armor."""
    plate = vanilla("item/iron_chestplate.png")
    arrow = vanilla("item/arrow.png")
    im = canvas()
    im.alpha_composite(plate, (0, 0))
    # Punched hole with a couple of split cracks.
    plot(im, [(7, 8), (8, 8), (9, 8), (7, 9), (8, 9), (9, 9), (8, 10), (8, 7)], DARK)
    plot(im, [(6, 7), (10, 10), (6, 10), (10, 7)], DARK)
    im.alpha_composite(arrow, (0, 0))
    save(im, "piercing_tips")


def seeker_arrow():
    """Capstone. The eye of ender — it seeks — with an arrow curving in to
    track it: the shot that steers itself onto the target."""
    eye = vanilla("item/ender_eye.png")
    im = canvas()
    im.alpha_composite(eye, (2, 3))
    # A homing arrow: dashed trajectory bending up from the lower-left toward
    # the eye, ending in an arrowhead aimed at it.
    trail = ((1, 15), (2, 14), (3, 12), (3, 10), (4, 8), (6, 6), (8, 5))
    for i, (x, y) in enumerate(trail):
        plot(im, [(x, y)], WHITE if i >= 3 else DIM)
    # Arrowhead pointing up-right into the eye.
    plot(im, [(8, 5), (9, 4), (10, 5), (8, 6), (9, 6)], WHITE)
    plot(im, [(7, 4)], DIM)
    save(im, "seeker_arrow")


def snap_shot():
    """Capstone. A loaded crossbow firing the instant you ask — a white muzzle
    flash bursting off the tip, speed-lines behind, the x4 shot with no draw."""
    xbow = vanilla("item/crossbow_arrow.png")
    im = canvas()
    # Speed lines behind (fired-from-the-hip instant).
    for y, x1 in ((5, 3), (7, 4)):
        for x in range(0, x1 + 1):
            plot(im, [(x, y)], WHITE if x >= x1 - 1 else DIM)
    im.alpha_composite(xbow, (0, 0))
    # Muzzle flash bursting off the front (right) tip.
    cx, cy = 14, 6
    plot(im, [(cx, cy)], IMPACT)
    plot(im, [(cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)], GOLD_HI)
    plot(im, [(cx - 2, cy), (cx + 2 if cx + 2 < 16 else 15, cy),
              (cx, cy - 2), (cx, cy + 2), (cx + 1, cy - 1), (cx + 1, cy + 1),
              (cx - 1, cy - 1), (cx - 1, cy + 1)], GOLD)
    save(im, "snap_shot")


FAMILIES = [
    true_shot, conservation, pinning, swift_flight, acrobatics, nimble_draw,
    rapid_reload, combustion, focus, piercing_tips, seeker_arrow, snap_shot,
]

LABELS = [
    "true_shot", "conservation", "pinning", "swift_flight", "acrobatics",
    "nimble_draw", "rapid_reload", "combustion", "focus", "piercing_tips",
    "seeker_arrow", "snap_shot",
]


def contact_sheet():
    from PIL import ImageDraw
    scale = 4
    cols = 4
    cell = 32 * scale
    padx, pady, labelh = 22, 20, 22
    rows = (len(LABELS) + cols - 1) // cols
    W = cols * cell + padx * (cols + 1)
    H = rows * (cell + labelh) + pady * (rows + 1)
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(LABELS):
        icon = Image.open(os.path.join(DST, f"{name}.png")).convert("RGBA")
        big = icon.resize((cell, cell), Image.NEAREST)
        r, c = divmod(i, cols)
        x = padx + c * (cell + padx)
        y = pady + r * (cell + labelh + pady)
        sheet.alpha_composite(big, (x, y))
        tw = d.textlength(name)
        d.text((x + (cell - tw) / 2, y + cell + 5), name, fill=(232, 232, 232, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(DST, exist_ok=True)
    for fn in FAMILIES:
        fn()
    contact_sheet()


if __name__ == "__main__":
    main()
