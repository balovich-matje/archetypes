"""Generate the archetype picker portraits.

One image per archetype, a heraldic collage of its three sub-archetype
weapons — for strength: the Protector's shield front and center, the
Slayer's sword and the Crusher's mace crossed behind it. Composed from
vanilla sprites at integer scales on a small working canvas, then
NEAREST-upscaled to the 256px the picker screen samples, so the pixel
grid stays honest.

Usage: python3 make_picker_art.py
"""
import os
import zipfile

from PIL import Image

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
ASSETS = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/assets"))
DST = os.path.join(ASSETS, "archetypes/textures/gui/picker")

# The screen's PORTRAIT_TEXTURE constant.
OUT_SIZE = 256


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def shield_face():
    """The vanilla shield's front plate, cut from the entity texture — the
    only flat pixels of the real shield that exist (the item icon is a 3D
    render)."""
    return vanilla("entity/shield/shield_base_nopattern.png").crop((0, 0, 13, 23))


def scaled(im, factor):
    return im.resize((im.width * factor, im.height * factor), Image.NEAREST)


def save(im, name):
    im.resize((OUT_SIZE, OUT_SIZE), Image.NEAREST).save(os.path.join(DST, f"{name}.png"))
    print(f"{name}.png")


def strength():
    """Crossed sword and mace behind the shield: the crest of a class whose
    three trees are exactly these three items. The shield is deliberately a
    scale step smaller than the weapons — at parity it swallows the X and
    the collage reads as "a shield" instead of "three arms"."""
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))

    # Both weapon sprites run hilt-bottom-left to business-end-top-right;
    # mirroring the mace turns the pair into an X crossing at the canvas
    # middle, where the shield will sit.
    sword = scaled(vanilla("item/iron_sword.png"), 6)
    mace = scaled(vanilla("item/mace.png").transpose(Image.FLIP_LEFT_RIGHT), 6)
    im.alpha_composite(mace, (16, 16))
    im.alpha_composite(sword, (16, 16))

    # Shield in front, nudged low so the tips read clear above it.
    shield = scaled(shield_face(), 3)
    im.alpha_composite(shield, ((128 - shield.width) // 2, 31))

    save(im, "strength")


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)
    strength()
