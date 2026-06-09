"""Shared Beacon icon artwork.

One geometric lighthouse, drawn the same way for every platform so the desktop
.png/.ico, the macOS .icns and the Android adaptive icon never drift apart.
Flat shapes, a calm navy field and two symmetric light beams - no glossy
gradients or noisy "sea" lines.
"""

from PIL import Image, ImageDraw, ImageFilter, ImageChops

# palette (brand navy + a single accent blue, matching BeaconTheme)
NAVY_TOP = (20, 28, 70)
NAVY_BOT = (8, 12, 32)
GLOW = (96, 168, 255)

TOWER = (237, 242, 252)
TOWER_EDGE = (206, 217, 238)
BAND = (52, 110, 228)
ROOF = (45, 98, 212)
LANTERN = (14, 21, 50)
GALLERY = (210, 220, 240)
LENS = (150, 212, 255)
LENS_CORE = (230, 245, 255)
BEAM = (150, 205, 255)


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def _vertical_gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        c = _lerp(top, bottom, y / max(h - 1, 1))
        for x in range(w):
            px[x, y] = c
    return img


def _radial_mask(w, h, cx, cy, radius, max_val=255, falloff=2.2):
    m = Image.new("L", (w, h), 0)
    px = m.load()
    r2 = radius * radius
    for y in range(h):
        dy = y - cy
        for x in range(w):
            dx = x - cx
            d2 = dx * dx + dy * dy
            if d2 < r2:
                t = 1 - (d2 / r2) ** 0.5
                v = int(max_val * (t ** falloff))
                if v:
                    px[x, y] = v
    return m


def _radial_glow(w, h, cx, cy, radius, color, max_alpha, falloff=2.2):
    glow = Image.new("RGBA", (w, h), (*color, 0))
    glow.putalpha(_radial_mask(w, h, cx, cy, radius, max_alpha, falloff))
    return glow


def render_background(s):
    """Full-bleed navy field with a soft glow behind the lamp."""
    bg = _vertical_gradient(s, s, NAVY_TOP, NAVY_BOT).convert("RGBA")
    bg.alpha_composite(_radial_glow(s, s, s // 2, int(s * 0.33), int(s * 0.52), GLOW, 80))
    return bg


def render_foreground(s):
    """Transparent layer with the lighthouse and its beams, drawn full-bleed."""
    fg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg, "RGBA")
    cx = s / 2

    roof_apex_y = 0.150 * s
    lantern_y0 = 0.290 * s
    lantern_y1 = 0.410 * s
    tower_y0 = lantern_y1
    tower_y1 = 0.800 * s
    base_y0 = tower_y1
    base_y1 = 0.862 * s

    lens_cy = (lantern_y0 + lantern_y1) / 2
    lens_r = 0.052 * s

    # two symmetric beams sweeping up to the corners from the lamp, faded
    # along their length so they read as light at any scale (no hard far edge)
    beam = Image.new("RGBA", (s, s), (*BEAM, 0))
    bmask = Image.new("L", (s, s), 0)
    bd = ImageDraw.Draw(bmask)
    spread = 0.042 * s
    for sign in (-1, 1):
        far_x = cx + sign * 0.95 * s
        bd.polygon(
            [(cx, lens_cy - spread), (cx, lens_cy + spread),
             (far_x, -0.10 * s), (far_x, 0.34 * s)],
            fill=110,
        )
    fade = _radial_mask(s, s, int(cx), int(lens_cy), int(s * 0.52))
    beam.putalpha(ImageChops.multiply(bmask, fade))
    beam = beam.filter(ImageFilter.GaussianBlur(int(s * 0.012)))
    fg.alpha_composite(beam)

    # lamp glow
    fg.alpha_composite(
        _radial_glow(s, s, int(cx), int(lens_cy), int(s * 0.18), LENS, 130).filter(
            ImageFilter.GaussianBlur(int(s * 0.01))
        )
    )

    # base platform
    base_w = 0.44 * s
    d.rounded_rectangle(
        (cx - base_w / 2, base_y0, cx + base_w / 2, base_y1),
        radius=int(s * 0.014), fill=(*TOWER, 255),
    )

    # tapered tower
    top_w, bot_w = 0.195 * s, 0.320 * s
    tt_l, tt_r = cx - top_w / 2, cx + top_w / 2
    tb_l, tb_r = cx - bot_w / 2, cx + bot_w / 2
    d.polygon([(tt_l, tower_y0), (tt_r, tower_y0), (tb_r, tower_y1), (tb_l, tower_y1)],
              fill=(*TOWER, 255))

    # two accent bands, clipped to the taper
    def edge_at(y):
        t = (y - tower_y0) / (tower_y1 - tower_y0)
        return tt_l + (tb_l - tt_l) * t, tt_r + (tb_r - tt_r) * t

    for frac in (0.34, 0.66):
        sy = tower_y0 + frac * (tower_y1 - tower_y0)
        bh = 0.052 * s
        l0, r0 = edge_at(sy - bh / 2)
        l1, r1 = edge_at(sy + bh / 2)
        d.polygon([(l0, sy - bh / 2), (r0, sy - bh / 2),
                   (r1, sy + bh / 2), (l1, sy + bh / 2)], fill=(*BAND, 255))

    # gallery walkway under the lantern
    gal_w = 0.300 * s
    d.rounded_rectangle(
        (cx - gal_w / 2, lantern_y1 - 0.012 * s, cx + gal_w / 2, lantern_y1 + 0.022 * s),
        radius=int(s * 0.008), fill=(*GALLERY, 255),
    )

    # lantern housing
    lan_w = 0.235 * s
    d.rounded_rectangle(
        (cx - lan_w / 2, lantern_y0, cx + lan_w / 2, lantern_y1),
        radius=int(s * 0.016), fill=(*LANTERN, 255),
    )

    # lens
    d.ellipse((cx - lens_r, lens_cy - lens_r, cx + lens_r, lens_cy + lens_r), fill=(*LENS, 255))
    d.ellipse((cx - lens_r * 0.52, lens_cy - lens_r * 0.52,
               cx + lens_r * 0.52, lens_cy + lens_r * 0.52), fill=(*LENS_CORE, 255))

    # roof cap + finial
    roof_half = 0.150 * s
    d.polygon([(cx - roof_half, lantern_y0), (cx + roof_half, lantern_y0), (cx, roof_apex_y)],
              fill=(*ROOF, 255))
    fin_r = 0.016 * s
    d.ellipse((cx - fin_r, roof_apex_y - fin_r * 1.6, cx + fin_r, roof_apex_y + fin_r * 0.4),
              fill=(*TOWER, 255))

    return fg


def render_app_icon(s, radius_frac=0.22):
    """Composited rounded-square icon for desktop / .ico / .icns."""
    bg = render_background(s)
    bg.alpha_composite(render_foreground(s))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s, s), radius=int(s * radius_frac), fill=255)
    icon = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    icon.paste(bg, (0, 0), mask)
    return icon
