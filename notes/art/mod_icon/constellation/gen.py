#!/usr/bin/env python3
"""Archetypes Modrinth icon — CONSTELLATION lens.
A three-branched constellation 'skill tree': root star at bottom, trunk splits
into three star-node branches fanning up; each tip is a chunky hero-star tinted
to one archetype (Brawler red / Seeker blue / Cutpurse green). Family-matched
dark-navy rounded-square bg, chunky NEAREST-upscaled pixel art with dark outline.
"""
from PIL import Image, ImageDraw

# ---- family background constants (sampled from specialities/icon.png) ----
SIZE = 512
GRID = 64                      # art grid; NEAREST x8 -> 512
FILL   = (34, 36, 46, 255)     # dark navy fill
BORDER = (64, 68, 84, 255)     # lighter rim
INSET  = 8                     # px inset of rounded square in 512
BORDER_W = 6
RADIUS = 96

# ---- palette ----
OUT   = (18, 19, 28, 255)      # dark outline (a touch darker than bg)
LINE  = (120, 143, 184, 255)   # constellation line (steel blue)
LINE_D= (60, 74, 108, 255)     # line shadow
CORE  = (245, 248, 255, 255)   # white-hot core
STAR_M= (206, 220, 246, 255)   # pale blue star body
STAR_P= (150, 172, 212, 255)   # star point / small node
FILLER= (150, 172, 212, 255)

# archetype tints: (body, point/deep, glow)
BRAWLER = ((236, 120, 96), (176, 58, 46), (150, 60, 48))    # red/orange - melee
SEEKER  = ((120, 176, 244), (54, 108, 196), (56, 96, 180))  # blue/cyan - arcane
CUTPURSE= ((122, 210, 128), (52, 140, 74), (54, 128, 74))   # green - rogue
GOLD    = ((236, 202, 96), (166, 122, 34), (150, 116, 40))  # warm origin star

def blend(a, b, t):
    return tuple(round(a[i]*(1-t) + b[i]*t) for i in range(3)) + (255,)

def px(img, x, y, c):
    if 0 <= x < GRID and 0 <= y < GRID:
        img.putpixel((x, y), c)

def draw_line(img, a, b, col):
    # thin 1px line, Bresenham
    x0, y0 = a; x1, y1 = b
    dx = abs(x1 - x0); dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1; sy = 1 if y0 < y1 else -1
    err = dx + dy
    pts = []
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1: break
        e2 = 2 * err
        if e2 >= dy: err += dy; x0 += sx
        if e2 <= dx: err += dx; y0 += sy
    return pts

def stamp_star(img, cx, cy, r_spike, r_body, tint=None, glow=False):
    """4-pointed sparkle star. tint=(body,point,glow) or None for pale-blue."""
    if tint:
        body_c = tint[0] + (255,)
        point_c = tint[1] + (255,)
    else:
        body_c = STAR_M
        point_c = STAR_P
    # subtle pixel glow halo (a soft round tinted cloud behind the star)
    if glow and tint and tint[2]:
        gnear = blend(FILL[:3], tint[2], 0.62)
        gfar  = blend(FILL[:3], tint[2], 0.34)
        rg = r_spike + 1
        for dy in range(-rg, rg + 1):
            for dx in range(-rg, rg + 1):
                dist = (dx*dx + dy*dy) ** 0.5
                if dist <= rg + 0.3:
                    if img.getpixel(((cx+dx) % GRID, (cy+dy) % GRID))[3] == 0:
                        px(img, cx+dx, cy+dy, gnear if dist <= rg - 1.2 else gfar)
    mask = {}
    # body diamond
    for dy in range(-r_body, r_body + 1):
        for dx in range(-r_body, r_body + 1):
            if abs(dx) + abs(dy) <= r_body:
                mask[(dx, dy)] = 'body'
    # spikes along axes (taper: width 1 near tip)
    for d in range(-r_spike, r_spike + 1):
        # horizontal spike
        if abs(d) > r_body:
            mask[(d, 0)] = 'point'
            if abs(d) <= r_spike - 2:
                pass
        # vertical spike
        if abs(d) > r_body:
            mask[(0, d)] = 'point'
    # widen inner spikes a touch for chunk
    for d in range(-(r_body+1), r_body+2):
        if abs(d) == r_body + 1:
            mask.setdefault((d, 0), 'point')
            mask.setdefault((0, d), 'point')
    # core
    for dy in range(-1, 2):
        for dx in range(-1, 2):
            if abs(dx) + abs(dy) <= 1:
                mask[(dx, dy)] = 'core'
    # outline: dilate
    outline = set()
    for (dx, dy) in mask:
        for ox, oy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)):
            k = (dx+ox, dy+oy)
            if k not in mask:
                outline.add(k)
    for (dx, dy) in outline:
        px(img, cx+dx, cy+dy, OUT)
    for (dx, dy), kind in mask.items():
        c = {'core': CORE, 'body': body_c, 'point': point_c}[kind]
        px(img, cx+dx, cy+dy, c)

def build_subject():
    img = Image.new('RGBA', (GRID, GRID), (0, 0, 0, 0))

    # Family-mirrored Y: three arms meet at ONE bottom convergence (root),
    # fan up-left / up-center / up-right; center arm highest (like the gold node).
    root = (32, 52)
    # left (Brawler): root -> mid -> tip (upper-left)
    L = [root, (21, 37), (11, 23)]
    # center (Seeker): root -> mid -> tip (top-center, highest)
    C = [root, (32, 35), (32, 16)]
    # right (Cutpurse): root -> mid -> tip (upper-right)
    R = [root, (43, 37), (53, 23)]

    # --- draw connecting lines first (faint shadow underlay + line) ---
    segs = []
    for chain in (L, C, R):
        for i in range(len(chain) - 1):
            segs.append((chain[i], chain[i+1]))
    for a, b in segs:
        for (x, y) in draw_line(img, a, b, LINE_D):
            if img.getpixel((x, (y+1) % GRID))[3] == 0:
                px(img, x, y+1, LINE_D)
    for a, b in segs:
        for (x, y) in draw_line(img, a, b, LINE):
            px(img, x, y, LINE)

    # --- filler night-sky stars (tiny, in the empty corners) ---
    for (fx, fy) in [(9, 46), (55, 46), (44, 12), (20, 12), (7, 27), (57, 27)]:
        px(img, fx, fy, OUT); px(img, fx+1, fy, OUT); px(img, fx-1, fy, OUT)
        px(img, fx, fy+1, OUT); px(img, fx, fy-1, OUT)
        px(img, fx, fy, FILLER)

    # --- glow halos behind hero tips (drawn before nodes) ---
    stamp_star(img, *L[-1], r_spike=5, r_body=3, tint=BRAWLER, glow=True)
    stamp_star(img, *C[-1], r_spike=5, r_body=3, tint=SEEKER, glow=True)
    stamp_star(img, *R[-1], r_spike=5, r_body=3, tint=CUTPURSE, glow=True)

    # --- intermediate nodes ---
    for chain in (L, C, R):
        for n in chain[1:-1]:
            stamp_star(img, n[0], n[1], r_spike=2, r_body=1)
    # --- warm gold origin star at the convergence ---
    stamp_star(img, *root, r_spike=4, r_body=2, tint=GOLD, glow=True)
    # --- hero tips (redraw over glow) ---
    stamp_star(img, *L[-1], r_spike=5, r_body=3, tint=BRAWLER)
    stamp_star(img, *C[-1], r_spike=5, r_body=3, tint=SEEKER)
    stamp_star(img, *R[-1], r_spike=5, r_body=3, tint=CUTPURSE)

    return img

def build_background():
    ss = 4
    S = SIZE * ss
    bg = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    d.rounded_rectangle([INSET*ss, INSET*ss, (SIZE-INSET)*ss-1, (SIZE-INSET)*ss-1],
                        radius=RADIUS*ss, fill=BORDER)
    ins = (INSET + BORDER_W) * ss
    d.rounded_rectangle([ins, ins, S-ins-1, S-ins-1],
                        radius=(RADIUS-BORDER_W)*ss, fill=FILL)
    return bg.resize((SIZE, SIZE), Image.LANCZOS)

def main():
    subj = build_subject().resize((SIZE, SIZE), Image.NEAREST)
    bg = build_background()
    bg.alpha_composite(subj)
    bg.save('icon.png')
    bg.resize((96, 96), Image.LANCZOS).save('preview.png')
    # extra: 96 nearest-from-original for crisp check
    print('wrote icon.png (512) and preview.png (96)')

if __name__ == '__main__':
    main()
