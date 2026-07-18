#!/usr/bin/env python3
"""Archetypes Modrinth icon — THE TRIAD.
One symbol per archetype, fanned like the Skill Proficiencies family icon:
  - Brawler : steel sword   (up-left)
  - Cutpurse: wood bow       (up-right)
  - Seeker  : arcane bolt     (crowning top-center)
Drawn on a COARSE 32px art grid (16px blocks @512, matching the sibling icon's
chunky vanilla look) and upscaled NEAREST to 512. Each subject gets a dilated
dark outline. Background sampled from the sibling: fill 34,36,46 / border
64,68,84 / inset 8 / corner radius 80 @512.
"""
import math
from PIL import Image, ImageDraw, ImageChops, ImageFilter

GRID = 32
SCALE = 16          # 32 * 16 = 512
OUT = 512

OUTLINE = (23, 19, 29, 255)
# steel
ST_D = (70, 74, 84, 255)
ST_M = (132, 138, 148, 255)
ST_L = (196, 202, 212, 255)
ST_W = (242, 246, 251, 255)
# gold
GO_D = (156, 112, 24, 255)
GO_M = (220, 168, 44, 255)
GO_L = (252, 216, 96, 255)
# wood (brown — must not read as a gold blade)
WD_D = (54, 38, 17, 255)
WD_M = (112, 78, 34, 255)
WD_L = (152, 112, 50, 255)
STRING = (214, 217, 224, 255)
# arcane (magic_bolt purples)
AR_D = (56, 38, 94, 255)
AR_M = (120, 86, 182, 255)
AR_H = (188, 152, 248, 255)
AR_T = (230, 214, 255, 255)
AR_CORE = (255, 254, 240, 255)


def layer():
    return Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))


def outline(lyr, t=1, color=OUTLINE):
    a = lyr.split()[3]
    dil = a
    for _ in range(t):
        dil = dil.filter(ImageFilter.MaxFilter(3))
    ring = ImageChops.subtract(dil, a)
    out = Image.new("RGBA", lyr.size, (0, 0, 0, 0))
    out.paste(color, mask=ring)
    out.alpha_composite(lyr)
    return out


def unit(a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    n = math.hypot(dx, dy) or 1
    return dx / n, dy / n


def bezier(a, c, b, n=80):
    out = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        out.append((mt * mt * a[0] + 2 * mt * t * c[0] + t * t * b[0],
                    mt * mt * a[1] + 2 * mt * t * c[1] + t * t * b[1]))
    return out


def disc(d, p, r, color):
    d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color)


# ---------------------------------------------------------------------------
def sword_layer():
    lyr = layer()
    d = ImageDraw.Draw(lyr)
    T = (4.5, 8.0)       # tip (up-left)
    G = (15.5, 18.0)     # blade base at guard
    ux, uy = unit(T, G)
    px, py = -uy, ux
    w = 1.75
    sh = 0.68
    S = (T[0] + (G[0] - T[0]) * sh, T[1] + (G[1] - T[1]) * sh)
    def off(p, s):
        return (p[0] + px * s, p[1] + py * s)
    # blade body
    d.polygon([off(G, w), off(S, w * 0.9), T, off(S, -w * 0.9), off(G, -w)], fill=ST_M)
    # dark facet (lower-right half)
    d.polygon([off(G, w), off(S, w * 0.9), T, off(S, w * 0.1), off(G, w * 0.1)], fill=ST_D)
    # bright edge (upper-left) + white glint at tip
    d.line([off(G, -w * 0.55), off(S, -w * 0.55)], fill=ST_L, width=1)
    d.line([off(S, -w * 0.4), T], fill=ST_W, width=1)
    # --- hilt: compact. grip (brown) first, small gold guard, tiny pommel ---
    C = (G[0] + ux * 1.3, G[1] + uy * 1.3)          # guard centre, just past blade
    P = (G[0] + ux * 5.8, G[1] + uy * 5.8)          # pommel position
    d.line([C, P], fill=WD_M, width=3)
    d.line([(C[0] - px * 0.6, C[1] - py * 0.6), (P[0] - px * 0.6, P[1] - py * 0.6)],
           fill=WD_L, width=1)
    # crossguard — small solid gold bar, only a touch wider than the blade
    gl, ht = 2.5, 0.95
    quad = [(C[0] + px * gl + ux * ht, C[1] + py * gl + uy * ht),
            (C[0] + px * gl - ux * ht, C[1] + py * gl - uy * ht),
            (C[0] - px * gl - ux * ht, C[1] - py * gl - uy * ht),
            (C[0] - px * gl + ux * ht, C[1] - py * gl + uy * ht)]
    d.polygon(quad, fill=GO_M)
    d.line([quad[0], quad[3]], fill=GO_L, width=1)
    # pommel — tiny gold diamond below the grip
    pr = 1.5
    d.polygon([(P[0], P[1] - pr), (P[0] + pr, P[1]), (P[0], P[1] + pr), (P[0] - pr, P[1])],
              fill=GO_M)
    d.point([(P[0], P[1] - 0.4)], fill=GO_L)
    return outline(lyr, t=1)


def bow_layer():
    lyr = layer()
    d = ImageDraw.Draw(lyr)
    A = (18.0, 24.5)     # lower limb tip (tucks by the grip)
    B = (25.5, 6.0)      # upper limb tip
    dx, dy = unit(A, B)
    perp = (dy, -dx)                 # outer (right) side
    M = ((A[0] + B[0]) / 2, (A[1] + B[1]) / 2)
    bulge = 9.0
    Cp = (M[0] + perp[0] * bulge, M[1] + perp[1] * bulge)
    for p in bezier(A, Cp, B):
        disc(d, p, 1.95, WD_M)
    # inner highlight edge (faces the string / centre)
    Cp_l = (M[0] + perp[0] * (bulge - 1.4), M[1] + perp[1] * (bulge - 1.4))
    for p in bezier((A[0] - perp[0] * 1.2, A[1] - perp[1] * 1.2), Cp_l,
                    (B[0] - perp[0] * 1.2, B[1] - perp[1] * 1.2)):
        disc(d, p, 0.75, WD_L)
    # outer shadow edge
    Cp_d = (M[0] + perp[0] * (bulge + 1.3), M[1] + perp[1] * (bulge + 1.3))
    for p in bezier((A[0] + perp[0] * 1.2, A[1] + perp[1] * 1.2), Cp_d,
                    (B[0] + perp[0] * 1.2, B[1] + perp[1] * 1.2)):
        disc(d, p, 0.65, WD_D)
    for tp in (A, B):
        disc(d, tp, 2.0, WD_D)
    lyr = outline(lyr, t=1)
    # bold straight string across the opening -> unmistakable D-shape
    s = layer()
    ds = ImageDraw.Draw(s)
    ds.line([A, B], fill=STRING, width=1)
    s = outline(s, t=1)
    lyr.alpha_composite(s)
    return lyr


def bolt_layer():
    lyr = layer()
    d = ImageDraw.Draw(lyr)
    cx, cy = 15.5, 7.0
    L, S = 5.6, 1.7
    def star(scale, color):
        lo, so = L * scale, S * scale
        d.polygon([(cx, cy - lo), (cx + so, cy - so), (cx + lo, cy), (cx + so, cy + so),
                   (cx, cy + lo), (cx - so, cy + so), (cx - lo, cy), (cx - so, cy - so)],
                  fill=color)
    star(1.0, AR_D)
    star(0.72, AR_M)
    star(0.42, AR_H)
    disc(d, (cx, cy), 1.1, AR_T)
    disc(d, (cx, cy), 0.5, AR_CORE)
    lyr = outline(lyr, t=1)
    # two sparkle satellites
    sp = layer()
    dsp = ImageDraw.Draw(sp)
    for sx, sy, r in [(cx + 6.5, cy - 4.0, 1.0), (cx - 6.0, cy - 3.0, 0.8)]:
        dsp.polygon([(sx, sy - r), (sx + r, sy), (sx, sy + r), (sx - r, sy)], fill=AR_T)
    sp = outline(sp, t=1)
    lyr.alpha_composite(sp)
    return lyr, (cx, cy)


def glow(size, center, radius, rgb, alpha):
    g = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse(
        [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius],
        fill=rgb + (alpha,))
    return g.filter(ImageFilter.GaussianBlur(radius * 0.45))


def background():
    bg = Image.new("RGBA", (OUT, OUT), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    inset, rad, bw = 8, 80, 6
    d.rounded_rectangle([inset, inset, OUT - inset, OUT - inset], radius=rad, fill=(64, 68, 84, 255))
    d.rounded_rectangle([inset + bw, inset + bw, OUT - inset - bw, OUT - inset - bw],
                        radius=rad - bw, fill=(34, 36, 46, 255))
    return bg


def main():
    art = layer()
    art.alpha_composite(bow_layer())
    art.alpha_composite(sword_layer())
    bolt, bc = bolt_layer()
    art.alpha_composite(bolt)
    art_big = art.resize((OUT, OUT), Image.NEAREST)

    icon = background()
    gc = (bc[0] * SCALE, bc[1] * SCALE)
    icon.alpha_composite(glow(OUT, gc, 80, (116, 88, 200), 85))
    icon.alpha_composite(glow(OUT, gc, 46, (150, 120, 235), 78))
    icon.alpha_composite(art_big)
    icon.save("icon.png")
    icon.resize((96, 96), Image.LANCZOS).save("preview.png")
    icon.resize((96, 96), Image.NEAREST).save("preview_nn.png")
    print("wrote icon.png + previews")


if __name__ == "__main__":
    main()
