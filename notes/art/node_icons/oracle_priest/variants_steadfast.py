"""Steadfast was the weak one at 16px — three takes, judged side by side.

A  both iron boots planted on a pool of the aura's light
B  one big iron boot, planted, with the shove stopping either side
C  a gold holy pavise with a cross, planted, shove bouncing off

Usage: python3 variants_steadfast.py
"""
import os

from PIL import Image

import make_oracle_priest_icons as G

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "variants")


def shove(im, y=5):
    for side, x in ((-1, 0), (1, 15)):
        for k in range(3):
            G.put(im, x + side * -k, y + abs(k - 1), G.STEEL)
            G.put(im, x + side * -k, y + abs(k - 1) + 1, G.STEEL_D)


def ground(im, y=13, x0=1, x1=14):
    for x in range(x0, x1):
        G.put(im, x, y, G.GOLD_D)
        G.put(im, x, y + 1, G.OUT)


def var_a():
    im = G.c16()
    G.disc(im, 7, 8, 6.2, G.GOLD_D)
    G.disc(im, 7, 8, 5.0, G.GOLD)
    G.disc(im, 7, 8, 3.4, G.GOLD_L)
    ground(im)
    boots = G.fit(G.van("iron_boots"), 13)
    im.alpha_composite(G.outline(boots, G.OUT), (1, 1))
    shove(im)
    return im


def var_b():
    im = G.c16()
    G.disc(im, 7, 8, 6.2, G.GOLD_D)
    G.disc(im, 7, 8, 5.0, G.GOLD)
    G.disc(im, 7, 8, 3.4, G.GOLD_L)
    ground(im)
    boot = G.van("iron_boots").crop((2, 3, 8, 13)).resize((7, 12), Image.NEAREST)
    im.alpha_composite(G.outline(boot, G.OUT), (4, 1))
    shove(im)
    return im


def var_c():
    im = G.c16()
    o, g, gl, gd, c = G.OUT, G.GOLD, G.GOLD_L, G.GOLD_D, G.CREAM
    rows = [
        " oooooooo ",
        "oggggggggo",
        "ogglccgggo",
        "oggglcgggo",
        "oglcccclgo",
        "oggglcgggo",
        "ogggl cggo",
        "ogggl cggo",
        " ogggggggo",
        "  oggggo  ",
        "   oggo   ",
        "    oo    ",
    ]
    cmap = {"o": o, "g": g, "l": c, "c": gl, " ": None}
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            col = cmap[ch]
            if col:
                G.put(im, 3 + rx, 1 + ry, col)
    shove(im, y=6)
    return im


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (("steadfast_A", var_a), ("steadfast_B", var_b), ("steadfast_C", var_c)):
        G.up(fn()).save(os.path.join(OUT, name + ".png"))
        print(name)
