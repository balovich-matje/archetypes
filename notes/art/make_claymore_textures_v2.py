"""Generate 32x32 claymore item sprites, one per tool material.

v2 of make_claymore_textures.py: same idea (palettes sampled from each vanilla
sword sprite so materials match for free), but double resolution and a real
greatsword silhouette — long blade with a central fuller, wide diagonal
crossguard with flared tips, two-hand grip with leather wraps, round pommel.

The sprite plane in-world is still one "item unit" scaled by the model's
display transforms, so doubling the texture doubles detail, not size.

Usage: python3 make_claymore_textures_v2.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
DST = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "../../src/main/resources/assets/archetypes/textures/item"))

MATERIALS = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"]

SIZE = 32


def palette(name):
    """Pull blade/edge/guard/handle colours out of the vanilla sword sprite."""
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/item/{name}_sword.png") as f:
            sword = Image.open(f).convert("RGBA").copy()

    px = [sword.getpixel((x, y)) for y in range(16) for x in range(16)]
    opaque = [p for p in px if p[3] > 0]

    def luma(p):
        return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]

    opaque.sort(key=luma)
    handle = opaque[len(opaque) // 8]
    blade = opaque[int(len(opaque) * 0.72)]
    edge = opaque[-1]
    guard = opaque[len(opaque) // 3]

    def shade(p, f):
        return (max(0, min(255, int(p[0] * f))),
                max(0, min(255, int(p[1] * f))),
                max(0, min(255, int(p[2] * f))), 255)

    return {
        "B": blade,                 # blade face
        "E": edge,                  # bright edge highlight (upper-left)
        "F": shade(blade, 0.78),    # fuller groove down the middle
        "D": shade(blade, 0.60),    # shaded edge (lower-right)
        "G": guard,                 # crossguard / pommel / wraps
        "g": shade(guard, 0.65),    # guard shadow side
        "H": handle,                # grip leather
    }


def build(name):
    c = palette(name)
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    def put(x, y, key):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            im.putpixel((x, y), c[key])

    # Blade runs up the main diagonal: on row y its left edge sits at
    # x = 28 - y, then the cross-section extends to the right. Left pixel is
    # the lit edge, the doubled middle carries the fuller, right pixel is in
    # shadow. Eight pixels across — this is a six-ingot slab of metal, so it
    # reads as a wall of blade with a single bevel at the tip, not a taper.
    def blade_row(y, section):
        x = 28 - y
        for i, key in enumerate(section):
            put(x + i, y, key)

    blade_row(1, "EB")
    blade_row(2, "EBBD")
    blade_row(3, "EBBBBD")
    blade_row(4, "EBBFBBD")
    for y in range(5, 19):
        blade_row(y, "EBBFFBBD")

    # Crossguard: a chunky straight bar, wider than even this blade.
    for y, x0 in ((19, 7), (20, 6)):
        for x in range(x0, x0 + 12):
            put(x, y, "G")
        put(x0 - 1, y, "g")
    put(19, 19, "E")  # upper tip glint
    put(17, 21, "g")  # lower tip shadow

    # Two-hand grip: two pixels thick, leather with guard-coloured wraps.
    for y in range(21, 28):
        x = 29 - y
        wrap = y in (22, 24, 26)
        put(x, y, "G" if wrap else "H")
        put(x + 1, y, "H" if wrap else "g")

    # Pommel: a squared-off counterweight block, same slab language.
    for x, y in ((2, 28), (3, 28), (1, 29), (2, 29), (3, 29), (1, 30), (2, 30)):
        put(x, y, "G")
    put(1, 28, "E")
    put(3, 30, "g")

    im.save(os.path.join(DST, f"{name}_claymore.png"))
    print(f"{name}_claymore.png")


def main():
    os.makedirs(DST, exist_ok=True)
    for m in MATERIALS:
        build(m)


if __name__ == "__main__":
    main()
