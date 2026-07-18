"""Magic Missile sprites, variant-b: the "arcane star-mote".

The old amethyst-shard read as a dropped gem — an inventory item, not a spell in
flight. This replaces it with a compact four-point star of arcane light: the
vanilla nether_star's radial silhouette (a real client-jar sprite) recolored
pixel-for-pixel from its teal/cream palette into the Wizard tree's amethyst
purples (sampled from the real amethyst_shard), with a hand-plotted cream-lilac
core. Radially symmetric, so it never looks "wrong" when the thrown-item
renderer spins or billboards it, and it reads as MAGIC at a glance instead of as
loot. Empowered is the same mote overcharged: hotter white core, rays pushed one
pixel longer, and a faint lilac halo — a clear step up that the 1.5x render size
then amplifies.

Usage: python3 make_missile_fx.py   (writes missile.png, empowered.png, preview.png)
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))

# Amethyst ramp, sampled straight out of item/amethyst_shard.png so the mote
# belongs to the same material as the AMETHYST_BLOCK_CHIME cast sound and the
# Wizard tree. Dark -> light.
A_EDGE = (0x3A, 0x27, 0x60, 255)   # deep arcane, one step below the shard's dark
A_DARK = (0x54, 0x39, 0x8A, 255)   # amethyst_shard #54398A
A_MID = (0x6F, 0x4F, 0xAB, 255)    # amethyst_shard #6F4FAB (body)
A_LM = (0x8D, 0x6A, 0xCC, 255)     # amethyst_shard #8D6ACC
A_LIGHT = (0xB3, 0x8E, 0xF3, 255)  # amethyst_shard #B38EF3 (highlight)
A_PALE = (0xD9, 0xC6, 0xFF, 255)   # pushed one lighter for the inner glow
CORE = (0xF3, 0xEA, 0xFF, 255)     # near-white lilac core
CREAM = (0xFF, 0xFD, 0xD5, 255)    # the shard's own #FFFDD5 sparkle, one pixel


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def luma(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def ramp(l):
    """Map a nether_star luminance onto the amethyst ramp."""
    if l < 118:
        return A_EDGE
    if l < 140:
        return A_DARK
    if l < 165:
        return A_MID
    if l < 200:
        return A_LM
    if l < 219:
        return A_LIGHT
    return A_PALE


def recolored_star():
    """nether_star, arm for arm, in amethyst. Alpha preserved."""
    star = vanilla("item/nether_star.png")
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    src = star.load()
    dst = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = src[x, y]
            if a < 40:
                continue
            dst[x, y] = ramp(luma(r, g, b))[:3] + (a,)
    return out


def blend(base, over):
    """over at full onto base, honoring over's own alpha as coverage."""
    b = base.copy()
    b.alpha_composite(over)
    return b


def soft_pixel(im, x, y, color, alpha):
    """Add a translucent pixel only where the canvas is currently empty
    (so the halo sits *around* the star, never dulls its lit body)."""
    if not (0 <= x < 16 and 0 <= y < 16):
        return
    r, g, b, a = im.getpixel((x, y))
    if a > 20:
        return
    im.putpixel((x, y), color[:3] + (alpha,))


def missile():
    im = recolored_star()
    px = im.load()
    # Hand-plotted core: the star's centre is nether-yellow; overwrite the
    # bright middle with a lilac-white heart + one cream glint, so the mote
    # has a hot centre that stays in the amethyst family.
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8), (7, 6), (8, 9)):
        px[x, y] = A_PALE
    for x, y in ((7, 7), (8, 8)):
        px[x, y] = CORE
    px[7, 8] = CREAM  # the single warm sparkle, borrowed from the shard
    im.save(os.path.join(HERE, "missile.png"))
    return im


def empowered():
    im = recolored_star()
    px = im.load()
    # Overcharged core: a fatter, hotter white heart.
    for x, y in ((6, 7), (9, 7), (6, 8), (9, 8), (7, 6), (8, 6), (7, 9), (8, 9)):
        px[x, y] = A_PALE
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = CORE
    px[7, 7] = (0xFF, 0xFF, 0xFF, 255)
    px[8, 8] = CREAM
    # Rays pushed one pixel longer, on the star's true axes (its tips sit at
    # (2,8),(13,8),(8,2),(8,13)) so the extensions read as the mote straining
    # bigger, not as stray specks.
    for x, y in ((8, 1), (1, 8), (14, 8), (8, 14)):
        soft_pixel(im, x, y, A_LIGHT, 235)
    # Faint lilac halo hugging the star, empty cells only, so it glows without
    # smearing. One-pixel skirt around the existing silhouette.
    ring = []
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] > 60:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ring.append((x + dx, y + dy))
    for x, y in ring:
        soft_pixel(im, x, y, A_LIGHT, 70)
    im.save(os.path.join(HERE, "empowered.png"))
    return im


def preview(normal, emp):
    """Both sprites at 4x on #2b2b2b, plus a light-background strip and a
    45-degree-rotated pair (the renderer billboards & can spin these)."""
    scale = 8
    pad = 12
    bg = (0x2B, 0x2B, 0x2B, 255)
    light = (0xCF, 0xD6, 0xDE, 255)   # a snow/sky-ish worst case
    cell = 16 * scale
    cols = 3
    rows = 2
    W = cols * cell + (cols + 1) * pad
    H = rows * cell + (rows + 1) * pad + 20
    sheet = Image.new("RGBA", (W, H), bg)

    def place(im, col, row, background):
        x0 = pad + col * (cell + pad)
        y0 = pad + row * (cell + pad)
        tile = Image.new("RGBA", (cell, cell), background)
        tile.alpha_composite(im.resize((cell, cell), Image.NEAREST))
        sheet.alpha_composite(tile, (x0, y0))

    place(normal, 0, 0, bg)
    place(emp, 1, 0, bg)
    # rotated normal (worst-case spin) at 30 deg
    rot = normal.rotate(30, resample=Image.NEAREST, expand=False)
    place(rot, 2, 0, bg)
    # light background row
    place(normal, 0, 1, light)
    place(emp, 1, 1, light)
    place(rot, 2, 1, light)
    sheet.convert("RGB").save(os.path.join(HERE, "preview.png"))


def main():
    n = missile()
    e = empowered()
    preview(n, e)
    print("wrote missile.png, empowered.png, preview.png")


if __name__ == "__main__":
    main()
