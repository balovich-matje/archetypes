"""Variant-A Magic Missile art: the Arcane Mote.

The current amethyst shard reads as "a purple rock." This replaces it with a
bolt of condensed arcane force: a four-point star (the universal magic-sparkle
silhouette, radially symmetric so it survives the projectile's tumble) recolored
from vanilla's own nether_star sprite into the amethyst palette, with a
hand-plotted white-hot heart. NORMAL is a compact 4-point mote; EMPOWERED opens
into an 8-point burst with a hotter core and a faint aura — "same family, more
presence," recognizable at a glance and with eyes closed (its cast layers a bell).

Composed from real vanilla sprites (nether_star silhouette + amethyst_shard
palette, both pulled from the client jar) plus hand-plotted accents, exactly the
rule make_node_icons.py follows. Writes only into this variant-a folder.

Usage: python3 make_missile_art.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))

# Real vanilla amethyst_shard pixels, dark -> light: the whole recolour ramp is
# sourced from the game so the mote matches amethyst (and the chime) for free.
RAMP = [(58, 39, 102), (84, 57, 138), (111, 79, 171), (141, 106, 204),
        (179, 142, 243), (207, 160, 243), (254, 203, 230)]
HEART = (255, 255, 255)          # white-hot centre
HEART_WARM = (255, 240, 250)     # pink-white just off centre


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def lum(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def ramp_for(luminance):
    return RAMP[min(len(RAMP) - 1, int(luminance / 255 * len(RAMP)))]


def recolor(star):
    """nether_star -> amethyst violet. Its yellow core is dropped; a fresh
    white-hot heart is plotted afterwards, so the mote reads arcane not astral."""
    out = Image.new("RGBA", star.size, (0, 0, 0, 0))
    sp, op = star.load(), out.load()
    for y in range(star.height):
        for x in range(star.width):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            # The yellow cross gets folded into the violet body by luminance;
            # the plotted heart replaces it, so no astral yellow survives.
            op[x, y] = ramp_for(lum(r, g, b)) + (a,)
    return out


def heart(im, cx=7, cy=7):
    """A 2x2 white core with a pink-white bloom around it — the mote's glow
    source, so the flight trail can stay nearly bare."""
    for dx in (0, 1):
        for dy in (0, 1):
            im.putpixel((cx + dx, cy + dy), HEART + (255,))
    for x, y in ((cx, cy - 1), (cx + 1, cy - 1), (cx - 1, cy), (cx + 2, cy),
                 (cx - 1, cy + 1), (cx + 2, cy + 1), (cx, cy + 2), (cx + 1, cy + 2)):
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), HEART_WARM + (255,))


def diagonal_spikes(im, cx=7.5, cy=7.5, reach=6):
    """The empowered burst's four extra arms at 45 deg -> a real 8-point star,
    the arms nearly as long as the cardinals so it reads as double the points
    (not a 4-point with filled corners). Bright near the core, tapering to the
    deep-violet tip so each spike keeps a crisp edge instead of fogging out."""
    for sx, sy in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        for i in range(2, reach + 1):
            x, y = int(round(cx + sx * i * 0.8)), int(round(cy + sy * i * 0.8))
            if 0 <= x < 16 and 0 <= y < 16 and im.getpixel((x, y))[3] == 0:
                col = RAMP[4] if i <= 3 else (RAMP[3] if i <= 4 else RAMP[1])
                im.putpixel((x, y), col + (255,))


def extend_points(im, cx=7, cy=7):
    """Grow each cardinal arm one pixel past its current tip, so the empowered
    star fills more of the frame while every point stays connected."""
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        tip = 0
        for i in range(1, 8):
            x, y = cx + dx * i, cy + dy * i
            if 0 <= x < 16 and 0 <= y < 16 and im.getpixel((x, y))[3] > 0:
                tip = i
        x, y = cx + dx * (tip + 1), cy + dy * (tip + 1)
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), RAMP[2] + (255,))


def build_normal():
    im = recolor(vanilla("item/nether_star.png"))
    heart(im)
    return im


def build_empowered():
    im = recolor(vanilla("item/nether_star.png"))
    diagonal_spikes(im)
    extend_points(im)
    # A fuller, hotter heart than the normal mote — the "charged" read.
    heart(im)
    for x, y in ((7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)):
        im.putpixel((x, y), HEART_WARM + (255,))
    return im


def main():
    build_normal().save(os.path.join(HERE, "missile.png"))
    build_empowered().save(os.path.join(HERE, "empowered.png"))
    print("missile.png, empowered.png")


if __name__ == "__main__":
    main()
