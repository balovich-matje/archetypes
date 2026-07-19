import sys
sys.path.insert(0, '/Users/german-mac-mini/repos/mc-modding/archetypes/notes/art/node_icons/oracle_elementalist')
from PIL import Image, ImageDraw
from make_icons import grid, outline, stamp, PAL, paste_art, BOLT_BIG, BOLT_MID, BOLT_SMALL

def ring(inner, outer, colour, cx=7.5, cy=7.5, skip=None):
    im = Image.new("RGBA", (16,16), (0,0,0,0)); px = im.load()
    for y in range(16):
        for x in range(16):
            d = ((x-cx)**2 + (y-cy)**2) ** 0.5
            if inner <= d <= outer and not (skip and skip(x,y)):
                px[x,y] = colour
    return im

def var_a2():
    r = ring(4.6, 5.6, PAL["G"], skip=lambda x,y: (x>=8 and y<=4))
    im = outline(r)
    head = grid([
        "................",
        "................",
        ".......GGG......",
        ".......GGGG.....",
        "........GGGG....",
        ".........GG.....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    im.alpha_composite(outline(head), (0,0))
    b = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(b, BOLT_MID, 6, 4)
    im.alpha_composite(outline(b), (0,0))
    return im

def var_e():
    """Tight strobe: bolt with two afterimages 2px behind, one impact flash."""
    back = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(back, BOLT_BIG, 2, 0)
    mid  = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(mid,  BOLT_BIG, 5, 1)
    front= Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(front,BOLT_BIG, 8, 2)
    for im_, c in ((back, PAL["E"]), (mid, PAL["D"])):
        for y in range(16):
            for x in range(16):
                if im_.getpixel((x,y))[3]: im_.putpixel((x,y), c)
    im = outline(back)
    im.alpha_composite(outline(mid),(0,0))
    im.alpha_composite(outline(front),(0,0))
    flash = grid([
        "................","................","................","................",
        "................","................","................","................",
        "................","................","................",
        "................",
        "....WWWWWWWW....",
        "...WWWWWWWWWW...",
        "................","................",
    ])
    im.alpha_composite(outline(flash),(0,0))
    return im

def var_f():
    """One bolt driving into a target ring on the ground, struck 3x: the bolt
    plus two tight afterimages directly over the same point."""
    ech = Image.new("RGBA",(16,16),(0,0,0,0))
    paste_art(ech, BOLT_BIG, 3, 1)
    paste_art(ech, BOLT_BIG, 7, 1)
    for y in range(16):
        for x in range(16):
            if ech.getpixel((x,y))[3]: ech.putpixel((x,y), PAL["D"])
    front = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(front, BOLT_BIG, 5, 0)
    im = outline(ech)
    im.alpha_composite(outline(front),(0,0))
    tgt = grid([
        "................","................","................","................",
        "................","................","................","................",
        "................","................","................",
        "................","................",
        "....SS....SS....",
        "..SS........SS..",
        "................",
    ])
    im.alpha_composite(outline(tgt),(0,0))
    return im

def var_g():
    """Ring of gold, bolt inside, no arrowhead -- a sigil."""
    r = ring(4.6, 5.6, PAL["G"])
    im = outline(r)
    b = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(b, BOLT_MID, 6, 4)
    im.alpha_composite(outline(b), (0,0))
    return im

vs = [("A2 arrow ring", var_a2()), ("E strobe", var_e()), ("F same spot", var_f()), ("G sigil", var_g())]
big = [(n, s.resize((32,32), Image.NEAREST)) for n,s in vs]
cell, pad = 128, 12
im = Image.new("RGBA", (len(big)*(cell+pad)+pad, cell+pad*2+40+20), (139,139,139,255))
d = ImageDraw.Draw(im)
for i,(n,s) in enumerate(big):
    x = pad+i*(cell+pad)
    d.rectangle([x-2,pad-2,x+cell+1,pad+cell+1], fill=(38,38,44,255))
    im.alpha_composite(s.resize((cell,cell), Image.NEAREST), (x,pad))
    d.text((x, pad+cell+2), n, fill=(20,20,20,255))
    im.alpha_composite(s.resize((16,16), Image.BOX).resize((64,64), Image.NEAREST), (x, pad+cell+16))
im.save('sheets/variants_recurrence2.png'); print('ok')
