"""Contact sheets: 4x on the tree-screen grey, and a true-16px strip.

Usage: python3 sheet.py <dir-or-treename>[,<more>] <out.png> [cols] [scale]
"""
import os
import sys

from PIL import Image, ImageDraw

BASE = ("/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/"
        "assets/archetypes/textures/node")
GREY = (0x8B, 0x8B, 0x8B)


def collect(spec):
    paths = []
    for t in spec.split(","):
        d = t if os.path.isdir(t) else os.path.join(BASE, t)
        paths += sorted(os.path.join(d, f) for f in os.listdir(d) if f.endswith(".png"))
    return paths


def sheet(paths, out, scale=4, cols=8, bg=GREY):
    items = [(os.path.basename(p)[:-4], Image.open(p).convert("RGBA")) for p in paths]
    cell = 32 * scale + 10
    rows = (len(items) + cols - 1) // cols
    im = Image.new("RGB", (cols * cell, rows * (cell + 12)), bg)
    dr = ImageDraw.Draw(im)
    for i, (n, ic) in enumerate(items):
        cx = (i % cols) * cell + 5
        cy = (i // cols) * (cell + 12) + 5
        big = ic.resize((32 * scale, 32 * scale), Image.NEAREST)
        im.paste(big, (cx, cy), big)
        dr.text((cx, cy + 32 * scale + 2), n[:22], fill=(20, 20, 20))
    im.save(out)
    print(out, im.size, len(items))


def true16(paths, out, cols=8, pad=6, reps=3):
    """Every icon at the size the tree screen actually draws it: 16px.
    Repeated in a row so the eye can compare neighbours, on the plate grey."""
    items = [(os.path.basename(p)[:-4], Image.open(p).convert("RGBA")) for p in paths]
    cell = 16 + pad * 2
    rows = (len(items) + cols - 1) // cols
    W, H = cols * cell, rows * (cell + 10)
    im = Image.new("RGB", (W, H), GREY)
    dr = ImageDraw.Draw(im)
    for i, (n, ic) in enumerate(items):
        cx = (i % cols) * cell + pad
        cy = (i // cols) * (cell + 10) + pad
        sm = ic.resize((16, 16), Image.NEAREST)
        im.paste(sm, (cx, cy), sm)
        dr.text((cx - 4, cy + 18), n[:9], fill=(30, 30, 30))
    im = im.resize((W * reps, H * reps), Image.NEAREST)
    im.save(out)
    print(out, im.size, len(items))


if __name__ == "__main__":
    ps = collect(sys.argv[1])
    cols = int(sys.argv[3]) if len(sys.argv) > 3 else 8
    scale = int(sys.argv[4]) if len(sys.argv) > 4 else 4
    out = sys.argv[2]
    sheet(ps, out, cols=cols, scale=scale)
    true16(ps, out.replace(".png", "_true16.png"), cols=cols)
