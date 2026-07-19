"""Throwaway explorer: shaft palette x head palette, rendered side by side."""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_oracle_wand import vanilla  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "variants")

STICK = [(73, 54, 21, 255), (137, 103, 39, 255), (104, 78, 30, 255), (40, 30, 11, 255)]

SHAFTS = {
    # left outline, lit core, shaded core, darkest outline, ferrule, ferrule dim
    "s1violet": ["#383341", "#6a6474", "#524d5c", "#191620", "#8d869b", "#6c667a"],
    "s2warm":   ["#3a3339", "#6b6570", "#55505b", "#1b1618", "#8b8593", "#6d6776"],
    "s3dark":   ["#342d36", "#5f5a6a", "#4a4553", "#161318", "#837d90", "#645f70"],
}

HEADS = {
    # core, arms, points, notches
    "h1white": ["#fdffa8", "#dae2e2", "#b9c9c9", "#556b6b"],
    "h2lemon": ["#e0e277", "#fdffa8", "#b9c9c9", "#556b6b"],
    "h3mixed": ["#fdffa8", "#e0e277", "#dae2e2", "#556b6b"],
    # vanilla's own outward ramp: saturated yellow -> pale lemon -> white -> rim
    "h4vanillaramp": ["#e0e277", "#fdffa8", "#dae2e2", "#556b6b"],
}


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def build(shaft, head):
    s = [rgb(c) for c in SHAFTS[shaft]]
    hd = [rgb(c) for c in HEADS[head]]
    ramp = dict(zip(STICK, s[:4]))
    stick = vanilla("item/stick.png")
    src = stick.load()
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] and 0 <= x - 2 < 16 and 0 <= y + 2 < 16:
                px[x - 2, y + 2] = ramp[src[x, y]]
    tx, ty = next((x, y) for y in range(16) for x in range(15, -1, -1) if px[x, y][3])
    px[tx - 1, ty + 1] = s[4]
    px[tx - 2, ty + 2] = s[5]
    core, arms, points, notch = hd
    for dx, dy, c in ((0, -2, core), (0, -3, arms), (-1, -2, arms), (1, -2, arms),
                      (0, -1, arms), (-1, -3, notch), (1, -3, notch), (-1, -1, notch),
                      (1, -1, notch), (0, -4, points), (-2, -2, points),
                      (2, -2, points), (0, 0, points)):
        x, y = tx + dx, ty + dy
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c
    return im


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for s in SHAFTS:
        for h in HEADS:
            build(s, h).save(os.path.join(OUT, f"{s}_{h}.png"))
    print("wrote", len(SHAFTS) * len(HEADS))
