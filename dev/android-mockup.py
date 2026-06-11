"""Renders Android phone mockups of Mayak from the shared UI palette.

Not a live capture: replicates the Compose Home screen (MayakApp.kt) with the
exact MayakColors palette and reuses the real lighthouse hero from the desktop
screenshot so the artwork matches the app.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / ".assets"

# MayakColors (app/src/main/kotlin/app/mayak/ui/MayakTheme.kt)
BG_TOP = (15, 21, 53)
BG_BOT = (8, 12, 34)
CARD = (22, 32, 70)
BORDER = (42, 58, 110)
TEXT = (225, 232, 250)
TEXT_DIM = (180, 195, 230)
MUTED = (120, 140, 190)
ACCENT = (60, 110, 230)
ACCENT_LIGHT = (130, 180, 255)
SUCCESS = (52, 211, 153)
DANGER = (248, 113, 113)
WHITE = (245, 248, 255)

W, H = 1080, 2240
P = 64

def fnt(size, weight="r"):
    name = {"r": "segoeui.ttf", "sb": "seguisb.ttf", "b": "segoeuib.ttf"}[weight]
    try:
        return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
    except OSError:
        return ImageFont.load_default()

def vgrad(w, h, top, bot):
    base = Image.new("RGB", (w, h), top)
    top_img = Image.new("RGB", (w, h), bot)
    mask = Image.new("L", (1, h))
    for y in range(h):
        mask.putpixel((0, y), int(255 * y / h))
    base.paste(top_img, (0, 0), mask.resize((w, h)))
    return base.convert("RGBA")

def hero_crop(src, box, target_w, target_h):
    img = Image.open(src).convert("RGBA").crop(box)
    iw, ih = img.size
    scale = max(target_w / iw, target_h / ih)
    img = img.resize((int(iw * scale), int(ih * scale)), Image.LANCZOS)
    left = (img.width - target_w) // 2
    top = (img.height - target_h) // 2
    img = img.crop((left, top, left + target_w, target_h + top))
    mask = Image.new("L", (target_w, target_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, target_w - 1, target_h - 1], 30, fill=255)
    img.putalpha(mask)
    return img

def power_icon(d, cx, cy, r, color):
    d.arc([cx - r, cy - r, cx + r, cy + r], start=300, end=240, fill=color, width=9)
    d.line([cx, cy - r - 4, cx, cy + 2], fill=color, width=9)

def nav_icon(d, name, cx, cy, color):
    s = 30
    if name == "home":
        d.line([cx - s, cy + 4, cx, cy - s + 2], fill=color, width=7)
        d.line([cx, cy - s + 2, cx + s, cy + 4], fill=color, width=7)
        d.rounded_rectangle([cx - s + 8, cy + 2, cx + s - 8, cy + s], 4, outline=color, width=7)
    elif name == "key":
        d.ellipse([cx - s, cy - s + 6, cx - 2, cy + 12], outline=color, width=7)
        d.line([cx - 6, cy + 2, cx + s, cy + s], fill=color, width=7)
        d.line([cx + s - 12, cy + s - 12, cx + s, cy + s - 24], fill=color, width=7)
    elif name == "globe":
        d.ellipse([cx - s, cy - s, cx + s, cy + s], outline=color, width=7)
        d.ellipse([cx - s // 2, cy - s, cx + s // 2, cy + s], outline=color, width=6)
        d.line([cx - s, cy, cx + s, cy], fill=color, width=6)
    elif name == "journal":
        d.rounded_rectangle([cx - s + 6, cy - s, cx + s - 6, cy + s], 4, outline=color, width=7)
        for y in (-13, 0, 13):
            d.line([cx - 12, cy + y, cx + 13, cy + y], fill=color, width=5)
    elif name == "gear":
        d.ellipse([cx - s + 6, cy - s + 6, cx + s - 6, cy + s - 6], outline=color, width=7)
        d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=color)

def card(canvas, x, y, w, h, r=30, fill=CARD, border=None):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dd = ImageDraw.Draw(layer)
    dd.rounded_rectangle([0, 0, w - 1, h - 1], r, fill=fill,
                         outline=border, width=2 if border else 0)
    canvas.alpha_composite(layer, (x, y))

def screen(connected, hero_box, hero_src):
    cv = vgrad(W, H, BG_TOP, BG_BOT)
    d = ImageDraw.Draw(cv)

    # status bar
    d.text((P, 30), "9:41", font=fnt(30, "sb"), fill=TEXT)
    d.rounded_rectangle([W - P - 46, 34, W - P - 8, 56], 4, outline=TEXT, width=3)
    d.rectangle([W - P - 6, 40, W - P - 2, 50], fill=TEXT)
    d.ellipse([W - P - 96, 36, W - P - 70, 56], outline=TEXT, width=3)

    # top app bar
    d.text((P, 118), "Маяк", font=fnt(70, "b"), fill=TEXT)
    status = "Подключено" if connected else "Отключено"
    d.text((P, 210), status, font=fnt(36), fill=MUTED)

    # hero
    hy = 300
    hh = 600
    hero = hero_crop(hero_src, hero_box, W - 2 * P, hh)
    cv.alpha_composite(hero, (P, hy))

    # status row
    sy = hy + hh + 56
    dot = SUCCESS if connected else MUTED
    d.ellipse([P, sy, P + 24, sy + 24], fill=dot)
    d.text((P + 42, sy - 6), status, font=fnt(40, "sb"),
           fill=SUCCESS if connected else TEXT_DIM)

    # connect button
    by = sy + 70
    bh = 132
    btn = ACCENT if not connected else DANGER
    card(cv, P, by, W - 2 * P, bh, r=bh // 2, fill=btn)
    label = "Отключить" if connected else "Подключить"
    f = fnt(44, "sb")
    tw = d.textlength(label, font=f)
    icon_cx = (W - tw) // 2 - 50
    power_icon(d, icon_cx, by + bh // 2, 26, WHITE)
    d.text(((W - tw) // 2 + 20, by + bh // 2), label, font=f, fill=WHITE, anchor="lm")

    # active profile card
    cy = by + bh + 44
    ch = 220
    card(cv, P, cy, W - 2 * P, ch, border=BORDER)
    d.text((P + 44, cy + 36), "Активный ключ", font=fnt(32, "sb"), fill=MUTED)
    d.text((P + 44, cy + 84), "default-test", font=fnt(56, "b"), fill=TEXT)
    d.text((P + 44, cy + 150), "vpn.example.com:443", font=fnt(34), fill=TEXT_DIM)

    # traffic card (connected only)
    if connected:
        ty = cy + ch + 36
        th = 180
        card(cv, P, ty, W - 2 * P, th, fill=(15, 22, 54))
        half = (W - 2 * P) // 2
        for i, (lab, val) in enumerate([("Входящий", "1.4 MB/s"), ("Исходящий", "180 KB/s")]):
            cxp = P + half * i + half // 2
            d.text((cxp, ty + 52), lab, font=fnt(32), fill=MUTED, anchor="mm")
            d.text((cxp, ty + 116), val, font=fnt(46, "b"), fill=TEXT, anchor="mm")

    # bottom nav
    nh = 150
    ny = H - nh
    d.rectangle([0, ny, W, H], fill=BG_TOP)
    d.line([0, ny, W, ny], fill=(30, 42, 80), width=2)
    tabs = [
        ("home", "Главная"),
        ("key", "Ключи"),
        ("globe", "Подписки"),
        ("journal", "Журнал"),
        ("gear", "Настройки"),
    ]
    seg = W // len(tabs)
    for i, (icon, lab) in enumerate(tabs):
        cx = seg * i + seg // 2
        sel = i == 0
        col = WHITE if sel else MUTED
        if sel:
            d.rounded_rectangle([cx - 56, ny + 24, cx + 56, ny + 60], 18, fill=ACCENT)
        nav_icon(d, icon, cx, ny + 42, col)
        d.text((cx, ny + 96), lab, font=fnt(22, "sb" if sel else "r"),
               fill=ACCENT_LIGHT if sel else MUTED, anchor="mm")
    return cv

def bezel(screen_img):
    sw, sh = screen_img.size
    bw = 44
    pad = 70
    fw, fh = sw + bw * 2, sh + bw * 2
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle([0, 0, fw - 1, fh - 1], 120, fill=(6, 8, 16))
    d.rounded_rectangle([6, 6, fw - 7, fh - 7], 116, outline=(40, 48, 72), width=3)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], 84, fill=255)
    frame.paste(screen_img, (bw, bw), mask)
    # punch-hole camera
    d.ellipse([fw // 2 - 12, bw + 20, fw // 2 + 12, bw + 44], fill=(2, 3, 8))

    canvas = Image.new("RGBA", (fw + pad * 2, fh + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [pad, pad + 12, pad + fw, pad + fh + 12], 120, fill=(0, 0, 0, 160))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(30)))
    canvas.alpha_composite(frame, (pad, pad))
    return canvas

ON_BOX = (8, 84, 919, 422)
OFF_BOX = (8, 92, 935, 424)

on_phone = bezel(screen(True, ON_BOX, ASSETS / "screenshot-on.png"))
off_phone = bezel(screen(False, OFF_BOX, ASSETS / "screenshot-off.png"))

on_phone.save(ASSETS / "android-on.png")
print("wrote android-on.png")
off_phone.save(ASSETS / "android-off.png")
print("wrote android-off.png")

canvas = Image.new("RGBA", (1600, 1100), (8, 13, 22, 255))
phone_h = 1000
phone_w = round(off_phone.width * phone_h / off_phone.height)
off_small = off_phone.resize((phone_w, phone_h), Image.LANCZOS)
on_small = on_phone.resize((phone_w, phone_h), Image.LANCZOS)
gap = 70
left = (canvas.width - phone_w * 2 - gap) // 2
canvas.alpha_composite(off_small, (left, 50))
canvas.alpha_composite(on_small, (left + phone_w + gap, 50))
canvas.convert("RGB").save(ASSETS / "android.png", quality=95)
print("wrote android.png")
