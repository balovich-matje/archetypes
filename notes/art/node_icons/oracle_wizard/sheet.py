"""Contact-sheet renderer: icons at 4x and at true 16px, on the tree grey and
the dark node plate. Used to judge the oracle_wizard set against shipped sets.

Usage: python3 sheet.py <out.png> <dir-or-glob> [<dir-or-glob> ...]
"""
import glob
import os
import sys

from PIL import Image, ImageDraw

GREY = (0x8B, 0x8B, 0x8B, 255)
PLATE = (0x2B, 0x2B, 0x33, 255)


def files(spec):
    if os.path.isdir(spec):
        return sorted(glob.glob(os.path.join(spec, "*.png")))
    return sorted(glob.glob(spec))


def sheet(specs, out):
    rows = []
    for spec in specs:
        fs = files(spec)
        if fs:
            rows.append((os.path.basename(spec.rstrip("/")), fs))

    cols = max(len(fs) for _, fs in rows)
    cell = 4 * 32 + 8          # 4x icon + gutter
    strip = 16 * 3 + 8         # 16px block, x3 zoom for eyeballing, + gutter
    row_h = cell + strip + strip + 18
    w = 90 + cols * cell
    h = 8 + len(rows) * row_h
    im = Image.new("RGBA", (w, h), (24, 24, 28, 255))
    d = ImageDraw.Draw(im)

    y = 8
    for label, fs in rows:
        d.text((6, y + cell // 2), label, fill=(230, 230, 230, 255))
        for i, f in enumerate(fs):
            x = 90 + i * cell
            ic = Image.open(f).convert("RGBA")

            # 4x on grey
            bg = Image.new("RGBA", (32 * 4, 32 * 4), GREY)
            bg.alpha_composite(ic.resize((128, 128), Image.NEAREST))
            im.alpha_composite(bg, (x, y))
            d.text((x, y + cell - 14), os.path.basename(f)[:-4][:18],
                   fill=(200, 200, 200, 255))

            # true 16px, zoomed x3 with no smoothing, on grey then on plate
            small = ic.resize((16, 16), Image.LANCZOS)
            for k, back in enumerate((GREY, PLATE)):
                b = Image.new("RGBA", (16, 16), back)
                b.alpha_composite(small)
                im.alpha_composite(b.resize((48, 48), Image.NEAREST),
                                   (x, y + cell + k * strip))
        y += row_h

    im.save(out)
    print(out, im.size)


if __name__ == "__main__":
    sheet(sys.argv[2:], sys.argv[1])
