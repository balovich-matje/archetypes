"""Bake dim + vignette + edge feather into the shipped tree backdrops.

Doing it here rather than at runtime: the look is fixed, it costs the mod
nothing to draw, and there is no per-frame gradient code to get wrong.

The edge feather is the important part. The screen draws the art at 85% centred
on black, so without it the texture ends on a hard rectangular cut against the
black canvas. Fading the borders to *pure* black makes that boundary vanish —
the art has to reach 0, not merely "dark", or the seam still shows.

Usage: python3 bake_art.py
"""
import os

from PIL import Image, ImageChops, ImageDraw, ImageEnhance

SRC = "/Volumes/ADATA SE920 SSD/repos/stable-diffusion.cpp/out"
DST = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "../../src/main/resources/assets/archetypes/textures/gui/tree"))

BRIGHTNESS = 0.55
VIGNETTE_STRENGTH = 0.85
FEATHER = 0.18  # fraction of the short edge spent fading out


def smoothstep(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def radial_vignette(im, strength=VIGNETTE_STRENGTH):
    """Darken toward the corners — mood, not the edge blend."""
    w, h = im.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)

    for i in range(60):
        f = i / 60
        box = [-w * 0.35 * (1 - f), -h * 0.35 * (1 - f),
               w - 1 + w * 0.35 * (1 - f), h - 1 + h * 0.35 * (1 - f)]
        d.ellipse(box, fill=int(255 * (1 - f) * strength))

    im.paste(Image.new("RGBA", (w, h), (0, 0, 0, 255)), (0, 0), mask)
    return im


def edge_feather(im, fraction=FEATHER):
    """Multiply toward black over a soft band at all four borders."""
    w, h = im.size
    band = max(1, int(min(w, h) * fraction))
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)

    # Outermost ring first; inner rings overwrite, so the interior ends at 255
    # and each ring carries its eased value.
    for i in range(band + 1):
        d.rectangle([i, i, w - 1 - i, h - 1 - i], fill=int(255 * smoothstep(i / band)))

    rgb = im.convert("RGB")
    return ImageChops.multiply(rgb, Image.merge("RGB", (mask, mask, mask)))


def bake(name):
    im = Image.open(f"{SRC}/{name}.png").convert("RGBA")
    im = ImageEnhance.Brightness(im).enhance(BRIGHTNESS).convert("RGBA")
    im = radial_vignette(im)
    out = edge_feather(im)

    path = f"{DST}/{name}.png"
    out.save(path, optimize=True)
    print(f"{name}: {out.size} {os.path.getsize(path) // 1024} KB  corner={out.getpixel((0, 0))}")


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)

    for n in ("strength", "agility", "intellect"):
        bake(n)
