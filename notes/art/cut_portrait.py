"""Cut the background off a tier portrait, keeping glow.

Two keyers, because the two portraits were shot differently:

  chroma  — for the green-screen shots. Keys on how green a pixel is relative to
            its own red/blue, so it does not care how dark the subject is. This
            matters: netherite is near-black, and a luma key would eat the
            armour along with the backdrop.
  luma    — for the older dark-backdrop shots, where the subject is simply
            brighter than the background.

Both produce *soft* alpha across a band rather than a binary cut, which is what
keeps a glow: an enchantment glimmer is a half-bright halo, so a hard threshold
would either clip it off or leave a rectangle of backdrop around it.

Green screens also spill onto the subject's edges, so chroma mode despills by
pulling green down to the red/blue average where it overshoots.

Usage: python3 cut_portrait.py <in.png> <out.png> [chroma|luma]
"""
import sys

from PIL import Image


def smoothstep(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def chroma_key(im, low=0.06, high=0.28):
    """Alpha from green dominance: g - max(r, b), normalised."""
    px = im.load()
    w, h = im.size
    out = Image.new("RGBA", (w, h))
    op = out.load()

    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            other = max(r, b)
            # How much greener than anything else, 0..1.
            greenness = (g - other) / 255.0
            alpha = 1.0 - smoothstep((greenness - low) / (high - low))

            if alpha <= 0.0:
                op[x, y] = (0, 0, 0, 0)
                continue

            # Despill: green that overshoots its neighbours is backdrop bounce.
            avg = (r + b) // 2
            g2 = min(g, avg + 12) if g > avg + 12 else g
            op[x, y] = (r, g2, b, int(round(alpha * 255)))

    return out


def flood_key(im, thresh=42, feather=1.5):
    """Alpha by flooding inward from the border.

    For a dark subject on a dark backdrop, brightness cannot separate them — but
    contiguity can: the backdrop is one connected flat region touching the edge,
    and the subject is not. So flood from every border pixel and keep whatever the
    flood never reaches. Only valid while the subject does not touch the frame.

    A slight blur on the resulting mask gives the soft edge that a flood, being
    binary, otherwise lacks.
    """
    from PIL import ImageDraw, ImageFilter

    rgb = im.convert("RGB")
    w, h = rgb.size
    marker = (255, 0, 255)
    probe = rgb.copy()

    for x in range(0, w, 3):
        for y in (0, h - 1):
            if probe.getpixel((x, y)) != marker:
                ImageDraw.floodfill(probe, (x, y), marker, thresh=thresh)

    for y in range(0, h, 3):
        for x in (0, w - 1):
            if probe.getpixel((x, y)) != marker:
                ImageDraw.floodfill(probe, (x, y), marker, thresh=thresh)

    mask = Image.new("L", (w, h), 255)
    mp, pp = mask.load(), probe.load()

    for y in range(h):
        for x in range(w):
            if pp[x, y] == marker:
                mp[x, y] = 0

    mask = mask.filter(ImageFilter.GaussianBlur(feather))
    out = rgb.convert("RGBA")
    out.putalpha(mask)
    return out


def luma_key(im, low=0.10, high=0.34):
    """Alpha from brightness. Only safe when the subject is not dark."""
    px = im.load()
    w, h = im.size
    out = Image.new("RGBA", (w, h))
    op = out.load()

    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            alpha = smoothstep((luma - low) / (high - low))
            op[x, y] = (r, g, b, int(round(alpha * 255))) if alpha > 0 else (0, 0, 0, 0)

    return out


def report(im, label):
    w, h = im.size
    px = im.load()
    edge = [px[x, 0][3] for x in range(0, w, 8)] + [px[x, h - 1][3] for x in range(0, w, 8)] \
        + [px[0, y][3] for y in range(0, h, 8)] + [px[w - 1, y][3] for y in range(0, h, 8)]
    opaque = sum(1 for y in range(0, h, 4) for x in range(0, w, 4) if px[x, y][3] > 200)
    total = len(range(0, h, 4)) * len(range(0, w, 4))
    print(f"{label}: border alpha max={max(edge)} (want ~0), "
          f"subject covers {100 * opaque // total}% of frame")


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    mode = sys.argv[3] if len(sys.argv) > 3 else "chroma"

    im = Image.open(src).convert("RGBA")
    keyers = {"chroma": chroma_key, "luma": luma_key, "flood": flood_key}
    out = keyers[mode](im)
    out.save(dst)
    report(out, f"{dst} [{mode}]")
