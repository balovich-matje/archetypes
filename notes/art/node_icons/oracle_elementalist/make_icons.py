"""Generate the 32x32 node icons for the epic Oracle-Elementalist sub-tree.

House rules taken from the nine shipped sets (elementalist/, wizard/, priest/,
shadow/, assassin/): chunky vanilla-item-scale pixel art, hard dark outlines,
binary alpha, a small saturated palette, shapes that still read when the tree
screen draws them at 16px.

So everything here is authored on a 16x16 grid -- exactly what the player sees
in the tree -- and blown up 2x NEAREST to the 32x32 file. What you draw is what
survives.

Usage: python3 make_icons.py [--install]
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node/oracle_elementalist"))
SHEETS = os.path.join(HERE, "sheets")
SHIPPED = os.path.normpath(os.path.join(
    HERE, "../../../../src/main/resources/assets/archetypes/textures/node"))

# Storm palette. The bolt is vanilla lightning: white core, pale blue body,
# a deep blue trailing edge. Gold is the house's sparkle colour (arcane_orb,
# first_strike, razor_edge all use it). Mana blue is lifted straight off
# gui/mana_orb_full.png so the mana nodes match the HUD.
PAL = {
    "W": (255, 255, 255, 255),   # bolt core
    "L": (208, 230, 255, 255),   # bolt light
    "M": (130, 180, 248, 255),   # bolt mid
    "D": (62, 108, 210, 255),    # bolt shade
    "E": (40, 70, 156, 255),     # dim echo
    "S": (255, 246, 190, 255),   # spark / glint
    "G": (242, 198, 66, 255),    # gold
    "c": (206, 212, 226, 255),   # cloud light
    "m": (146, 154, 176, 255),   # cloud mid
    "d": (94, 100, 124, 255),    # cloud dark
    "b": (45, 110, 230, 255),    # mana orb base
    "o": (24, 62, 160, 255),     # mana orb shade
    "h": (140, 190, 255, 255),   # mana orb highlight
    "y": (255, 214, 110, 255),   # hot gold
    "r": (255, 132, 40, 255),    # hot orange
    "R": (200, 44, 28, 255),     # hot red
}

COLD_OUTLINE = (16, 18, 40, 255)
HOT_OUTLINE = (48, 14, 12, 255)
DARK_OUTLINE = (22, 22, 28, 255)   # the mana orb's own outline colour


def grid(rows):
    """A 16x16 RGBA image from 16 strings of 16 chars ('.' = empty)."""
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError("map must be 16x16, got %d rows of %s"
                         % (len(rows), sorted({len(r) for r in rows})))
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = PAL[ch]
    return im


def outline(im, colour=COLD_OUTLINE, diagonal=True):
    """Wrap the silhouette in a 1px dark border, the way vanilla items are."""
    out = im.copy()
    src = im.load()
    dst = out.load()
    offs = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    if diagonal:
        offs += [(-1, -1), (1, -1), (-1, 1), (1, 1)]
    for y in range(im.height):
        for x in range(im.width):
            if src[x, y][3]:
                continue
            for dx, dy in offs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < im.width and 0 <= ny < im.height and src[nx, ny][3]:
                    dst[x, y] = colour
                    break
    return out


def stamp(im, other, x, y):
    im.alpha_composite(other, (x, y))


def halo(body, glow, edge):
    """Body, a 1px glow ring, then the dark edge -- what makes Overcharge read
    as the same bolt run too hot."""
    lit = outline(body, glow)
    return outline(lit, edge)


# --------------------------------------------------------------------------
# the seven icons
# --------------------------------------------------------------------------

def lightning_strike():
    """The epic active: one huge vanilla bolt, tip driven into a hit-flash."""
    body = grid([
        "................",
        ".......LWWM.....",
        "......LWWWM.....",
        ".....LWWWM......",
        "....LWWWM.......",
        "...LWWWM........",
        "...LWWWWWWWMD...",
        "......LWWWM.....",
        ".....LWWWM......",
        ".....LWWM.......",
        "....LWWM........",
        "....LWM.........",
        "...LWWM.........",
        "...LWM..........",
        "................",
        "................",
    ])
    im = outline(body)
    glint = grid([
        "................",
        "................",
        "................",
        "..S.............",
        ".SSS............",
        "..S.............",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..........S.....",
        ".........SSS....",
        "..........S.....",
        "................",
    ])
    stamp(im, glint, 0, 0)
    return im


BOLT_BIG = [
    "..WWM",
    ".WWM.",
    ".WWM.",
    "WWM..",
    "WWWWM",
    "..WWM",
    "..WM.",
    ".WWM.",
    ".WM..",
    ".M...",
]

BOLT_MID = [
    ".WWM",
    ".WM.",
    "WWM.",
    "WWWM",
    "..WM",
    ".WM.",
    ".WM.",
    ".M..",
]

BOLT_SMALL = [
    ".WM",
    "WM.",
    "WWM",
    ".WM",
    "WM.",
    "M..",
]

# Shorter and fatter, so it still has weight inside Recurrence's loop.
BOLT_RING = [
    "..WWM",
    ".WWM.",
    "WWM..",
    "WWWWM",
    "..WWM",
    ".WWM.",
    ".WM..",
    ".M...",
]


def paste_art(im, art, ox, oy):
    px = im.load()
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != "." and 0 <= ox + x < 16 and 0 <= oy + y < 16:
                px[ox + x, oy + y] = PAL[ch]


def chain():
    """Chain Reaction: the strike, then the same strike again on the next
    hostile, and the next -- three bolts walking away down the icon."""
    body = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paste_art(body, BOLT_BIG, 1, 1)
    paste_art(body, BOLT_MID, 7, 3)
    paste_art(body, BOLT_SMALL, 12, 8)
    im = outline(body)
    stamp(im, grid([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...........S....",
        "................",
    ]), 0, 0)
    return im


def ring(inner, outer, colour, skip=None, cx=7.5, cy=7.5):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if inner <= dist <= outer and not (skip and skip(x, y)):
                px[x, y] = colour
    return im


def recurrence():
    """Recurrence: the same target, struck again -- the bolt caught inside a
    turning arrow. Every other icon in the set is a loose bolt shape, so the
    ring is what makes this one legible at 16px."""
    loop = outline(ring(4.6, 5.6, PAL["G"], skip=lambda x, y: x >= 8 and y <= 4))
    head = outline(grid([
        "................",
        "................",
        ".........GGGG...",
        "..........GGGG..",
        "..........GGG...",
        "...........G....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]))
    bolt = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paste_art(bolt, BOLT_RING, 5, 4)
    im = loop
    im.alpha_composite(head, (0, 0))
    im.alpha_composite(outline(bolt), (0, 0))
    return im


def tempest():
    """Tempest: the capstone storm -- a thunderhead raining bolts on a field."""
    cloud = grid([
        "......cccc......",
        "....cccccccc....",
        "...cccccccccc...",
        "..cccccccccmmm..",
        "..cmmmmmmmmmmmm.",
        ".ccmmmmmmmmmmmm.",
        "..dddmmdddmmddd.",
        "...d.dd...dd.d..",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    bolts = grid([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..WW....WW...WW.",
        "..WM...WM....WM.",
        ".WM....WM...WM..",
        ".WWM..WWWM..WM..",
        "..WM....WM.WWM..",
        "..M....WM...WM..",
        ".......M....M...",
        "................",
    ])
    im = outline(bolts)
    im.alpha_composite(outline(cloud), (0, 0))
    return im


def overcharge():
    """Overcharge: the bolt run at double power -- fat, white-hot, throwing
    sparks off every edge."""
    body = grid([
        "................",
        ".......WWWy.....",
        "......WWWy......",
        "......WWWy......",
        ".....WWWy.......",
        ".....WWWWWWy....",
        ".......WWWy.....",
        "......WWWy......",
        "......WWWy......",
        ".....WWWy.......",
        ".....WWy........",
        "....WWy.........",
        "....Wy..........",
        "................",
        "................",
        "................",
    ])
    im = halo(body, PAL["r"], HOT_OUTLINE)
    stamp(im, grid([
        "................",
        "...S............",
        "................",
        "............S...",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..........S.....",
        "................",
        "................",
        "....S...........",
        "................",
        "................",
    ]), 0, 0)
    return im


def orb(size=9):
    """The HUD mana orb, hand-drawn at the size the icon needs."""
    if size == 9:
        return grid([
            "...bbb..........",
            "..bbbbb.........",
            ".bhbbbbb........",
            ".bbbbbbb........",
            "bbbbbbbbb.......",
            ".bbbbbbo........",
            ".bbbbboo........",
            "..bbooo.........",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        ]).crop((0, 0, 9, 8))
    raise ValueError(size)


def oracle_wisdom():
    """Oracle's Wisdom: a deeper mana pool -- the HUD orb grown big, with the
    rising arrow the other +max nodes use."""
    body = grid([
        "................",
        "....bbbb........",
        "...bbbbbb.......",
        "..bhbbbbbb......",
        "..hbbbbbbb......",
        ".bbbbbbbbbb.....",
        ".bbbbbbbbbb.....",
        ".bbbbbbbboo.....",
        "..bbbbbbooo.....",
        "..bbbbboooo.....",
        "...bbboooo......",
        "....booo........",
        "................",
        "................",
        "................",
        "................",
    ])
    im = outline(body, DARK_OUTLINE)
    arrow = grid([
        "................",
        "............S...",
        "...........SSS..",
        "..........SSSSS.",
        ".........SSSSSSS",
        "...........SSS..",
        "...........SSS..",
        "...........SSS..",
        "...........SSS..",
        "...........SSS..",
        "...........SSS..",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    im.alpha_composite(outline(arrow, DARK_OUTLINE), (0, 0))
    return im


def oracle_focus():
    """Oracle's Focus: mana coming back on its own -- motes streaming up into
    the orb."""
    body = grid([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...bbbb.........",
        "..bbbbbb........",
        ".bhbbbbbb.......",
        ".hbbbbbbb.......",
        ".bbbbbbbb.......",
        ".bbbbbboo.......",
        "..bbbbooo.......",
        "...bbooo........",
        "................",
        "................",
    ])
    im = outline(body, DARK_OUTLINE)
    motes = grid([
        "................",
        ".............hb.",
        ".............bo.",
        "................",
        "................",
        "..........hbb...",
        "..........bbbb..",
        "..........bbbo..",
        "...........bo...",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    im.alpha_composite(outline(motes, DARK_OUTLINE), (0, 0))
    glint = grid([
        "................",
        "................",
        "................",
        "..............S.",
        "................",
        "................",
        "..........S.....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    stamp(im, glint, 0, 0)
    return im


ICONS = {
    "lightning_strike": lightning_strike,
    "chain": chain,
    "recurrence": recurrence,
    "oracle_wisdom": oracle_wisdom,
    "oracle_focus": oracle_focus,
    "tempest": tempest,
    "overcharge": overcharge,
}

ORDER = ["lightning_strike", "chain", "recurrence", "oracle_wisdom",
         "oracle_focus", "tempest", "overcharge"]


def build():
    return [(n, ICONS[n]()) for n in ORDER]


def sheet_4x(icons, path):
    """Contact sheet at 4x on the tree screen's grey + dark node plate."""
    cell, pad, label = 128, 12, 12
    cols = 4
    rows = (len(icons) + cols - 1) // cols
    im = Image.new("RGBA", (cols * (cell + pad) + pad,
                            rows * (cell + pad + label) + pad), (139, 139, 139, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    for i, (n, s) in enumerate(icons):
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + pad + label)
        d.rectangle([x - 2, y - 2, x + cell + 1, y + cell + 1],
                    fill=(38, 38, 44, 255), outline=(90, 90, 96, 255))
        im.alpha_composite(s.resize((cell, cell), Image.NEAREST), (x, y))
        d.text((x, y + cell + 1), n, fill=(20, 20, 20, 255))
    im.save(path)
    return path


def sheet_true16(icons, path, zoom=5):
    """Every icon at the size the tree actually draws it (16px), then the whole
    sheet magnified so it can be judged -- no detail added."""
    from PIL import ImageDraw
    cell, pad = 16, 6
    plate = 20
    W = pad + len(icons) * (plate + pad)
    H = pad + plate + pad + plate + pad
    im = Image.new("RGBA", (W, H), (139, 139, 139, 255))
    d = ImageDraw.Draw(im)
    for i, (n, s) in enumerate(icons):
        small = s.resize((cell, cell), Image.BOX)
        x = pad + i * (plate + pad)
        # row 1: on the dark node plate
        d.rectangle([x, pad, x + plate - 1, pad + plate - 1], fill=(38, 38, 44, 255))
        im.alpha_composite(small, (x + 2, pad + 2))
        # row 2: bare on the screen grey
        im.alpha_composite(small, (x + 2, pad + plate + pad + 2))
    im = im.resize((W * zoom, H * zoom), Image.NEAREST)
    im.save(path)
    return path


def sheet_vs_shipped(icons, path, tree="elementalist", zoom=4):
    """Ours beside a shipped set, same scale, so family resemblance is testable."""
    import glob
    from PIL import ImageDraw
    ship = [(os.path.basename(f)[:-4], Image.open(f).convert("RGBA"))
            for f in sorted(glob.glob(os.path.join(SHIPPED, tree, "*.png")))]
    cell, pad, label = 32 * zoom // 2, 8, 11
    cols = 7
    groups = [("oracle_elementalist (new)", icons), (tree + " (shipped)", ship)]
    rows = sum((len(g[1]) + cols - 1) // cols for g in groups)
    H = pad + len(groups) * 14 + rows * (cell + pad + label) + pad
    im = Image.new("RGBA", (cols * (cell + pad) + pad, H), (139, 139, 139, 255))
    d = ImageDraw.Draw(im)
    y = pad
    for title, items in groups:
        d.text((pad, y), title, fill=(20, 20, 20, 255))
        y += 14
        for i, (n, s) in enumerate(items):
            x = pad + (i % cols) * (cell + pad)
            yy = y + (i // cols) * (cell + pad + label)
            d.rectangle([x - 2, yy - 2, x + cell + 1, yy + cell + 1],
                        fill=(38, 38, 44, 255), outline=(90, 90, 96, 255))
            im.alpha_composite(s.resize((cell, cell), Image.NEAREST), (x, yy))
            d.text((x, yy + cell + 1), n[:16], fill=(20, 20, 20, 255))
        y += ((len(items) + cols - 1) // cols) * (cell + pad + label)
    im.save(path)
    return path


def main():
    os.makedirs(SHEETS, exist_ok=True)
    icons = build()
    big = [(n, s.resize((32, 32), Image.NEAREST)) for n, s in icons]
    print(sheet_4x(big, os.path.join(SHEETS, "contact_4x.png")))
    print(sheet_true16(big, os.path.join(SHEETS, "contact_16px.png")))
    print(sheet_vs_shipped(big, os.path.join(SHEETS, "vs_elementalist.png")))
    print(sheet_vs_shipped(big, os.path.join(SHEETS, "vs_wizard.png"), "wizard"))
    if "--install" in sys.argv:
        os.makedirs(DST, exist_ok=True)
        for n, s in big:
            s.save(os.path.join(DST, n + ".png"))
            print("installed", os.path.join(DST, n + ".png"))


if __name__ == "__main__":
    main()
