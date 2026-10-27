#!/usr/bin/env python3
"""
Builds the README artwork in docs/readme/ from the screenshots in screenshots/: a hero banner
with phones fanned out, one strip of framed phones per feature, the share cards, and an animated
preview of the sample ride video. Re-run after updating screenshots:

    python3 tools/readme_art.py

Needs Pillow and ffmpeg.
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "screenshots")
OUT = os.path.join(ROOT, "docs", "readme")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")

BG = (16, 16, 16)
YELLOW = (242, 206, 27)
GREY = (150, 150, 150)
WHITE = (244, 245, 247)
WIDTH = 1800


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def shot(name):
    return Image.open(os.path.join(SHOTS, name)).convert("RGB")


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return mask


def phone(name, width):
    """A screenshot in a dark phone frame: the top of the screen at a 9:19.5 phone shape."""
    im = shot(name)
    screen_h = int(im.width * 2600 / 1200)
    im = im.crop((0, 0, im.width, min(screen_h, im.height)))
    sw = width - int(width * 0.06)
    sh = int(im.height * sw / im.width)
    im = im.resize((sw, sh), Image.LANCZOS)
    bezel = (width - sw) // 2
    h = sh + bezel * 2
    frame = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, width - 1, h - 1), int(width * 0.13), fill=(8, 8, 8, 255), outline=(70, 70, 70, 255), width=3)
    frame.paste(im, (bezel, bezel), rounded_mask(im.size, int(width * 0.10)))
    return frame


def card(name, width, radius=28):
    """A share image as a rounded card."""
    im = shot(name)
    h = int(im.height * width / im.width)
    im = im.resize((width, h), Image.LANCZOS).convert("RGBA")
    im.putalpha(rounded_mask(im.size, radius))
    return im


def place(canvas, item, x, y, angle=0.0, shadow=True):
    """Pastes [item] centred at (x, y), rotated by [angle] degrees, on a soft shadow."""
    if angle:
        item = item.rotate(angle, resample=Image.BICUBIC, expand=True)
    if shadow:
        alpha = item.split()[3]
        sh = Image.new("RGBA", item.size, (0, 0, 0, 0))
        sh.putalpha(alpha.point(lambda a: int(a * 0.75)))
        pad = 60
        big = Image.new("RGBA", (item.width + pad * 2, item.height + pad * 2), (0, 0, 0, 0))
        big.paste(sh, (pad, pad))
        big = big.filter(ImageFilter.GaussianBlur(26))
        canvas.alpha_composite(big, (int(x - big.width / 2), int(y - big.height / 2 + 24)))
    canvas.alpha_composite(item, (int(x - item.width / 2), int(y - item.height / 2)))


def background(w, h, glow=(0.72, 0.5), glow_r=0.55, strength=60):
    """Near-black with a soft yellow glow, so the panel reads as one piece."""
    base = Image.new("RGBA", (w, h), BG + (255,))
    g = Image.new("L", (w, h), 0)
    gd = ImageDraw.Draw(g)
    cx, cy, r = w * glow[0], h * glow[1], max(w, h) * glow_r
    for i in range(40, 0, -1):
        rr = r * i / 40
        gd.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=int(strength * (1 - i / 40) ** 1.6))
    g = g.filter(ImageFilter.GaussianBlur(40))
    tint = Image.new("RGBA", (w, h), YELLOW + (255,))
    tint.putalpha(g)
    base.alpha_composite(tint)
    return base


def tracked(draw, xy, text, fnt, fill, spacing):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill)
        x += draw.textlength(ch, font=fnt) + spacing
    return x


def save(img, name):
    path = os.path.join(OUT, name)
    img.convert("RGB").save(path, quality=86, optimize=True, progressive=True)
    print(f"{name}: {img.width}x{img.height}, {os.path.getsize(path) // 1024} KB")


def hero():
    w, h = WIDTH, 900
    c = background(w, h, glow=(0.74, 0.55), glow_r=0.5, strength=70)
    d = ImageDraw.Draw(c)
    x = 100
    tracked(d, (x, 150), "COMPANION APP FOR THE V-STROM 250 SX", font("rajdhani_bold.ttf", 34), YELLOW, 4)
    d.text((x - 6, 190), "BUMBLEBEE", font=font("anton_regular.ttf", 190), fill=WHITE)
    body = font("nunito_semibold.ttf", 38)
    lines = ["Live cluster link, turn arrows on the bike,", "every ride logged, your garage in one place,", "and cinematic 3D films of your rides."]
    for i, line in enumerate(lines):
        d.text((x, 450 + i * 54), line, font=body, fill=(205, 205, 205))
    chips = ["Live telemetry", "Maps arrows", "Crash alerts", "3D ride films", "No account"]
    cx, cy = x, 660
    chip_font = font("rajdhani_bold.ttf", 30)
    for i, chip in enumerate(chips):
        tw = d.textlength(chip, font=chip_font)
        if cx + tw + 50 > 900:
            cx, cy = x, cy + 70
        filled = i == 0
        d.rounded_rectangle((cx, cy, cx + tw + 44, cy + 52), 26, fill=YELLOW if filled else None, outline=YELLOW, width=3)
        d.text((cx + 22, cy + 8), chip, font=chip_font, fill=(17, 17, 17) if filled else YELLOW)
        cx += tw + 64
    place(c, phone("Explore - Recap - Yearly 3.jpg", 320), 1110, 470, angle=9)
    place(c, phone("Explore - Rider DNA.jpg", 320), 1550, 470, angle=-9)
    place(c, phone("Cockpit.jpg", 350), 1330, 450)
    save(c, "hero.jpg")
    # The link preview in chats and social posts: GitHub's size, 1280×640, under 1 MB.
    # Upload it once in the repo's Settings → General → Social preview.
    save(c.resize((1280, 640), Image.LANCZOS), "social-preview.jpg")


def strip(name, items, labels, phone_w=330, gap=60, height=None, angle_step=0.0):
    n = len(items)
    total = n * phone_w + (n - 1) * gap
    phones = [phone(i, phone_w) for i in items]
    ph = max(p.height for p in phones)
    h = height or ph + 170
    c = background(WIDTH, h, glow=(0.5, 0.45), glow_r=0.6, strength=40)
    d = ImageDraw.Draw(c)
    x0 = (WIDTH - total) / 2 + phone_w / 2
    label_font = font("rajdhani_bold.ttf", 32)
    for i, p in enumerate(phones):
        cx = x0 + i * (phone_w + gap)
        angle = (i - (n - 1) / 2) * -angle_step
        place(c, p, cx, 40 + ph / 2, angle=angle)
        label = labels[i].upper()
        tw = sum(d.textlength(ch, font=label_font) + 3 for ch in label)
        tracked(d, (cx - tw / 2, 40 + ph + 45), label, label_font, GREY, 3)
    save(c, name)


def share_cards():
    w, h = WIDTH, 1020
    c = background(w, h, glow=(0.5, 0.5), glow_r=0.55, strength=60)
    place(c, card("Explore - Rider DNA - Share.jpg", 420), 330, 520, angle=-7)
    place(c, card("Garage - Fuel Log - Share Bill.jpg", 420), 1470, 520, angle=7)
    place(c, phone("Ride Detail - Export - Image 1.jpg", 360), 1210, 500, angle=3)
    place(c, card("Ride Detail - Export - Image - Sample.png", 470, radius=34), 760, 505, angle=-2)
    save(c, "share.jpg")


def video_preview():
    """The whole sample video as a looping animated WebP: a third of a GIF's size, sharper."""
    src = os.path.join(SHOTS, "Ride Detail - Export - Video - Sample.mp4")
    out = os.path.join(OUT, "ride-video.webp")
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", src, "-vf", "fps=15,scale=320:-1:flags=lanczos",
         "-c:v", "libwebp", "-lossless", "0", "-q:v", "65", "-loop", "0", "-an", out],
        check=True,
    )
    print(f"ride-video.webp: {os.path.getsize(out) // 1024} KB")


def main():
    os.makedirs(OUT, exist_ok=True)
    hero()
    strip("ride.jpg", ["Cockpit.jpg", "Live⁄Last Ride.jpg", "Ride Detail 1.jpg", "Ride Detail 2.jpg"], ["Cockpit", "Live ride", "Replay", "Ride score"])
    strip("explore.jpg", ["Explore 1.jpg", "Explore - Rider DNA.jpg", "Explore - Territory.jpg", "Explore - Badges.jpg"], ["Calendar", "Riding DNA", "Territory", "Badges"])
    strip(
        "recap.jpg",
        [f"Explore - Recap - Yearly {i}.jpg" for i in range(1, 6)],
        ["Kilometres", "Rides", "Longest ride", "Top speed", "Busiest month"],
        phone_w=290,
        gap=40,
        angle_step=2.5,
    )
    strip("garage.jpg", ["Garage.jpg", "Garage - Fuel Log.jpg", "Garage - Services - Parts.jpg", "Garage - Bike Passport.jpg"], ["Garage", "Fuel log", "Parts wear", "Bike passport"])
    strip("journey.jpg", ["Journey.jpg", "Journey-Cluster Check.jpg", "Garage - Settings 2.jpg", "Garage - Transfer.jpg"], ["Journey", "Cluster arrows", "Crash alerts", "Backup"])
    share_cards()
    studio = phone("Ride Detail - Export - Video.jpg", 600)
    s = Image.new("RGBA", (studio.width + 120, studio.height + 120), BG + (255,))
    place(s, studio, s.width / 2, s.height / 2 - 10)
    save(s, "studio.jpg")
    video_preview()


if __name__ == "__main__":
    sys.exit(main())
