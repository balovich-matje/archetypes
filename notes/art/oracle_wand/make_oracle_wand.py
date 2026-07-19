"""Generate the Oracle's Wand item texture (16x16, static).

Sibling of the five existing wands, so it is built the same way they are in
notes/art/make_weapon_textures.py: take the vanilla stick, shift it down-left
to clear room for a big head, then dress the tip. Nothing is hand-drawn that
the family already answers -- the silhouette, the 3px diagonal shaft, the
alternating core dither and the light-outline/dark-outline pair are the
vanilla stick's own pixels, only recoloured from wood to netherite.

The head is vanilla's nether_star compressed to a 5x5 four-point star: the
same white-yellow core, white ring and grey-teal rim that read as "nether
star" at inventory size, socketed onto the shaft's tip the way the
apprentice wand's crystal is.

Usage: python3 make_oracle_wand.py [preview]
"""
import os
import sys
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.normpath(os.path.join(
    HERE, "../../../src/main/resources/assets/archetypes/textures/item"))

# The stick's four roles -> netherite. Same luminance ordering as the wood
# (mid outline / bright core / dim core / darkest outline), same 4-entry
# palette, hue pulled to netherite's grey-brown with a faint violet sheen.
NETHERITE = {
    (73, 54, 21, 255): (52, 45, 54, 255),      # left outline, mid-dark
    (137, 103, 39, 255): (95, 90, 106, 255),   # core, lit
    (104, 78, 30, 255): (74, 69, 83, 255),     # core, shaded (the dither)
    (40, 30, 11, 255): (22, 19, 24, 255),      # right outline, darkest
}
FITTING = (131, 125, 144, 255)   # polished collar under the star
FITTING_DIM = (100, 95, 112, 255)

# Nether star head, as (dx, dy, colour) offsets from the shaft's tip pixel --
# the same convention the other wands' tips use. Colours are lifted straight
# out of assets/minecraft/textures/item/nether_star.png.
STAR = (
    (0, -2, (224, 226, 119, 255)),   # heart: the star's saturated yellow
    (0, -3, (253, 255, 168, 255)),   # arms: pale lemon, N/S/E/W
    (-1, -2, (253, 255, 168, 255)),
    (1, -2, (253, 255, 168, 255)),
    (0, -1, (253, 255, 168, 255)),
    (-1, -3, (85, 107, 107, 255)),   # dark diagonals: the notches between arms
    (1, -3, (85, 107, 107, 255)),
    (-1, -1, (85, 107, 107, 255)),
    (1, -1, (85, 107, 107, 255)),
    (0, -4, (218, 226, 226, 255)),   # the four points, white like the star's rim
    (-2, -2, (218, 226, 226, 255)),
    (2, -2, (218, 226, 226, 255)),
    (0, 0, (218, 226, 226, 255)),    # lower point, socketed over the tip pixel
)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def oracle_wand():
    stick = vanilla("item/stick.png")
    src = stick.load()
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()

    shift = (-2, 2)   # holy_wand's shift: the longest shaft that still fits
    for y in range(16):
        for x in range(16):
            if src[x, y][3]:
                tx, ty = x + shift[0], y + shift[1]
                if 0 <= tx < 16 and 0 <= ty < 16:
                    px[tx, ty] = NETHERITE[src[x, y]]

    tx, ty = next((x, y) for y in range(16) for x in range(15, -1, -1) if px[x, y][3])

    # A two-pixel ferrule where the shaft meets the star, so the netherite
    # reads as worked metal rather than a grey stick.
    px[tx - 1, ty + 1] = FITTING          # core pixel, so the outlines survive
    px[tx - 2, ty + 2] = FITTING_DIM

    for dx, dy, c in STAR:
        x, y = tx + dx, ty + dy
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    return im


if __name__ == "__main__":
    im = oracle_wand()
    im.save(os.path.join(DST, "oracle_wand.png"))
    print(os.path.join(DST, "oracle_wand.png"))

    if "preview" in sys.argv[1:]:
        im.resize((128, 128), Image.NEAREST).save(os.path.join(HERE, "oracle_wand_8x.png"))
