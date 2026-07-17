"""Generate 32x32 skill-node icons for the Assassin constellation (bake-off
test, round two).

Same pipeline as notes/art/make_node_icons.py and the round-one sheets:
vanilla sprites pulled straight from the client jar (palettes match the game
for free) or the mod's own dagger textures, composed on a transparent 32x32
canvas at 2x NEAREST, with a small readable mechanic grammar laid on top in a
few hand-plotted pixels -- a status-effect badge for "applies X", a "-" for
"shorter", a "+" for "more", ghost echoes for "hits three times". Every icon
starts from a real sprite; nothing is built out of pure hand-plotted pixels
alone.

One icon per family, MINOR skipped.

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

WHITE = (255, 255, 255, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)

IRON_LIGHT = (238, 238, 244, 255)


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


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def item2x(path):
    """A 16x16 vanilla item/block sprite upscaled 2x NEAREST to fill the
    32px canvas -- the house norm (see greatsword_item() in
    make_node_icons.py)."""
    return vanilla(path).resize((SIZE, SIZE), Image.NEAREST)


def mod_item2x(name):
    """A 16x16 mod item sprite (the daggers) upscaled 2x NEAREST."""
    return mod_tex(f"item/{name}.png").resize((SIZE, SIZE), Image.NEAREST)


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
    """A tiny 4-point twinkle -- "just happened" flash, reused wherever a
    node fires an instantaneous effect rather than a steady one."""
    blot(im, cx, cy, color, 3, 3)
    for dx, dy in ((-4, 0), (4, 0), (0, -4), (0, 4)):
        blot(im, cx + dx, cy + dy, color_dim, 2, 2)


def brighten(im, mul=15, add=20):
    """Lift a dark vanilla sprite's midtones so it still reads against a
    near-black background, without touching pure transparent/black pixels
    (sockets, outlines) that should stay dark for contrast."""
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


def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# ---------------------------------------------------------------------------
# SHADOW_STEP -- the active. Blink behind a creature (16 blocks away) and
# strike once at full power. The pearl IS the teleport; a ghost dagger and a
# short teal trail land the "behind, then strike" beat the tooltip promises.
def shadow_step():
    im = canvas()
    im.alpha_composite(item2x("item/ender_pearl.png"), (0, 0))
    dagger = mod_tex("item/iron_dagger.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(faded(dagger, 225), (17, 16))
    # White sparkle trail, not teal -- teal dots vanished straight into the
    # pearl's own palette on the first pass.
    for x, y, w, h in ((9, 10, 2, 2), (12, 13, 1, 1), (15, 15, 2, 2)):
        blot(im, x, y, WHITE, w, h)
    blot(im, 27, 17, WHITE, 2, 2)
    save(im, "shadow_step")


# ---------------------------------------------------------------------------
# LIGHTFOOT -- +10% move speed per rank with a dagger in hand. The boots
# themselves plus trailing motion dashes, same grammar as the house's
# velocity/fervent_cast icons.
def lightfoot():
    im = canvas()
    im.alpha_composite(item2x("item/leather_boots.png"), (0, 0))
    for i, y in enumerate((5, 13, 21)):
        length = 3 - (i % 2)
        for j in range(length):
            x = 5 - j * 2
            if x >= 0:
                blot(im, x, y, WHITE if j == 0 else ARC_DIM, 2, 2)
    save(im, "lightfoot")


# ---------------------------------------------------------------------------
# SIDESTEP -- 7% per rank to dodge a melee hit outright. A feather (light on
# your feet) with an attack's arc curving past and missing entirely.
def sidestep():
    im = canvas()
    im.alpha_composite(item2x("item/feather.png"), (0, 0))
    # A "miss" X, dropped in the feather's own dead-empty top-left corner --
    # anywhere touching the plumage's grey/white antialiasing just melted
    # into it on the first pass, so this sits well clear of it instead.
    for x, y in ((2, 2), (5, 5), (8, 8), (8, 2), (2, 8)):
        blot(im, x, y, WHITE, 2, 2)
    save(im, "sidestep")


# ---------------------------------------------------------------------------
# ADRENALINE_RUSH -- Shadow Step grants Speed II for 3s after the blink.
# Sugar (the potion ingredient) plus vanilla's own Speed effect badge --
# both real sprites, the pairing does all the telling.
def adrenaline_rush():
    im = canvas()
    im.alpha_composite(item2x("item/sugar.png"), (0, 0))
    im.alpha_composite(vanilla("mob_effect/speed.png"), (14, 0))
    save(im, "adrenaline_rush")


# ---------------------------------------------------------------------------
# OPPORTUNIST -- Shadow Step's cooldown is 3s shorter. The clock the house
# canon already ties to cooldown (braced_overlay), with a rewind "<<" for
# the shortening itself.
def opportunist():
    im = canvas()
    im.alpha_composite(item2x("item/clock_00.png"), (0, 0))
    minus(im, 6, 26, WHITE, arm=1, thick=2)
    save(im, "opportunist")


# ---------------------------------------------------------------------------
# RAZOR_EDGE -- flat +8% dagger damage per rank. The dagger itself,
# sharpened -- glint pixels along the edge and the house "+" for more.
def razor_edge():
    im = canvas()
    im.alpha_composite(mod_item2x("iron_dagger"), (0, 0))
    for x, y in ((21, 7), (24, 10), (18, 11)):
        blot(im, x, y, WHITE, 1, 1)
    plus(im, 27, 4, WHITE, arm=1, thick=2)
    save(im, "razor_edge")


# ---------------------------------------------------------------------------
# EXPOSE -- +10% per rank against targets below half health. Vanilla's own
# target block (a bullseye -- the weak point) plus a half heart for the
# health-threshold condition.
def expose():
    im = canvas()
    im.alpha_composite(item2x("block/target_top.png"), (0, 0))
    im.alpha_composite(vanilla("gui/sprites/hud/heart/half.png"), (21, 21))
    save(im, "expose")


# ---------------------------------------------------------------------------
# VENOM -- dagger hits apply Poison I then II. Spider eye (the poison
# ingredient) plus the real Poison effect badge.
def venom():
    im = canvas()
    im.alpha_composite(item2x("item/spider_eye.png"), (0, 0))
    im.alpha_composite(vanilla("mob_effect/poison.png"), (14, 0))
    save(im, "venom")


# ---------------------------------------------------------------------------
# BLIGHT -- dagger hits apply Wither I then II. Wither rose plus the real
# Wither effect badge -- the same pairing grammar as Venom, one rank up the
# same edge.
def blight():
    im = canvas()
    im.alpha_composite(item2x("block/wither_rose.png"), (0, 0))
    # The wither badge is dark enough to melt into both the rose's own
    # near-black petals and a near-black tree background -- brightened, and
    # tucked into the rose's genuinely empty top-right corner instead of
    # over its petals, so it reads as its own shape.
    badge = brighten(vanilla("mob_effect/wither.png"), mul=16, add=35)
    badge = badge.resize((13, 13), Image.NEAREST)
    im.alpha_composite(badge, (19, 0))
    save(im, "blight")


# ---------------------------------------------------------------------------
# FLENSE -- dagger damage ignores half the target's armor, then all of it.
# Shears plus an iron chestplate with a bright shear-cut straight through
# it -- the armor visibly parted.
def flense():
    im = canvas()
    im.alpha_composite(item2x("item/shears.png"), (0, 0))
    chest = vanilla("item/iron_chestplate.png").resize((14, 14), Image.NEAREST)
    im.alpha_composite(chest, (17, 17))
    for i in range(14):
        x, y = 17 + i, 17 + 13 - i
        if 17 <= x < 31 and 17 <= y < 31:
            im.putpixel((x, y), IRON_LIGHT)
    save(im, "flense")


# ---------------------------------------------------------------------------
# SHADOW_FLURRY -- capstone: Shadow Step's strike lands with three daggers'
# weight (x3.0 damage). Literally three daggers -- the real netherite one
# plus two rotated, fading echoes, the exact bladestorm() grammar from the
# house canon, and the flavour text's "three daggers" made literal.
def shadow_flurry():
    im = canvas()
    dagger = mod_item2x("netherite_dagger")
    # Netherite's own palette is dark; wider spread and brighter echoes than
    # the house bladestorm() keep the three blades legible against a near-
    # black background instead of smearing into one dark blob.
    for angle, alpha in ((-55, 150), (-27, 210)):
        echo = dagger.rotate(angle, resample=Image.NEAREST, expand=False)
        im.alpha_composite(faded(echo, alpha), (0, 0))
    im.alpha_composite(dagger, (0, 0))
    # A glint on the real (foremost) blade only -- netherite's own palette
    # is dark enough that the three blades want one bright edge to anchor
    # the eye on the "real" strike.
    for x, y in ((21, 7), (24, 10)):
        blot(im, x, y, WHITE, 1, 1)
    save(im, "shadow_flurry")


# ---------------------------------------------------------------------------
# MOMENTUM -- capstone: any kill resets Shadow Step's cooldown. The wither
# skeleton skull's own front face, cropped straight off its entity skin
# (brightened so the sockets read at this size) -- the kill -- plus a small
# gold loop snapping back around, the cooldown resetting.
def momentum():
    im = canvas()
    face = vanilla("entity/skeleton/wither_skeleton.png").crop((8, 8, 16, 16))
    face = brighten(face, mul=17, add=20)
    face = face.resize((26, 26), Image.NEAREST)
    im.alpha_composite(face, (2, 3))
    # The kill -- a bright green "ready again" twinkle, not a loop-arrow
    # (too fussy to read at this size): Shadow Step just came off cooldown.
    spark(im, 25, 23, (150, 255, 130, 255), (90, 200, 90, 220))
    save(im, "momentum")


# ---------------------------------------------------------------------------
# DEATHBLOW -- the point: Shadow Step's strikes, flurry included, deal +50%
# damage. Nether star (the tree's ultimate-capstone item) with the dagger
# itself laid through it -- the star empowering every blade -- and a
# scatter of extra sparkle for the boost.
def deathblow():
    im = canvas()
    im.alpha_composite(item2x("item/nether_star.png"), (0, 0))
    dagger = mod_tex("item/netherite_dagger.png").resize((18, 18), Image.NEAREST)
    im.alpha_composite(faded(dagger, 235), (7, 7))
    for x, y in ((3, 3), (27, 4), (4, 27), (27, 27)):
        blot(im, x, y, WHITE, 1, 1)
    save(im, "deathblow")


ORDER = [
    ("shadow_step", shadow_step),
    ("lightfoot", lightfoot),
    ("sidestep", sidestep),
    ("adrenaline_rush", adrenaline_rush),
    ("opportunist", opportunist),
    ("razor_edge", razor_edge),
    ("expose", expose),
    ("venom", venom),
    ("blight", blight),
    ("flense", flense),
    ("shadow_flurry", shadow_flurry),
    ("momentum", momentum),
    ("deathblow", deathblow),
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
