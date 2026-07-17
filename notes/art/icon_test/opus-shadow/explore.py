import zipfile
from PIL import Image
JAR="/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar"
def v(p):
    with zipfile.ZipFile(JAR) as z:
        with z.open("assets/minecraft/textures/"+p) as f:
            return Image.open(f).convert("RGBA").copy()
items=[
 ("item/golden_carrot.png"),("item/glow_ink_sac.png"),("item/sugar.png"),
 ("item/glistering_melon_slice.png"),("item/phantom_membrane.png"),("item/milk_bucket.png"),
 ("item/clock_00.png"),("item/iron_sword.png"),("item/redstone.png"),
 ("item/chainmail_chestplate.png"),("item/totem_of_undying.png"),("item/ender_eye.png"),
 ("item/spider_eye.png"),("block/wither_rose.png"),
 ("mob_effect/night_vision.png"),("mob_effect/glowing.png"),("mob_effect/speed.png"),
 ("mob_effect/strength.png"),("mob_effect/invisibility.png"),("mob_effect/regeneration.png"),
 ("mob_effect/poison.png"),("mob_effect/wither.png"),
 ("gui/sprites/hud/heart/full.png"),
 ("entity/skeleton/skeleton.png"),
]
scale=10
cols=6
cell=18*scale
rows=(len(items)+cols-1)//cols
sheet=Image.new("RGBA",(cols*cell, rows*(cell+scale*2)),(43,43,43,255))
from PIL import ImageDraw
d=ImageDraw.Draw(sheet)
for i,p in enumerate(items):
    im=v(p)
    print(p, im.size)
    # crop first 16x16 frame for animated / large
    big=im.resize((im.width*scale, im.height*scale), Image.NEAREST)
    cx=(i%cols)*cell; cy=(i//cols)*(cell+scale*2)
    sheet.paste(big,(cx+scale,cy+scale),big)
    d.text((cx+2, cy+cell), p.split("/")[-1][:16], fill=(230,230,230,255))
sheet.save("explore.png")
print("saved")
