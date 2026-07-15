import shapes
PAD, HEADER, FOOTER, SECTION_HEADER = 8, 22, 28, 22
MAX_NODE, MIN_NODE, NODE_GAP, MAX_SPACING = 18, 5, 4, 30

def layout(w, h, gw, gh):
    panelW, panelH = w * 90 // 100, h * 90 // 100
    panelTop = (h - panelH) // 2
    canvasW = panelW - PAD * 2
    canvasTop = panelTop + HEADER
    canvasBottom = panelTop + panelH - FOOTER
    availW = canvasW // 3 - PAD * 2
    top = canvasTop + SECTION_HEADER
    availH = canvasBottom - top - PAD
    spacing = min((availW+NODE_GAP)//max(gw,1), (availH+NODE_GAP)//max(gh,1), MAX_SPACING)
    node = max(MIN_NODE, min(spacing - NODE_GAP, MAX_NODE))
    if gw > 1: spacing = min(spacing, (availW - node)//(gw-1))
    if gh > 1: spacing = min(spacing, (availH - node)//(gh-1))
    spacing = max(spacing, 1)
    node = min(node, spacing)
    return availW, availH, spacing, node, (gw-1)*spacing+node, (gh-1)*spacing+node

dims = {k: (max(len(r) for r in v), len(v)) for k, v in shapes.SHAPES.items()}
fails = 0
for (w,h) in [(320,240),(340,250),(400,300),(427,240),(480,270),(640,360),(720,405),(854,480),(960,540),(1024,600),(1280,720),(1600,900),(1920,1080),(2560,1440)]:
    for name,(gw,gh) in dims.items():
        aW,aH,sp,node,sw,sh = layout(w,h,gw,gh)
        if sw > aW or sh > aH:
            fails += 1
            print(f"OVERFLOW {w}x{h} {name}: {sw}x{sh} vs {aW}x{aH}")
        if node < 1 or sp < 1:
            fails += 1; print(f"DEGENERATE {w}x{h} {name}: sp={sp} node={node}")
print("overflows/degenerate:", fails)
# vertical centring check at a roomy size
aW,aH,sp,node,sw,sh = layout(1920,1080,7,13)
print(f"1920x1080 staff: avail {aW}x{aH} shape {sw}x{sh} -> top margin == bottom margin: {(aH-sh)//2}")
