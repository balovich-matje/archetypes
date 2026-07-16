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
    great = Image.open(os.path.join(ASSETS, "archetypes/textures/item/iron_greatsword.png"))
    im = canvas()
    im.alpha_composite(great.convert("RGBA").resize((16, 16), Image.NEAREST), (0, 0))
    im.putpixel((14, 0), BLOOD)
    im.putpixel((13, 1), BLOOD)
    im.putpixel((14, 1), BLOOD_LIGHT)
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
    great = Image.open(os.path.join(ASSETS, "archetypes/textures/item/iron_greatsword.png"))
    sweep = vanilla("particle/sweep_2.png")
    im = canvas()
    im.alpha_composite(faded(sweep.resize((16, 16), Image.NEAREST), 230), (0, 0))
    im.alpha_composite(great.convert("RGBA").resize((16, 16), Image.NEAREST), (0, 0))
    save(im, "decimate")


def main():
    os.makedirs(DST, exist_ok=True)
    bladestorm()
    blade_dance()
    rend()
    taste_of_blood()
    first_blood()
    executioner()
    decimate()


if __name__ == "__main__":
    main()
