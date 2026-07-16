"""Generate 16x16 skill-node icons for the Slayer tree.

Item icons only say "this is an item"; these say what the node DOES. Each is
composed from vanilla sprites (pulled from the client jar, so palettes match
the game for free) plus a few hand-plotted pixels, and lands in
assets/archetypes/textures/node/ — drawn by the tree screen through the
Family sprite-icon path, not registered as items.

Usage: python3 make_node_icons.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
ASSETS = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/assets"))
DST = os.path.join(ASSETS, "archetypes/textures/node")

BLOOD = (200, 32, 32, 255)
BLOOD_DARK = (122, 16, 16, 255)
BLOOD_LIGHT = (240, 96, 96, 255)
ARC = (222, 222, 222, 255)
ARC_DIM = (160, 160, 160, 200)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


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
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def mini_greatsword():
    """The slab greatsword redrawn at 16px — a NEAREST downscale of the item
    texture halves the blade back to slender, so the icon gets its own plot:
    four pixels of blade, bevel tip, chunky guard, stub grip."""
    from make_greatsword_textures import palette
    c = palette("iron")
    im = canvas()

    def put(x, y, key):
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), c[key])

    def blade_row(y, section):
        x = 13 - y
        for i, key in enumerate(section):
            put(x + i, y, key)

    blade_row(1, "EB")
    for y in range(2, 9):
        blade_row(y, "EBFD")

    for x in range(1, 8):
        put(x, 9, "G")
    put(0, 9, "g")
    put(8, 9, "E")

    put(3, 10, "H")
    put(2, 11, "G")
    put(1, 12, "H")
    put(0, 13, "G")
    put(1, 13, "G")
    put(0, 12, "E")
    return im


IRON = (200, 200, 208, 255)
IRON_DARK = (120, 120, 128, 255)
IRON_LIGHT = (238, 238, 244, 255)
WOOD = (140, 106, 62, 255)
WOOD_DARK = (104, 76, 42, 255)


def mini_shield():
    """A heater shield: iron rim, wood face, reused across the Protector set."""
    im = canvas()
    for y, (x0, x1) in enumerate(((4, 11), (3, 12), (3, 12), (3, 12), (3, 12),
                                  (3, 12), (4, 11), (5, 10), (6, 9), (7, 8)), start=2):
        for x in range(x0, x1 + 1):
            rim = x == x0 or x == x1 or y == 2 or y == 11
            im.putpixel((x, y), IRON if rim else (WOOD if (x + y) % 5 else WOOD_DARK))
    im.putpixel((4, 2), IRON_LIGHT)
    im.putpixel((7, 7), IRON_DARK)
    im.putpixel((8, 7), IRON_DARK)
    return im


def save(im, name):
    im.save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


def drop(im, x, y):
    """A 3x4 blood teardrop with its point at (x, y)."""
    for dx, dy, c in ((0, 0, BLOOD), (0, 1, BLOOD), (-1, 2, BLOOD_DARK), (0, 2, BLOOD_LIGHT),
                      (1, 2, BLOOD), (-1, 3, BLOOD), (0, 3, BLOOD_DARK), (1, 3, BLOOD_DARK)):
        px = x + dx
        py = y + dy
        if 0 <= px < 16 and 0 <= py < 16:
            im.putpixel((px, py), c)


def bladestorm():
    """A fan of the same sword mid-whirl: two rotated echoes behind the real
    blade, all sweeping the same way."""
    sword = vanilla("item/iron_sword.png")
    im = canvas()
    for angle, alpha in ((-52, 110), (-26, 185)):
        echo = sword.rotate(angle, resample=Image.NEAREST, expand=False)
        im.alpha_composite(faded(echo, alpha), (0, 0))
    im.alpha_composite(sword, (0, 0))
    save(im, "bladestorm")


def rend():
    """Three claw gashes, top-right to bottom-left."""
    im = canvas()
    for i, (sx, length) in enumerate(((7, 9), (11, 11), (14, 9))):
        for j in range(length):
            x = sx - j
            y = 2 + i + j
            if 0 <= x < 16 and 0 <= y < 16:
                im.putpixel((x, y), BLOOD_LIGHT if j == 0 else BLOOD)
                if x + 1 < 16:
                    im.putpixel((x + 1, y), BLOOD_DARK)
    save(im, "rend")


def taste_of_blood():
    """A heart, and the drip it came from."""
    heart = vanilla("gui/sprites/hud/heart/full.png")
    im = canvas()
    im.alpha_composite(heart.resize((12, 12), Image.NEAREST), (2, 0))
    im.putpixel((8, 11), BLOOD_DARK)
    drop(im, 8, 12)
    im.putpixel((12, 13), BLOOD)
    save(im, "taste_of_blood")


def first_blood():
    """An unbloodied greatsword — except the very tip. This is the
    greatsword path's opener, so it wears the greatsword, not a sword."""
    im = canvas()
    im.alpha_composite(mini_greatsword(), (0, 0))
    im.putpixel((12, 1), BLOOD)
    im.putpixel((13, 1), BLOOD)
    im.putpixel((12, 2), BLOOD_LIGHT)
    im.putpixel((13, 2), BLOOD_DARK)
    drop(im, 14, 4)
    save(im, "first_blood")


def blade_dance():
    """Two swords swinging opposite ways out of the middle, an arc over
    each — strikes going in different directions at once."""
    sword = vanilla("item/iron_sword.png").resize((13, 13), Image.NEAREST)
    im = canvas()
    im.alpha_composite(sword.transpose(Image.FLIP_LEFT_RIGHT), (0, 3))
    im.alpha_composite(sword, (3, 3))
    for x, y in ((12, 1), (14, 2), (3, 1), (1, 2)):
        im.putpixel((x, y), ARC)
    for x, y in ((10, 1), (5, 1)):
        im.putpixel((x, y), ARC_DIM)
    save(im, "blade_dance")


def executioner():
    """The headsman's frame: axe fallen head-down onto the block, swing arc
    still hanging in the air behind it."""
    axe = vanilla("item/iron_axe.png").rotate(180)
    im = canvas()

    # The chopping block, under where the blade lands.
    block = ((90, 58, 26, 255), (60, 38, 16, 255))
    for x in range(1, 9):
        im.putpixel((x, 14), block[0])
        im.putpixel((x, 15), block[1])

    im.alpha_composite(axe, (0, -1))

    # The swing: it came over the top, right to left.
    for x, y in ((11, 1), (13, 3), (14, 5), (15, 8)):
        im.putpixel((x, y), ARC)
    for x, y in ((10, 3), (12, 5), (13, 7)):
        im.putpixel((x, y), ARC_DIM)
    save(im, "executioner")


def decimate():
    """The cleave itself: vanilla's own sweep flash, greatsword through it."""
    sweep = vanilla("particle/sweep_2.png")
    im = canvas()
    im.alpha_composite(faded(sweep.resize((16, 16), Image.NEAREST), 230), (0, 0))
    im.alpha_composite(mini_greatsword(), (0, 0))
    save(im, "decimate")


def bulwark():
    """One shield ahead, its ghosts guarding the sides — block from anywhere."""
    im = canvas()
    for dx, alpha in ((-4, 120), (4, 120)):
        im.alpha_composite(faded(mini_shield(), alpha), (dx, 1))
    im.alpha_composite(mini_shield(), (0, 0))
    save(im, "bulwark")


def shield_slam():
    """The shield mid-slam, impact burst off its face."""
    im = canvas()
    im.alpha_composite(mini_shield(), (-2, 2))
    for x, y in ((12, 4), (14, 4), (13, 3), (13, 5)):
        im.putpixel((x, y), ARC)
    im.putpixel((13, 4), (255, 236, 160, 255))
    im.putpixel((11, 6), ARC_DIM)
    im.putpixel((14, 7), ARC_DIM)
    save(im, "shield_slam")


def iron_spikes():
    """The shield grown teeth."""
    im = canvas()
    im.alpha_composite(mini_shield(), (0, 1))
    for sx, sy, dx, dy in ((2, 4, -1, -1), (13, 4, 1, -1), (2, 9, -1, 1), (13, 9, 1, 1)):
        im.putpixel((sx, sy), IRON_LIGHT)
        im.putpixel((sx + dx, sy + dy), IRON)
    im.putpixel((7, 1), IRON_LIGHT)
    im.putpixel((7, 0), IRON)
    save(im, "iron_spikes")


def wide_swings():
    """The bash's arc thrown wide, shield under it."""
    im = canvas()
    im.alpha_composite(mini_shield().resize((12, 12), Image.NEAREST), (2, 4))
    for i, (x, y) in enumerate(((1, 5), (2, 3), (4, 2), (6, 1), (9, 1), (11, 2), (13, 3), (14, 5))):
        im.putpixel((x, y), ARC if i % 2 else ARC_DIM)
    save(im, "wide_swings")


def braced():
    """Held blocks feed the bash: the shield with the refund circling it."""
    im = canvas()
    im.alpha_composite(mini_shield(), (0, 2))
    for x, y in ((13, 1), (14, 2), (15, 4), (15, 6), (14, 8), (13, 9)):
        im.putpixel((x, y), ARC)
    # Arrowhead at the loop's end, pointing back down-left into the shield.
    im.putpixel((12, 10), ARC)
    im.putpixel((12, 8), ARC_DIM)
    im.putpixel((14, 10), ARC_DIM)
    save(im, "braced")


def ground_slam():
    """The anvil arriving: classic silhouette, ground cracking under it."""
    im = canvas()
    ground = ((110, 78, 46, 255), (78, 54, 30, 255))
    for x in range(0, 16):
        im.putpixel((x, 13), ground[0])
        im.putpixel((x, 14), ground[1])

    for x in range(3, 13):
        im.putpixel((x, 2), IRON_LIGHT)
        im.putpixel((x, 3), IRON)
        im.putpixel((x, 4), IRON_DARK)
    for x in range(6, 10):
        for y in range(5, 9):
            im.putpixel((x, y), IRON if y < 7 else IRON_DARK)
    for x in range(4, 12):
        im.putpixel((x, 9), IRON)
        im.putpixel((x, 10), IRON_DARK)
        im.putpixel((x, 11), IRON_DARK)

    crack = (48, 32, 16, 255)
    for x, y in ((3, 12), (2, 13), (1, 14), (12, 12), (13, 13), (14, 14), (7, 12), (8, 13)):
        im.putpixel((x, y), crack)
    im.putpixel((0, 11), ARC_DIM)
    im.putpixel((15, 11), ARC_DIM)
    save(im, "ground_slam")


def reflection():
    """An arrow meeting the shield and leaving the way it came."""
    arrow = vanilla("item/arrow.png")
    im = canvas()
    shield = mini_shield()
    im.alpha_composite(shield, (6, 2))
    # Incoming arrow, flipped to fly down-right into the shield's face…
    im.alpha_composite(arrow.resize((10, 10), Image.NEAREST).transpose(Image.FLIP_LEFT_RIGHT), (0, 6))
    # …and the ricochet leaving up-left, brightest at the impact.
    im.putpixel((8, 7), IRON_LIGHT)
    for i, (x, y) in enumerate(((6, 5), (4, 3), (2, 1), (1, 0))):
        im.putpixel((x, y), ARC if i < 2 else ARC_DIM)
    save(im, "reflection")


def main():
    os.makedirs(DST, exist_ok=True)
    bladestorm()
    blade_dance()
    rend()
    taste_of_blood()
    first_blood()
    executioner()
    decimate()
    bulwark()
    shield_slam()
    iron_spikes()
    wide_swings()
    braced()
    ground_slam()
    reflection()


if __name__ == "__main__":
    main()
