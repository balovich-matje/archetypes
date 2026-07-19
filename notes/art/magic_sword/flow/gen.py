#!/usr/bin/env python3
"""
Magic Armament (Seeker) -- animated item sprite generator.  Variant key: "flow".

Concept: translucent violet conjured blade with a brighter band of magic that
travels from the guard up to the tip on a seamless loop, so the weapon reads as
actively channelled rather than merely tinted.

Silhouette is taken 1:1 from vanilla iron_sword.png (no pixel added or removed);
only colour + alpha are rewritten per frame.

Outputs (all next to this file):
    magic_sword.png         vertical frame sheet, 16 x (16*FRAMES)
    magic_sword.png.mcmeta  animation metadata
    preview_*.png           review renders
"""

import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "iron_sword.png")

FRAMES = 16
FRAMETIME = 2          # ticks per frame -> 16*2 = 32 ticks = 1.6 s per loop
SIZE = 16

# ---------------------------------------------------------------------------
# geometry: (u, v) blade-aligned coordinates
#   u = distance along the sword axis, 0 at pommel corner -> 15 at tip
#   v = distance perpendicular to the axis
# ---------------------------------------------------------------------------


def uv(x, y):
    return (x + (15 - y)) / 2.0, (x - (15 - y)) / 2.0


# ---------------------------------------------------------------------------
# palette ramp.  Vanilla luminance level -> (colour, base alpha) for the blade.
# Dark levels are the silhouette edge: high alpha so the shape survives on any
# backdrop.  Mid levels are the translucent body.
# ---------------------------------------------------------------------------

BLADE_RAMP = {
    24:  ((40, 12, 82), 250),      # shadow-side outline, deepest
    68:  ((88, 34, 168), 238),     # light-side outline
    107: ((104, 44, 186), 152),
    150: ((118, 52, 206), 142),
    190: ((136, 66, 226), 148),
    216: ((160, 96, 242), 162),
    255: ((196, 150, 250), 176),   # vanilla specular -> lilac core
}

# Levels that form the silhouette edge.  These resist the flow entirely: the
# rim must stay dark and near-opaque in every frame or the sprite dissolves
# against snow / sand / any bright block.
EDGE_LEVELS = (24, 68)
EDGE_HEAT = 0.18               # how much of the flow an edge pixel accepts

# Hard ceiling on the see-through part of the blade, so even the crest of the
# pulse stays visibly translucent rather than turning the blade solid.
BLADE_ALPHA_CAP = 198

# hardware (guard / pommel): same hue family, deeper + noticeably more solid,
# so the cross reads as a physical anchor for the energy.
HARD_RAMP = {
    24:  ((46, 18, 92), 252),
    68:  ((104, 54, 178), 244),
    107: ((122, 68, 198), 232),
    150: ((138, 82, 214), 226),
    190: ((154, 98, 228), 228),
    216: ((172, 122, 240), 234),
    255: ((202, 166, 250), 240),
}

# grip: vanilla's leather browns become a solid dark indigo so there is a clear
# "held" section the energy has to climb out of -- but light enough that the
# hilt does not dissolve into a dark background.
GRIP_RAMP = {
    40:  ((44, 26, 84), 255),
    73:  ((60, 38, 108), 253),
    104: ((78, 52, 136), 251),
    137: ((96, 68, 162), 249),
}

FLOW_COLOR = (244, 236, 255)   # crest of the travelling band
FLOW_WARM = (198, 168, 255)    # shoulder of the band


def lum(px):
    r, g, b, _ = px
    return int(round(0.299 * r + 0.587 * g + 0.114 * b))


def nearest(ramp, v):
    return ramp[min(ramp, key=lambda k: abs(k - v))]


def classify(x, y, px):
    """-> 'grip' | 'hard' (guard+pommel) | 'blade'"""
    r, g, b, _ = px
    if r > b + 12 and r >= g >= b:          # vanilla leather browns
        return "grip"
    u, v = uv(x, y)
    if u < 3.0:                             # pommel lump
        return "hard"
    # the blade's core strip continues down through the guard so the flow has
    # somewhere to well up from; the crossguard arms sit outside it
    if u >= 4.2 and abs(v) <= 1.25:
        return "blade"
    return "hard"


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(lerp(c1[i], c2[i], t) for i in range(3))


def wrapped_delta(a, b, period):
    d = (a - b) % period
    return d - period if d > period / 2 else d


def build_frame(src, kinds, f):
    """One 16x16 RGBA frame at loop phase f in [0,1)."""
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px_out = im.load()

    # Pulse spacing in u.  Deliberately shorter than the sprite's 0..15 span so
    # a fresh pulse is entering at the guard as the previous one clears the
    # tip -- the blade is never idle, and the wrap makes the loop seamless.
    period = 11.0
    crest = (f * period) % period      # band centre, marches toward the tip
    sigma = 1.30                       # band half-width in u -- keep it tight
    # slow breathing of the whole blade, independent of the band
    breathe = 0.5 + 0.5 * math.sin(2 * math.pi * f)

    for y in range(SIZE):
        for x in range(SIZE):
            src_px = src.getpixel((x, y))
            if src_px[3] == 0:
                continue
            kind = kinds[(x, y)]
            L = lum(src_px)
            u, v = uv(x, y)

            if kind == "grip":
                col, alpha = nearest(GRIP_RAMP, L)
                gain = 0.30            # energy barely leaks into the handle
            elif kind == "hard":
                col, alpha = nearest(HARD_RAMP, L)
                gain = 0.55
            else:
                col, alpha = nearest(BLADE_RAMP, L)
                gain = 1.0
            if L in EDGE_LEVELS:
                gain *= EDGE_HEAT      # rim holds the silhouette, never blooms

            col = list(col)
            alpha = float(alpha)

            # --- travelling band -------------------------------------------
            d = wrapped_delta(u, crest, period)
            band = math.exp(-(d * d) / (2 * sigma * sigma))
            # the band wells up out of the guard and blooms toward the tip
            band *= 0.55 + 0.45 * min(1.0, max(0.0, (u - 4.0) / 8.0))
            # a touch tighter across the blade's width so it reads as a core
            band *= math.exp(-(v * v) / 6.0)
            band *= gain

            # --- ambient shimmer, keeps the body from ever looking static ---
            shimmer = 0.5 + 0.5 * math.sin(2 * math.pi * (f * 2.0 - u * 0.16))
            amb = 0.07 * shimmer * gain + 0.04 * breathe * gain

            heat = min(1.0, band * 1.12 + amb)
            # stay in the violet family: the crest only ever gets part-way to
            # white, so the item never reads as "a white sword"
            warm = mix(col, FLOW_WARM, min(1.0, heat * 1.25))
            col = mix(warm, FLOW_COLOR, 0.72 * max(0.0, heat - 0.45) / 0.55)

            # Translucent body solidifies a little where the magic is dense,
            # but the blade never goes opaque -- see-through is the brief.
            alpha = alpha + (255 - alpha) * min(1.0, heat * 0.48)
            if kind == "blade" and L not in EDGE_LEVELS:
                alpha = min(alpha, BLADE_ALPHA_CAP)

            px_out[x, y] = (
                int(round(min(255, col[0]))),
                int(round(min(255, col[1]))),
                int(round(min(255, col[2]))),
                int(round(min(255, alpha))),
            )
    return im


# ---------------------------------------------------------------------------
# preview helpers
# ---------------------------------------------------------------------------

BACKDROPS = {
    "snow":  (243, 246, 250),
    "gui":   (139, 139, 139),
    "stone": (122, 122, 122),
    "dark":  (22, 20, 28),
}


def on_bg(frame, bg, scale):
    base = Image.new("RGBA", frame.size, bg + (255,))
    base.alpha_composite(frame)
    return base.resize((frame.width * scale, frame.height * scale), Image.NEAREST)


def stone_tile(size):
    """Cheap deterministic stone-ish noise so 'stone' isn't a flat swatch."""
    im = Image.new("RGBA", (size, size))
    p = im.load()
    for y in range(size):
        for x in range(size):
            n = (x * 37 + y * 91 + (x * y) % 13) % 23
            g = 108 + n
            p[x, y] = (g, g, g + 2, 255)
    return im


def main():
    src = Image.open(SRC).convert("RGBA")
    kinds = {}
    for y in range(SIZE):
        for x in range(SIZE):
            p = src.getpixel((x, y))
            if p[3]:
                kinds[(x, y)] = classify(x, y, p)

    frames = [build_frame(src, kinds, i / FRAMES) for i in range(FRAMES)]

    # --- the deliverable ----------------------------------------------------
    sheet = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * SIZE))
    sheet.save(os.path.join(HERE, "magic_sword.png"))

    with open(os.path.join(HERE, "magic_sword.png.mcmeta"), "w") as fh:
        json.dump({"animation": {"frametime": FRAMETIME, "interpolate": True}},
                  fh, indent=2)
        fh.write("\n")

    # --- preview: 1x filmstrip on gui grey (true in-game pixel scale) -------
    strip = Image.new("RGBA", (FRAMES * (SIZE + 2) + 2, SIZE + 4),
                      BACKDROPS["gui"] + (255,))
    for i, fr in enumerate(frames):
        strip.alpha_composite(fr, (2 + i * (SIZE + 2), 2))
    strip.save(os.path.join(HERE, "preview_filmstrip_1x.png"))

    # --- preview: contact sheet, every frame at 6x over four backdrops ------
    sc = 6
    pad = 4
    names = ["gui", "snow", "stone", "dark"]
    cw, ch = SIZE * sc, SIZE * sc
    sheet_w = FRAMES * (cw + pad) + pad
    sheet_h = len(names) * (ch + pad) + pad
    contact = Image.new("RGBA", (sheet_w, sheet_h), (46, 46, 52, 255))
    stone = stone_tile(SIZE).resize((cw, ch), Image.NEAREST)
    for r, nm in enumerate(names):
        for i, fr in enumerate(frames):
            if nm == "stone":
                cell = stone.copy()
                cell.alpha_composite(fr.resize((cw, ch), Image.NEAREST))
            else:
                cell = on_bg(fr, BACKDROPS[nm], sc)
            contact.paste(cell, (pad + i * (cw + pad), pad + r * (ch + pad)))
    contact.save(os.path.join(HERE, "preview_contact_sheet.png"))

    # --- preview: single frame at 8x, four backdrops ------------------------
    sc = 8
    pick = [0, 4, 8, 12]
    big = Image.new("RGBA", (len(pick) * (SIZE * sc + 6) + 6,
                             len(names) * (SIZE * sc + 6) + 6), (46, 46, 52, 255))
    stone_b = stone_tile(SIZE).resize((SIZE * sc, SIZE * sc), Image.NEAREST)
    for r, nm in enumerate(names):
        for c, idx in enumerate(pick):
            fr = frames[idx]
            if nm == "stone":
                cell = stone_b.copy()
                cell.alpha_composite(fr.resize((SIZE * sc, SIZE * sc), Image.NEAREST))
            else:
                cell = on_bg(fr, BACKDROPS[nm], sc)
            big.paste(cell, (6 + c * (SIZE * sc + 6), 6 + r * (SIZE * sc + 6)))
    big.save(os.path.join(HERE, "preview_frame_8x.png"))

    # --- preview: mock hotbar rows -----------------------------------------
    sc = 3
    slot = 20 * sc                      # vanilla hotbar slot pitch is 20 px
    rows = ["gui", "snow", "stone", "dark"]
    hb = Image.new("RGBA", (FRAMES // 2 * slot + 8, len(rows) * (slot + 6) + 6),
                   (28, 28, 32, 255))
    stone_s = stone_tile(20).resize((slot, slot), Image.NEAREST)
    for r, nm in enumerate(rows):
        for i in range(FRAMES // 2):
            fr = frames[i * 2]
            if nm == "stone":
                cell = stone_s.copy()
            else:
                cell = Image.new("RGBA", (slot, slot), BACKDROPS[nm] + (255,))
            # slot border, vanilla-ish
            d = cell.load()
            for k in range(slot):
                for e in (0, 1, slot - 2, slot - 1):
                    px = d[k, e]
                    d[k, e] = (max(0, px[0] - 30), max(0, px[1] - 30),
                               max(0, px[2] - 30), 255)
                    px = d[e, k]
                    d[e, k] = (max(0, px[0] - 30), max(0, px[1] - 30),
                               max(0, px[2] - 30), 255)
            cell.alpha_composite(fr.resize((SIZE * sc, SIZE * sc), Image.NEAREST),
                                 (2 * sc, 2 * sc))
            hb.paste(cell, (4 + i * slot, 6 + r * (slot + 6)))
    hb.save(os.path.join(HERE, "preview_hotbar.png"))

    print(f"frames={FRAMES} frametime={FRAMETIME} sheet=16x{16*FRAMES}")
    counts = {}
    for v in kinds.values():
        counts[v] = counts.get(v, 0) + 1
    print("regions:", counts)


if __name__ == "__main__":
    main()
