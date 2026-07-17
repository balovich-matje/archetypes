"""Generate 32x32 skill-node icons for the Marksman constellation (bake-off
entry, round two). Same pipeline as notes/art/make_node_icons.py and the
round-one wizard/priest scripts: vanilla sprites pulled straight from the
client jar (so palettes match the game for free), composed on a transparent
32x32 canvas, with a handful of hand-plotted effect pixels on top. Every
icon starts from a real vanilla sprite; nothing is built from bare plotted
pixels alone. One icon per family, MINOR skipped.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "icons")

# ---------------------------------------------------------------- palettes
# House motion / callout accents, reused from the Slayer and Wizard scripts
# so the whole mod's node icons share one visual language.
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)
GOLD = (255, 236, 160, 255)
WHITE = (255, 255, 255, 255)
RED = (200, 32, 32, 255)

# Fire (sampled from item/fire_charge.png's own embers).
FIRE_CORE = (255, 214, 90, 255)
FIRE_MID = (240, 120, 30, 255)
FIRE_DIM = (200, 70, 20, 210)

# Iron (Slayer's own iron_spikes palette, reused for Piercing Tips).
IRON_LIGHT = (238, 238, 244, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def item2x(im):
    """A 16px vanilla sprite, upscaled 2x NEAREST to the 32px canvas — the
    house norm so these icons match the item art's own blockiness."""
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


def paste_centered(base, im, cx=16, cy=16):
    base.alpha_composite(im, (cx - im.width // 2, cy - im.height // 2))


def dot(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def save(im, name):
    os.makedirs(DST, exist_ok=True)
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# --------------------------------------------------------------- families
def true_shot():
    """The Spectral Arrow itself (vanilla's own glowing arrow — the
    family's assigned icon), sized down and tucked to the bottom-left so
    its own flight line has room to keep going: a bold dashed trail
    continues dead straight off the tip clear across the top-right
    corner, ending in a bright flare. A normal shot would already be
    curving down by here; this one refuses to bend, and hits twice as
    hard doing it."""
    im = canvas()
    arrow = scale(vanilla("item/spectral_arrow.png"), 1.6)
    im.alpha_composite(arrow, (0, 6))
    # Native tip cluster (14,2)-(14,3) at 1.6x + (0,6) offset lands ~(22,9).
    # Continue the same 45-degree line on out to the corner, dashed.
    for x, y, c in ((24, 7, GOLD), (26, 5, GOLD), (28, 3, GOLD), (30, 1, WHITE)):
        dot(im, x, y, c)
    for x, y, c in ((25, 6, ARC_DIM), (27, 4, ARC_DIM), (29, 2, ARC_DIM)):
        dot(im, x, y, c)
    # The flare where it "lands" at the frame corner.
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        dot(im, 30 + dx, 1 + dy, GOLD)
    dot(im, 30, 1, WHITE)
    save(im, "true_shot")


def conservation():
    """A fired arrow (vanilla's own, the family's assigned icon) with a
    faint ghost twin drifting back behind it, low and left of the shot —
    the house's established "extra copy" grammar (Wizard's Echo, Slayer's
    Bulwark), here standing for the shot that comes back to you instead of
    breaking."""
    im = canvas()
    arrow = item2x(vanilla("item/arrow.png"))
    ghost = faded(arrow, 130)
    im.alpha_composite(ghost, (-5, 4))
    im.alpha_composite(arrow, (0, 0))
    save(im, "conservation")


def pinning():
    """The cobweb (the family's assigned icon, vanilla's own "this slows
    you" prop) filling the frame, with vanilla's own Slowness status icon
    badged in the corner on a small dark backing chip — the same "sprite
    plus the exact status-effect badge it applies" pattern the Slayer tree
    uses for Concussion and Shield Slam, so the rank-up (I, then II) reads
    without extra clutter. The backing chip is what keeps the badge from
    dissolving into the web's own white lattice."""
    im = canvas()
    web = item2x(vanilla("block/cobweb.png"))
    im.alpha_composite(web, (0, 0))
    badge = scale(vanilla("mob_effect/slowness.png"), 0.95)
    bx, by = 32 - badge.width - 1, 32 - badge.height - 1
    chip = Image.new("RGBA", (badge.width + 2, badge.height + 2), (26, 26, 30, 235))
    ImageDraw.Draw(chip).rectangle(
        (0, 0, chip.width - 1, chip.height - 1), outline=(210, 210, 215, 255))
    im.alpha_composite(chip, (bx - 1, by - 1))
    im.alpha_composite(badge, (bx, by))
    save(im, "pinning")


def swift_flight():
    """The feather (the family's assigned icon) with speed-line dashes
    streaming off its trailing edge — the exact Velocity motif the Wizard
    tree already uses for the same "projectile flies faster" mechanic, on
    the same anchor sprite, reused on purpose."""
    im = canvas()
    feather = item2x(vanilla("item/feather.png"))
    im.alpha_composite(feather, (2, 0))
    for i, y in enumerate((9, 15, 21)):
        for x in range(0, 6 - i):
            dot(im, x, y, ARC if x > 2 else ARC_DIM)
    save(im, "swift_flight")


def acrobatics():
    """The rabbit foot (the family's assigned icon — vanilla's own jump/
    agility charm), pushed up-right, with two short crisp motion dashes
    trailing off its low-left edge and a small dust puff under it — a
    quick hop-roll forward, not a sustained sprint, so the trail is short
    and blunt rather than Swift Flight's long streaks."""
    im = canvas()
    foot = item2x(vanilla("item/rabbit_foot.png"))
    im.alpha_composite(foot, (4, 0))
    for y, c in ((22, ARC), (26, ARC_DIM)):
        for x in range(0, 5):
            dot(im, x, y, c)
    for x, y, c in ((2, 30, ARC_DIM), (5, 31, ARC_DIM), (8, 30, ARC_DIM)):
        dot(im, x, y, c)
    save(im, "acrobatics")


def nimble_draw():
    """A bow at full, held draw (vanilla's own bow_pulling_2 pose — the
    taut triangle every player already reads as "string all the way
    back"), sized down and tucked to the top so a clear strip along the
    bottom is free for two bold forward-step chevrons: still walking,
    string still drawn, none of the usual crawl."""
    im = canvas()
    bow = scale(vanilla("item/bow_pulling_2.png"), 1.7)
    im.alpha_composite(bow, (2, 0))
    chevrons = (25, 29)
    for cy in chevrons:
        c = GOLD if cy == 25 else (255, 200, 90, 200)
        for i in range(3):
            dot(im, 10 - i, cy - i, c)
            dot(im, 10 - i, cy + i, c)
    save(im, "nimble_draw")


def rapid_reload():
    """The crossbow at rest (the branch's default pose) with a cluster of
    the family's own assigned icon — vanilla's redstone shard — sparking at
    the stock, as if the reload just got a jolt of charge from the kill
    that fed it, plus a pair of fast-forward chevrons so the charge reads
    as speed and not just decoration."""
    im = canvas()
    xbow = item2x(vanilla("item/crossbow_standby.png"))
    im.alpha_composite(xbow, (0, 0))
    dust = scale(vanilla("item/redstone.png"), 1.1)
    im.alpha_composite(dust, (1, 16))
    for x, y, c in ((14, 16, RED), (16, 14, GOLD), (12, 19, RED)):
        dot(im, x, y, c)
    for x, y in ((20, 25), (22, 27), (24, 29)):
        dot(im, x, y, GOLD)
        dot(im, x + 2, y, WHITE)
    save(im, "rapid_reload")


def combustion():
    """The fire charge (the family's assigned icon), shrunk slightly to
    leave a clear ring of open frame around it, with a small arrowhead
    stuck into its top edge and a burst of blast-ticks thrown well clear
    of the fireball's own texture on all sides — this isn't a fireball
    sitting in the frame, it's the burning target it just detonated."""
    im = canvas()
    charge = scale(vanilla("item/fire_charge.png"), 1.7)
    paste_centered(im, charge, cx=16, cy=17)
    arrowhead = item2x(vanilla("item/arrow.png")).crop((20, 0, 32, 12))
    im.alpha_composite(arrowhead, (16, 0))
    burst = ((16, 1), (16, 31), (1, 16), (31, 16),
             (5, 5), (27, 5), (5, 27), (27, 27))
    for i, (x, y) in enumerate(burst):
        dot(im, x, y, FIRE_CORE if i % 2 == 0 else FIRE_MID)
    inner = ((16, 5), (16, 27), (5, 16), (27, 16))
    for x, y in inner:
        dot(im, x, y, FIRE_DIM)
    save(im, "combustion")


def focus():
    """The spyglass (the family's assigned icon) with a small vanilla clock
    badged in the corner and a red minus dash beside it — the same
    "reduction" mark the Wizard tree's Clarity puts on its mana orb, here
    shaving time off a cooldown instead of cost off a cast."""
    im = canvas()
    glass = item2x(vanilla("item/spyglass.png"))
    im.alpha_composite(glass, (0, 0))
    clock = scale(vanilla("item/clock_00.png"), 0.95)
    im.alpha_composite(clock, (0, 19))
    for dx in (0, 1, 2):
        dot(im, 13 + dx, 24, RED)
    save(im, "focus")


def piercing_tips():
    """A whole (not cropped, so its silhouette stays recognisable) but
    small, dim chestplate tucked in the top-left, a bright dashed line
    punching straight out of it, and the family's own assigned icon — the
    iron nugget, blown up big — sitting clear of the armour at the
    bottom-right as the tip that came out the other side: the shot that
    doesn't stop at the plate."""
    im = canvas()
    plate = scale(vanilla("item/iron_chestplate.png"), 1.3)
    im.alpha_composite(faded(plate, 165), (0, 0))
    # The exit hole, right at the plate's lower edge.
    outline = (30, 30, 34, 255)
    for x, y in ((13, 15), (14, 15), (13, 16), (14, 16)):
        dot(im, x, y, outline)
    dot(im, 13, 15, IRON_LIGHT)
    dot(im, 14, 16, IRON_LIGHT)
    # The pierce line, clear of the plate's own silhouette, on to the tip.
    for x, y in ((17, 19), (19, 21), (21, 23)):
        dot(im, x, y, ARC)
    nugget = scale(vanilla("item/iron_nugget.png"), 2.3)
    paste_centered(im, nugget, cx=24, cy=25)
    save(im, "piercing_tips")


def seeker_arrow():
    """The Eye of Ender (the family's assigned icon — vanilla's own
    "seeks the stronghold" homer) on a bent dashed path curving hard
    toward a small red target mark — the same steer-itself grammar the
    Wizard tree's Seeker Missile already uses for a homing shot, bent onto
    a fresh path here so it reads as this tree's own hostile-seeking arrow."""
    im = canvas()
    eye = scale(vanilla("item/ender_eye.png"), 1.7)
    paste_centered(im, eye, cx=11, cy=11)
    path = ((21, 5), (25, 8), (27, 13), (26, 18), (23, 22))
    for i, (x, y) in enumerate(path):
        dot(im, x, y, ARC if i < 3 else ARC_DIM)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
        dot(im, 22 + dx, 27 + dy, RED)
    dot(im, 22, 27, (255, 90, 90, 255))
    save(im, "seeker_arrow")


def snap_shot():
    """The crossbow already loaded (vanilla's own crossbow_arrow pose —
    nothing left to draw, nothing left to load) with a bright muzzle burst
    thrown wide at the front: the shot that leaves the instant you press
    the button, no wind-up at all."""
    im = canvas()
    xbow = item2x(vanilla("item/crossbow_arrow.png"))
    im.alpha_composite(xbow, (0, 0))
    for dx, dy in ((0, -3), (0, 3), (-3, 0), (3, 0),
                   (-2, -2), (2, -2), (-2, 2), (2, 2)):
        dot(im, 27 + dx, 6 + dy, GOLD)
    dot(im, 27, 6, WHITE)
    for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0)):
        dot(im, 27 + dx, 6 + dy, ARC_DIM)
    save(im, "snap_shot")


# Render order follows the constellation: the bottom tip, up the string
# (crossbow branch), up the stave (bow branch), the shared shaft, the top
# tip — see MarksmanNodes.
FAMILIES = [
    "true_shot",
    "piercing_tips", "rapid_reload", "pinning", "combustion", "snap_shot",
    "acrobatics", "nimble_draw", "swift_flight", "seeker_arrow",
    "conservation",
    "focus",
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
    true_shot()
    conservation()
    pinning()
    swift_flight()
    acrobatics()
    nimble_draw()
    rapid_reload()
    combustion()
    focus()
    piercing_tips()
    seeker_arrow()
    snap_shot()
    contact_sheet()


if __name__ == "__main__":
    main()
