"""Skill-node icons for the Shadow tree (crescent-moon constellation).

The verdict from round one: build every icon FROM a real vanilla sprite pulled
out of the client jar, recolour/crop/compose it, then add a small readable
mechanic grammar in hand-plotted pixels on top. Nothing built from nothing.

Each family here is anchored on the vanilla item the tree already assigns it
(ShadowNodes.Family), or on a mob-effect sprite when that reads the mechanic
harder — e.g. the vanilla Night Vision icon is literally a crescent moon, which
happens to be this whole tree's symbol. On top of the sprite sits one clear
cue: a heart for healing, speed-dashes for faster, a glow outline for "they
light up", a fade for "it vanishes", a plus for "more", a slash for "removed".

Compose at 16 (vanilla item resolution, so every pixel lands on the game's own
grid) then upscale 2x NEAREST to the 32px node canvas — the house norm.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "icons")

# ---- palette --------------------------------------------------------------
WHITE = (255, 255, 255, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (150, 150, 150, 220)
CYAN = (130, 240, 232, 255)          # glow-outline colour (glow squid teal)
CYAN_DIM = (90, 190, 190, 200)
ORANGE = (255, 150, 0, 255)          # the tree's eye-glow (matches invisibility.png)
ORANGE_HI = (255, 205, 70, 255)
MOON = (214, 224, 246, 255)          # pale crescent
MOON_DIM = (150, 164, 200, 255)
SHADOW = (58, 44, 82, 255)           # vanish-smoke purple
SHADOW_HI = (120, 96, 168, 255)
GOLD = (168, 140, 72, 255)           # capstone frame
GOLD_HI = (214, 184, 108, 255)
RED = (214, 44, 44, 255)             # "removed" slash


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def effect(name):
    """A mob-effect icon, cropped from its 18x18 sheet to a clean 16x16."""
    return vanilla(f"mob_effect/{name}.png").crop((1, 1, 17, 17))


def face(entity, box=(8, 8, 16, 16)):
    """The front 8x8 face off an entity head-layout texture."""
    return vanilla(f"entity/{entity}").crop(box)


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


def glow_outline(im, colour, thick=1):
    """A one-pixel halo hugging the opaque silhouette of im — vanilla's own
    Glowing effect, hand-made: a coloured outline that says 'this is lit up'."""
    src = im.load()
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    op = out.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3] > 40:
                continue
            near = False
            for dx in range(-thick, thick + 1):
                for dy in range(-thick, thick + 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 40:
                        near = True
            if near:
                op[x, y] = colour
    return out


def plot(im, pts, colour):
    for x, y in pts:
        if 0 <= x < im.width and 0 <= y < im.height:
            im.putpixel((x, y), colour)


def crescent(im, ox, oy, colour=MOON, shade=MOON_DIM):
    """A little 4x5 waxing crescent, top-left corner at (ox, oy)."""
    lit = [(1, 0), (2, 0), (0, 1), (1, 1), (0, 2), (0, 3), (1, 4), (2, 4)]
    dim = [(2, 1), (1, 2), (1, 3), (2, 3)]
    plot(im, [(ox + x, oy + y) for x, y in lit], colour)
    plot(im, [(ox + x, oy + y) for x, y in dim], shade)


def heart(size=8):
    return vanilla("gui/sprites/hud/heart/full.png").resize((size, size), Image.NEAREST)


def corner_frame(im, colour=GOLD, hi=GOLD_HI):
    """L-brackets in the four corners: the 'this is a capstone' mark."""
    ticks = []
    for cx, cy, ax, ay in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        ticks += [(cx, cy), (cx + ax, cy), (cx, cy + ay)]
    plot(im, ticks, colour)
    plot(im, [(0, 0), (15, 0), (0, 15), (15, 15)], hi)


def save(im, name):
    im.resize((32, 32), Image.NEAREST).save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


# ---- the outer arc: surviving the dark ------------------------------------
def night_eyes():
    """The vanilla Night Vision icon IS a crescent moon on a starry sky — the
    tree's own symbol. Two glowing eyes wake in the dark below it: Night Eyes."""
    im = effect("night_vision")
    plot(im, [(5, 12), (6, 12), (9, 12), (10, 12)], ORANGE_HI)
    plot(im, [(5, 13), (6, 13), (9, 13), (10, 13)], ORANGE)
    save(im, "night_eyes")


def umbral_sight():
    """A creeper's face — the most-known 'hostile' in the game — wrapped in the
    cyan glow outline vanilla paints on Glowing mobs: enemies light up for you."""
    cre = face("creeper/creeper.png").resize((12, 12), Image.NEAREST)
    im = canvas()
    im.alpha_composite(glow_outline(cre, CYAN, thick=1), (2, 2))
    im.alpha_composite(cre, (2, 2))
    save(im, "umbral_sight")


def swift_shadow():
    """Sugar — the vanilla Speed source — flung to the right, three white
    speed-dashes streaming off it: sneaking without the slow."""
    sugar = vanilla("item/sugar.png").resize((12, 12), Image.NEAREST)
    im = canvas()
    im.alpha_composite(sugar, (4, 3))
    for y, x0 in ((6, 0), (9, 1), (12, 2)):
        plot(im, [(x0, y), (x0 + 1, y)], ARC_DIM)
        plot(im, [(x0 + 2, y), (x0 + 3, y)], WHITE)
    save(im, "swift_shadow")


def dark_mending():
    """A glistering melon slice — vanilla's regen ingredient — with a heart
    lifting off it, under a small crescent: hearts regrow while you're hidden."""
    im = canvas()
    im.alpha_composite(vanilla("item/glistering_melon_slice.png"), (0, 1))
    im.alpha_composite(heart(8), (7, 0))
    crescent(im, 0, 0)
    save(im, "dark_mending")


def dim_presence():
    """A phantom membrane thinned almost to nothing — you fade from the world —
    and an eye struck through: hostiles look right where you are and find no one."""
    im = canvas()
    im.alpha_composite(faded(vanilla("item/phantom_membrane.png"), 85), (0, 0))
    # a bold almond eye: dark rim, white lens, a dim pupil
    dark = (26, 24, 38, 255)
    plot(im, [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (4, 7), (11, 7),
              (6, 8), (7, 8), (8, 8), (9, 8)], dark)
    plot(im, [(6, 6), (7, 6), (8, 6), (9, 6), (5, 7), (6, 7), (9, 7), (10, 7)],
         (238, 238, 246, 255))
    plot(im, [(7, 7), (8, 7)], (78, 66, 118, 255))
    # struck through, dark-edged so the red reads over the white lens
    slash = [(3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4), (11, 3), (12, 2)]
    for x, y in slash:
        im.putpixel((x, y), (96, 12, 12, 255))
    for x, y in slash:
        if x + 1 < 16:
            im.putpixel((x + 1, y), RED)
    save(im, "dim_presence")


def cleansing_veil():
    """A milk bucket — the one thing in vanilla that wipes every effect — and a
    poison mote struck out beside it: casting the veil scrubs your ailments."""
    im = canvas()
    im.alpha_composite(vanilla("item/milk_bucket.png"), (0, 1))
    blob = effect("poison").resize((8, 8), Image.NEAREST)
    im.alpha_composite(faded(blob, 200), (8, 0))
    for i in range(7):
        plot(im, [(8 + i, 7 - i)], RED)
    plot(im, [(8, 6), (14, 0)], (255, 120, 120, 255))
    save(im, "cleansing_veil")


# ---- the inner arc: killing in it -----------------------------------------
def stillness():
    """A gold clock, and a plus: standing still stretches the vanish timer,
    the invisibility simply lasts longer."""
    im = canvas()
    im.alpha_composite(vanilla("item/clock_00.png"), (0, 1))
    plot(im, [(12, 1), (12, 2), (12, 3), (12, 4), (12, 5),
              (10, 3), (11, 3), (13, 3), (14, 3)], WHITE)
    plot(im, [(12, 0)], ORANGE_HI)
    save(im, "stillness")


def first_strike():
    """The iron sword lunging out of a dark echo of itself — the strike that
    comes from nowhere — a white impact star bursting at its point."""
    sword = vanilla("item/iron_sword.png")
    im = canvas()
    im.alpha_composite(faded(sword.rotate(-14, resample=Image.NEAREST, expand=False), 90), (-2, 2))
    im.alpha_composite(sword, (0, 0))
    # impact star at the tip (upper-right of the blade)
    star = [(12, 2), (13, 1), (14, 2), (13, 3), (11, 2), (13, 0), (15, 2), (13, 4)]
    plot(im, star, WHITE)
    plot(im, [(13, 2)], ORANGE_HI)
    save(im, "first_strike")


def bloodrush():
    """A pile of redstone — vanilla's Strength reagent, blood-red for the rush —
    and a surge-arrow spiking off it: a kill in the dark spikes your power."""
    im = canvas()
    im.alpha_composite(vanilla("item/redstone.png"), (0, 3))
    arrow = canvas()
    fill = [(11, 1), (10, 2), (11, 2), (12, 2), (9, 3), (10, 3), (11, 3), (12, 3), (13, 3),
            (11, 4), (12, 4), (11, 5), (12, 5), (11, 6), (12, 6), (11, 7), (12, 7)]
    plot(arrow, fill, (235, 70, 70, 255))
    plot(arrow, [(11, 1), (10, 2), (9, 3), (11, 4), (11, 5), (11, 6)], (255, 152, 152, 255))
    im.alpha_composite(glow_outline(arrow, (48, 8, 8, 255)), (0, 0))
    im.alpha_composite(arrow, (0, 0))
    save(im, "bloodrush")


def reaper():
    """A wither rose — the black bloom death leaves where a mob fell — its
    withering wisps curling up into a bright heart: the kill hands you life."""
    im = canvas()
    rose = vanilla("block/wither_rose.png")
    im.alpha_composite(rose, (-3, 4))
    # withering wisps rising off the flower
    for x, y, c in ((6, 6, SHADOW_HI), (7, 5, SHADOW), (5, 4, SHADOW),
                    (7, 7, SHADOW_HI)):
        im.putpixel((x, y), c)
    im.alpha_composite(heart(9), (6, 0))
    plot(im, [(8, 8), (8, 9), (9, 7)], (255, 120, 120, 255))
    save(im, "reaper")


def ghost_armor():
    """A chainmail chestplate dissolving left-to-right into nothing but a few
    stray sparkles — the armour goes invisible with its wearer."""
    src = vanilla("item/chainmail_chestplate.png")
    im = canvas()
    px = src.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if not a:
                continue
            # fade harder toward the right edge
            k = max(0, 255 - int((x - 4) * 26)) if x > 4 else 255
            im.putpixel((x, y), (r, g, b, a * k // 255))
    for x, y in ((12, 4), (14, 7), (13, 10), (15, 12), (11, 13)):
        im.putpixel((x, y), MOON if (x + y) % 2 else SHADOW_HI)
    save(im, "ghost_armor")


# ---- the crown row: capstones and the crown -------------------------------
def last_shadow():
    """The Totem of Undying — vanilla's cheat-death — trailing up into shadow
    smoke as it fires you invisible. Corner-framed: a capstone."""
    im = canvas()
    im.alpha_composite(vanilla("item/totem_of_undying.png"), (0, 1))
    for x, y, c in ((2, 3, SHADOW_HI), (1, 1, SHADOW), (13, 3, SHADOW_HI),
                    (14, 1, SHADOW), (3, 0, SHADOW_HI), (12, 0, SHADOW)):
        im.putpixel((x, y), c)
    corner_frame(im)
    save(im, "last_shadow")


def predator():
    """A skull, eyes lit the tree's hunting orange — the same glare the active
    icon wears — the apex of the dark that never has to leave it. Capstone."""
    im = canvas()
    sk = face("skeleton/skeleton.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(sk, (1, 1))
    # burn the eye-sockets orange
    plot(im, [(5, 7), (6, 7), (9, 7), (10, 7)], ORANGE)
    plot(im, [(5, 6), (9, 6)], ORANGE_HI)
    corner_frame(im)
    save(im, "predator")


def umbral_mastery():
    """The Eye of Ender turned all-seeing over a crescent moon, haloed in
    shadow-light — the crown of the constellation. Full-framed and brightest."""
    im = canvas()
    eye = vanilla("item/ender_eye.png").resize((12, 12), Image.NEAREST)
    # a soft shadow halo behind the eye
    im.alpha_composite(faded(glow_outline(eye, SHADOW_HI, thick=1), 220), (2, 2))
    im.alpha_composite(eye, (2, 2))
    corner_frame(im, colour=GOLD_HI, hi=WHITE)
    save(im, "umbral_mastery")


# the drawing order of the two crescent arcs, bottom tips up to the crown
ORDER = ["night_eyes", "umbral_sight", "swift_shadow", "dark_mending", "dim_presence",
         "cleansing_veil", "stillness", "first_strike", "bloodrush", "reaper",
         "ghost_armor", "last_shadow", "predator", "umbral_mastery"]


def contact_sheet():
    """Every icon at 4x NEAREST, family name under each, on the dark tree bg."""
    scale, pad, labelh, cols = 4, 18, 26, 5
    cell = 32 * scale
    rows = (len(ORDER) + cols - 1) // cols
    W = cols * (cell + pad) + pad
    H = rows * (cell + labelh + pad) + pad
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 18)
    except OSError:
        font = ImageFont.load_default()
    for i, name in enumerate(ORDER):
        im = Image.open(os.path.join(DST, name + ".png")).convert("RGBA")
        big = im.resize((cell, cell), Image.NEAREST)
        cx = pad + (i % cols) * (cell + pad)
        cy = pad + (i // cols) * (cell + labelh + pad)
        sheet.paste(big, (cx, cy), big)
        tw = d.textlength(name, font=font)
        d.text((cx + (cell - tw) / 2, cy + cell + 4), name, fill=(228, 228, 228, 255), font=font)
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(DST, exist_ok=True)
    night_eyes()
    umbral_sight()
    swift_shadow()
    dark_mending()
    dim_presence()
    cleansing_veil()
    stillness()
    first_strike()
    bloodrush()
    reaper()
    ghost_armor()
    last_shadow()
    predator()
    umbral_mastery()
    contact_sheet()


if __name__ == "__main__":
    main()
