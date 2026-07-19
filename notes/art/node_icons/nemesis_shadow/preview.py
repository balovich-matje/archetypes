"""Contact sheets for the Nemesis-Shadow node icons.

Three views, because an icon that looks fine at 4x is often mush at the size
the tree screen actually draws it:
  sheet_4x.png    — the set blown up, for spotting palette and outline errors
  sheet_16.png    — true 16px on the unowned grey (0x8B8B8B), the hovered
                    grey, the owned Agility mint (0x7FCF9F) and the dimmed
                    plate, which is every state the node plate has
  sheet_beside.png — the set next to a shipped tree, to check it belongs

Usage: python3 preview.py
"""
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "out")
NODE = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node"))

NAMES = ["dark_ritual", "extra_sensory_perception", "night_eyes",
         "feast", "ghost_form", "incorporeal"]

GREY = (0x8B, 0x8B, 0x8B, 255)
HOVER = (0xA3, 0xA3, 0xA3, 255)
MINT = (0x7F, 0xCF, 0x9F, 255)
DIM = (0x3E, 0x3E, 0x3E, 255)      # grey under the 0x99000000 unreachable wash
PAGE = (0x2E, 0x2E, 0x34, 255)


def load(name):
    return Image.open(os.path.join(OUT_DIR, f"{name}.png")).convert("RGBA")


def sheet_4x():
    cell, pad, lab = 128, 12, 12
    im = Image.new("RGBA", (len(NAMES) * (cell + pad) + pad, cell + pad * 2 + lab), PAGE)
    d = ImageDraw.Draw(im)
    for i, n in enumerate(NAMES):
        x, y = pad + i * (cell + pad), pad
        im.alpha_composite(Image.new("RGBA", (cell, cell), GREY), (x, y))
        im.alpha_composite(load(n).resize((cell, cell), Image.NEAREST), (x, y))
        d.text((x, y + cell + 1), n, fill=(235, 235, 235, 255))
    im.save(os.path.join(OUT_DIR, "sheet_4x.png"))


def sheet_16():
    """The real thing: 16px icons on the four plate states, then the same rows
    magnified with no resampling so the 16px pixels stay 16px pixels."""
    plates = [("unowned 8B8B8B", GREY), ("hovered A3A3A3", HOVER),
              ("owned 7FCF9F", MINT), ("unreachable", DIM)]
    zoom = 4
    cell = 20                       # 16px icon + 2px plate margin each side
    im = Image.new("RGBA",
                   (140 + len(NAMES) * (cell + 6) * zoom,
                    len(plates) * (cell * zoom + 10) + 10), PAGE)
    d = ImageDraw.Draw(im)
    for r, (label, plate) in enumerate(plates):
        y = 10 + r * (cell * zoom + 10)
        d.text((6, y + cell * zoom // 2), label, fill=(235, 235, 235, 255))
        for i, n in enumerate(NAMES):
            tile = Image.new("RGBA", (cell, cell), plate)
            icon = load(n).resize((16, 16), Image.NEAREST)
            if label == "unreachable":
                icon = Image.alpha_composite(
                    Image.alpha_composite(Image.new("RGBA", (16, 16), plate), icon),
                    Image.new("RGBA", (16, 16), (0, 0, 0, 0x99)))
            tile.alpha_composite(icon, (2, 2))
            im.alpha_composite(tile.resize((cell * zoom, cell * zoom), Image.NEAREST),
                               (140 + i * (cell + 6) * zoom, y))
    im.save(os.path.join(OUT_DIR, "sheet_16.png"))


def sheet_beside(trees=("shadow", "assassin")):
    rows = [("nemesis_shadow", [os.path.join(OUT_DIR, f"{n}.png") for n in NAMES])]
    for t in trees:
        d = os.path.join(NODE, t)
        rows.append((t, sorted(os.path.join(d, f) for f in os.listdir(d)
                               if f.endswith(".png"))[:8]))
    cell, pad = 64, 8
    cols = max(len(r[1]) for r in rows)
    im = Image.new("RGBA", (110 + cols * (cell + pad), len(rows) * (cell + pad) + pad), PAGE)
    dr = ImageDraw.Draw(im)
    for r, (label, paths) in enumerate(rows):
        y = pad + r * (cell + pad)
        dr.text((6, y + cell // 2), label, fill=(235, 235, 235, 255))
        for i, p in enumerate(paths):
            x = 110 + i * (cell + pad)
            im.alpha_composite(Image.new("RGBA", (cell, cell), GREY), (x, y))
            im.alpha_composite(
                Image.open(p).convert("RGBA").resize((cell, cell), Image.NEAREST), (x, y))
    im.save(os.path.join(OUT_DIR, "sheet_beside.png"))


if __name__ == "__main__":
    sheet_4x()
    sheet_16()
    sheet_beside()
    print("sheets written to", OUT_DIR)
