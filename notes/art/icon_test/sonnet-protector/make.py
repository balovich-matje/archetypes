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
import os
import zipfile

from PIL import Image

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


def shield_face_crop():
    """The real front-plate texture (plank + iron studs), cropped from the
    entity atlas at its native UV size - used as a small texture SAMPLE
    (for straps / colour) rather than as the whole icon body."""
    return vanilla("entity/shield/shield_base_nopattern.png").crop((0, 0, 13, 23))


def iso_block(texture, size=16):
    """A small isometric block 'item icon' from a block texture: diamond
    top, shaded left/right faces - same trick notes/art/make_node_icons.py
    uses for its block overlays."""
    tex = vanilla(f"block/{texture}.png")
    cube = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    top = tex.rotate(45, expand=True).resize((size, size // 2), Image.NEAREST)
    cube.alpha_composite(top, (0, 0))
    half = size // 2
    for face, x0, shade in ((0, 0, 150), (1, half, 210)):
        for x in range(half):
            sx = x * (tex.width // half)
            drop_y = (x if face == 0 else half - 1 - x) // 2
            for y in range(half):
                r, g, b, a = tex.getpixel((sx, y * (tex.height // half)))
                if a:
                    py = size // 4 + drop_y + y
                    if py < size:
                        cube.putpixel((x0 + x, py),
                                (r * shade // 255, g * shade // 255, b * shade // 255, 255))
    return cube


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


# ---------------------------------------------------------------- icons ---

def bash():
    """Shield Bash: the shove itself - shield up, a burst where it lands,
    two speed dashes trailing the strike."""
    im = shield_icon()
    for x, y in ((1, 7), (1, 8), (2, 4)):
        put(im, x, y, ARC_DIM)
    burst = ((15, 6, ARC), (15, 8, ARC), (14, 5, (255, 236, 160, 255)),
             (14, 9, ARC_DIM), (16, 7, ARC), (13, 7, (255, 236, 160, 255)))
    for x, y, c in burst:
        put(im, x, y, c)
    save(im, "bash")


def slam():
    """Shield Slam: the bash, but the impact is hotter/bigger and a
    Strength badge (vanilla's own effect icon) rides the top corner - the
    mod's own '+damage' tell."""
    im = shield_icon()
    burst = ((14, 5, (255, 240, 200, 255)), (15, 5, BLOOD), (16, 6, BLOOD),
             (15, 7, BLOOD), (16, 8, BLOOD), (15, 9, BLOOD), (14, 9, (255, 240, 200, 255)),
             (16, 4, ARC_DIM), (16, 10, ARC_DIM))
    for x, y, c in burst:
        put(im, x, y, c)
    badge = vanilla("mob_effect/strength.png").resize((7, 7), Image.NEAREST)
    im.alpha_composite(badge, (10, -1))
    save(im, "slam")


def cooldown():
    """Quick Recovery: vanilla's own resting clock, wound backwards - a
    counter-clockwise rewind arrow so it reads as time coming BACK, not
    forward."""
    clock = vanilla("item/clock_00.png")
    im = canvas()
    im.alpha_composite(clock, (0, 0))
    arrow = ((2, 1), (1, 2), (1, 3), (2, 4), (3, 1))
    for x, y in arrow:
        put(im, x, y, ARC)
    put(im, 0, 2, ARC)  # arrowhead barb, counter-clockwise
    put(im, 1, 1, ARC_DIM)
    save(im, "cooldown")


def knockback():
    """Concussive Blow: the piston's own retracted face, ram punching out
    hard - a bigger, blunter shove than Bash's, and less spark since damage
    is traded away for it."""
    piston = vanilla("block/piston_top.png")
    im = canvas()
    im.alpha_composite(piston, (0, 0))
    chevrons = ((13, 6), (14, 6), (13, 7), (15, 7), (13, 8), (14, 8),
                (13, 3), (14, 2), (13, 12), (14, 13))
    for x, y in chevrons:
        put(im, x, y, ARC)
    for x, y in ((15, 5), (16, 6), (15, 9), (16, 8)):
        put(im, x, y, ARC_DIM)
    save(im, "knockback")


def wide():
    """Wide Swings: the shield under a bash arc thrown all the way from one
    side to the other, instead of a single-target burst."""
    im = shield_icon()
    arc = ((0, 5), (0, 4), (1, 2), (3, 1), (6, 0), (9, 0), (12, 1), (14, 2),
           (15, 4), (15, 5))
    for x, y in arc:
        put(im, x, y, ARC)
    save(im, "wide")


def unbreaking():
    """Reinforced Straps: the shield with an actual leather strap (cropped
    straight from the item's own hide texture) buckled across it, iron
    rivets pinning it down - built-in Unbreaking, worn on the sleeve."""
    im = shield_icon()
    hide = vanilla("item/leather.png")
    strap = hide.resize((16, 16), Image.NEAREST).crop((1, 6, 15, 9))
    strap = strap.rotate(-24, resample=Image.NEAREST, expand=True)
    im.alpha_composite(strap, (0, 8))
    for x, y in ((2, 9), (13, 6)):
        put(im, x, y, IRON_LIGHT)
        put(im, x + 1, y, IRON_DARK)
    save(im, "unbreaking")


def spikes():
    """Iron Spikes: the same shield, now rimmed with short iron spikes -
    Thorns worn on the shield instead of drawn as a stray caltrop."""
    im = shield_icon()
    tips = ((3, 0), (7, -1), (11, 0), (0, 6), (15, 6), (2, 13), (13, 13))
    for x, y in tips:
        put(im, x, y, IRON_LIGHT)
        put(im, x, y + 1 if y < 8 else y - 1, IRON_DARK)
    put(im, 15, 6, BLOOD)
    put(im, 16, 5, BLOOD)
    save(im, "spikes")


def rush():
    """Shield Rush: the shield leaning into a sprint, a wind-charge swirl
    (vanilla's own burst sprite, shrunk) kicked up at the heel, three speed
    dashes trailing it."""
    im = shield_icon()
    for i, y in enumerate((3, 7, 11)):
        for x in range(0, 3 - i % 2):
            put(im, x, y, ARC_DIM if x == 0 else ARC)
    gust = vanilla("item/wind_charge.png").resize((7, 7), Image.NEAREST)
    im.alpha_composite(faded(gust, 210), (-2, 9))
    save(im, "rush")


def braced():
    """Braced: the shield with a small gold clock riding the corner and a
    downward tick beside it - every block shaves the second right off."""
    im = shield_icon()
    clock = vanilla("item/clock_00.png").resize((8, 8), Image.NEAREST)
    im.alpha_composite(clock, (9, 8))
    for x, y in ((15, 7), (16, 8), (14, 8)):
        put(im, x, y, GOLD)
    put(im, 15, 9, GOLD_DARK)
    save(im, "braced")


def reflect():
    """Reflection: an arrow arriving low, the same arrow already leaving
    high and flipped - the shield sends it back the way it came."""
    im = shield_icon()
    arrow = vanilla("item/arrow.png").resize((10, 10), Image.NEAREST)
    incoming = faded(arrow.rotate(90, expand=True), 235)
    im.alpha_composite(incoming, (-4, 7))
    outgoing = arrow.rotate(-90, expand=True).transpose(Image.FLIP_TOP_BOTTOM)
    im.alpha_composite(outgoing, (-5, -4))
    for x, y in ((3, 3), (5, 1)):
        put(im, x, y, ARC_DIM)
    save(im, "reflect")


def taunt():
    """Taunt: the goat horn, mid-call - sound rings spreading from the
    bell, one gone hostile-red to say the call is a challenge."""
    horn = vanilla("item/goat_horn.png")
    im = canvas()
    im.alpha_composite(horn, (0, 0))
    rings = (((14, 3), ARC), ((15, 4), ARC), ((14, 5), ARC),
             ((16, 2), ARC_DIM), ((16, 6), ARC_DIM))
    for (x, y), c in rings:
        put(im, x, y, c)
    for x, y in ((13, 1), (14, 0)):
        put(im, x, y, (224, 64, 32, 255))
    save(im, "taunt")


def omni_block():
    """Bulwark (capstone): the real shield flanked by two faded ghost
    copies covering the sides - blocking every direction at once - framed
    with the small gold corner ticks this set uses for capstones."""
    im = canvas()
    ghost = faded(shield_icon(), 130)
    small = ghost.resize((11, 11), Image.NEAREST)
    im.alpha_composite(small, (-3, 3))
    im.alpha_composite(small.transpose(Image.FLIP_LEFT_RIGHT), (8, 3))
    im.alpha_composite(shield_icon(), (2, 1))
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(im, x, y, GOLD)
    save(im, "omni_block")


def ground_slam():
    """Ground Slam (capstone): the anvil (built the way this set builds any
    block item - real block texture folded into an iso cube) coming down
    on ground that's already cracking, shockwaves kicked out both sides."""
    im = canvas()
    ground = ((110, 78, 46, 255), (78, 54, 30, 255))
    for x in range(16):
        put(im, x, 13, ground[0])
        put(im, x, 14, ground[1])
    crack = (46, 30, 14, 255)
    for x, y in ((3, 13), (2, 14), (12, 13), (13, 14), (7, 13), (8, 14)):
        put(im, x, y, crack)
    cube = iso_block("anvil", size=12)
    im.alpha_composite(cube, (2, 0))
    for x, y in ((0, 11), (15, 11), (2, 9), (13, 9)):
        put(im, x, y, ARC_DIM)
    for x, y in ((0, 0), (15, 0)):
        put(im, x, y, GOLD)
    save(im, "ground_slam")


if __name__ == "__main__":
    main()
