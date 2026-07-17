import zipfile
from PIL import Image
JAR="/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar"
def v(p):
    with zipfile.ZipFile(JAR) as z:
        with z.open("assets/minecraft/textures/"+p) as f:
            return Image.open(f).convert("RGBA").copy()
# creeper head front face: front is at (8,8)-(16,16) in the 64x32 head layout
cre=v("entity/creeper/creeper.png")
cre_front=cre.crop((8,8,16,16))
# skeleton head front face same layout
sk=v("entity/skeleton/skeleton.png")
sk_front=sk.crop((8,8,16,16))
scale=20
from PIL import ImageDraw
sheet=Image.new("RGBA",(8*scale*3, 8*scale+40),(43,43,43,255))
d=ImageDraw.Draw(sheet)
for i,(im,name) in enumerate([(cre_front,"creeper_face"),(sk_front,"skull_face"),(v("mob_effect/night_vision.png"),"nv")]):
    big=im.resize((im.width*scale, im.height*scale), Image.NEAREST)
    sheet.paste(big,(i*8*scale,0),big)
    d.text((i*8*scale+4, 8*scale+10), name, fill=(230,230,230,255))
sheet.save("explore2.png")
# also print skull face pixels to understand
print("skull face size", sk_front.size)
