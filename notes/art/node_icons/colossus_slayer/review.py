"""Contact sheets for judging the Colossus-Slayer icon set.

sheet_4x.png        every icon at 4x on the tree screen's grey + dark plate
sheet_true16.png    the icons as the tree screen draws them (16px), 1:1 and
                    zoomed so the mush is visible
sheet_vs_slayer.png my set beside the shipped Slayer set, same scale
sheet_vs_mixed.png  my set beside shipped protector + oracle_priest

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

ORDER = ["parry", "barbarian", "blade_master",
         "spell_reflect", "stalwart", "riposte"]


def load(d, names=None):
    if names is None:
        names = sorted(f[:-4] for f in os.listdir(d) if f.endswith(".png"))
    return [(n, Image.open(os.path.join(d, n + ".png")).convert("RGBA")) for n in names]


def grid(items, path, scale, cols, pad=8):
    cell = 32 * scale
    lab = 12
    im = Image.new("RGB", (cols * (cell + pad) + pad,
                           ((len(items) + cols - 1) // cols) * (cell + pad + lab) + pad), GREY)
    dr = ImageDraw.Draw(im)
    for i, (n, ic) in enumerate(items):
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + pad + lab)
        dr.rectangle([x - 2, y - 2, x + cell + 1, y + cell + 1], fill=PLATE)
        big = ic.resize((cell, cell), Image.NEAREST)
        im.paste(big, (x, y), big)
        dr.text((x, y + cell + 2), n[:20], fill=(0, 0, 0))
    im.save(path)
    print(os.path.basename(path), im.size)


def true16_row(items, path, zoom=7):
    """What the tree screen shows: 32px art squeezed into a 16px slot."""
    cell = 16 * zoom
    im = Image.new("RGB", (len(items) * (cell + 8) + 8, cell + 40), GREY)
    dr = ImageDraw.Draw(im)
    for i, (name, ic) in enumerate(items):
        small = ic.resize((16, 16), Image.BOX)
        x = 8 + i * (cell + 8)
        dr.rectangle([x - 3, 5, x + cell + 2, cell + 10], fill=PLATE)
        im.paste(small.resize((cell, cell), Image.NEAREST), (x, 8))
        dr.rectangle([x + cell // 2 - 10, cell + 14, x + cell // 2 + 7, cell + 31], fill=PLATE)
        im.paste(small, (x + cell // 2 - 8, cell + 16), small)
        dr.text((x, cell + 2), name[:18], fill=(0, 0, 0))
    im.save(path)
    print(os.path.basename(path), im.size)


if __name__ == "__main__":
    mine = load(OUT, ORDER)
    grid(mine, os.path.join(HERE, "sheet_4x.png"), 4, 6)
    true16_row(mine, os.path.join(HERE, "sheet_true16.png"))
    grid(mine + load(os.path.join(NODE, "slayer")),
         os.path.join(HERE, "sheet_vs_slayer.png"), 3, 7)
    grid(mine + load(os.path.join(NODE, "protector")) + load(os.path.join(NODE, "oracle_priest")),
         os.path.join(HERE, "sheet_vs_mixed.png"), 3, 7)
