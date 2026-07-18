"""Variant-C Magic Missile sprites: the Arcane Mote.

A four-pointed spark of force with a hot core and a violet glow, built on the
game's own amethyst palette (sampled from item/amethyst_shard.png so it reads
as the same material the wand is chiming). Radially symmetric on purpose: the
thrown-item renderer billboards and tumbles the sprite, so a four-point star
looks deliberate from every angle where an arrow or shard would look wrong.

normal   -> missile.png    (compact, the spammed cast)
empowered-> empowered.png  (arms to the edge, a second 45 cross, whiter core)

Usage: python3 make_missile.py
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# Amethyst family, sampled from the vanilla client jar (item/amethyst_shard,
# block/amethyst_block). The cream/pink highlights are the stone's own shine.
CORE  = (255, 251, 246, 255)   # hot center
PINK  = (254, 203, 230, 255)   # #FECBE6 amethyst shine
LILAC = (206, 165, 243, 255)   # blend of CFA0F3 / B38EF3
LIGHT = (179, 142, 243, 255)   # #B38EF3
VIOLET = (141, 106, 204, 255)  # #8D6ACC
PURPLE = (111, 79, 171, 255)   # #6F4FAB body
DEEP  = (84, 57, 138, 255)     # #54398A
EDGE  = (52, 34, 88, 255)      # derived outline, violet not black


def bands(h):
    """Heat 0..1 -> amethyst band, or None below the floor."""
    if h >= 0.88:
        return CORE
    if h >= 0.73:
        return PINK
    if h >= 0.59:
        return LILAC
    if h >= 0.45:
        return LIGHT
    if h >= 0.31:
        return VIOLET
    if h >= 0.18:
        return PURPLE
    if h >= 0.06:
        return DEEP
    return None


def star(reach, core_r, width0, taper, diag_reach, diag_gain,
         plateau=0.95):
    """A four-point star heat map on a 16x16 grid, centred at (7.5, 7.5).

    reach      arm half-length (pixels) before heat hits zero
    core_r     radial falloff of the bright core past its white plateau
    width0     arm half-width at the core
    taper      how fast the arm narrows per pixel of length
    diag_reach length of the dim 45-degree glints
    diag_gain  brightness of those glints (0..1)
    plateau    radius held at full-white before the core falls off
    """
    heat = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            adx, ady = abs(dx), abs(dy)
            t = max(adx, ady)      # along the dominant axis
            s = min(adx, ady)      # perpendicular to it
            r = (dx * dx + dy * dy) ** 0.5
            h = 0.0

            # Round core with a white plateau at the very centre so the mote
            # has a hot spark, not a pastel smudge.
            core = 1.0 - max(0.0, r - plateau) / core_r
            h = max(h, core)

            # Vertical / horizontal arms: present while inside the tapering
            # width, heat falling off with length.
            w = width0 - taper * t
            if w > 0.0 and s <= w:
                arm = 1.0 - t / reach
                # a light edge-soften so the arm's flank isn't a hard step
                arm *= max(0.0, 1.0 - (s / (w + 0.35)) * 0.32)
                h = max(h, arm)

            # Dim diagonal glints, the 45-degree hint of a spark.
            if diag_gain > 0.0:
                on_diag = abs(adx - ady) <= 0.75
                d = adx  # == ady on the diagonal
                if on_diag and d <= diag_reach:
                    h = max(h, (1.0 - d / (diag_reach + 0.5)) * diag_gain)

            heat[y][x] = h
    return heat


def render(heat, outline=True):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = bands(heat[y][x])
            if c:
                px[x, y] = c

    if outline:
        # A one-pixel violet edge where the glow meets empty space, for the
        # item-slot definition vanilla sprites carry — dark, but never black.
        empties = []
        for y in range(16):
            for x in range(16):
                if px[x, y][3] != 0:
                    continue
                touches = False
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ox, y + oy
                    if 0 <= nx < 16 and 0 <= ny < 16 and heat[ny][nx] >= 0.10:
                        touches = True
                        break
                if touches:
                    empties.append((x, y))
        for x, y in empties:
            px[x, y] = EDGE
    return im


def normal():
    # Compact: arms stop short of the border, calm at 500 casts an hour.
    heat = star(reach=6.6, core_r=2.7, width0=1.45, taper=0.20,
                diag_reach=2.4, diag_gain=0.32, plateau=0.95)
    return render(heat, outline=True)


def empowered():
    # Bigger presence: arms to the edge, a brighter 45 cross, a wider white
    # core and a faint outer bloom. Same family, unmistakably the charged one.
    heat = star(reach=7.8, core_r=3.1, width0=1.85, taper=0.16,
                diag_reach=5.2, diag_gain=0.58, plateau=1.55)
    # A faint violet bloom so the empowered mote sits in a halo.
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if r <= 7.3:
                heat[y][x] = max(heat[y][x], (1.0 - r / 7.3) * 0.17)
    return render(heat, outline=True)


def main():
    normal().save(os.path.join(HERE, "missile.png"))
    empowered().save(os.path.join(HERE, "empowered.png"))
    print("missile.png, empowered.png")

    # Preview: both at 4x on the brief's #2b2b2b, side by side.
    scale = 4
    pad = 8
    tile = 16 * scale
    prev = Image.new("RGBA", (pad * 3 + tile * 2, pad * 2 + tile), (43, 43, 43, 255))
    for i, im in enumerate((normal(), empowered())):
        big = im.resize((tile, tile), Image.NEAREST)
        prev.alpha_composite(big, (pad + i * (tile + pad), pad))
    prev.save(os.path.join(HERE, "preview.png"))
    print("preview.png")


if __name__ == "__main__":
    main()
