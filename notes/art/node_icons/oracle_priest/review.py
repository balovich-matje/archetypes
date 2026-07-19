"""Contact sheets for judging the Oracle-Priest icon set.

sheet_4x.png       every icon at 4x on the tree screen's grey + dark plate
sheet_true16.png   the icons as the tree screen actually draws them (16px),
                   plus a 6x zoom of that same 16px downscale
sheet_vs_priest.png  my set beside the shipped Priest set, same scale

Usage: python3 review.py
"""
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
NODE = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node"))

GREY = (0x8B, 0x8B, 0x8B)
PLATE = (0x30, 0x30, 0x38)

ORDER = ["aura_of_radiance", "brilliance", "beacon_of_light",
         "blinding_light", "steadfast", "retribution"]


def load(d, names=None):
    if names is None:
        names = sorted(f[:-4] for f in os.listdir(d) if f.endswith(".png"))
    return [(n, Image.open(os.path.join(d, n + ".png")).convert("RGBA")) for n in names]


def grid(items, path, scale, cols, label=True, pad=6):
    cell = 32 * scale
    lab = 12 if label else 2
    im = Image.new("RGB", (cols * (cell + pad) + pad,
                           ((len(items) + cols - 1) // cols) * (cell + pad + lab) + pad), GREY)
    dr = ImageDraw.Draw(im)
    for i, (n, ic) in enumerate(items):
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + pad + lab)
        dr.rectangle([x - 2, y - 2, x + cell + 1, y + cell + 1], fill=PLATE)
        big = ic.resize((cell, cell), Image.NEAREST)
        im.paste(big, (x, y), big)
        if label:
            dr.text((x, y + cell + 2), n[:22], fill=(0, 0, 0))
    im.save(path)
    print(os.path.basename(path), im.size)


def true16(items, path):
    """What the tree screen shows: the 32px art squeezed into a 16px slot,
    drawn once at 1:1 and once zoomed 6x so the mush is visible."""
    n = len(items)
    im = Image.new("RGB", (n * 24 + 8, 24 + 16 * 6 + 24), GREY)
    dr = ImageDraw.Draw(im)
    for i, (name, ic) in enumerate(items):
        small = ic.resize((16, 16), Image.BOX)
        x = 8 + i * 24
        dr.rectangle([x - 2, 6, x + 17, 25], fill=PLATE)
        im.paste(small, (x, 8), small)
        big = small.resize((16 * 6, 16 * 6), Image.NEAREST)
        bx = 8 + i * (16 * 6 + 8)
        if bx + 96 < im.width:
            dr.rectangle([bx - 2, 34, bx + 97, 133], fill=PLATE)
            im.paste(big, (bx, 36), big)
    im.save(path)
    print(os.path.basename(path), im.size)


def true16_row(items, path, zoom=6):
    cell = 16 * zoom
    im = Image.new("RGB", (len(items) * (cell + 8) + 8, cell + 34), GREY)
    dr = ImageDraw.Draw(im)
    for i, (name, ic) in enumerate(items):
        small = ic.resize((16, 16), Image.BOX)
        x = 8 + i * (cell + 8)
        dr.rectangle([x - 3, 5, x + cell + 2, cell + 10], fill=PLATE)
        im.paste(small.resize((cell, cell), Image.NEAREST), (x, 8))
        # and the honest 1:1 version right under the label
        im.paste(small, (x + cell // 2 - 8, cell + 16), small)
        dr.text((x, cell + 2), name[:16], fill=(0, 0, 0))
    im.save(path)
    print(os.path.basename(path), im.size)


if __name__ == "__main__":
    mine = load(OUT, ORDER)
    grid(mine, os.path.join(HERE, "sheet_4x.png"), 4, 6)
    true16_row(mine, os.path.join(HERE, "sheet_true16.png"))
    priest = load(os.path.join(NODE, "priest"))
    grid(mine + priest, os.path.join(HERE, "sheet_vs_priest.png"), 3, 6)
