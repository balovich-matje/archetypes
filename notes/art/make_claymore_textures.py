"""Generate placeholder claymore item sprites, one per tool material.

Palettes are sampled from each vanilla sword sprite rather than invented, so a
netherite claymore sits next to a netherite sword without clashing — and so the
set stays consistent for free if Mojang ever retints a material.

The shape is hand-plotted on the 16x16 grid: same diagonal as a sword, but a
two-pixel-wide blade running further up the frame, and a wider crossguard. It is
a placeholder — the point is that it reads as "bigger sword" at a glance.

Usage: python3 make_claymore_textures.py
"""
import os

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
DST = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "../../src/main/resources/assets/archetypes/textures/item"))

MATERIALS = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"]

# Rows top-to-bottom, 16x16. B = blade, E = edge highlight, G = guard/pommel
# (dark), H = grip, . = empty.
#
# Laid out on vanilla's own sword skeleton — blade on the up-right diagonal in
# rows 0-9, crossguard around 10-11, grip and pommel trailing to the bottom-left.
# The claymore differences: a blade one pixel thicker, and a crossguard notably
# wider than the blade, which is what reads as "greatsword" at 16px.
SHAPE = [
    "............EBB.",
    "...........EBBB.",
    "..........EBBB..",
    ".........EBBB...",
    "........EBBB....",
    ".......EBBB.....",
    "......EBBB......",
    ".....EBBB.......",
    "....EBBB........",
    "...EBBB.........",
    "..EBBB..........",
    ".GGGGGG.........",
    "GGGGGGGG........",
    "..HH............",
    ".HHH............",
    "GGH.............",
]


def palette(name):
    """Pull blade/edge/guard/handle colours out of the vanilla sword sprite."""
    import zipfile

    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/item/{name}_sword.png") as f:
            sword = Image.open(f).convert("RGBA").copy()

    px = [sword.getpixel((x, y)) for y in range(16) for x in range(16)]
    opaque = [p for p in px if p[3] > 0]

    def luma(p):
        return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]

    opaque.sort(key=luma)
    # Handle is the dark end (wood/stick), blade the bright end.
    handle = opaque[len(opaque) // 8]
    blade = opaque[int(len(opaque) * 0.72)]
    edge = opaque[-1]
    guard = opaque[len(opaque) // 3]
    return {"B": blade, "E": edge, "G": guard, "H": handle}


def build(name):
    colours = palette(name)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    for y, row in enumerate(SHAPE):
        for x, ch in enumerate(row):
            if ch in colours:
                im.putpixel((x, y), colours[ch])

    path = f"{DST}/{name}_claymore.png"
    im.save(path)
    print(f"{name}_claymore.png  blade={colours['B'][:3]} handle={colours['H'][:3]}")


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)

    for m in MATERIALS:
        build(m)
