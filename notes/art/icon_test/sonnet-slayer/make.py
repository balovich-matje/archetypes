"""Generate 32x32 skill-node icons for the Slayer constellation (bake-off
test, round three).

Same pipeline as notes/art/make_node_icons.py (the tree's own canon script)
and the round-two sheets (sonnet-assassin, opus-marksman, etc): every icon
starts from a real vanilla sprite -- pulled straight from the client jar, so
palettes match the game for free -- or the mod's own greatsword textures,
composed on a transparent 32x32 canvas at 2x NEAREST, with a small readable
mechanic grammar laid on top in a few hand-plotted pixels: "+" for more,
ghost echoes for a repeated/duplicated strike, speed dashes for faster,
down-chevrons for slowed, a cracked/near-empty thing for a bonus tied to a
target's state, a badge pairing (ingredient + the real status-effect icon)
for "applies X". Nothing here is built out of pure hand-plotted pixels
alone.

One icon per family, MINOR skipped. Order matches the Family enum in
SlayerNodes.java: the grip root, the crossguard row, the two blade edges,
the tip.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
MOD_TEXTURES = ("/Users/german-mac-mini/repos/mc-modding/archetypes/"
                "src/main/resources/assets/archetypes/textures")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

SIZE = 32

# House palette -- lifted straight from notes/art/make_node_icons.py so
# these icons sit in the same family as the tree's existing hand-made ones.
BLOOD = (200, 32, 32, 255)
BLOOD_DARK = (122, 16, 16, 255)
BLOOD_LIGHT = (240, 96, 96, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)

WHITE = (255, 255, 255, 255)
IRON = (200, 200, 208, 255)
IRON_DARK = (120, 120, 128, 255)
IRON_LIGHT = (238, 238, 244, 255)

# A cool, slightly desaturated blue for the "slowed" chevrons -- distinct
# from Rend's hot blood-red so the two debuff languages don't collide.
SLOW = (150, 178, 224, 255)
SLOW_DIM = (95, 115, 165, 210)

# The "just happened / ready again" spark, reused from the Assassin sheet's
# own momentum() -- a kill snapping a cooldown back to zero.
GREEN = (150, 255, 130, 255)
GREEN_DIM = (90, 200, 90, 220)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def mod_tex(path):
    return Image.open(os.path.join(MOD_TEXTURES, path)).convert("RGBA").copy()


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def brighten(im, mul=15, add=20):
    """Lift a dark vanilla sprite's midtones so it still reads against a
    near-black tree background, without touching transparent pixels."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) != (0, 0, 0):
                px[x, y] = (min(255, r * mul // 10 + add),
                            min(255, g * mul // 10 + add),
                            min(255, b * mul // 10 + add), a)
    return out


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def item2x(path):
    """A 16x16 vanilla item/block sprite upscaled 2x NEAREST to fill the
    32px canvas -- the house norm."""
    return vanilla(path).resize((SIZE, SIZE), Image.NEAREST)


def mod_item2x(name):
    """A 16x16 mod item sprite (the greatswords) upscaled 2x NEAREST."""
    return mod_tex(f"item/{name}.png").resize((SIZE, SIZE), Image.NEAREST)


def iso_block(texture):
    """A quarter-size isometric block 'item icon' -- diamond top, shaded
    left/right faces -- ported straight from make_node_icons.py's own
    helper, the way the canon script builds inventory-cube corner accents."""
    tex = vanilla(f"block/{texture}.png")
    cube = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    top = tex.rotate(45, expand=True).resize((16, 8), Image.NEAREST)
    cube.alpha_composite(top, (0, 0))
    for face, x0, shade in ((0, 0, 150), (1, 8, 210)):
        for x in range(8):
            sx = x * 2
            drop_y = (x if face == 0 else 7 - x) // 2
            for y in range(8):
                r, g, b, a = tex.getpixel((sx, y * 2))
                if a:
                    py = 4 + drop_y + y
                    if py < 16:
                        cube.putpixel((x0 + x, py),
                                (r * shade // 255, g * shade // 255, b * shade // 255, 255))
    return cube


def blot(im, x, y, color, w=2, h=2):
    """A single hand-plotted 'pixel' drawn as a wxh block so it stays crisp
    against the 2x-scaled sprites around it."""
    for dx in range(w):
        for dy in range(h):
            px, py = x + dx, y + dy
            if 0 <= px < SIZE and 0 <= py < SIZE:
                im.putpixel((px, py), color)


def plus(im, cx, cy, color, arm=2, thick=2):
    """A '+' centred at (cx, cy) -- the house "more" glyph."""
    blot(im, cx - thick // 2, cy - thick // 2, color, thick, thick)
    for i in range(1, arm + 1):
        blot(im, cx - thick // 2, cy - thick // 2 - i * thick, color, thick, thick)
        blot(im, cx - thick // 2, cy - thick // 2 + i * thick, color, thick, thick)
        blot(im, cx - thick // 2 - i * thick, cy - thick // 2, color, thick, thick)
        blot(im, cx - thick // 2 + i * thick, cy - thick // 2, color, thick, thick)


def minus(im, cx, cy, color, arm=2, thick=2):
    """A '-' centred at (cx, cy) -- the house "less" glyph."""
    blot(im, cx - thick // 2, cy - thick // 2, color, thick, thick)
    for i in range(1, arm + 1):
        blot(im, cx - thick // 2 - i * thick, cy - thick // 2, color, thick, thick)
        blot(im, cx - thick // 2 + i * thick, cy - thick // 2, color, thick, thick)


def spark(im, cx, cy, color, color_dim):
    """A tiny 4-point twinkle -- "just happened" flash, for a kill snapping
    a cooldown back to ready."""
    blot(im, cx, cy, color, 3, 3)
    for dx, dy in ((-4, 0), (4, 0), (0, -4), (0, 4)):
        blot(im, cx + dx, cy + dy, color_dim, 2, 2)


DROP_PIXELS = ((0, 0, BLOOD), (0, 1, BLOOD), (-1, 2, BLOOD_DARK), (0, 2, BLOOD_LIGHT),
               (1, 2, BLOOD), (-1, 3, BLOOD), (0, 3, BLOOD_DARK), (1, 3, BLOOD_DARK))


def drop(im, x, y, scale=2):
    """A blood teardrop, point at (x, y) -- make_node_icons.py's own drop()
    shape, replotted at the house's 2x scale (or bigger, for emphasis)."""
    for dx, dy, c in DROP_PIXELS:
        blot(im, x + dx * scale, y + dy * scale, c, scale, scale)


def chevron_down(im, cx, cy, color, arm=4):
    """A single downward chevron ( v ), apex at (cx, cy) -- the house
    "slowed" glyph, one pixel-block per step."""
    for i in range(arm):
        blot(im, cx - arm + i, cy - i, color, 2, 2)
        blot(im, cx + arm - i, cy - i, color, 2, 2)


def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# ---------------------------------------------------------------------------
# SLOWNESS (Hamstring) -- root grip node, either weapon: hits apply
# Slowness I/II for 3s. Vanilla's own Slowness badge (already what the live
# tree shows for this family) made dominant, a sword tucked into its empty
# bottom-right corner as the trigger, two down-chevrons stacked at bottom-
# left -- the house "slowed" glyph -- so it reads as effect even before the
# badge itself is recognised.
def slowness():
    im = canvas()
    badge = vanilla("mob_effect/slowness.png").resize((26, 26), Image.NEAREST)
    im.alpha_composite(badge, (1, 1))
    sword = vanilla("item/iron_sword.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(sword, (18, 18))
    chevron_down(im, 6, 22, SLOW_DIM, arm=2)
    chevron_down(im, 6, 27, SLOW, arm=2)
    save(im, "slowness")


# ---------------------------------------------------------------------------
# TASTE_OF_BLOOD -- kills with either weapon restore 0.5/1/1.5 hearts. A
# full heart with its own falling drop (the family's original composition,
# rebuilt at 32px) plus a small "+" -- the house "more/gain" glyph -- so it
# doesn't read as a flat health display but as healing gained.
def taste_of_blood():
    im = canvas()
    heart = vanilla("gui/sprites/hud/heart/full.png").resize((20, 20), Image.NEAREST)
    im.alpha_composite(heart, (6, 1))
    drop(im, 15, 19, scale=2)
    plus(im, 27, 6, WHITE, arm=1, thick=2)
    save(im, "taste_of_blood")


# ---------------------------------------------------------------------------
# LUNGE -- while sprinting, sword swings fling you forward (and up). The
# rabbit foot already assigned to this family (a real leap ingredient) with
# a diagonal burst of motion dashes driving up and to the right -- forward,
# including upward -- brightest at the front.
def lunge():
    im = canvas()
    im.alpha_composite(item2x("item/rabbit_foot.png"), (0, 0))
    for i, (x, y) in enumerate(((2, 29), (7, 24), (12, 19))):
        blot(im, x, y, ARC if i == 2 else ARC_DIM, 3, 3)
    blot(im, 15, 16, WHITE, 2, 2)
    save(im, "lunge")


# ---------------------------------------------------------------------------
# KBRES (Immovable) -- with a greatsword in hand, 30/60% knockback
# resistance. The greatsword itself with an obsidian mini-cube anchored in
# its empty bottom-right corner (obsidian is the family's own assigned
# item -- the game's own "cannot be moved" block) and a few pale impact
# ticks bouncing off its top edge -- force arriving and going nowhere.
def kbres():
    im = canvas()
    im.alpha_composite(mod_item2x("iron_greatsword"), (0, 0))
    ob = iso_block("obsidian").resize((13, 13), Image.NEAREST)
    im.alpha_composite(ob, (19, 19))
    for x, y in ((17, 18), (22, 15), (26, 19)):
        blot(im, x, y, WHITE, 1, 1)
    save(im, "kbres")


# ---------------------------------------------------------------------------
# BLEED (Rend) -- sword hits open a wound: 0.5/1/1.5 hearts every second,
# for 3 seconds. The family's own claw-gash motif rebuilt bigger, a sword
# tucked in the corner as the trigger, and three small tally drops along
# the bottom edge -- the "every second, for 3 seconds" ticks a single gash
# alone doesn't show.
def bleed():
    im = canvas()
    im.alpha_composite(vanilla("item/iron_sword.png").resize((13, 13), Image.NEAREST), (0, 0))
    for i, (sx, length) in enumerate(((17, 17), (23, 21), (29, 17))):
        for j in range(length):
            x = sx - j
            y = 5 + i * 2 + j
            if 0 <= x < SIZE and 0 <= y < SIZE:
                c = BLOOD_LIGHT if j == 0 else (BLOOD_DARK if j == length - 1 else BLOOD)
                blot(im, x, y, c, 2, 2)
    for x in (5, 13, 21):
        blot(im, x, 28, BLOOD, 2, 2)
    save(im, "bleed")


# ---------------------------------------------------------------------------
# BLADE_DANCE -- every manual sword strike has a chance to lash out at
# another enemy nearby, any direction. Two swords crossing opposite ways
# with arcs curving off each tip -- the family's original composition,
# doubled cleanly onto the 32px canvas.
def blade_dance():
    im = canvas()
    sword = vanilla("item/iron_sword.png").resize((26, 26), Image.NEAREST)
    im.alpha_composite(sword.transpose(Image.FLIP_LEFT_RIGHT), (0, 6))
    im.alpha_composite(sword, (6, 6))
    for x, y in ((24, 2), (28, 4), (6, 2), (2, 4)):
        blot(im, x, y, ARC, 2, 2)
    for x, y in ((20, 2), (10, 2)):
        blot(im, x, y, ARC_DIM, 2, 2)
    save(im, "blade_dance")


# ---------------------------------------------------------------------------
# HEAVY (Heavy Blows) -- the greatsword hits 10/20/30% harder and swings
# 10/20/30% slower to match. The family's own motion-echo grammar (a faded
# ghost of the blade hanging behind the real one -- weight read as motion
# blur, which also carries "slower") plus a small "+" for the extra damage.
def heavy_blows():
    im = canvas()
    gs = mod_item2x("iron_greatsword")
    echo = gs.rotate(-28, resample=Image.NEAREST, expand=False)
    im.alpha_composite(faded(echo, 120), (0, 0))
    im.alpha_composite(gs, (0, 0))
    plus(im, 27, 5, WHITE, arm=1, thick=2)
    save(im, "heavy_blows")


# ---------------------------------------------------------------------------
# FIRSTBLOOD -- greatsword hits against unhurt targets deal +40%. An
# unbloodied greatsword, except the very tip where the first cut lands, and
# the drop it's already shedding -- the family's original read, rebuilt.
def first_blood():
    im = canvas()
    im.alpha_composite(mod_item2x("iron_greatsword"), (0, 0))
    for x, y, c in ((28, 1, BLOOD), (29, 1, BLOOD), (27, 2, BLOOD), (28, 2, BLOOD_LIGHT),
                    (29, 2, BLOOD), (27, 3, BLOOD_DARK), (28, 3, BLOOD)):
        im.putpixel((x, y), c)
    drop(im, 27, 6, scale=2)
    save(im, "first_blood")


# ---------------------------------------------------------------------------
# FLURRY -- sword kills reset Lunge's cooldown. Lunge's own rabbit foot,
# tying the two families together, with a small kill-drop and a bright
# "ready again" spark -- the same kill-resets-cooldown grammar as the
# Assassin sheet's momentum(), told with the actual family it resets.
def flurry():
    im = canvas()
    im.alpha_composite(item2x("item/rabbit_foot.png"), (0, 0))
    drop(im, 5, 2, scale=1)
    spark(im, 25, 7, GREEN, GREEN_DIM)
    save(im, "flurry")


# ---------------------------------------------------------------------------
# EXECUTIONER -- greatsword blows finish any target below 15% health
# outright. The greatsword with a heart CONTAINER (vanilla's empty-heart
# outline) cracked and down to its last red sliver, tucked in the corner --
# the classic "cracked thing for a bonus tied to a target's state," here a
# death sentence instead of a damage multiplier.
def executioner():
    im = canvas()
    im.alpha_composite(mod_item2x("iron_greatsword"), (0, 0))
    heart = vanilla("gui/sprites/hud/heart/container.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(heart, (18, 18))
    for x, y in ((22, 27), (23, 28)):
        blot(im, x, y, BLOOD, 1, 1)
    for x, y in ((20, 21), (22, 23), (24, 25)):
        im.putpixel((x, y), (25, 25, 28, 255))
    save(im, "executioner")


# ---------------------------------------------------------------------------
# BLOODLUST -- sword and greatsword kills grant Speed I for 3s. A kill (the
# house blood drop, oversized) paired with vanilla's own Speed badge and a
# couple of motion dashes trailing off it -- the same ingredient+badge
# pairing the Assassin sheet uses for its own on-hit effects.
def bloodlust():
    im = canvas()
    drop(im, 9, 13, scale=3)
    im.alpha_composite(vanilla("mob_effect/speed.png").resize((17, 17), Image.NEAREST), (15, 0))
    for x, y in ((13, 5), (10, 8)):
        blot(im, x, y, ARC_DIM, 2, 2)
    save(im, "bloodlust")


# ---------------------------------------------------------------------------
# RELENTLESS -- the point: your capstone comes back 15 seconds sooner,
# whichever one you run. Vanilla's own clock (the house's established
# cooldown glyph -- see make_node_icons.py's braced_overlay) with two
# "less" marks at opposite corners: the cooldown shrinking, whichever
# capstone it lands on.
def relentless():
    im = canvas()
    im.alpha_composite(item2x("item/clock_00.png"), (0, 0))
    minus(im, 6, 25, WHITE, arm=1, thick=2)
    minus(im, 26, 7, WHITE, arm=1, thick=2)
    save(im, "relentless")


# ---------------------------------------------------------------------------
# BLADESTORM -- capstone: whirl for 3s, striking everything within 3 blocks
# six times. The family's own fan-of-echoes whirl, doubled onto the 32px
# canvas: two faded rotated copies of the same sword behind the real one,
# all sweeping the same way.
def bladestorm():
    im = canvas()
    sword = vanilla("item/iron_sword.png").resize((32, 32), Image.NEAREST)
    for angle, alpha in ((-52, 110), (-26, 185)):
        echo = sword.rotate(angle, resample=Image.NEAREST, expand=False)
        im.alpha_composite(faded(echo, alpha), (0, 0))
    im.alpha_composite(sword, (0, 0))
    save(im, "bladestorm")


# ---------------------------------------------------------------------------
# DECIMATE -- capstone: one massive tilted cleave, double damage, sweeping
# clutter from its path. Vanilla's own sweep-attack flash behind the
# greatsword, the blade itself rotated off-axis for the "tilted" cleave the
# tooltip promises -- the family's original composition, given the tilt.
def decimate():
    im = canvas()
    im.alpha_composite(faded(vanilla("particle/sweep_2.png").resize((32, 32), Image.NEAREST), 230), (0, 0))
    gs = mod_item2x("iron_greatsword").rotate(-16, resample=Image.NEAREST, expand=False)
    im.alpha_composite(gs, (0, 0))
    save(im, "decimate")


ORDER = [
    ("slowness", slowness),
    ("taste_of_blood", taste_of_blood),
    ("lunge", lunge),
    ("kbres", kbres),
    ("bleed", bleed),
    ("blade_dance", blade_dance),
    ("heavy_blows", heavy_blows),
    ("first_blood", first_blood),
    ("flurry", flurry),
    ("executioner", executioner),
    ("bloodlust", bloodlust),
    ("relentless", relentless),
    ("bladestorm", bladestorm),
    ("decimate", decimate),
]


def contact_sheet():
    cols = 5
    rows = (len(ORDER) + cols - 1) // cols
    scale = 4
    icon_px = SIZE * scale
    pad_top, pad_mid, text_h, pad_bottom, pad_side = 10, 8, 18, 10, 10
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
        tw = draw.textlength(name, font=font)
        draw.text((cx + (cell_w - tw) / 2, oy + icon_px + pad_mid), name,
                   fill=(255, 255, 255, 255), font=font)
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(ICONS, exist_ok=True)
    for _, fn in ORDER:
        fn()
    contact_sheet()


if __name__ == "__main__":
    main()
