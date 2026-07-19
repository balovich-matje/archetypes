import sys, os
sys.path.insert(0, '/Users/german-mac-mini/repos/mc-modding/archetypes/notes/art/node_icons/oracle_elementalist')
from PIL import Image, ImageDraw
from make_icons import (grid, outline, stamp, PAL, COLD_OUTLINE, paste_art,
                        BOLT_BIG, BOLT_MID, BOLT_SMALL)

def ring(inner, outer, colour, cx=7.5, cy=7.5, skip=None):
    im = Image.new("RGBA", (16,16), (0,0,0,0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = ((x-cx)**2 + (y-cy)**2) ** 0.5
            if inner <= d <= outer:
                if skip and skip(x,y): continue
                px[x,y] = colour
    return im

# A: repeat-arrow ring around a bolt
def var_a():
    r = ring(4.4, 5.4, PAL["G"], skip=lambda x,y: x>=8 and y<=5)
    im = outline(r)
    head = grid([
        "................",
        "................",
        "........GG......",
        "........GGG.....",
        ".........GGG....",
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
        "................",
    ])
    im.alpha_composite(outline(head), (0,0))
    b = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(b, BOLT_MID, 6, 4)
    im.alpha_composite(outline(b), (0,0))
    return im

# B: echo stack -- same bolt, three afterimages offset
def var_b():
    back = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(back, BOLT_BIG, 0, 4)
    for y in range(16):
        for x in range(16):
            if back.getpixel((x,y))[3]: back.putpixel((x,y), PAL["E"])
    mid = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(mid, BOLT_BIG, 4, 3)
    for y in range(16):
        for x in range(16):
            if mid.getpixel((x,y))[3]: mid.putpixel((x,y), PAL["D"])
    front = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(front, BOLT_BIG, 8, 2)
    im = outline(back)
    im.alpha_composite(outline(mid),(0,0))
    im.alpha_composite(outline(front),(0,0))
    return im

# C: one bolt, three impact flashes stacked on the same spot
def var_c():
    b = Image.new("RGBA",(16,16),(0,0,0,0)); paste_art(b, BOLT_BIG, 5, 0)
    im = outline(b)
    rings = grid([
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
        "....SS....SS....",
        "................",
        "..SSSS....SSSS..",
        "................",
        "SSSSSSSSSSSSSSSS",
        "................",
    ])
    im.alpha_composite(outline(rings),(0,0))
    return im

# D: bolt + two smaller repeat bolts stacked vertically on one point
def var_d():
    b = Image.new("RGBA",(16,16),(0,0,0,0))
    paste_art(b, BOLT_BIG, 5, 1)
    im = outline(b)
    ech = Image.new("RGBA",(16,16),(0,0,0,0))
    paste_art(ech, BOLT_SMALL, 1, 3)
    paste_art(ech, BOLT_SMALL, 12, 3)
    for y in range(16):
        for x in range(16):
            if ech.getpixel((x,y))[3]: ech.putpixel((x,y), PAL["D"])
    im2 = outline(ech)
    im2.alpha_composite(im,(0,0))
    flash = grid([
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
        "................",
        "................",
        ".....WWWWWW.....",
        "....WWWWWWWW....",
        "................",
        "................",
    ])
    im2.alpha_composite(outline(flash),(0,0))
    return im2

vs = [("A ring", var_a()), ("B echo", var_b()), ("C rings", var_c()), ("D trio", var_d())]
big = [(n, s.resize((32,32), Image.NEAREST)) for n,s in vs]
# 4x + 16px sheet
cell, pad = 128, 12
im = Image.new("RGBA", (len(big)*(cell+pad)+pad, cell+pad*2+40+20), (139,139,139,255))
d = ImageDraw.Draw(im)
for i,(n,s) in enumerate(big):
    x = pad+i*(cell+pad)
    d.rectangle([x-2,pad-2,x+cell+1,pad+cell+1], fill=(38,38,44,255))
    im.alpha_composite(s.resize((cell,cell), Image.NEAREST), (x,pad))
    d.text((x, pad+cell+2), n, fill=(20,20,20,255))
    small = s.resize((16,16), Image.BOX).resize((64,64), Image.NEAREST)
    im.alpha_composite(small, (x, pad+cell+16))
im.save('sheets/variants_recurrence.png')
print('ok')
