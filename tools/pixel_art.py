"""
32x32 block texture engine for Material Generators (needs only Pillow).

Each block gets three textures:
  <name>.png         side   (bolted plate, window with a shaded icon, status light)
  <name>_top.png     top    (output hole with a glowing grate)
  <name>_bottom.png  bottom (plain plate)

Icons are drawn at 8x resolution with gradients and outlines, then shrunk to the 16x16 window.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

SIZE = 32
WIN = 16          # window interior in pixels (x/y 8..23)
SS = 8            # icon supersampling
CANVAS = WIN * SS


# ------------------------------------------------------------------ colour helpers
def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, d):
    return tuple(clamp(x + d) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def led_colour(base):
    return tuple(clamp(c * 1.15 + 45) for c in base)


# ------------------------------------------------------------------ drawing helpers
def grad(size, c0, c1, kind="v"):
    """Gradient image. v: top->bottom, h: left->right, r: centre(c0)->edge(c1)."""
    w, h = size
    w, h = max(1, w), max(1, h)
    if kind == "v":
        mask = Image.linear_gradient("L").resize((w, h))
    elif kind == "h":
        mask = Image.linear_gradient("L").rotate(90).resize((w, h))
    else:
        mask = Image.radial_gradient("L").resize((w, h))
    return Image.composite(Image.new("RGB", (w, h), c1), Image.new("RGB", (w, h), c0), mask)


def fill(canvas, draw_fn, c0, c1, kind="v", outline=None, grow=9):
    """Fill a shape (drawn by draw_fn onto a mask) with a gradient and an optional outline."""
    w, h = canvas.size
    mask = Image.new("L", (w, h), 0)
    draw_fn(ImageDraw.Draw(mask))
    bbox = mask.getbbox()
    if not bbox:
        return
    if outline is not None:
        canvas.paste(Image.new("RGB", (w, h), outline), (0, 0), mask.filter(ImageFilter.MaxFilter(grow)))
    g = grad((bbox[2] - bbox[0], bbox[3] - bbox[1]), c0, c1, kind)
    canvas.paste(g, (bbox[0], bbox[1]), mask.crop(bbox))


def glow(canvas, cx, cy, radius, colour, strength=110, blur=14):
    w, h = canvas.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=strength)
    mask = mask.filter(ImageFilter.GaussianBlur(blur))
    canvas.paste(Image.new("RGB", (w, h), colour), (0, 0), mask)


def window_bg(base):
    return grad((CANVAS, CANVAS), mix((22, 24, 30), base, 0.22), (11, 13, 17), "r")


def shadow(canvas, bbox):
    w, h = canvas.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse(bbox, fill=150)
    mask = mask.filter(ImageFilter.GaussianBlur(6))
    canvas.paste(Image.new("RGB", (w, h), (4, 5, 8)), (0, 0), mask)


# ------------------------------------------------------------------ icons (128x128 canvas)
def icon_ingot(base, extra=None):
    c = window_bg(base)
    out = shade(base, -120)
    light, dark = shade(base, 55), shade(base, -65)
    shadow(c, [14, 90, 114, 112])
    fill(c, lambda d: d.polygon([(22, 62), (108, 62), (102, 94), (28, 94)], fill=255), shade(base, 5), dark, "v", out)
    fill(c, lambda d: d.polygon([(40, 36), (90, 36), (110, 62), (20, 62)], fill=255), shade(base, 80), light, "v", out)
    d = ImageDraw.Draw(c)
    d.line([(44, 42), (86, 42)], fill=shade(base, 120), width=4)
    d.line([(30, 70), (98, 70)], fill=shade(base, 35), width=3)
    d.line([(34, 86), (96, 86)], fill=shade(base, -85), width=3)
    return c


def gear_points(cx, cy, r_out, r_in, teeth):
    step = 2 * math.pi / teeth
    pts = []
    for i in range(teeth):
        a = i * step
        for da, r in ((-0.5, r_in), (-0.2, r_out), (0.2, r_out), (0.5, r_in)):
            pts.append((cx + r * math.cos(a + da * step), cy + r * math.sin(a + da * step)))
    return pts


def icon_cog(base, extra=None):
    c = window_bg(base)
    out = shade(base, -120)
    shadow(c, [18, 96, 110, 118])
    pts = gear_points(64, 62, 56, 42, 8)
    fill(c, lambda d: d.polygon(pts, fill=255), shade(base, 60), shade(base, -60), "v", out)
    d = ImageDraw.Draw(c)
    d.ellipse([30, 28, 98, 96], outline=shade(base, -70), width=4)
    fill(c, lambda d2: d2.ellipse([42, 40, 86, 84], fill=255), shade(base, 70), shade(base, -30), "v", out)
    fill(c, lambda d2: d2.ellipse([55, 53, 73, 71], fill=255), (10, 11, 14), (30, 32, 38), "v", shade(base, -90))
    d.arc([6, 6, 122, 118], 200, 255, fill=shade(base, 110), width=4)
    return c


def icon_crystal(base, extra=None):
    c = window_bg(base)
    out = shade(base, -120)
    shadow(c, [26, 100, 102, 124])
    # small side shard
    fill(c, lambda d: d.polygon([(14, 96), (22, 66), (34, 66), (36, 96), (26, 108)], fill=255),
         shade(base, 25), shade(base, -70), "v", out)
    # main crystal
    fill(c, lambda d: d.polygon([(64, 6), (34, 32), (34, 94), (64, 120)], fill=255), shade(base, 85), shade(base, 5), "v", out)
    fill(c, lambda d: d.polygon([(64, 6), (94, 32), (94, 94), (64, 120)], fill=255), shade(base, 10), shade(base, -75), "v", out)
    d = ImageDraw.Draw(c)
    d.polygon([(64, 6), (34, 32), (64, 40)], fill=shade(base, 120))
    d.polygon([(64, 6), (94, 32), (64, 40)], fill=shade(base, 35))
    d.line([(64, 40), (64, 120)], fill=shade(base, -40), width=3)
    d.line([(41, 44), (41, 84)], fill=(255, 255, 255), width=4)
    d.line([(100, 14), (100, 30)], fill=(255, 255, 255), width=3)
    d.line([(92, 22), (108, 22)], fill=(255, 255, 255), width=3)
    return c


def icon_log(base, bark):
    c = window_bg(base)
    bark = bark or shade(base, -70)
    out = shade(bark, -80)
    shadow(c, [10, 96, 118, 124])
    fill(c, lambda d: d.ellipse([10, 10, 118, 118], fill=255), shade(bark, 35), shade(bark, -35), "v", out)
    d = ImageDraw.Draw(c)
    for a in range(0, 360, 30):                               # bark notches
        x = 64 + 53 * math.cos(math.radians(a))
        y = 64 + 53 * math.sin(math.radians(a))
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=shade(bark, -50))
    fill(c, lambda d2: d2.ellipse([24, 24, 104, 104], fill=255), shade(base, 32), shade(base, -18), "r", shade(bark, -55))
    for r in (31, 20, 9):                                     # growth rings
        d.ellipse([64 - r, 64 - r, 64 + r, 64 + r], outline=shade(base, -48), width=5)
    d.ellipse([57, 57, 71, 71], fill=shade(base, -70))
    d.line([(64, 64), (96, 38)], fill=shade(base, -60), width=3)    # crack
    d.arc([28, 28, 100, 100], 195, 255, fill=shade(base, 80), width=5)
    return c


def icon_rod(base, extra=None):
    c = window_bg(base)
    out = (60, 24, 4)
    shadow(c, [20, 104, 112, 124])
    a, b = (26, 102), (102, 26)
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    nx, ny = -uy, ux
    half = 14
    segs = 5
    for i in range(segs):
        t0, t1 = i / segs, (i + 1) / segs
        p0 = (a[0] + dx * t0, a[1] + dy * t0)
        p1 = (a[0] + dx * t1, a[1] + dy * t1)
        col = shade(base, 25) if i % 2 == 0 else shade(base, -55)
        pts = [(p0[0] + nx * half, p0[1] + ny * half), (p1[0] + nx * half, p1[1] + ny * half),
               (p1[0] - nx * half, p1[1] - ny * half), (p0[0] - nx * half, p0[1] - ny * half)]
        fill(c, lambda d, pts=pts: d.polygon(pts, fill=255), shade(col, 40), shade(col, -35), "v", out, grow=7)
        hi = [(p0[0] + nx * half * 0.8, p0[1] + ny * half * 0.8), (p1[0] + nx * half * 0.8, p1[1] + ny * half * 0.8),
              (p1[0] + nx * half * 0.35, p1[1] + ny * half * 0.35), (p0[0] + nx * half * 0.35, p0[1] + ny * half * 0.35)]
        ImageDraw.Draw(c).polygon(hi, fill=shade(col, 85))
    glow(c, 104, 22, 16, (255, 190, 60), 160, 8)
    d = ImageDraw.Draw(c)
    for (x, y, r) in ((100, 18, 4), (112, 30, 3), (92, 10, 3)):
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 235, 140))
    return c


def icon_dust(base, extra=None):
    c = window_bg(base)
    out = shade(base, -115)
    rnd = random.Random(11)
    shadow(c, [10, 96, 118, 124])
    fill(c, lambda d: d.ellipse([12, 66, 116, 112], fill=255), shade(base, 10), shade(base, -70), "v", out)
    fill(c, lambda d: d.ellipse([28, 38, 100, 100], fill=255), shade(base, 55), shade(base, -25), "v", out)
    fill(c, lambda d: d.ellipse([46, 26, 82, 70], fill=255), shade(base, 85), shade(base, 10), "v", out)
    d = ImageDraw.Draw(c)
    for _ in range(46):                                        # grains
        x, y = rnd.randint(16, 112), rnd.randint(40, 108)
        if ((x - 64) / 52.0) ** 2 + ((y - 78) / 34.0) ** 2 > 1:
            continue
        r = rnd.choice((2, 2, 3))
        col = shade(base, rnd.choice((-80, -55, 70, 100)))
        d.ellipse([x - r, y - r, x + r, y + r], fill=col)
    return c


def star_points(cx, cy, long_r, short_r, inner_r):
    pts = []
    for i in range(16):
        a = math.radians(i * 22.5 - 90)
        r = long_r if i % 4 == 0 else (short_r if i % 4 == 2 else inner_r)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def icon_star(base, extra=None):
    c = window_bg(base)
    gold = (255, 214, 70)
    glow(c, 64, 64, 46, gold, 95, 12)
    fill(c, lambda d: d.polygon(star_points(64, 64, 58, 34, 13), fill=255), (255, 252, 225), gold, "r", (110, 70, 10), grow=11)
    ImageDraw.Draw(c).polygon(star_points(64, 64, 38, 22, 9), fill=(255, 255, 250))
    d = ImageDraw.Draw(c)
    d.ellipse([52, 52, 76, 76], fill=(255, 255, 255))
    return c


def icon_copy(base, extra=None):
    c = window_bg(base)
    out = shade(base, -130)
    shadow(c, [20, 96, 116, 124])
    fill(c, lambda d: d.rounded_rectangle([14, 12, 82, 80], radius=12, fill=255),
         shade(base, -25), shade(base, -95), "v", out)
    fill(c, lambda d: d.rounded_rectangle([46, 44, 114, 112], radius=12, fill=255),
         shade(base, 80), shade(base, -10), "v", out)
    d = ImageDraw.Draw(c)
    d.rounded_rectangle([54, 52, 106, 104], radius=8, outline=shade(base, 110), width=3)
    d.line([(80, 62), (80, 94)], fill=(255, 255, 255), width=5)       # plus sign
    d.line([(64, 78), (96, 78)], fill=(255, 255, 255), width=5)
    d.line([(100, 14), (100, 34)], fill=(255, 255, 255), width=3)    # sparkle
    d.line([(90, 24), (110, 24)], fill=(255, 255, 255), width=3)
    return c


def icon_core(base, extra=None):
    c = window_bg(base)
    out = (20, 14, 14)
    shadow(c, [14, 100, 114, 124])
    fill(c, lambda d: d.ellipse([10, 8, 118, 116], fill=255), (150, 154, 168), (50, 52, 62), "v", out)
    fill(c, lambda d: d.ellipse([22, 20, 106, 104], fill=255), (36, 38, 46), (70, 72, 84), "v", out)
    glow(c, 64, 62, 44, base, 140, 12)
    fill(c, lambda d: d.ellipse([32, 30, 96, 94], fill=255), (255, 215, 170), shade(base, -10), "r", shade(base, -100))
    d = ImageDraw.Draw(c)
    d.ellipse([42, 38, 62, 56], fill=(255, 245, 225))
    for (x0, y0, x1, y1) in ((60, 2, 68, 14), (60, 110, 68, 122), (2, 58, 14, 66), (114, 58, 126, 66)):
        d.rectangle([x0, y0, x1, y1], fill=(200, 204, 216))
    return c


def icon_gem(base, extra=None):
    c = window_bg(base)
    out = shade(base, -125)
    shadow(c, [16, 100, 112, 124])
    crown = [(30, 38), (98, 38), (118, 62), (10, 62)]
    pav = [(10, 62), (118, 62), (64, 116)]
    fill(c, lambda d: d.polygon(pav, fill=255), shade(base, 10), shade(base, -75), "v", out)
    fill(c, lambda d: d.polygon(crown, fill=255), shade(base, 95), shade(base, 25), "v", out)
    d = ImageDraw.Draw(c)
    for (a, b) in (((64, 38), (44, 62)), ((64, 38), (84, 62)), ((44, 62), (64, 116)), ((84, 62), (64, 116))):
        d.line([a, b], fill=shade(base, -60), width=3)
    d.polygon([(64, 38), (44, 62), (84, 62)], fill=shade(base, 70))
    d.line([(34, 44), (52, 44)], fill=(255, 255, 255), width=4)
    d.line([(100, 14), (100, 30)], fill=(255, 255, 255), width=3)
    d.line([(92, 22), (108, 22)], fill=(255, 255, 255), width=3)
    return c


def icon_lump(base, extra=None):
    c = window_bg(base)
    out = shade(base, -125)
    rnd = random.Random(5)
    shadow(c, [14, 98, 114, 124])
    pts = [(24, 74), (30, 42), (58, 22), (92, 30), (114, 58), (106, 92), (76, 110), (40, 104)]
    fill(c, lambda d: d.polygon(pts, fill=255), shade(base, 55), shade(base, -60), "v", out, grow=11)
    d = ImageDraw.Draw(c)
    d.polygon([(40, 52), (60, 32), (84, 38), (62, 60)], fill=shade(base, 85))
    d.polygon([(70, 74), (100, 62), (98, 90), (76, 102)], fill=shade(base, -80))
    for _ in range(14):                                       # specks
        x, y = rnd.randint(34, 100), rnd.randint(40, 98)
        r = rnd.choice((2, 3))
        d.ellipse([x - r, y - r, x + r, y + r], fill=shade(base, rnd.choice((-70, 110))))
    d.line([(46, 48), (58, 40)], fill=(255, 255, 255), width=4)
    return c


def icon_spike(base, extra=None):
    c = window_bg(base)
    out = shade(base, -125)
    shadow(c, [8, 100, 120, 124])
    for (x0, x1, tip, y1) in ((6, 40, (22, 62), 112), (86, 122, (104, 52), 112)):
        fill(c, lambda d, x0=x0, x1=x1, tip=tip, y1=y1: d.polygon([(x0, y1), tip, (x1, y1)], fill=255),
             shade(base, 20), shade(base, -70), "v", out, grow=9)
    fill(c, lambda d: d.polygon([(30, 112), (64, 8), (98, 112)], fill=255), shade(base, 60), shade(base, -50), "v", out)
    d = ImageDraw.Draw(c)
    d.polygon([(64, 8), (98, 112), (64, 112)], fill=shade(base, -45))
    d.line([(60, 30), (44, 100)], fill=shade(base, 100), width=4)
    for y in (46, 70, 92):                                    # layered rings
        d.line([(64 - (y - 8) * 0.28, y), (64 + (y - 8) * 0.28, y)], fill=shade(base, -80), width=2)
    return c


def icon_cube(base, extra=None):
    c = window_bg(base)
    out = shade(base, -125)
    rnd = random.Random(9)
    shadow(c, [14, 104, 114, 126])
    top = [(64, 10), (116, 36), (64, 62), (12, 36)]
    left = [(12, 36), (64, 62), (64, 118), (12, 92)]
    right = [(64, 62), (116, 36), (116, 92), (64, 118)]
    fill(c, lambda d: d.polygon(left, fill=255), shade(base, 5), shade(base, -45), "v", out)
    fill(c, lambda d: d.polygon(right, fill=255), shade(base, -35), shade(base, -85), "v", out)
    fill(c, lambda d: d.polygon(top, fill=255), shade(base, 80), shade(base, 35), "v", out)
    d = ImageDraw.Draw(c)
    for _ in range(26):                                       # rock speckle
        x, y = rnd.randint(18, 112), rnd.randint(30, 108)
        r = rnd.choice((2, 2, 3))
        d.ellipse([x - r, y - r, x + r, y + r], fill=shade(base, rnd.choice((-60, -40, 50))))
    d.line([(20, 42), (60, 62)], fill=shade(base, 110), width=3)
    return c


def icon_hub(base, extra=None):
    c = window_bg(base)
    out = (14, 24, 34)
    colours = [(205, 118, 80), (214, 214, 220), (250, 214, 64), (90, 236, 226), (60, 214, 110), (176, 120, 240)]
    glow(c, 64, 64, 50, base, 120, 14)
    d = ImageDraw.Draw(c)
    cells = []
    for r in range(2):
        for k in range(3):
            x0, y0 = 6 + k * 42, 22 + r * 46
            cells.append((x0, y0, colours[r * 3 + k]))
    for (x0, y0, _) in cells:                                  # wiring to the middle
        d.line([(x0 + 18, y0 + 18), (64, 64)], fill=shade(base, -20), width=5)
    for (x0, y0, col) in cells:
        fill(c, lambda dd, x0=x0, y0=y0: dd.rounded_rectangle([x0, y0, x0 + 36, y0 + 36], radius=7, fill=255),
             shade(col, 45), shade(col, -70), "v", out, grow=9)
        d.rounded_rectangle([x0 + 8, y0 + 8, x0 + 28, y0 + 28], radius=4, fill=(20, 22, 28))
        d.rectangle([x0 + 12, y0 + 12, x0 + 24, y0 + 24], fill=shade(col, 20))
        d.line([(x0 + 12, y0 + 12), (x0 + 22, y0 + 12)], fill=shade(col, 120), width=3)
    d.ellipse([52, 52, 76, 76], fill=(255, 255, 255), outline=out, width=4)
    return c


def icon_tome(base, extra=None):
    c = window_bg(base)
    out = (30, 18, 50)
    shadow(c, [10, 100, 118, 124])
    glow(c, 64, 26, 30, base, 140, 10)
    fill(c, lambda d: d.polygon([(8, 36), (64, 48), (120, 36), (120, 104), (64, 116), (8, 104)], fill=255),
         shade(base, -20), shade(base, -90), "v", out)
    fill(c, lambda d: d.polygon([(14, 42), (60, 52), (60, 108), (14, 98)], fill=255),
         (255, 248, 225), (214, 198, 160), "v", (90, 70, 40), grow=7)
    fill(c, lambda d: d.polygon([(68, 52), (114, 42), (114, 98), (68, 108)], fill=255),
         (255, 248, 225), (214, 198, 160), "v", (90, 70, 40), grow=7)
    d = ImageDraw.Draw(c)
    for k in range(4):                                         # lines of runes
        y = 60 + k * 11
        d.line([(22, y - 2 + k * 0), (52, y + 4)], fill=shade(base, -40), width=3)
        d.line([(76, y + 4), (106, y - 2)], fill=shade(base, -40), width=3)
    d.line([(64, 48), (64, 116)], fill=(60, 40, 20), width=4)
    for (x, y, r) in ((64, 14, 8), (40, 24, 4), (90, 20, 4)):  # sparkles
        d.line([(x - r, y), (x + r, y)], fill=(255, 255, 255), width=3)
        d.line([(x, y - r), (x, y + r)], fill=(255, 255, 255), width=3)
    return c


ICON_FUNCS = {"hub": icon_hub, "tome": icon_tome, "gem": icon_gem, "lump": icon_lump, "spike": icon_spike, "cube": icon_cube, "ingot": icon_ingot, "cog": icon_cog, "crystal": icon_crystal, "log": icon_log, "rod": icon_rod,
              "dust": icon_dust, "star": icon_star, "copy": icon_copy, "core": icon_core}
_icon_cache = {}


def render_icon(kind, base, extra=None):
    key = (kind, base, extra)
    if key not in _icon_cache:
        big = ICON_FUNCS[kind](base, extra)
        big = big.crop((9, 9, 119, 119)).resize((CANVAS, CANVAS), Image.LANCZOS)
        # soft glass reflection in the top-left corner
        ov = Image.new("RGB", big.size, (255, 255, 255))
        mask = Image.new("L", big.size, 0)
        ImageDraw.Draw(mask).polygon([(0, 0), (70, 0), (0, 70)], fill=22)
        big.paste(ov, (0, 0), mask)
        _icon_cache[key] = big.resize((WIN, WIN), Image.BOX)
    return _icon_cache[key]


# ------------------------------------------------------------------ plate pieces (32x32)
PALETTES = {
    1: dict(base=(104, 108, 120), outline=(28, 30, 38), bev_l=(150, 154, 168), bev_d=(66, 69, 80),
            bolt=(176, 180, 194), frame=(30, 32, 40), frame_l=(140, 144, 158), frame_d=(60, 63, 74),
            ring=(60, 63, 74)),
    # gold
    2: dict(base=(70, 72, 88), outline=(96, 70, 12), bev_l=(255, 226, 120), bev_d=(140, 104, 24),
            bolt=(236, 196, 72), frame=(214, 170, 50), frame_l=(255, 232, 140), frame_d=(120, 88, 16),
            ring=(214, 170, 50)),
    # diamond
    3: dict(base=(58, 82, 96), outline=(12, 88, 100), bev_l=(150, 255, 250), bev_d=(30, 140, 150),
            bolt=(120, 240, 235), frame=(80, 230, 225), frame_l=(190, 255, 252), frame_d=(20, 120, 130),
            ring=(80, 230, 225)),
    # netherite + embers
    4: dict(base=(58, 48, 52), outline=(22, 14, 16), bev_l=(140, 112, 122), bev_d=(36, 28, 32),
            bolt=(255, 150, 50), frame=(255, 140, 40), frame_l=(255, 205, 120), frame_d=(150, 70, 10),
            ring=(255, 140, 40)),
    # nether star / prismatic
    5: dict(base=(44, 36, 74), outline=(70, 20, 120), bev_l=(255, 205, 255), bev_d=(120, 60, 200),
            bolt=(255, 235, 255), frame=(255, 110, 235), frame_l=(255, 225, 255), frame_d=(160, 40, 150),
            ring=(255, 110, 235)),
}


def palette(tier, plate_base=None):
    p = dict(PALETTES[tier])
    if plate_base:
        p["base"] = plate_base
    return p


def new_plate(tier, seed, plate_base=None, dark=0):
    p = palette(tier, plate_base)
    rnd = random.Random(seed)
    streak = [rnd.randint(-3, 3) for _ in range(SIZE)]
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            d = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            n = rnd.randint(-3, 3)
            if d == 0:
                c = shade(p["outline"], n)
            elif d <= 2:
                lit = min(x, y) < min(SIZE - 1 - x, SIZE - 1 - y)
                c = shade(p["bev_l"] if lit else p["bev_d"], n)
                if d == 2:
                    c = mix(c, p["base"], 0.35)
            else:
                c = shade(p["base"], n + streak[y] + (3 if y < 16 else -3))
            px[x, y] = shade(c, dark) + (255,)
    return img, px, p


def draw_bolts(px, p, dark=0):
    bc = p["bolt"]
    pat = [("l", "l", "m"), ("l", "k", "d"), ("m", "d", "d")]
    colours = {"l": shade(bc, 45), "m": bc, "d": shade(bc, -55), "k": shade(bc, -100)}
    for cx, cy in ((4, 4), (27, 4), (4, 27), (27, 27)):
        for j in range(3):
            for i in range(3):
                px[cx - 1 + i, cy - 1 + j] = shade(colours[pat[j][i]], dark) + (255,)


def draw_window_frame(px, p):
    for t in range(6, 26):
        # outer raised bevel (6/25), outline (7/24)
        px[t, 6] = p["frame_l"] + (255,)
        px[6, t] = p["frame_l"] + (255,)
        px[t, 25] = p["frame_d"] + (255,)
        px[25, t] = p["frame_d"] + (255,)
    for t in range(7, 25):
        for (x, y) in ((t, 7), (7, t), (t, 24), (24, t)):
            px[x, y] = p["frame"] + (255,)


def draw_vents(px, p):
    for x in range(10, 22):
        px[x, 4] = (24, 26, 32, 255)
        px[x, 5] = shade(p["base"], 26) + (255,)
    for y in range(12, 20):
        px[4, y] = (24, 26, 32, 255)
        px[5, y] = shade(p["base"], 26) + (255,)
        px[27, y] = (24, 26, 32, 255)
        px[26, y] = shade(p["base"], 26) + (255,)


def draw_led(px, led):
    for y in (26, 27, 28):
        for x in range(10, 22):
            px[x, y] = (18, 20, 26, 255)
    for x in range(11, 21):
        edge = x in (11, 20)
        px[x, 27] = (shade(led, -70) if edge else led) + (255,)
        if not edge:
            px[x, 26] = mix((18, 20, 26), led, 0.30) + (255,)
            px[x, 28] = mix((18, 20, 26), led, 0.30) + (255,)


# ------------------------------------------------------------------ the three faces
def make_side(path, kind, base, tier, seed, extra=None, plate_base=None):
    img, px, p = new_plate(tier, seed, plate_base)
    draw_vents(px, p)
    draw_window_frame(px, p)
    draw_bolts(px, p)
    draw_led(px, led_colour(base))
    icon = render_icon(kind, base, extra)
    img.paste(icon.convert("RGBA"), (8, 8))
    for t in range(8, 24):                                  # inner shadow of the window
        for (x, y) in ((t, 8), (8, t)):
            r, g, b, a = img.getpixel((x, y))
            px[x, y] = (int(r * 0.7), int(g * 0.7), int(b * 0.7), 255)
    img.save(path)


def make_top(path, base, tier, seed, plate_base=None):
    img, px, p = new_plate(tier, seed, plate_base)
    draw_bolts(px, p)
    led = led_colour(base)
    cx = cy = 15.5
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - cx, y - cy)
            if r <= 11.0:
                t = max(0.0, 1.0 - r / 9.5)
                c = mix((10, 11, 14), led, 0.85 * t ** 1.2)
                if x % 4 == 0 or y == 15 or y == 16:           # grate bars
                    c = mix(c, (58, 61, 72), 0.8)
                px[x, y] = c + (255,)
            elif r <= 13.2:
                lit = (x - cx) + (y - cy) < 0
                ring = p["ring"]
                px[x, y] = (shade(ring, 40) if lit else shade(ring, -35)) + (255,)
            elif r <= 14.0:
                px[x, y] = (22, 24, 30, 255)
    img.save(path)


def make_bottom(path, tier, seed, plate_base=None):
    img, px, p = new_plate(tier, seed, plate_base, dark=-16)
    draw_bolts(px, p, dark=-16)
    for t in range(3, 29):
        for (x, y) in ((15, t), (t, 15)):
            px[x, y] = shade(p["outline"], 6) + (255,)
    img.save(path)


def make_block_textures(directory, name, kind, base, tier, seed, extra=None, plate_base=None):
    os.makedirs(directory, exist_ok=True)
    make_side(os.path.join(directory, f"{name}.png"), kind, base, tier, seed, extra, plate_base)
    make_top(os.path.join(directory, f"{name}_top.png"), base, tier, seed + 1, plate_base)
    make_bottom(os.path.join(directory, f"{name}_bottom.png"), tier, seed + 2, plate_base)


def make_expansion_icon(path):
    """32x32 item texture for the Hub Expansion: a circuit board with a plus sign and gold pins."""
    c = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    out = (10, 30, 44)
    for y in (26, 48, 70, 92):                                  # gold pins on both sides
        d.rectangle([2, y, 24, y + 10], fill=(244, 204, 72), outline=(120, 84, 10), width=2)
        d.rectangle([104, y, 126, y + 10], fill=(244, 204, 72), outline=(120, 84, 10), width=2)
    fill(c, lambda dd: dd.rounded_rectangle([18, 10, 110, 118], radius=14, fill=255),
         (110, 214, 240), (28, 88, 130), "v", out, grow=9)
    d.rounded_rectangle([28, 20, 100, 108], radius=8, outline=(190, 244, 255), width=4)
    d.line([(64, 42), (64, 86)], fill=(255, 255, 255), width=12)     # plus sign
    d.line([(42, 64), (86, 64)], fill=(255, 255, 255), width=12)
    for (x, y) in ((36, 30), (86, 30), (36, 92), (86, 92)):           # little chips
        d.rectangle([x, y, x + 8, y + 8], fill=(24, 60, 96))
    small = c.convert("RGBa").resize((32, 32), Image.BOX).convert("RGBA")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    small.save(path)
