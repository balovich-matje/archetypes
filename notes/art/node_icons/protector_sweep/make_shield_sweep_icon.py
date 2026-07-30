"""Redraw the Protector capstone's node icon for Shield Sweep.

The cell kept its constant (Family.GROUND_SLAM) through two reworks, so the
file it draws is still ground_slam.png; only the picture moves. What it held
before was two converging spears, which is now a picture of nothing this mod
does.

Composed, like every other icon in this set, from art already in the tree so
the palette matches without anyone matching it by hand:

  * the plain shield is source/plain_shield.png, the Reinforced Straps icon as
    it was drawn — the one icon in the set carrying a shield and nothing else.
    It is kept beside the generator rather than read out of the shipped set for
    the reason every node_icons folder keeps its own sources: a generator that
    reads a shipped file breaks the day that file is redrawn. Cropped at y=26
    to drop the little green durability bar the node draws under it.
  * the two arcs use wide.png's own greys.

Two arcs opening left and right from behind the shield, not one arc over the
top, and the difference is the point: Wide Swings' icon is a single high, thin
arc with red tips, and Shield Sweep has to read as a different picture from the
node that feeds it. Two thick wings leaving the centre is also literally what
the ability animates — one shield sweeping out, or two sweeping out mirrored.

Usage: python3 make_shield_sweep_icon.py
"""
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
NODES = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/protector"))

SIZE = 32
# The shield alone: plain_shield.png's alpha bbox runs to y=30, but rows 27-30
# are the durability bar. Cropping at 26 leaves the shield and drops the bar.
SHIELD_BOX = (9, 3, 22, 26)
SHIELD_AT = (9, 3)

ARC_LIGHT = (206, 207, 213, 255)
ARC_MID = (163, 164, 170, 255)
ARC_DARK = (96, 97, 104, 255)

# The arcs are two circle segments sharing a centre ABOVE the icon, so both
# hang below it and open outward — the path a shield takes leaving the block
# position. The numbers are the ones that were looked at rather than derived:
# a 32px arc taken straight off a circle equation lands on the wrong pixels as
# often as not.
ARC_CENTRE = (16, 6)
ARC_RADIUS = 16
ARC_SPAN = 155


def arc_pixels(centre, radius, start, end, step=1.2):
    """Distinct pixels along a circle segment, in degrees, y growing downward."""
    pixels = []
    angle = min(start, end)

    while angle <= max(start, end):
        point = (int(round(centre[0] + radius * math.cos(math.radians(angle)))),
                 int(round(centre[1] - radius * math.sin(math.radians(angle)))))

        if point not in pixels:
            pixels.append(point)

        angle += step

    return pixels


def wings(canvas):
    pixels = canvas.load()

    def put(point, colour):
        if 0 <= point[0] < SIZE and 0 <= point[1] < SIZE:
            pixels[point] = colour

    for outward in (1, -1):
        # The right wing runs -ARC_SPAN..-8; the left is its mirror across 180.
        near, far = (-ARC_SPAN, -8) if outward > 0 else (188, 180 + ARC_SPAN)
        inner_near = near + 6 * outward
        inner_far = far - 6 * outward

        for point in arc_pixels(ARC_CENTRE, ARC_RADIUS, near, far):
            put(point, ARC_MID)
            # The dark row sits ABOVE the arc: these hang under the light
            # source the whole set shares, and a rim drawn below would read as
            # a second, thinner arc.
            put((point[0], point[1] - 1), ARC_DARK)

        for point in arc_pixels(ARC_CENTRE, ARC_RADIUS - 2, inner_near, inner_far):
            put(point, ARC_LIGHT)


def main():
    source = Image.open(os.path.join(HERE, "source", "plain_shield.png")).convert("RGBA")
    shield = source.crop(SHIELD_BOX)

    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # Arcs first: the shield is the subject and nothing may cross it.
    wings(canvas)
    canvas.alpha_composite(shield, SHIELD_AT)

    out = os.path.join(NODES, "ground_slam.png")
    canvas.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
