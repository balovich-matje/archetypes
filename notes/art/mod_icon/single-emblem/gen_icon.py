#!/usr/bin/env python3
"""Archetypes mod icon -- "single emblem" angle.

A single branching symbol for "choose your class": a golden origin gem at the
base, wooden branches (a family callback to Skill Proficiencies' wooden tool
handles) forking up into three class gems -- red (Strength/Brawler), green
(Agility/Cutpurse), blue (Intellect/Seeker), the classic RPG stat triad.

Art authored on a coarse 32px grid, upscaled NEAREST x16 to 512, composited on
the family's dark-navy rounded square (measured from specialities/icon.png:
8px margin, 6px border ring (64,68,84), fill (34,36,46), outer corner radius 96).
"""
from PIL import Image, ImageDraw
import math, os

N = 32                     # coarse art grid
SCALE = 16                 # -> 512
OUTDIR = os.path.dirname(os.path.abspath(__file__))

# ---- family background spec (measured from specialities/icon.png) ----
SIZE = 512
MARGIN = 8
BORDER_W = 6
R_OUT = 96
BORDER = (64, 68, 84, 255)
FILL = (34, 36, 46, 255)

# ---- palette ----
OUT = (24, 24, 24, 255)          # dark outline (family uses 24,24,24)
# wood (family handle tones, boosted contrast so branches read on navy)
WB = (96, 71, 28, 255)           # base
WH = (137, 103, 39, 255)         # bright highlight edge
WS = (52, 38, 14, 255)           # shadow edge

GEM = {
    'gold':  {'O': OUT, 'G': (255, 248, 205, 255), 'H': (255, 226, 120, 255), 'B': (240, 190, 55, 255), 'S': (168, 112, 18, 255)},
    'red':   {'O': OUT, 'G': (255, 200, 180, 255), 'H': (255, 120, 105, 255), 'B': (206, 52, 52, 255),  'S': (112, 22, 28, 255)},
    'green': {'O': OUT, 'G': (205, 255, 175, 255), 'H': (150, 240, 130, 255), 'B': (58, 182, 74, 255),  'S': (22, 100, 42, 255)},
    'blue':  {'O': OUT, 'G': (205, 225, 255, 255), 'H': (160, 195, 255, 255), 'B': (74, 122, 240, 255), 'S': (32, 56, 150, 255)},
}

grid = [[None]*N for _ in range(N)]     # RGBA or None
locked = [[False]*N for _ in range(N)]  # pre-shaded gem cells

def put(x, y, c, lock=False):
    if 0 <= x < N and 0 <= y < N:
        grid[y][x] = c
        if lock:
            locked[y][x] = True

def brush(cx, cy, c, t):
    """stamp a t x t square centered on (cx,cy)."""
    h = t // 2
    for dy in range(-h, t - h):
        for dx in range(-h, t - h):
            put(cx + dx, cy + dy, c)

def branch(p0, p1, t):
    x0, y0 = p0; x1, y1 = p1
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) * 3 + 1
    for i in range(steps + 1):
        u = i / steps
        x = round(x0 + (x1 - x0) * u)
        y = round(y0 + (y1 - y0) * u)
        brush(x, y, WB, t)

def gem(cx, cy, kind, r):
    """diamond (rhombus) cut-gem, radius r (Manhattan), shaded UL->LR + glint."""
    pal = GEM[kind]
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            d = abs(dx) + abs(dy)
            if d > r:
                continue
            if d == r:
                c = pal['O']
            else:
                s = dx + dy  # <0 upper-left lit, >0 lower-right shadow
                if s <= -max(1, r // 2):
                    c = pal['H']
                elif s >= max(1, r // 2):
                    c = pal['S']
                else:
                    c = pal['B']
            put(cx + dx, cy + dy, c, lock=True)
    # glint: brightest cell just inside the upper-left face
    put(cx - 1, cy - 1, pal['G'], lock=True)
    put(cx, cy - 1, pal['H'], lock=True)

# ---- layout (32 grid): solid staff trunk, bold wide-spread gems ----
root = (16, 23)
fork = (16, 15)
tipL = (7, 8)     # red   - Brawler / Strength
tipC = (16, 6)    # blue  - Seeker  / Intellect
tipR = (25, 8)    # green - Cutpurse / Agility

# branches (draw before gems so gems cap the ends). solid limbs + a clean,
# straight 4px trunk so the whole connecting structure reads as one staff.
branch(fork, tipL, 3)
branch(fork, tipC, 3)
branch(fork, tipR, 3)
for yy in range(fork[1], root[1] + 1):
    for dx in (-2, -1, 0, 1):
        put(16 + dx, yy, WB)

# auto-shade wood: highlight on up/left silhouette, shadow on down/right
def is_empty(x, y):
    return not (0 <= x < N and 0 <= y < N) or grid[y][x] is None

shaded = [row[:] for row in grid]
for y in range(N):
    for x in range(N):
        if grid[y][x] == WB and not locked[y][x]:
            if is_empty(x, y-1) or is_empty(x-1, y):
                shaded[y][x] = WH
            elif is_empty(x, y+1) or is_empty(x+1, y):
                shaded[y][x] = WS
grid = shaded

# gems on top (bold, to match the sibling's ink weight)
gem(*root, 'gold', 5)
gem(*tipL, 'red', 5)
gem(*tipC, 'blue', 5)
gem(*tipR, 'green', 5)

# outline pass: empty cell adjacent to a NON-outline subject cell -> outline
add = []
for y in range(N):
    for x in range(N):
        if grid[y][x] is not None:
            continue
        touch = False
        for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
            nx, ny = x+dx, y+dy
            if 0 <= nx < N and 0 <= ny < N and grid[ny][nx] is not None and grid[ny][nx] != OUT:
                touch = True; break
        if touch:
            add.append((x, y))
for x, y in add:
    grid[y][x] = OUT

# ---- render art to 32px image ----
art = Image.new('RGBA', (N, N), (0, 0, 0, 0))
ap = art.load()
for y in range(N):
    for x in range(N):
        if grid[y][x] is not None:
            ap[x, y] = grid[y][x]
art.save(os.path.join(OUTDIR, 'art32.png'))

# upscale NEAREST
subj = art.resize((SIZE, SIZE), Image.NEAREST)

# ---- background: rounded square, border ring + fill ----
def rounded_mask(size, inset, radius):
    m = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([inset, inset, size-1-inset, size-1-inset], radius=radius, fill=255)
    return m

bg = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
outer = rounded_mask(SIZE, MARGIN, R_OUT)
inner = rounded_mask(SIZE, MARGIN + BORDER_W, R_OUT - BORDER_W)
bg.paste(BORDER, (0, 0), outer)
bg.paste(FILL, (0, 0), inner)

# composite subject, clipped to the rounded square
comp = bg.copy()
subj_clipped = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
subj_clipped.paste(subj, (0, 0), outer)
comp = Image.alpha_composite(comp, subj_clipped)

comp.save(os.path.join(OUTDIR, 'icon.png'))
comp.resize((96, 96), Image.LANCZOS).save(os.path.join(OUTDIR, 'preview.png'))
print('wrote icon.png (512), preview.png (96), art32.png')
