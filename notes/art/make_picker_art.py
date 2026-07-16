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
    three trees are exactly these three items. Shield-forward on purpose —
    a rebalanced pass with the weapons dominant was tried and the big
    shield with just the tips and grips peeking out read better."""
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # Both weapon sprites run hilt-bottom-left to business-end-top-right;
    # mirroring the mace turns the pair into an X crossing at the canvas
    # middle, where the shield will sit.
    sword = scaled(vanilla("item/iron_sword.png"), 3)
    mace = scaled(vanilla("item/mace.png").transpose(Image.FLIP_LEFT_RIGHT), 3)
    im.alpha_composite(mace, (8, 8))
    im.alpha_composite(sword, (8, 8))

    # Shield in front, nudged low so the tips read clear above it.
    shield = scaled(shield_face(), 2)
    im.alpha_composite(shield, ((64 - shield.width) // 2, 11))

    save(im, "strength")


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)
    strength()
