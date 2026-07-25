"""Generate the Protector tree's two spear node icons at 32px.

Both are composed from art already in the game or already in the tree's own
set, for the same reason every other icon here is: the palettes match without
anyone matching them by hand.

- spearwall   — a shield with an iron spear levelled across it. The node IS
                "shield up and spear out", so the icon is the two pictures
                held together, spear passing behind the rim on both sides.
- ground_slam — redrawn for the rework. The old one was a shockwave ring and
                the ability is not a ring any more; it is two flanking spears
                thrusting past the caster, so the icon is a converging pair
                with the shield small between them.

The plain shield is lifted from unbreaking.png, which is the one icon in the
set carrying a shield and nothing else (braced.png has a spark emblem beside
it that would read as clutter under a spear).

Usage: python3 make_spear_icons.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
ASSETS = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/assets"))
DST = os.path.join(ASSETS, "archetypes/textures/node/protector")

# The shield alone. unbreaking.png's alpha bbox runs to y=30, but rows 28-29
# are the little green durability bar that node draws under it — cropping at
# 26 leaves the shield and drops the bar, which would otherwise ride along
# into both new icons and read as a health bar on a spear.
SHIELD_BOX = (9, 3, 22, 26)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def save(im, name):
    im.save(os.path.join(DST, f"{name}.png"))
    print("wrote", os.path.join(DST, f"{name}.png"))


def shield(height):
    """The plain shield, scaled to a target height and kept in proportion."""
    src = Image.open(os.path.join(DST, "unbreaking.png")).convert("RGBA").crop(SHIELD_BOX)
    width = max(1, round(src.width * height / src.height))
    return src.resize((width, height), Image.NEAREST)


def spear(length, angle):
    """The vanilla iron spear, scaled square then rotated.

    The source art lies at 45 degrees pointing up-right, so `angle` here is
    measured from that: -45 lays it flat, -30 leaves the shallow upward tilt a
    levelled spear actually has.
    """
    src = vanilla("item/iron_spear.png").resize((length, length), Image.NEAREST)
    return src.rotate(angle, resample=Image.NEAREST, expand=True)


def centred(canvas, im, dx=0, dy=0):
    canvas.alpha_composite(
        im, ((canvas.width - im.width) // 2 + dx, (canvas.height - im.height) // 2 + dy))


def spearwall():
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # Spear first: it passes BEHIND the shield, which is what sells the rim as
    # something the shaft rests on rather than a sticker over it.
    centred(out, spear(30, -32), 0, -2)
    centred(out, shield(24), 0, 3)
    save(out, "spearwall")


def ground_slam():
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    right = spear(21, -20)
    left = right.transpose(Image.FLIP_LEFT_RIGHT)
    centred(out, left, -9, -3)
    centred(out, right, 9, -3)
    # The caster stays in the picture, small, between the two that appear
    # beside them.
    centred(out, shield(17), 0, 4)
    save(out, "ground_slam")


if __name__ == "__main__":
    spearwall()
    ground_slam()
