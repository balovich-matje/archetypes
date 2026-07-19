"""Contact sheet: N textures at 8x, plus true-16px and 2x strips on both a
dark GUI background and a vanilla inventory-slot grey."""
import os
import sys
from PIL import Image

DARK = (58, 58, 66, 255)
SLOT = (139, 139, 139, 255)     # vanilla inventory slot grey
CELL, PAD = 128, 10


def sheet(paths, out):
    n = len(paths)
    w = n * (CELL + PAD) + PAD
    strip = 40
    h = PAD + CELL + PAD + strip * 2 + PAD
    im = Image.new("RGBA", (w, h), DARK)
    y_dark = PAD + CELL + PAD
    y_slot = y_dark + strip
    Image.new("RGBA", (w, strip), SLOT).convert("RGBA")
    im.paste(Image.new("RGBA", (w, strip), SLOT), (0, y_slot))
    for i, p in enumerate(paths):
        t = Image.open(p).convert("RGBA")
        x = PAD + i * (CELL + PAD)
        im.alpha_composite(t.resize((CELL, CELL), Image.NEAREST), (x, PAD))
        for y0 in (y_dark, y_slot):
            im.alpha_composite(t, (x + 8, y0 + 12))
            im.alpha_composite(t.resize((32, 32), Image.NEAREST), (x + 36, y0 + 4))
    im.save(out)
    print(out, im.size, " | ".join(os.path.basename(p) for p in paths))


if __name__ == "__main__":
    sheet(sys.argv[2:], sys.argv[1])
