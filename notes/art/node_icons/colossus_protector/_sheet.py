import os, sys
from PIL import Image, ImageDraw
BASE="/Users/german-mac-mini/repos/mc-modding/archetypes/src/main/resources/assets/archetypes/textures/node"
def sheet(paths, out, scale=4, cols=8, bg=(0x8B,0x8B,0x8B)):
    items=[(os.path.basename(p)[:-4], Image.open(p).convert("RGBA")) for p in paths]
    cell=32*scale+8
    rows=(len(items)+cols-1)//cols
    im=Image.new("RGB",(cols*cell, rows*(cell+12)),bg)
    dr=ImageDraw.Draw(im)
    for i,(n,ic) in enumerate(items):
        cx=(i%cols)*cell+4; cy=(i//cols)*(cell+12)+4
        dr.rectangle([cx-2,cy-2,cx+32*scale+1,cy+32*scale+1], fill=(0x30,0x30,0x38))
        big=ic.resize((32*scale,32*scale),Image.NEAREST)
        im.paste(big,(cx,cy),big)
        dr.text((cx,cy+32*scale+2), n[:20], fill=(0,0,0))
    im.save(out); print(out, im.size, len(items))
if __name__=="__main__":
    trees=sys.argv[1].split(',')
    ps=[]
    for t in trees:
        d=t if os.path.isdir(t) else os.path.join(BASE,t)
        ps += sorted(os.path.join(d,f) for f in os.listdir(d) if f.endswith('.png'))
    sheet(ps, sys.argv[2], cols=int(sys.argv[3]) if len(sys.argv)>3 else 8)
