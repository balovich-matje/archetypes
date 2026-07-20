"""Contact sheets for the Colossus-Protector set: 4x, true 16px, and beside
a shipped set -- all on the tree screen's grey (0x8B8B8B)."""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
BASE = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node"))
GREY = (0x8B, 0x8B, 0x8B)
ORDER = ["ironclad", "well_fed", "hearty_meal", "instinctive_guard",
         "free_hand", "immovable_object"]


def load(d, names=None):
    fs = sorted(f for f in os.listdir(d) if f.endswith(".png"))
    if names:
        fs = [n + ".png" for n in names if os.path.exists(os.path.join(d, n + ".png"))]
    return [(f[:-4], Image.open(os.path.join(d, f)).convert("RGBA")) for f in fs]


def sheet(items, path, scale=4, cols=6, label=True):
    cell = 32 * scale + 8
    rows = (len(items) + cols - 1) // cols
    im = Image.new("RGB", (cols * cell, rows * (cell + 12)), GREY)
    dr = ImageDraw.Draw(im)
    for i, (n, ic) in enumerate(items):
        cx = (i % cols) * cell + 4
        cy = (i // cols) * (cell + 12) + 4
        dr.rectangle([cx - 2, cy - 2, cx + 32 * scale + 1, cy + 32 * scale + 1],
                     fill=(0x30, 0x30, 0x38))
        big = ic.resize((32 * scale, 32 * scale), Image.NEAREST)
        im.paste(big, (cx, cy), big)
        if label:
            dr.text((cx, cy + 32 * scale + 2), n[:22], fill=(0, 0, 0))
    im.save(path)
    print(path, im.size, len(items))


def true16(items, path, reps=3, zoom=1):
    """The icons at the size the tree screen actually draws them (32px file
    -> 16px slot), repeated in a row and again zoomed for inspection."""
    pad, slot = 6, 16
    w = len(items) * (slot + pad) + pad
    h = pad + slot + pad + slot * 6 + pad
    im = Image.new("RGB", (w, h), GREY)
    for i, (n, ic) in enumerate(items):
        small = ic.resize((16, 16), Image.NEAREST)
        x = pad + i * (slot + pad)
        im.paste(small, (x, pad), small)
        big = small.resize((16 * 6, 16 * 6), Image.NEAREST)
        im.paste(big, (pad + i * (slot + pad) * 1, pad + slot + pad), big)
    im = im.resize((w * 3, h * 3), Image.NEAREST)
    im.save(path)
    print(path, im.size)


def true16_grid(items, path):
    """Every icon at exactly 16px on grey, with a 6x blow-up beneath it."""
    n = len(items)
    cw, ch = 16 * 7, 16 * 7 + 24
    im = Image.new("RGB", (n * cw, ch), GREY)
    for i, (name, ic) in enumerate(items):
        small = ic.resize((16, 16), Image.NEAREST)
        im.paste(small, (i * cw + 8, 8), small)
        big = small.resize((16 * 6, 16 * 6), Image.NEAREST)
        im.paste(big, (i * cw + 8, 32), big)
    im.save(path)
    print(path, im.size)


if __name__ == "__main__":
    mine = load(OUT, ORDER)
    sheet(mine, os.path.join(HERE, "sheet_4x.png"))
    true16_grid(mine, os.path.join(HERE, "sheet_true16.png"))
    ship = load(os.path.join(BASE, "protector"))[:6] + \
        load(os.path.join(BASE, "oracle_priest"))[:6]
    sheet(mine + ship, os.path.join(HERE, "sheet_vs_shipped.png"), scale=3, cols=6)
    true16_grid(mine + ship[:6], os.path.join(HERE, "sheet_vs_shipped_16.png"))
