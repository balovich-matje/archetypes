#!/usr/bin/env python3
"""
Magic Armament (Seeker conjured sword) -- animated item texture generator.
Aesthetic key: "pulse" -- a calm arcane breath. The blade cycles between a deep
resting violet and a bright charged lilac, with a slow hilt->tip flow so it
reads as mana moving through glass rather than a flat blink.

Silhouette is taken pixel-for-pixel from vanilla iron_sword.png; only the
palette and alpha are replaced.

Outputs (in this directory):
  magic_sword.png         vertically stacked frame sheet (16 x 16N)
  magic_sword.png.mcmeta  {"animation": {"frametime": F, "interpolate": true}}
  preview_*.png           review renders
"""

import json
import math
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "iron_sword.png")

# ---------------------------------------------------------------- parameters
FRAMES = 6          # few frames -- interpolation does the work
FRAMETIME = 15      # ticks per frame -> 6 * 15 = 90 ticks = 4.5 s per breath
FLOW = 0.35         # fraction of a cycle the pulse lags from hilt to tip

# ---------------------------------------------------------------- palettes
# Roles keyed by the vanilla iron-sword hex they replace. Values are RGBA.
# LOW = resting/deep, HIGH = charged/bright.
#
# Two-rim strategy: vanilla gives the blade a dark edge (181818) on one side
# and a lighter edge (444444) on the other. The dark rim stays near-black
# violet so the shape survives on snow; the light rim is kept a luminous
# mid-violet so the shape survives on dark stone/night. One of the two always
# contrasts, whatever the backdrop, and both stay near-opaque at all times.
# The shell (rims) barely breathes -- it is the glass. The interior swings
# hard -- it is the mana. That split is what keeps the sprite legible while
# still obviously animating.
LOW = {
    "181818": (26, 12, 50, 255),     # dark rim      - always solid, tiny swing
    "444444": (72, 36, 128, 250),    # light rim     - always solid, tiny swing
    "6b6b6b": (74, 38, 132, 208),    # guard shadow
    "969696": (86, 46, 148, 198),    # guard face
    "bebebe": (92, 50, 158, 124),    # blade core    - most transparent
    "d8d8d8": (104, 58, 172, 134),   # blade sheen
    "ffffff": (140, 96, 212, 160),   # blade specular edge
    # hilt / grip / pommel (vanilla wood browns) - solid, the hand-side anchor
    "281e0b": (28, 12, 52, 255),
    "493615": (52, 24, 94, 252),
    "684e1e": (76, 40, 136, 246),
    "896727": (104, 60, 176, 240),
}
HIGH = {
    "181818": (40, 18, 74, 255),
    "444444": (100, 58, 168, 252),
    "6b6b6b": (152, 102, 222, 216),
    "969696": (176, 128, 238, 204),
    "bebebe": (206, 168, 250, 162),
    "d8d8d8": (222, 190, 253, 172),
    "ffffff": (246, 232, 255, 205),
    "281e0b": (42, 18, 78, 255),
    "493615": (78, 40, 138, 253),
    "684e1e": (118, 70, 192, 248),
    "896727": (166, 120, 236, 244),
}

# Roles that must never breathe much: the silhouette has to survive on any
# backdrop, so the outline swings in colour but barely in alpha.
OUTLINE = {"181818", "444444"}


def hexof(px):
    return "%02x%02x%02x" % px[:3]


def lerp(a, b, t):
    return a + (b - a) * t


def axis_coord(x, y):
    """0 at the pommel (bottom-left), 1 at the tip (top-right)."""
    return (x + (15 - y)) / 30.0


def charge(x, y, f):
    """Per-pixel breath value in [0,1] for frame f."""
    phase = f / float(FRAMES)
    u = axis_coord(x, y)
    return 0.5 + 0.5 * math.cos(2.0 * math.pi * (phase - u * FLOW))


def build_frame(src, f):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            p = src.getpixel((x, y))
            if p[3] == 0:
                continue
            key = hexof(p)
            lo, hi = LOW[key], HIGH[key]
            c = charge(x, y, f)
            rgba = tuple(int(round(lerp(lo[i], hi[i], c))) for i in range(4))
            out.putpixel((x, y), rgba)
    return out


def build_sheet():
    src = Image.open(SRC).convert("RGBA")
    frames = [build_frame(src, f) for f in range(FRAMES)]
    sheet = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, 16 * i))
    return sheet, frames


# ------------------------------------------------------------------ previews
def interp_frame(frames, t):
    """What the game actually shows: linear blend between frame i and i+1."""
    n = len(frames)
    pos = t * n
    i = int(pos) % n
    j = (i + 1) % n
    k = pos - int(pos)
    a, b = frames[i], frames[j]
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            pa, pb = a.getpixel((x, y)), b.getpixel((x, y))
            out.putpixel((x, y), tuple(int(round(lerp(pa[c], pb[c], k))) for c in range(4)))
    return out


def noise(seed):
    s = seed
    while True:
        s = (1103515245 * s + 12345) & 0x7FFFFFFF
        yield s / float(0x7FFFFFFF)


def backdrop(kind, w, h):
    """Rough stand-ins for the surfaces this sprite has to survive on."""
    bases = {
        "grey": [(139, 139, 139), (127, 127, 127), (150, 150, 150)],
        "snow": [(249, 254, 254), (240, 247, 247), (255, 255, 255)],
        "stone": [(122, 122, 122), (104, 104, 104), (136, 136, 136)],
        "dark": [(38, 38, 44), (30, 30, 36), (46, 46, 54)],
    }[kind]
    rng = noise({"grey": 7, "snow": 11, "stone": 13, "dark": 17}[kind])
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            im.putpixel((x, y), bases[int(next(rng) * len(bases))] + (255,))
    return im


def up(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def preview_filmstrip(frames, path):
    """Native 16px frames in a row, then the same row at 4x."""
    n = len(frames)
    pad = 2
    w = n * (16 + pad) + pad
    top = Image.new("RGBA", (w, 16 + 2 * pad), (60, 60, 66, 255))
    for i, fr in enumerate(frames):
        top.alpha_composite(fr, (pad + i * (16 + pad), pad))
    big = up(top, 4)
    canvas = Image.new("RGBA", (big.width, big.height + top.height + 4), (60, 60, 66, 255))
    canvas.paste(top, (0, 0))
    canvas.alpha_composite(big, (0, top.height + 4))
    canvas.save(path)


def preview_single(frames, path, k=8):
    """One charged frame and one resting frame, 8x, on split backgrounds."""
    picks = [(0, "peak"), (FRAMES // 2, "trough")]
    cell = 16 * k
    canvas = Image.new("RGBA", (cell * 2 + 12, cell * 2 + 12), (24, 24, 28, 255))
    for col, (fi, _) in enumerate(picks):
        for row, bg in enumerate(["stone", "snow"]):
            b = up(backdrop(bg, 16, 16), k)
            b.alpha_composite(up(frames[fi], k))
            canvas.paste(b, (4 + col * (cell + 4), 4 + row * (cell + 4)))
    canvas.save(path)


def preview_contact(frames, path, k=6):
    """Every stored frame plus the interpolated in-betweens the game renders."""
    n = len(frames)
    steps = [interp_frame(frames, i / float(n * 3)) for i in range(n * 3)]
    rows = [("stored frames", frames), ("interpolated", steps)]
    cell = 16 * k
    cols = max(len(r[1]) for r in rows)
    canvas = Image.new("RGBA", (cols * (cell + 3) + 3, len(rows) * (cell + 3) + 3), (52, 52, 58, 255))
    for ri, (_, seq) in enumerate(rows):
        for ci, fr in enumerate(seq):
            b = up(backdrop("grey", 16, 16), k)
            b.alpha_composite(up(fr, k))
            canvas.paste(b, (3 + ci * (cell + 3), 3 + ri * (cell + 3)))
    canvas.save(path)


def preview_hotbar(frames, path, k=4):
    """Sprite in 20x20 slots over grey/snow/stone/dark, at real hotbar scale
    and at 4x, so the 16px read can be judged honestly."""
    n = len(frames)
    picks = [frames[i] for i in range(n)]
    slot = 20
    bgs = ["grey", "snow", "stone", "dark"]
    w = len(picks) * slot
    strip = Image.new("RGBA", (w, len(bgs) * slot), (0, 0, 0, 255))
    for ri, bg in enumerate(bgs):
        row = backdrop(bg, w, slot)
        for ci, fr in enumerate(picks):
            row.alpha_composite(fr, (ci * slot + 2, 2))
        strip.paste(row, (0, ri * slot))
    native = strip
    big = up(strip, k)
    canvas = Image.new("RGBA", (big.width, native.height + big.height + 8), (18, 18, 22, 255))
    canvas.paste(native, (0, 2))
    canvas.alpha_composite(big, (0, native.height + 6))
    canvas.save(path)


def audit(frames):
    """Check the sprite against the brief's hard constraints, including the
    interpolated in-betweens (the game shows those too, so they must hold)."""
    src = Image.open(SRC).convert("RGBA")
    interior = {"bebebe", "d8d8d8"}
    seq = frames + [interp_frame(frames, i / 60.0) for i in range(60)]
    ia, oa, worst = [], [], 255.0
    for fr in seq:
        for y in range(16):
            for x in range(16):
                p = src.getpixel((x, y))
                if p[3] == 0:
                    continue
                a = fr.getpixel((x, y))[3]
                if hexof(p) in interior:
                    ia.append(a)
                elif hexof(p) in OUTLINE:
                    oa.append(a)
        # contrast of the solid rim against white and against near-black
        for y in range(16):
            for x in range(16):
                if src.getpixel((x, y))[3] == 0 or hexof(src.getpixel((x, y))) not in OUTLINE:
                    continue
                r, g, b, a = fr.getpixel((x, y))
                lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                af = a / 255.0
                on_snow = abs((lum * af + 1.0 * (1 - af)) - 1.0)
                on_dark = abs((lum * af + 0.13 * (1 - af)) - 0.13)
                worst = min(worst, max(on_snow, on_dark))
    print("  blade interior alpha  %d-%d  (brief: 120-190)" % (min(ia), max(ia)))
    print("  rim/outline alpha     %d-%d  (must stay near-solid)" % (min(oa), max(oa)))
    print("  worst rim contrast    %.2f  (vs the better of snow/dark)" % worst)


def main():
    sheet, frames = build_sheet()
    sheet.save(os.path.join(HERE, "magic_sword.png"))
    with open(os.path.join(HERE, "magic_sword.png.mcmeta"), "w") as fh:
        json.dump({"animation": {"frametime": FRAMETIME, "interpolate": True}}, fh, indent=2)
        fh.write("\n")

    audit(frames)
    preview_filmstrip(frames, os.path.join(HERE, "preview_filmstrip.png"))
    preview_single(frames, os.path.join(HERE, "preview_8x.png"))
    preview_contact(frames, os.path.join(HERE, "preview_contact_sheet.png"))
    preview_hotbar(frames, os.path.join(HERE, "preview_hotbar.png"))
    print("sheet", sheet.size, "frames", FRAMES, "frametime", FRAMETIME)


if __name__ == "__main__":
    main()
