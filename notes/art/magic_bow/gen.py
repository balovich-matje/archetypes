#!/usr/bin/env python3
"""
Magic Armament (Seeker) -- animated BOW sprite generator.

Sibling of notes/art/magic_sword/flow/gen.py.  Same conjured-violet language:
translucent body, near-solid deep-indigo rim so the silhouette survives any
backdrop, and a brighter band of magic travelling along the weapon on a
seamless loop.

A bow is four sprites (idle + three pulling stages).  All four share ONE
palette, ONE pulse period and ONE phase, so the draw reads as a single
continuous weapon rather than four unrelated pictures.  What changes per stage
is the CHARGE: band amplitude, ambient glow and the heat of the nocked bolt all
climb with the draw, so stage 2 is visibly the hottest.

Geometry note: the bow, its string and the nocked arrow all live on the same
anti-diagonal as the sword, so the sword's (u, v) frame transfers verbatim:

    u = (x + (15 - y)) / 2      distance along the weapon, 0 = lower-left
    v = (x - (15 - y)) / 2      distance across it

In that frame the vanilla art separates cleanly:
    limb wood   u in [1, 5.5] and [9, 14]
    grip wrap   u in [6, 7]
    arrow       u in [7.5, 8]   (a line of CONSTANT u -- it crosses the bow)
    string      u in [2.5, 12.5] running parallel to the limb

so a single wavefront marching in u sweeps limb + string together and flashes
across the nocked bolt once per loop.

Outputs (all next to this file):
    magic_bow{,_pulling_0,_pulling_1,_pulling_2}.png[.mcmeta]
    preview_*.png   review renders
"""

import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

FRAMES = 16
FRAMETIME = 2          # 16 * 2 = 32 ticks = 1.6 s per loop
SIZE = 16

# name, vanilla source, charge 0..1
STAGES = [
    ("magic_bow",           "bow.png",           0.00),
    ("magic_bow_pulling_0", "bow_pulling_0.png", 0.36),
    ("magic_bow_pulling_1", "bow_pulling_1.png", 0.68),
    ("magic_bow_pulling_2", "bow_pulling_2.png", 1.00),
]

# ---------------------------------------------------------------------------
# palette.  Vanilla luminance level -> (colour, base alpha).
# Values are lifted from the shipped magic_sword ramp wherever a level lines up
# so the two conjured weapons are demonstrably the same material.
# ---------------------------------------------------------------------------

# limb wood: vanilla browns 31 / 56 / 80 / 106
WOOD_RAMP = {
    31:  ((34, 10, 74), 252),      # shadow-side outline, deepest  (sword L24)
    56:  ((80, 28, 158), 236),     # light-side outline            (sword L68)
    # A bow limb is a 1 px core between two rims, so the body needs a little
    # more alpha than the sword's broad blade did or it reads as haze.
    80:  ((92, 36, 170), 164),     # translucent body
    106: ((108, 46, 192), 158),    # translucent body, lit side
}

# The two outline levels hold the silhouette.  A bow limb is only ~3 px across
# (outer rim / core / inner rim), so the rim is most of the sprite -- it has to
# resist the flow or the whole bow blooms into a white noodle.
EDGE_HEAT = {31: 0.14, 56: 0.26}

# Ceiling on the see-through core, so even the crest of the pulse stays
# translucent rather than turning the limb solid.
BODY_ALPHA_CAP = 198

# grip wrap: vanilla greys 107 / 150.  Same hue family, deeper + noticeably
# more solid, so the riser reads as the physical anchor for the energy.
GRIP_RAMP = {
    107: ((110, 58, 186), 236),
    150: ((130, 74, 208), 230),
}

# bowstring: vanilla grey 68.  One pixel wide, so it needs a fair bit of alpha
# to exist at all, but it is the most "conjured" part -- a taut thread.
STRING_RAMP = {
    68:  ((96, 56, 172), 202),
}

# nocked bolt.  Shaft reuses the vanilla browns; the head is vanilla's
# 177 / 216 / 255 whites.  Colours borrowed from magic_bolt.png.
SHAFT_RAMP = {
    31:  ((50, 26, 92), 250),
    106: ((150, 108, 220), 176),
    80:  ((124, 84, 196), 172),
    56:  ((96, 62, 160), 232),
}
# The head is only three pixels and vanilla gives it no dark neighbour, so the
# darkest of the three has to act as its own rim -- otherwise the nocked bolt
# blooms to white and vanishes against snow.
HEAD_RAMP = {
    177: ((104, 58, 186), 250),
    216: ((172, 132, 238), 248),
    255: ((226, 214, 255), 252),
}

FLOW_COLOR = (244, 236, 255)   # crest of the travelling band
FLOW_WARM = (198, 168, 255)    # shoulder of the band

# Pulse spacing in u.  The bow spans u 1..14 (span ~13).  A period of exactly
# the span puts both tips permanently in phase, so the bow was lit at one end
# or the other in every frame; a period of 10 put two crests on the limb at
# once.  15.5 leaves a genuine gap: one crest sweeps tip to tip, the bow
# settles back to deep violet, and the next crest is already ~0.5 up at the
# lower tip as the old one leaves the upper one, so the wrap still cross-fades.
PERIOD = 15.5
SIGMA = 1.35                   # band half-width in u

ARROW_U_LO, ARROW_U_HI = 7.25, 8.25


def uv(x, y):
    return (x + (15 - y)) / 2.0, (x - (15 - y)) / 2.0


def lum(px):
    r, g, b, _ = px
    return int(round(0.299 * r + 0.587 * g + 0.114 * b))


def nearest(ramp, v):
    return ramp[min(ramp, key=lambda k: abs(k - v))]


def classify(x, y, px, pulling):
    """-> 'wood' | 'grip' | 'string' | 'shaft' | 'head'"""
    r, g, b, _ = px
    u, _v = uv(x, y)
    if r == g == b:                                  # vanilla greys
        L = r
        if L >= 165:
            return "head"                            # arrowhead 177/216/255
        if L <= 80:
            return "string"                          # 68
        return "grip"                                # 107 / 150
    # browns
    if pulling and ARROW_U_LO <= u <= ARROW_U_HI:
        return "shaft"
    return "wood"


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(lerp(c1[i], c2[i], t) for i in range(3))


def wrapped_delta(a, b, period):
    d = (a - b) % period
    return d - period if d > period / 2 else d


def build_frame(src, kinds, f, charge):
    """One 16x16 RGBA frame at loop phase f in [0,1), draw charge in [0,1]."""
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out = im.load()

    crest = (f * PERIOD) % PERIOD          # band centre, marches toward the tip
    sigma = SIGMA + 0.35 * charge          # a fuller drawn bow carries a fatter band
    band_gain = 1.0 + 0.55 * charge
    amb_gain = 1.0 + 0.25 * charge
    breathe = 0.5 + 0.5 * math.sin(2 * math.pi * f)

    # standing glow gathering at the riser as the bow is drawn
    nock_gain = 0.13 * charge

    for y in range(SIZE):
        for x in range(SIZE):
            sp = src.getpixel((x, y))
            if sp[3] == 0:
                continue
            kind = kinds[(x, y)]
            L = lum(sp)
            u, v = uv(x, y)

            if kind == "wood":
                col, alpha = nearest(WOOD_RAMP, L)
                gain = 1.0 * EDGE_HEAT.get(L, 1.0)
                translucent = L not in EDGE_HEAT
            elif kind == "grip":
                col, alpha = nearest(GRIP_RAMP, L)
                gain, translucent = 0.55, False
            elif kind == "string":
                col, alpha = nearest(STRING_RAMP, L)
                gain, translucent = 0.45, False
            elif kind == "shaft":
                col, alpha = nearest(SHAFT_RAMP, L)
                gain, translucent = (0.22 if L == 31 else 0.72), (L != 31)
            else:                                    # head
                col, alpha = nearest(HEAD_RAMP, L)
                gain, translucent = 0.62, False

            col = list(col)
            alpha = float(alpha)

            # --- travelling band ------------------------------------------
            d = wrapped_delta(u, crest, PERIOD)
            band = math.exp(-(d * d) / (2 * sigma * sigma))
            band *= band_gain * gain

            # --- ambient shimmer, keeps the body from ever looking static --
            shimmer = 0.5 + 0.5 * math.sin(2 * math.pi * (f * 2.0 - u * 0.16))
            amb = (0.05 * shimmer + 0.03 * breathe) * amb_gain * gain

            raw = band * 1.60 + amb

            # --- charge terms ---------------------------------------------
            if kind in ("shaft", "head"):
                # the nocked bolt is the thing being charged: it holds a steady
                # heat that climbs hard with the draw, plus a quick shiver that
                # runs down its length (which is the v axis, not u).
                shiver = 0.5 + 0.5 * math.sin(2 * math.pi * (f * 3.0 + v * 0.28))
                raw += (0.10 + 0.80 * charge) * gain
                raw += 0.12 * shiver * gain * (0.4 + charge)
            else:
                # power gathering at the riser / nocking point
                raw += nock_gain * gain * math.exp(-((u - 7.5) ** 2) / 26.0)

            # Soft saturation.  A hard min(1.0, ...) flattened the crest into a
            # uniform white bar wherever the band ran along the limb; this keeps
            # the falloff readable no matter how hot the stage gets.
            heat = 1.0 - math.exp(-raw)

            # stay in the violet family: the crest only ever gets part-way to
            # white, so the bow never reads as "a white bow".
            warm = mix(col, FLOW_WARM, min(1.0, heat * 1.25))
            col = mix(warm, FLOW_COLOR, 0.66 * max(0.0, heat - 0.55) / 0.45)

            alpha = alpha + (255 - alpha) * min(1.0, heat * 0.48)
            if translucent:
                alpha = min(alpha, BODY_ALPHA_CAP)

            out[x, y] = tuple(int(round(min(255, c))) for c in col) + (
                int(round(min(255, alpha))),)
    return im


# ---------------------------------------------------------------------------
# preview helpers
# ---------------------------------------------------------------------------

FLATS = {
    "gui":   (139, 139, 139),
    "snow":  (243, 246, 250),
    "night": (16, 14, 22),
}


def noise_tile(size, base, spread, tint):
    im = Image.new("RGBA", (size, size))
    p = im.load()
    for y in range(size):
        for x in range(size):
            n = (x * 37 + y * 91 + (x * y) % 13) % spread
            r = base[0] + n
            g = base[1] + n
            b = base[2] + n
            p[x, y] = (min(255, r + tint[0]), min(255, g + tint[1]),
                       min(255, b + tint[2]), 255)
    return im


def bg_cell(name, w, h):
    if name == "stone":
        return noise_tile(16, (108, 108, 110), 23, (0, 0, 0)).resize((w, h), Image.NEAREST)
    if name == "grass":
        return noise_tile(16, (60, 96, 40), 26, (0, 0, 0)).resize((w, h), Image.NEAREST)
    return Image.new("RGBA", (w, h), FLATS[name] + (255,))


BG_NAMES = ["gui", "snow", "stone", "grass", "night"]


def paste_on(bg, frame, scale, at=(0, 0)):
    bg.alpha_composite(frame.resize((frame.width * scale, frame.height * scale),
                                    Image.NEAREST), at)
    return bg


def render_previews(all_frames):
    """all_frames: list of (name, charge, [16 frames])"""
    # --- 1. filmstrip per sprite, 1x on gui grey (true in-game pixel scale) --
    for name, _c, frames in all_frames:
        strip = Image.new("RGBA", (FRAMES * (SIZE + 2) + 2, SIZE + 4),
                          FLATS["gui"] + (255,))
        for i, fr in enumerate(frames):
            strip.alpha_composite(fr, (2 + i * (SIZE + 2), 2))
        strip.save(os.path.join(HERE, f"preview_filmstrip_{name}.png"))

    # --- 2. filmstrip stack: all four sprites, 4x, phase-aligned rows -------
    sc = 4
    cw = SIZE * sc
    pad = 3
    stack = Image.new("RGBA", (FRAMES * (cw + pad) + pad,
                               len(all_frames) * (cw + pad) + pad), (46, 46, 52, 255))
    for r, (_n, _c, frames) in enumerate(all_frames):
        for i, fr in enumerate(frames):
            cell = bg_cell("gui", cw, cw)
            paste_on(cell, fr, sc)
            stack.paste(cell, (pad + i * (cw + pad), pad + r * (cw + pad)))
    stack.save(os.path.join(HERE, "preview_filmstrip_all_4x.png"))

    # --- 3. 8x frame grid: rows = backdrops, cols = the four sprites --------
    for idx in (0, 5, 10):
        sc = 8
        cw = SIZE * sc
        pad = 6
        big = Image.new("RGBA", (len(all_frames) * (cw + pad) + pad,
                                 len(BG_NAMES) * (cw + pad) + pad), (46, 46, 52, 255))
        for r, nm in enumerate(BG_NAMES):
            for c, (_n, _ch, frames) in enumerate(all_frames):
                cell = bg_cell(nm, cw, cw)
                paste_on(cell, frames[idx], sc)
                big.paste(cell, (pad + c * (cw + pad), pad + r * (cw + pad)))
        big.save(os.path.join(HERE, f"preview_frame8x_f{idx:02d}.png"))

    # --- 4. draw sequence: one sprite per column, phase advancing downward --
    sc = 6
    cw = SIZE * sc
    pad = 4
    seq = Image.new("RGBA", (len(all_frames) * (cw + pad) + pad,
                             8 * (cw + pad) + pad), (46, 46, 52, 255))
    for r in range(8):
        for c, (_n, _ch, frames) in enumerate(all_frames):
            cell = bg_cell("stone", cw, cw)
            paste_on(cell, frames[r * 2], sc)
            seq.paste(cell, (pad + c * (cw + pad), pad + r * (cw + pad)))
    seq.save(os.path.join(HERE, "preview_draw_sequence.png"))

    # --- 5. mock hotbar rows over each backdrop -----------------------------
    sc = 3
    slot = 20 * sc
    cols = 8
    hb = Image.new("RGBA", (len(all_frames) * cols * slot + 8,
                            len(BG_NAMES) * (slot + 6) + 6), (28, 28, 32, 255))
    for r, nm in enumerate(BG_NAMES):
        k = 0
        for _n, _ch, frames in all_frames:
            for i in range(cols):
                cell = bg_cell(nm, slot, slot)
                d = cell.load()
                for a in range(slot):
                    for e in (0, 1, slot - 2, slot - 1):
                        for (px_, py_) in ((a, e), (e, a)):
                            p = d[px_, py_]
                            d[px_, py_] = (max(0, p[0] - 30), max(0, p[1] - 30),
                                           max(0, p[2] - 30), 255)
                paste_on(cell, frames[i * 2], sc, (2 * sc, 2 * sc))
                hb.paste(cell, (4 + k * slot, 6 + r * (slot + 6)))
                k += 1
    hb.save(os.path.join(HERE, "preview_hotbar.png"))


def main():
    all_frames = []
    for name, srcname, charge in STAGES:
        src = Image.open(os.path.join(HERE, srcname)).convert("RGBA")
        pulling = srcname != "bow.png"
        kinds = {}
        for y in range(SIZE):
            for x in range(SIZE):
                p = src.getpixel((x, y))
                if p[3]:
                    kinds[(x, y)] = classify(x, y, p, pulling)
        frames = [build_frame(src, kinds, i / FRAMES, charge) for i in range(FRAMES)]
        all_frames.append((name, charge, frames))

        sheet = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
        for i, fr in enumerate(frames):
            sheet.paste(fr, (0, i * SIZE))
        sheet.save(os.path.join(HERE, name + ".png"))
        with open(os.path.join(HERE, name + ".png.mcmeta"), "w") as fh:
            json.dump({"animation": {"frametime": FRAMETIME, "interpolate": True}},
                      fh, indent=2)
            fh.write("\n")

        counts = {}
        for v in kinds.values():
            counts[v] = counts.get(v, 0) + 1
        print(f"{name:22s} charge={charge:.2f} regions={counts}")

    render_previews(all_frames)
    print(f"frames={FRAMES} frametime={FRAMETIME} sheet=16x{16*FRAMES} period={PERIOD}")


if __name__ == "__main__":
    main()
