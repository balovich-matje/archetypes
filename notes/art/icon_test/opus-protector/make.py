"""Skill-node icons for the Protector tree (sword-and-board tank).

Every icon starts from a real vanilla sprite pulled from the client jar — the
wooden shield front-plate (the flat pixels of the actual Items.SHIELD render,
cut from the entity texture) is the recurring hero, matching what the tree
draws under most Protector nodes in-game. On top of it sits a small readable
mechanic grammar: + = more, ghost copies = duplicate/omni, speed dashes =
faster/lunge, an arc = wide, a launched target = knockback, a clock = cooldown,
spikes = thorns, a bounced arrow = reflect, a green bar = durability kept,
cracked ground = the ground slam. Where a vanilla sprite already IS the grammar
(strength = +damage, clock = cooldown, arrow = projectile, wind_charge = the
dash, resistance's blue shield = a warded aura) it is composed in directly.

Renders 32x32 icons (16px art upscaled 2x is the norm; the shield-based ones are
composed at native entity-texture scale which already sits at item-icon size),
a labeled contact sheet, and nothing outside this folder.

Usage: python3 make.py
"""
import os
import zipfile

from PIL import Image, ImageDraw

JAR = ("/Users/german-mac-mini/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
       "minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.path.join(HERE, "icons")

# ---------------------------------------------------------------- palette ----
# Iron rim / boss, sampled off the shield plate then brightened for the boss.
IRON_HI = (214, 216, 224, 255)
IRON = (163, 164, 170, 255)
IRON_MID = (145, 147, 155, 255)
IRON_DK = (104, 106, 114, 255)
# Motion / arc / impact.
ARC = (236, 236, 238, 255)
ARC_DIM = (156, 158, 166, 210)
WHITE = (255, 255, 255, 255)
IMP = (255, 232, 150, 255)      # impact yellow
IMP_DK = (232, 150, 40, 255)
# States.
RED = (206, 42, 42, 255)
RED_DK = (150, 24, 24, 255)
RED_LT = (240, 96, 96, 255)
GREEN = (104, 202, 78, 255)
GREEN_DK = (54, 140, 52, 255)
GREEN_LT = (158, 236, 118, 255)
# Resistance-blue aura (warded block).
AURA = (126, 168, 232, 255)
AURA_DK = (74, 112, 190, 255)
AURA_LT = (188, 214, 255, 255)


def vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        with z.open(f"assets/minecraft/textures/{path}") as f:
            return Image.open(f).convert("RGBA").copy()


def canvas(size=32):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def faded(im, alpha):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r, g, b, a * alpha // 255)
    return out


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def blend(im, x, y, c):
    """Alpha-composite a single pixel (for translucent effect dabs)."""
    if 0 <= x < im.width and 0 <= y < im.height:
        base = Image.new("RGBA", (1, 1), im.getpixel((x, y)))
        base.alpha_composite(Image.new("RGBA", (1, 1), c))
        im.putpixel((x, y), base.getpixel((0, 0)))


# ------------------------------------------------------------- the shield ----
def plate():
    """The vanilla shield's wooden front-plate (13x23), with a hand-plotted
    iron boss punched through the middle so the flat plate reads as a shield
    rather than a plank."""
    im = vanilla("entity/shield/shield_base_nopattern.png").crop((0, 0, 13, 23))
    # Central iron boss: a little domed stud, light top-left, dark bottom-right.
    boss = {
        (6, 9): IRON_HI,
        (5, 10): IRON, (6, 10): IRON_HI, (7, 10): IRON,
        (4, 11): IRON, (5, 11): IRON_HI, (6, 11): IRON, (7, 11): IRON, (8, 11): IRON_DK,
        (5, 12): IRON, (6, 12): IRON, (7, 12): IRON_DK,
        (6, 13): IRON_DK,
    }
    for (x, y), c in boss.items():
        im.putpixel((x, y), c)
    return im


PW, PH = 13, 23  # plate size


def place_plate(im, ox, oy, alpha=255):
    p = plate()
    if alpha != 255:
        p = faded(p, alpha)
    im.alpha_composite(p, (ox, oy))


# --------------------------------------------------------------- grammar ----
def impact(im, cx, cy, big=False):
    """A struck-metal burst: bright core, plus-spikes, diagonal sparks."""
    put(im, cx, cy, WHITE)
    for d in (1, 2):
        for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
            put(im, cx + dx, cy + dy, IMP if d == 1 else IMP_DK)
    for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        put(im, cx + dx, cy + dy, IMP)
    if big:
        for dx, dy in ((3, 0), (-3, 0), (0, 3), (0, -3),
                       (2, 2), (-2, 2), (2, -2), (-2, -2)):
            put(im, cx + dx, cy + dy, IMP_DK)


def speed_dashes(im, xs, ys, toward_right=True):
    """Tapering motion streaks; brightest at the leading (toward) end."""
    for y in ys:
        for i, x in enumerate(xs):
            lead = (i == len(xs) - 1) if toward_right else (i == 0)
            put(im, x, y, ARC if (lead or i >= len(xs) - 2) else ARC_DIM)


def chevron_right(im, tx, ty, c=WHITE, cdk=IRON_DK):
    """A bold '>' arrowhead with its tip at (tx, ty)."""
    for i in range(3):
        put(im, tx - i, ty - i - 1, c)
        put(im, tx - i, ty + i + 1, c)
    put(im, tx, ty, c)
    for i in range(3):
        put(im, tx - i - 1, ty - i - 1, cdk)
        put(im, tx - i - 1, ty + i + 1, cdk)


# =============================================================== ICONS ======
def bash():
    """Shield Bash (active): the shield shoved forward — motion streaks trailing
    behind it, an impact bursting off its leading face."""
    im = canvas()
    place_plate(im, 5, 5)
    speed_dashes(im, [0, 1, 2, 3, 4, 5], [9], toward_right=True)
    speed_dashes(im, [0, 1, 2, 3, 4, 5], [16], toward_right=True)
    speed_dashes(im, [0, 1, 2, 3, 4, 5], [23], toward_right=True)
    impact(im, 22, 15, big=True)
    save(im, "bash")


def slam():
    """Shield Slam (+% bash damage): vanilla's Strength icon — the game's own
    '+attack damage' mark — rising behind the shield, with a bold '+'."""
    im = canvas()
    strength = vanilla("mob_effect/strength.png")
    im.alpha_composite(strength, (13, -3))     # blade + sparkle peek top-right
    place_plate(im, 4, 6)
    impact(im, 17, 15, big=False)
    # A bold '+' = MORE, up in the corner.
    for x in range(24, 29):
        put(im, x, 4, IMP)
    for y in range(2, 7):
        put(im, 26, y, IMP)
    put(im, 26, 4, WHITE)
    save(im, "slam")


def cooldown():
    """Quick Recovery (bash cooldown down): vanilla clock, a green down-arrow
    dropping the timer."""
    im = canvas()
    clock = vanilla("item/clock_00.png").resize((20, 20), Image.NEAREST)
    im.alpha_composite(clock, (1, 6))
    # Green down-arrow on the right: shorter cooldown.
    for y in range(6, 20):
        put(im, 25, y, GREEN)
        put(im, 26, y, GREEN_DK)
    for i in range(4):
        put(im, 25 - i, 19 - i, GREEN)
        put(im, 26 + i, 19 - i, GREEN)
        put(im, 25 - i, 18 - i, GREEN_LT)
    put(im, 25, 21, GREEN)
    put(im, 26, 21, GREEN)
    put(im, 25, 20, GREEN_LT)
    put(im, 26, 20, GREEN_LT)
    save(im, "cooldown")


def knockback():
    """Concussive Blow (huge shove): the shield launches a target clear off the
    right edge, a bold arrow and trails chasing it."""
    im = canvas()
    place_plate(im, 2, 5)
    # Shove arrow off the shield's face.
    for x in range(16, 23):
        put(im, x, 15, IRON_HI)
        put(im, x, 16, IRON)
    chevron_right(im, 25, 15, c=WHITE, cdk=IRON_DK)
    chevron_right(im, 25, 16, c=IRON_HI, cdk=IRON_DK)
    # The launched enemy, tumbling off the edge with motion trails.
    figure = {(28, 12): RED_DK, (29, 12): RED, (28, 13): RED, (29, 13): RED_LT,
              (28, 14): RED, (29, 14): RED_DK}
    for (x, y), c in figure.items():
        put(im, x, y, c)
    for x, y in ((26, 11), (24, 10), (26, 18), (24, 19)):
        put(im, x, y, ARC_DIM)
    save(im, "knockback")


def wide():
    """Wide Swings (bash hits everyone in front): a broad sweep arc thrown over
    the shield, several targets struck along it."""
    im = canvas()
    place_plate(im, 9, 8)
    arc = [(2, 6), (4, 4), (6, 3), (9, 2), (12, 2), (15, 2), (18, 2), (21, 2),
           (24, 3), (26, 4), (28, 6)]
    # fill between the sampled points, 2px thick.
    pts = []
    for i in range(len(arc) - 1):
        (x0, y0), (x1, y1) = arc[i], arc[i + 1]
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for s in range(steps + 1):
            x = round(x0 + (x1 - x0) * s / steps)
            y = round(y0 + (y1 - y0) * s / steps)
            pts.append((x, y))
    for x, y in pts:
        put(im, x, y, WHITE)
        put(im, x, y + 1, ARC_DIM)
    # Targets struck along the arc.
    for x, y in ((4, 3), (15, 0), (27, 3)):
        put(im, x, y, RED)
        put(im, x - 1, y, RED_DK)
        put(im, x + 1, y, RED_DK)
        put(im, x, y - 1, RED_LT)
    save(im, "wide")


def unbreaking():
    """Reinforced Straps (shield takes half durability): bright iron rivets bolt
    the plate, and a full green durability bar rides underneath it."""
    im = canvas()
    place_plate(im, 9, 3)
    # A clean horizontal iron reinforcing band across the plate, riveted.
    for x in range(10, 21):
        put(im, x, 13, IRON_HI)
        put(im, x, 14, IRON_DK)
    for x in (11, 15, 19):
        put(im, x, 13, WHITE)          # rivet highlights on the band
    # Rivets at the plate's corners too.
    for x, y in ((11, 6), (19, 6), (11, 21), (19, 21)):
        put(im, x, y, IRON_HI)
        put(im, x, y + 1, IRON_DK)
    # Vanilla-style durability bar under the shield: dark base, full green.
    for x in range(9, 22):
        put(im, x, 29, (0, 0, 0, 255))
        put(im, x, 28, GREEN)
    for x in (9, 10, 11):
        put(im, x, 28, GREEN_LT)
    save(im, "unbreaking")


def spikes():
    """Iron Spikes (Thorns on a raised shield): iron spikes jut from every edge,
    one tip already wet where an attacker ran onto it."""
    im = canvas()
    place_plate(im, 9, 5)

    def spike(bx, by, dx, dy):
        # 3-long spike from the rim outward; bright tip.
        for i, c in enumerate((IRON_DK, IRON, IRON_HI)):
            put(im, bx + dx * i, by + dy * i, c)
        # give it a little width at the base
        put(im, bx - dy, by - dx, IRON_MID)

    for y in (9, 15, 21):                     # right edge
        spike(22, y, 1, 0)
    for y in (9, 15, 21):                     # left edge
        spike(9, y, -1, 0)
    for x in (13, 18):                        # top edge
        spike(x, 5, 0, -1)
    # blood on the top-right spike tip
    put(im, 24, 9, RED)
    put(im, 25, 9, RED_DK)
    put(im, 24, 8, RED_LT)
    save(im, "spikes")


def rush():
    """Shield Rush (lunge while blocking): the shield charges, a wind-charge puff
    at its back and long speed streaks streaming off it."""
    im = canvas()
    wind = faded(vanilla("item/wind_charge.png"), 200)
    im.alpha_composite(wind, (-3, 8))
    place_plate(im, 14, 5)
    # Long tapering streaks trailing the charge.
    for y in (10, 16, 22):
        for x in range(0, 13):
            lead = x >= 10
            near = x >= 6
            put(im, x, y, ARC if lead else (ARC_DIM if near else (170, 172, 180, 130)))
    save(im, "rush")


def braced():
    """Braced (blocked hits shave the cooldown): a hit sparks off the raised
    shield, and a clock in the corner ticks down for it."""
    im = canvas()
    place_plate(im, 4, 5)
    # A strike sparking off the shield face.
    impact(im, 12, 14)
    # Small clock, top-right corner.
    clock = vanilla("item/clock_00.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(clock, (19, 0))
    # green down-tick on the clock = time given back
    for i in range(3):
        put(im, 25 - i, 11 - i, GREEN)
        put(im, 25 + i, 11 - i, GREEN)
    put(im, 25, 12, GREEN_LT)
    save(im, "braced")


def reflect():
    """Reflection (blocked projectiles fly back): an arrow strikes the shield and
    a second ricochets away, a spark at the point of contact."""
    im = canvas()
    place_plate(im, 8, 5)
    # Contact point on the shield's leading (right) face.
    cx, cy = 21, 16
    # Incoming arrow: a dim shaft driving in from the lower-right toward contact.
    for i in range(1, 6):
        put(im, cx + i + 1, cy + i + 1, ARC_DIM if i > 2 else IRON)
    put(im, 28, 23, IRON_DK)
    put(im, 27, 24, IRON_DK)
    # Ricochet arrow flying back up-right — bright, with a clear arrowhead.
    shaft = [(cx + 1, cy - 1), (cx + 2, cy - 2), (cx + 3, cy - 3), (cx + 4, cy - 4),
             (cx + 5, cy - 5), (cx + 6, cy - 6)]
    for x, y in shaft:
        put(im, x, y, WHITE)
        put(im, x, y + 1, ARC_DIM)
    hx, hy = cx + 6, cy - 6            # arrowhead tip
    put(im, hx, hy, WHITE)
    put(im, hx - 2, hy, WHITE)
    put(im, hx - 1, hy, ARC)
    put(im, hx, hy + 2, WHITE)
    put(im, hx, hy + 1, ARC)
    # Bright ping where it bounces.
    impact(im, cx, cy)
    save(im, "reflect")


def taunt():
    """Taunt (every monster turns to attack you): a red aggro mark over the
    shield, enemies converging from all four corners."""
    im = canvas()
    place_plate(im, 10, 7)
    # Converging chevrons pointing inward at the shield.
    def chevron(cx, cy, dx, dy):
        put(im, cx, cy, RED)
        put(im, cx + dy, cy + dx, RED_DK)
        put(im, cx - dy, cy - dx, RED_DK)
        put(im, cx - dx, cy - dy, RED)
        put(im, cx - dx + dy, cy - dy + dx, RED_LT)
        put(im, cx - dx - dy, cy - dy - dx, RED_LT)
    chevron(4, 6, 1, 1)       # top-left -> in
    chevron(28, 6, -1, 1)     # top-right -> in
    chevron(4, 26, 1, -1)     # bottom-left -> in
    chevron(28, 26, -1, -1)   # bottom-right -> in
    # Bold red '!' rising off the shield.
    for y in range(0, 4):
        put(im, 16, y, RED)
        put(im, 17, y, RED_LT)
    put(im, 16, 5, RED)
    put(im, 17, 5, RED)
    save(im, "taunt")


def omni_block():
    """Bulwark (capstone: block from every direction): the shield flanked by two
    warded ghost shields, a resistance-blue aura ringing the whole guard."""
    im = canvas()
    # Blue warded ghost shields left and right (vanilla resistance sprite).
    res = vanilla("mob_effect/resistance.png").resize((13, 13), Image.NEAREST)
    im.alpha_composite(faded(res, 150), (0, 9))
    im.alpha_composite(faded(res, 150), (19, 9))
    place_plate(im, 10, 5)
    # Aura ring — dots all the way round the guard.
    ring = [(16, 0), (22, 1), (27, 4), (30, 9), (31, 16), (30, 23),
            (27, 28), (22, 30), (16, 31), (10, 30), (5, 28), (2, 23),
            (1, 16), (2, 9), (5, 4), (10, 1)]
    for i, (x, y) in enumerate(ring):
        put(im, x, y, AURA_LT if i % 2 == 0 else AURA)
        # faint inner glow
        put(im, x + (1 if x < 16 else -1), y, AURA_DK)
    save(im, "omni_block")


def ground_slam():
    """Ground Slam (capstone: the bash becomes an AoE): the shield driven down,
    the ground cracking open and a shockwave rolling out to both sides."""
    im = canvas()
    place_plate(im, 10, 1)
    # Cracked ground.
    ground = (108, 76, 44, 255)
    ground_dk = (74, 52, 30, 255)
    crack = (44, 30, 16, 255)
    for x in range(0, 32):
        put(im, x, 27, ground)
        put(im, x, 28, ground_dk)
        put(im, x, 29, ground_dk)
    for x, y in ((6, 27), (4, 28), (2, 29), (25, 27), (27, 28), (29, 29),
                 (15, 27), (16, 28), (11, 28), (20, 28)):
        put(im, x, y, crack)
    # impact where the shield lands.
    for x in (14, 15, 16, 17):
        put(im, x, 26, IMP)
    put(im, 15, 26, WHITE)
    put(im, 16, 26, WHITE)
    # shockwave arcs rolling outward, low to each side.
    for x, y in ((1, 25), (3, 23), (5, 22), (30, 25), (28, 23), (26, 22)):
        put(im, x, y, ARC)
    for x, y in ((0, 22), (2, 20), (31, 22), (29, 20)):
        put(im, x, y, ARC_DIM)
    save(im, "ground_slam")


# ---------------------------------------------------------------- output ----
def save(im, name):
    if im.width == 16:
        im = im.resize((32, 32), Image.NEAREST)
    im.save(os.path.join(ICONS, f"{name}.png"))
    print(f"{name}.png")


LABELS = [
    ("bash", "Shield Bash"), ("slam", "Shield Slam"), ("cooldown", "Quick Recovery"),
    ("knockback", "Concussive Blow"), ("wide", "Wide Swings"),
    ("unbreaking", "Reinf. Straps"), ("spikes", "Iron Spikes"), ("rush", "Shield Rush"),
    ("braced", "Braced"), ("reflect", "Reflection"), ("taunt", "Taunt"),
    ("omni_block", "Bulwark"), ("ground_slam", "Ground Slam"),
]


def contact_sheet():
    scale = 4
    cell = 32 * scale
    labelh = 20
    cols = 5
    rows = (len(LABELS) + cols - 1) // cols
    pad = 16
    gap = 14
    W = pad * 2 + cols * cell + (cols - 1) * gap
    H = pad * 2 + rows * (cell + labelh) + (rows - 1) * gap
    sheet = Image.new("RGBA", (W, H), (43, 43, 43, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, label) in enumerate(LABELS):
        r = i // cols
        c = i % cols
        x = pad + c * (cell + gap)
        y = pad + r * (cell + labelh + gap)
        icon = Image.open(os.path.join(ICONS, f"{name}.png")).convert("RGBA")
        sheet.alpha_composite(icon.resize((cell, cell), Image.NEAREST), (x, y))
        tw = d.textlength(label, )
        d.text((x + (cell - tw) / 2, y + cell + 4), label, fill=(224, 224, 224, 255))
    sheet.save(os.path.join(HERE, "contact_sheet.png"))
    print("contact_sheet.png")


def main():
    os.makedirs(ICONS, exist_ok=True)
    bash()
    slam()
    cooldown()
    knockback()
    wide()
    unbreaking()
    spikes()
    rush()
    braced()
    reflect()
    taunt()
    omni_block()
    ground_slam()
    contact_sheet()


if __name__ == "__main__":
    main()
