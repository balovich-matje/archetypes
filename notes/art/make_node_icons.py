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


def canvas(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def greatsword_item():
    """The greatsword item texture, upscaled 2x NEAREST to the icons' 32px
    canvas — the item art went vanilla-blocky at 16px, and the crisp double
    keeps these icons matching the weapon in hand."""
    im = Image.open(os.path.join(
        ASSETS, "archetypes/textures/item/iron_greatsword.png")).convert("RGBA")
    return im.resize((32, 32), Image.NEAREST)


def shield_face():
    """The vanilla shield's front plate, cut from the entity texture — the
    only flat pixels of the real shield that exist (the item icon is a 3D
    render). Used for Bulwark's ghost copies."""
    return vanilla("entity/shield/shield_base_nopattern.png").convert("RGBA").crop((0, 0, 13, 23))


IRON = (200, 200, 208, 255)
IRON_DARK = (120, 120, 128, 255)
IRON_LIGHT = (238, 238, 244, 255)


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
    """An unbloodied greatsword — except the very tip. Composed at the item
    texture's own 32px so it matches the weapon in hand."""
    im = canvas(32)
    im.alpha_composite(greatsword_item(), (0, 0))
    for x, y, c in ((28, 1, BLOOD), (29, 1, BLOOD), (27, 2, BLOOD), (28, 2, BLOOD_LIGHT),
                    (29, 2, BLOOD), (27, 3, BLOOD_DARK), (28, 3, BLOOD)):
        im.putpixel((x, y), c)
    # The falling drop, at double scale to match the canvas.
    for dx, dy, c in ((0, 0, BLOOD), (0, 1, BLOOD), (1, 1, BLOOD),
                      (-1, 2, BLOOD_DARK), (0, 2, BLOOD_LIGHT), (1, 2, BLOOD), (2, 2, BLOOD),
                      (-1, 3, BLOOD), (0, 3, BLOOD_LIGHT), (1, 3, BLOOD), (2, 3, BLOOD),
                      (0, 4, BLOOD_DARK), (1, 4, BLOOD_DARK)):
        im.putpixel((28 + dx, 7 + dy), c)
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
    """The cleave itself: vanilla's own sweep flash, greatsword through it —
    both at their native 32px."""
    im = canvas(32)
    im.alpha_composite(faded(vanilla("particle/sweep_2.png").resize((32, 32), Image.NEAREST), 230), (0, 0))
    im.alpha_composite(greatsword_item(), (0, 0))
    save(im, "decimate")


def heavy_blows():
    """The greatsword mid-heavy-swing: a semi-transparent echo of the blade
    hanging behind the real one, bladestorm-style — weight read as motion."""
    gs = greatsword_item()
    im = canvas(32)
    echo = gs.rotate(-28, resample=Image.NEAREST, expand=False)
    im.alpha_composite(faded(echo, 120), (0, 0))
    im.alpha_composite(gs, (0, 0))
    save(im, "heavy_blows")


def bash_overlay():
    """The thrust: speed lines trailing left, impact bursting front-right."""
    im = canvas(32)
    for y in (7, 15, 23):
        for x in range(1, 7):
            im.putpixel((x, y), ARC if x > 3 else ARC_DIM)
    for dx, dy in ((0, -2), (0, -1), (0, 1), (0, 2), (-2, 0), (-1, 0), (1, 0), (2, 0)):
        im.putpixel((27 + dx, 13 + dy), ARC)
    im.putpixel((27, 13), (255, 236, 160, 255))
    for dx, dy in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        im.putpixel((27 + dx, 13 + dy), ARC_DIM)
    save(im, "bash_overlay")


def shield_slam_overlay():
    """Vanilla's own Strength effect icon, peeking out from behind the
    shield (the tree draws this layer first, the item render covers it)."""
    im = canvas(32)
    im.alpha_composite(vanilla("mob_effect/strength.png"), (13, 0))
    save(im, "shield_slam_overlay")


def iron_spikes():
    """One big caltrop, item-sprite style: dark outline first, iron body
    over it, bright tips — readable on any background."""
    im = canvas(32)
    OUTLINE = (34, 34, 38, 255)

    def limb(x0, y0, x1, y1, width=2):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            for dx in range(-width, width + 1):
                for dy in range(-width, width + 1):
                    if abs(dx) + abs(dy) <= width and 0 <= x + dx < 32 and 0 <= y + dy < 32:
                        im.putpixel((x + dx, y + dy), OUTLINE)

    # Outline pass: thick dark limbs.
    limb(16, 17, 16, 4)      # up spike
    limb(16, 17, 6, 27)      # left leg
    limb(16, 17, 26, 27)     # right leg
    limb(16, 17, 16, 24)     # front leg, foreshortened

    # Body pass: iron over the outline, one pixel slimmer.
    def core(x0, y0, x1, y1, colour):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            im.putpixel((x, y), colour)
            if x + 1 < 32:
                im.putpixel((x + 1, y), colour)

    core(16, 17, 16, 5, IRON)
    core(16, 17, 7, 26, IRON_DARK)
    core(16, 17, 25, 26, IRON)
    core(16, 17, 16, 23, IRON_DARK)

    # Tips and the joint highlight.
    im.putpixel((16, 4), IRON_LIGHT)
    im.putpixel((17, 5), IRON_LIGHT)
    im.putpixel((6, 27), IRON_LIGHT)
    im.putpixel((26, 27), IRON_LIGHT)
    im.putpixel((16, 16), IRON_LIGHT)
    im.putpixel((17, 16), IRON_LIGHT)
    save(im, "iron_spikes")


def wide_swings_overlay():
    """The bash's arc thrown wide over the shield — two pixels thick and
    full white so it actually reads at slot size."""
    im = canvas(32)
    arc = ((1, 11), (2, 8), (3, 6), (5, 4), (7, 2), (10, 1), (13, 0), (16, 0),
           (19, 1), (22, 2), (24, 4), (26, 6), (27, 8), (28, 11))
    white = (255, 255, 255, 255)
    for x, y in arc:
        im.putpixel((x, y), white)
        im.putpixel((x + 1, y), white)
        if y + 1 < 32:
            im.putpixel((x, y + 1), ARC_DIM)
    save(im, "wide_swings_overlay")


def braced_overlay():
    """A quarter-size gold clock in the corner: blocking pays time back."""
    im = canvas(32)
    im.alpha_composite(vanilla("item/clock_00.png"), (16, 0))
    save(im, "braced_overlay")


def reflection_overlay():
    """An arrow arriving, its ricochet already leaving."""
    im = canvas(32)
    arrow = vanilla("item/arrow.png").transpose(Image.FLIP_TOP_BOTTOM)
    im.alpha_composite(arrow, (-3, 14))
    im.putpixel((11, 27), IRON_LIGHT)
    for i, (x, y) in enumerate(((8, 24), (5, 21), (3, 18), (2, 15))):
        im.putpixel((x, y), ARC if i < 2 else ARC_DIM)
    save(im, "reflection_overlay")


def bulwark_overlay():
    """Ghost shields guarding the flanks of the real one."""
    im = canvas(32)
    face = shield_face()
    im.alpha_composite(faded(face, 130), (0, 5))
    im.alpha_composite(faded(face, 130), (19, 5))
    save(im, "bulwark_overlay")


def ground_slam_overlay():
    """The ground giving way under the anvil."""
    im = canvas(32)
    ground = ((110, 78, 46, 255), (78, 54, 30, 255))
    for x in range(0, 32):
        im.putpixel((x, 28), ground[0])
        im.putpixel((x, 29), ground[1])
        im.putpixel((x, 30), ground[1])
    crack = (48, 32, 16, 255)
    for x, y in ((6, 28), (4, 29), (2, 30), (25, 28), (27, 29), (29, 30), (15, 28), (16, 29)):
        im.putpixel((x, y), crack)
    for x, y in ((1, 24), (30, 24), (4, 21), (27, 21)):
        im.putpixel((x, y), ARC_DIM)
    save(im, "ground_slam_overlay")


def meteor_overlay():
    """The mace with a fire charge riding its shoulder — the falling star."""
    im = canvas(32)
    im.alpha_composite(vanilla("item/fire_charge.png"), (16, 0))
    save(im, "meteor_overlay")


def shockwave_overlay():
    """The mace with a compass corner — the blow that reaches outward."""
    im = canvas(32)
    im.alpha_composite(vanilla("item/compass_16.png"), (16, 0))
    save(im, "shockwave_overlay")


def iso_block(texture):
    """A quarter-size isometric block 'item icon' built from a block texture:
    diamond top, shaded left and right faces — the classic inventory cube."""
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


def earth_shatter_overlay():
    """The stone block (as an inventory cube) behind the mace's corner."""
    im = canvas(32)
    im.alpha_composite(iso_block("stone"), (16, 16))
    save(im, "earth_shatter_overlay")


def quake_overlay():
    """Cobblestone filling the frame behind the mace — the earth about to
    answer for it."""
    im = canvas(32)
    im.alpha_composite(vanilla("block/cobblestone.png").resize((32, 32), Image.NEAREST), (0, 0))
    save(im, "quake_overlay")


def invisibility():
    """The Shadow's active: the bad-omen face, its red glare turned to two
    glowing orange eyes in the dark (user concept)."""
    im = vanilla("mob_effect/bad_omen.png")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a and r > 100 and r > g + 40 and r > b + 40:
                # Red glare -> orange glow, brightness preserved.
                k = r / 255
                px[x, y] = (int(255 * k), int(150 * k), 0, a)
    save(im, "invisibility")


def main():
    os.makedirs(DST, exist_ok=True)
    invisibility()
    bladestorm()
    blade_dance()
    rend()
    taste_of_blood()
    first_blood()
    executioner()
    decimate()
    heavy_blows()
    bash_overlay()
    shield_slam_overlay()
    iron_spikes()
    wide_swings_overlay()
    braced_overlay()
    reflection_overlay()
    bulwark_overlay()
    ground_slam_overlay()
    meteor_overlay()
    shockwave_overlay()
    earth_shatter_overlay()
    quake_overlay()


if __name__ == "__main__":
    main()
