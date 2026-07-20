"""Generate the 18x18 status-effect icon for Aura of Radiance.

Derived from the node sprite of the same name (make_oracle_priest_icons.py,
aura_of_radiance) so the buff on the effect list and the node in the tree read
as one thing: the same hollow gold ring, the same white-hot caster at the
middle, the same light breaking out past the rim, in the same holy palette.

Two things differ, and both are the format's rules rather than a redesign.
The node sprite is 16px art blown up 2x onto a 32px canvas; a mob-effect icon
is drawn at its native 18x18 and never scaled, so this is plotted at 1x like
the shipped mana_restore / mana_regeneration icons. And 18px is two pixels
wider than 16, which the ring simply grows into — a bigger hoop rather than a
16px drawing floating in a padded frame.

Output goes to ./out and, with --install, to
assets/archetypes/textures/mob_effect/aura_of_radiance.png.

Usage: python3 make_effect_icon.py [--install]
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(HERE, "out")
INSTALL = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/mob_effect"))

# The holy palette, identical to the node set.
GOLD_D = (150, 96, 18, 255)
GOLD = (232, 168, 34, 255)
GOLD_L = (255, 210, 84, 255)
CREAM = (255, 246, 206, 255)

SIZE = 18
C = SIZE / 2.0


def put(im, x, y, col):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((int(x), int(y)), col)


def ring(im, r_in, r_out, col):
    for y in range(im.height):
        for x in range(im.width):
            if r_in <= math.hypot(x + 0.5 - C, y + 0.5 - C) < r_out:
                put(im, x, y, col)


def disc(im, r, col):
    ring(im, 0, r, col)


def aura_of_radiance():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # The hoop: three concentric tones, dark on the outside so it keeps an
    # edge against a light inventory background.
    ring(im, 6.2, 8.4, GOLD_D)
    ring(im, 6.4, 8.0, GOLD)
    ring(im, 6.7, 7.6, GOLD_L)

    # The caster, white-hot at the middle.
    disc(im, 3.6, GOLD)
    disc(im, 2.7, GOLD_L)
    disc(im, 1.8, CREAM)

    # Light breaking out past the ring, so it radiates instead of sitting
    # there being a gold hoop. Diagonals step out, cardinals reach the frame.
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for k, col in ((6, GOLD_L), (7, GOLD)):
            put(im, C + dx * k - (1 if dx > 0 else 0),
                C + dy * k - (1 if dy > 0 else 0), col)

    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        for k, col in ((8, CREAM), (9, GOLD_L)):
            put(im, C + dx * k - (1 if dx > 0 else 0),
                C + dy * k - (1 if dy > 0 else 0), col)

    return im


def main():
    im = aura_of_radiance()
    os.makedirs(DST, exist_ok=True)
    im.save(os.path.join(DST, "effect_aura_of_radiance.png"))

    if "--install" in sys.argv:
        os.makedirs(INSTALL, exist_ok=True)
        im.save(os.path.join(INSTALL, "aura_of_radiance.png"))
        print("installed -> " + INSTALL)


if __name__ == "__main__":
    main()
