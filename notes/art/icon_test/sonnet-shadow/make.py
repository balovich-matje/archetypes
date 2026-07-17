"""Generate 32x32 skill-node icons for the Shadow constellation (bake-off
entry). Same pipeline as notes/art/make_node_icons.py: vanilla sprites pulled
straight from the client jar (so palettes match the game for free), composed
on a transparent 32x32 canvas at 2x NEAREST, with a few hand-plotted accent
pixels. One icon per family, INVISIBILITY (already drawn) and MINOR skipped.

Design grammar for this tree:
  * The vanilla Invisibility status badge (mob_effect/invisibility.png),
    pinned in a corner at its own native 18x18, is THIS TREE's one recurring
    mark: "this triggers while/because you're invisible." It's the exact
    badge the player's own HUD shows them mid-invisibility, so it's free
    vocabulary, not an invented glyph. Used on: Dark Mending, Cleansing
    Veil, Stillness, First Strike, Bloodrush, Reaper, Last Shadow, Predator.
  * Nodes that key off SNEAKING rather than invisibility (Night Eyes, Umbral
    Sight, Swift Shadow, Dim Presence) skip that badge on purpose -- the
    absence is itself information.
  * A faded/ghosted render of the item IS the icon for "goes invisible with
    you" (Ghost Armor, Dim Presence) -- reusing the exact alpha-fade trick
    the Slayer tree's bulwark_overlay already uses for "ghost" copies.
  * Real vanilla status badges are reused wherever they already are the
    mechanic: night_vision (eye+moon) for Night Eyes, glowing (white burst)
    for Umbral Sight, strength for Bloodrush -- these are literally the
    buffs the nodes grant, so pinning the game's own icon for them is the
    most vanilla-native option there is.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

SIZE = 32

WHITE = (255, 255, 255, 255)
GOLD = (255, 236, 160, 255)
GOLD_DIM = (196, 172, 104, 255)
RED = (210, 40, 40, 255)
RED_LIGHT = (245, 120, 110, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)
MOON = (223, 214, 255, 255)
MOON_DIM = (150, 138, 196, 140)
BONE = (228, 224, 205, 255)
BONE_DARK = (168, 160, 138, 255)
SOCKET = (40, 32, 26, 255)
OUTLINE = (24, 20, 24, 255)


def vanilla(path):
    """A vanilla texture, animated strips cropped to the first square frame."""
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            im = Image.open(f).convert("RGBA").copy()
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))
    return im


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def item2x(path):
    """A 16px vanilla item/block sprite, upscaled 2x NEAREST to the 32px
    canvas -- the house norm (see greatsword_item() in make_node_icons.py)."""
    return vanilla(path).resize((SIZE, SIZE), Image.NEAREST)


def scale(im, factor):
    return im.resize((max(1, round(im.width * factor)), max(1, round(im.height * factor))),
                      Image.NEAREST)


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def badge(im, name, xy, size=18):
    """A vanilla status-effect icon (native 18x18) pinned at xy, this tree's
    recurring 'triggers on invisibility' (or other real buff) mark."""
    icon = vanilla(f"mob_effect/{name}.png")
    if size != icon.width:
        icon = scale(icon, size / icon.width)
    im.alpha_composite(icon, xy)


def dot(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def blot(im, x, y, c, w=2, h=2):
    for dx in range(w):
        for dy in range(h):
            dot(im, x + dx, y + dy, c)


def outlined(im, col=OUTLINE):
    """An 8-neighbour dark border added around an image's own silhouette, so
    it reads as a discrete object on a dark background instead of blending
    into it (see opus-elementalist's identical helper)."""
    out = im.copy()
    src = im.load()
    dst = out.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            hit = False
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 128:
                        hit = True
            if hit:
                dst[x, y] = col
    return out


def save(im, name):
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


# ---------------------------------------------------------------------------
# NIGHT_EYES -- Night Vision while sneaking, no flicker.
def night_eyes():
    im = canvas()
    im.alpha_composite(item2x("item/golden_carrot.png"), (0, 0))
    badge(im, "night_vision", (13, -3))
    save(im, "night_eyes")


# ---------------------------------------------------------------------------
# UMBRAL_SIGHT -- hostiles glow while you sneak or hide.
def umbral_sight():
    im = canvas()
    im.alpha_composite(item2x("item/glow_ink_sac.png"), (0, 0))
    badge(im, "glowing", (13, -3))
    save(im, "umbral_sight")


# ---------------------------------------------------------------------------
# SWIFT_SHADOW -- sneaking costs less (then none) of your move speed.
def swift_shadow():
    im = canvas()
    sugar = scale(vanilla("item/sugar.png"), 1.7)
    im.alpha_composite(sugar, (5, 5))
    # Speed-dashes trailing off the pile's lower-left, the house "faster"
    # language (Slayer's bash_overlay, Wizard's velocity/fervent_cast).
    for i, y in enumerate((17, 22, 27)):
        length = 4 - i
        for j in range(length):
            x = 5 - j * 2
            if x >= -2:
                blot(im, x, y, ARC if j == 0 else ARC_DIM, 2, 2)
    save(im, "swift_shadow")


# ---------------------------------------------------------------------------
# DARK_MENDING -- a heart every 8/6/4/2s while invisible.
def dark_mending():
    im = canvas()
    im.alpha_composite(item2x("item/glistering_melon_slice.png"), (0, 0))
    heart = scale(vanilla("gui/sprites/hud/heart/full.png"), 1.6)
    im.alpha_composite(heart, (20, 19))
    badge(im, "invisibility", (16, -3), size=13)
    save(im, "dark_mending")


# ---------------------------------------------------------------------------
# DIM_PRESENCE -- mobs notice you less, hidden or not.
def dim_presence():
    im = canvas()
    # Phantom Membrane at full strength -- it's already vanilla's own
    # invisibility-potion ingredient, so its own hazy texture does the
    # "insubstantial" work without needing to fade it into a blur.
    im.alpha_composite(item2x("item/phantom_membrane.png"), (0, 0))
    # A small eye-with-a-slash, bottom-right -- "not noticed," the classic
    # stealth-game pictograph, kept small so it reads as a tag rather than
    # the icon's main subject.
    lyr = canvas()
    x0, y0 = 20, 21
    eye = ((2, 0), (3, 0), (1, 1), (4, 1), (0, 2), (5, 2),
           (1, 3), (4, 3), (2, 4), (3, 4))
    for dx, dy in eye:
        dot(lyr, x0 + dx, y0 + dy, WHITE)
    dot(lyr, x0 + 2, y0 + 2, (40, 40, 44, 255))
    dot(lyr, x0 + 3, y0 + 2, (40, 40, 44, 255))
    im.alpha_composite(outlined(lyr))
    im.alpha_composite(lyr)
    # The slash, drawn after outlining so it stays crisp red on top.
    for dx in range(-1, 7):
        dot(im, x0 + dx, y0 + 5 - dx, RED)
    save(im, "dim_presence")


# ---------------------------------------------------------------------------
# CLEANSING_VEIL -- casting Invisibility clears your negative effects.
def cleansing_veil():
    im = canvas()
    im.alpha_composite(item2x("item/milk_bucket.png"), (0, 0))
    badge(im, "invisibility", (13, -3))
    # A couple of pale glints off the pail's rim -- the "cleared clean" read.
    for x, y in ((6, 4), (9, 2)):
        dot(im, x, y, WHITE)
    save(im, "cleansing_veil")


# ---------------------------------------------------------------------------
# STILLNESS -- standing still slows (then stops) the invisibility timer.
def stillness():
    im = canvas()
    im.alpha_composite(item2x("item/clock_00.png"), (0, 0))
    badge(im, "invisibility", (13, -3))
    # A small white pause glyph tucked in the clock item's own transparent
    # bottom-right corner (the round face never reaches the square's
    # corners) -- doesn't fight the clock's face or hands for contrast.
    lyr = canvas()
    blot(lyr, 24, 24, WHITE, 2, 7)
    blot(lyr, 28, 24, WHITE, 2, 7)
    im.alpha_composite(outlined(lyr))
    im.alpha_composite(lyr)
    save(im, "stillness")


# ---------------------------------------------------------------------------
# FIRST_STRIKE -- melee from invisibility hits +30% per rank harder.
def first_strike():
    im = canvas()
    im.alpha_composite(item2x("item/iron_sword.png"), (0, 0))
    # An ambush burst at the blade's tip (top-right, where iron_sword's
    # point lands once upscaled) -- the surprise hit landing.
    cx, cy = 27, 3
    for dx, dy in ((0, -3), (0, 3), (-3, 0), (3, 0)):
        dot(im, cx + dx, cy + dy, RED)
    for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0), (0, 0)):
        dot(im, cx + dx, cy + dy, RED_LIGHT if (dx or dy) else WHITE)
    badge(im, "invisibility", (-3, 15), size=14)
    save(im, "first_strike")


# ---------------------------------------------------------------------------
# BLOODRUSH -- kills while invisible grant Strength I, then II.
def bloodrush():
    im = canvas()
    im.alpha_composite(item2x("item/redstone.png"), (0, 0))
    badge(im, "strength", (13, -3))
    badge(im, "invisibility", (-3, 16), size=14)
    save(im, "bloodrush")


# ---------------------------------------------------------------------------
# REAPER -- kills while invisible restore 2 hearts.
def reaper():
    im = canvas()
    im.alpha_composite(item2x("block/wither_rose.png"), (0, 0))
    heart = scale(vanilla("gui/sprites/hud/heart/full.png"), 1.6)
    im.alpha_composite(heart, (17, -1))
    badge(im, "invisibility", (-3, 16), size=14)
    save(im, "reaper")


# ---------------------------------------------------------------------------
# GHOST_ARMOR -- your armor vanishes with you.
def ghost_armor():
    im = canvas()
    plate = item2x("item/chainmail_chestplate.png")
    im.alpha_composite(faded(plate, 140), (0, 0))
    save(im, "ghost_armor")


# ---------------------------------------------------------------------------
# LAST_SHADOW -- capstone: fatal damage cleanses, grants immunity, casts
# Invisibility.
def last_shadow():
    im = canvas()
    im.alpha_composite(item2x("item/totem_of_undying.png"), (0, 0))
    badge(im, "invisibility", (13, -3))
    save(im, "last_shadow")


# ---------------------------------------------------------------------------
# PREDATOR -- capstone: kills while invisible renew the full duration.
def predator():
    im = canvas()
    # The skeleton's own front-face skin crop -- a real vanilla skull, not a
    # hand-drawn one (matches the mod's Family.icon() = SKELETON_SKULL). Its
    # native grays are low-contrast at this size, so remap by luminance into
    # a bone/socket palette -- shape stays 100% the vanilla UV, only the
    # palette changes (the same brightness-preserving trick the tree's own
    # invisibility() icon already uses on bad_omen.png).
    skin = vanilla("entity/skeleton/skeleton.png")
    face = skin.crop((8, 8, 16, 16)).convert("RGBA")
    px = face.load()
    for y in range(face.height):
        for x in range(face.width):
            r, g, b, a = px[x, y]
            lum = (r + g + b) / 3
            px[x, y] = SOCKET if lum <= 100 else (BONE_DARK if lum <= 160 else BONE)
    big = scale(face, 3.25)
    im.alpha_composite(outlined(big), (2, 2))
    im.alpha_composite(big, (2, 2))
    badge(im, "invisibility", (14, -3), size=15)
    # A small refresh loop, bottom-left -- the duration filling back up.
    d = ImageDraw.Draw(im)
    d.arc((0, 20, 13, 33), start=30, end=300, fill=GOLD, width=2)
    d.polygon([(11, 19), (14, 21), (10, 24)], fill=GOLD)
    save(im, "predator")


# ---------------------------------------------------------------------------
# UMBRAL_MASTERY -- the crown. Placeholder, no effect yet.
def umbral_mastery():
    im = canvas()
    # A thin crescent-moon outline behind the eye -- the crown sits between
    # the two capstones on the constellation's own moon, even with nothing
    # coded yet. Built by drawing a ring, then erasing the part covered by
    # a second, offset ring -- the classic crescent construction.
    ring = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(ring).ellipse((3, 3, 29, 29), outline=255, width=3)
    cut = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(cut).ellipse((9, 2, 35, 28), fill=255)
    crescent = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    rp, cp, xp = ring.load(), cut.load(), crescent.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if rp[x, y] and not cp[x, y]:
                xp[x, y] = MOON_DIM
    im.alpha_composite(crescent)
    # The eye itself, dimmed a touch -- awake, but not yet doing anything.
    eye = item2x("item/ender_eye.png")
    im.alpha_composite(faded(eye, 205), (0, 0))
    save(im, "umbral_mastery")


ORDER = [
    ("night_eyes", night_eyes),
    ("umbral_sight", umbral_sight),
    ("swift_shadow", swift_shadow),
    ("dark_mending", dark_mending),
    ("dim_presence", dim_presence),
    ("cleansing_veil", cleansing_veil),
    ("stillness", stillness),
    ("first_strike", first_strike),
    ("bloodrush", bloodrush),
    ("reaper", reaper),
    ("ghost_armor", ghost_armor),
    ("last_shadow", last_shadow),
    ("predator", predator),
    ("umbral_mastery", umbral_mastery),
]


def contact_sheet():
    cols = 4
    rows = (len(ORDER) + cols - 1) // cols
    scale_factor = 4
    icon_px = SIZE * scale_factor
    pad_top, pad_mid, text_h, pad_bottom, pad_side = 10, 8, 18, 10, 10
    cell_w = icon_px + pad_side * 2
    cell_h = pad_top + icon_px + pad_mid + text_h + pad_bottom
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (43, 43, 43, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 16)
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
