"""Tidy a cut portrait and pack it into its texture.

Three steps, in order, because each one depends on the last:

  1. Drop floating specks. The generator leaves stray embers and dust in the
     backdrop; they survive the key because they are not green. They also drag
     the alpha bounding box out to the frame edges, which would defeat step 2 —
     and in motion they read as pixels trailing the character.
  2. Crop to what is left. The subject only covers ~28% of the generated frame;
     the rest is transparent air that shrinks the character when drawn.
  3. Pad back to a square. The draw box is square, so keeping the character's
     own aspect here is what stops it being stretched on screen.

Usage: python3 frame_portrait.py <in.png> <out.png> [size]
"""
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ALPHA_FLOOR = 8


def largest_island(im):
    """Keep only the biggest connected blob of opacity: the character."""
    alpha = np.array(im.split()[3])
    labels, count = ndimage.label(alpha > ALPHA_FLOOR)

    if count <= 1:
        return im, count, 0

    # Label 0 is the transparent background; find the biggest real one.
    sizes = ndimage.sum(np.ones_like(labels), labels, range(1, count + 1))
    keep = int(np.argmax(sizes)) + 1

    rgba = np.array(im)
    rgba[..., 3] = np.where(labels == keep, rgba[..., 3], 0)
    return Image.fromarray(rgba, "RGBA"), count, count - 1


def frame(path_in, path_out, size=256):
    im = Image.open(path_in).convert("RGBA")
    before = im.split()[3].getbbox()

    im, islands, dropped = largest_island(im)
    box = im.split()[3].getbbox()
    im = im.crop(box)

    # Pad to square around the longer side so nothing is squashed.
    side = max(im.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    square.resize((size, size), Image.LANCZOS).save(path_out, optimize=True)

    aspect = im.width / im.height
    print(f"{path_out}")
    print(f"  islands found {islands}, dropped {dropped} speck(s)")
    print(f"  bbox {before} -> {box}")
    print(f"  subject {im.width}x{im.height}, aspect {aspect:.2f} "
          f"(drawn at box size S: {aspect:.2f}*S wide, S tall)")
    return aspect


if __name__ == "__main__":
    frame(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 256)
