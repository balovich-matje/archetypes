"""Bake dim + vignette into the shipped tree backdrops.

Doing it here rather than at runtime: the look was already validated in the
composites, it costs the mod nothing to draw, and there is no per-frame gradient
code to get wrong.
"""
from PIL import Image, ImageDraw, ImageEnhance

SRC = "/Volumes/ADATA SE920 SSD/repos/stable-diffusion.cpp/out"
DST = "/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/assets/archetypes/textures/gui/tree"

def vignette(im, strength=0.85, brightness=0.55):
    w, h = im.size
    im = ImageEnhance.Brightness(im).enhance(brightness).convert('RGBA')
    mask = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(mask)
    for i in range(60):
        f = i / 60
        box = [-w*0.35*(1-f), -h*0.35*(1-f), w-1 + w*0.35*(1-f), h-1 + h*0.35*(1-f)]
        d.ellipse(box, fill=int(255 * (1-f) * strength))
    im.paste(Image.new('RGBA', (w, h), (0, 0, 0, 255)), (0, 0), mask)
    return im

import os
os.makedirs(DST, exist_ok=True)
for name in ("strength", "agility", "intellect"):
    im = Image.open(f"{SRC}/{name}.png").convert('RGBA')
    out = vignette(im)
    out = out.convert('RGB')  # no alpha needed; smaller file
    out.save(f"{DST}/{name}.png", optimize=True)
    print(f"{name}: {out.size} {os.path.getsize(f'{DST}/{name}.png')//1024} KB")
