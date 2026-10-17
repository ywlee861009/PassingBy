"""Export approved artwork to Android bitmaps, a Play icon, and mask previews.

Run with Python 3 and Pillow. Artwork edits belong in the source PNGs; this
script only resamples, composites the declared layers, and previews masks.
"""

from pathlib import Path
import math
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SIZE = 1080


def paths(data):
    """Read the absolute M/L/H/V/C/Z subset used by the monochrome source."""
    tokens = re.findall(r"[MLHVCZ]|[-+]?(?:\d*\.\d+|\d+)", data)
    polygons, points = [], []
    x = y = 0.0
    i = 0
    while i < len(tokens):
        command = tokens[i]
        count = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "Z": 0}[command]
        values = list(map(float, tokens[i + 1:i + 1 + count]))
        i += count + 1
        if command == "M":
            if points:
                polygons.append(points)
            x, y = values
            points = [(x, y)]
        elif command == "L":
            x, y = values
            points.append((x, y))
        elif command == "H":
            x = values[0]
            points.append((x, y))
        elif command == "V":
            y = values[0]
            points.append((x, y))
        elif command == "C":
            x1, y1, x2, y2, x3, y3 = values
            for step in range(1, 33):
                t = step / 32
                u = 1 - t
                points.append((u**3*x + 3*u*u*t*x1 + 3*u*t*t*x2 + t**3*x3,
                               u**3*y + 3*u*u*t*y1 + 3*u*t*t*y2 + t**3*y3))
            x, y = x3, y3
        else:
            polygons.append(points)
            x, y = points[0]
            points = []
    if points:
        polygons.append(points)
    return polygons


def monochrome():
    canvas = Image.new("L", (SIZE, SIZE))
    source = ET.parse(RES / "drawable/ic_launcher_monochrome.xml").getroot()
    for node in source.findall("path"):
        mask = Image.new("L", canvas.size)
        for polygon in paths(node.get(ANDROID + "pathData")):
            part = Image.new("L", canvas.size)
            ImageDraw.Draw(part).polygon([(x*10, y*10) for x, y in polygon], fill=255)
            combine = ImageChops.difference if node.get(ANDROID + "fillType") == "evenOdd" else ImageChops.lighter
            mask = combine(mask, part)
        canvas = ImageChops.lighter(canvas, mask)
    return canvas


def compose(background, foreground, inset, offset=(0, 0)):
    canvas = background.resize((SIZE, SIZE), Image.Resampling.LANCZOS).convert("RGBA")
    pad = round(SIZE * inset)
    layer = foreground.resize((SIZE - 2*pad, SIZE - 2*pad), Image.Resampling.LANCZOS)
    canvas.alpha_composite(layer, (pad + offset[0], pad + offset[1]))
    return canvas.crop((180, 180, 900, 900))


def shape_mask(size, kind):
    n = size * 4
    mask = Image.new("L", (n, n))
    draw = ImageDraw.Draw(mask)
    if kind == "circle":
        draw.ellipse((0, 0, n-1, n-1), fill=255)
    elif kind == "squircle":
        points = []
        for i in range(720):
            theta = math.tau * i / 720
            c, s = math.cos(theta), math.sin(theta)
            points.append((n/2 + (n/2-.5)*math.copysign(abs(c)**.5, c),
                           n/2 + (n/2-.5)*math.copysign(abs(s)**.5, s)))
        draw.polygon(points, fill=255)
    else:
        draw.rounded_rectangle((0, 0, n-1, n-1), radius=n*.23, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def font(size, bold=False):
    names = (["/System/Library/Fonts/Supplemental/Arial Bold.ttf", "DejaVuSans-Bold.ttf"]
             if bold else ["/System/Library/Fonts/Supplemental/Arial.ttf", "DejaVuSans.ttf"])
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def main():
    background = Image.open(OUT / "train-background.png").convert("RGB")
    foreground = Image.open(OUT / "train-foreground.png").convert("RGBA")
    source = ET.parse(RES / "drawable/ic_launcher_foreground.xml").getroot()
    inset = float(source.get(ANDROID + "inset").rstrip("%")) / 100
    target = RES / "drawable-nodpi"
    target.mkdir(exist_ok=True)
    for image, name in [(background, "background"), (foreground, "foreground")]:
        image.resize((432, 432), Image.Resampling.LANCZOS).save(
            target / f"ic_launcher_{name}_art.webp", lossless=True, method=6)

    # Store export uses the source-resolution masters; previews use shipped bitmaps.
    store = compose(background, foreground, inset).resize((512, 512), Image.Resampling.LANCZOS)
    store.save(OUT / "play-store-icon.png")
    bg = Image.open(target / "ic_launcher_background_art.webp").convert("RGBA")
    fg = Image.open(target / "ic_launcher_foreground_art.webp").convert("RGBA")
    color = compose(bg, fg, inset)
    mono_full = monochrome()
    mono = mono_full.crop((180, 180, 900, 900))

    def tile(size, shape="round", theme=None):
        if theme:
            base, ink = theme
            result = Image.new("RGBA", mono.size, base)
            tint = Image.new("RGBA", mono.size, ink)
            tint.putalpha(mono)
            result.alpha_composite(tint)
        else:
            result = color.copy()
        result = result.resize((size, size), Image.Resampling.LANCZOS)
        result.putalpha(shape_mask(size, shape))
        return result

    tile(384).save(OUT / "icon-preview.png")
    board = Image.new("RGB", (1080, 700), "#F6F1EA")
    draw = ImageDraw.Draw(board)
    draw.text((64, 40), "PASSING BY", font=font(33, True), fill="#2B211C")
    draw.text((65, 86), "A little journey through the countryside.", font=font(20), fill="#806D60")
    draw.line((64, 130, 1016, 130), fill="#DDD1C4")
    for cx, shape, label in [(208, "round", "Rounded square"), (540, "circle", "Circle"), (872, "squircle", "Squircle")]:
        image = tile(212, shape)
        board.paste(image, (cx-106, 170), image)
        draw.text((cx, 405), label, anchor="mt", font=font(18), fill="#6E5E51")
    draw.line((64, 457, 1016, 457), fill="#DDD1C4")
    for x, theme, label in [(88, ("#D9E7C4", "#354E3C"), "Themed / light"),
                             (295, ("#263B30", "#D9E7C4"), "Themed / dark")]:
        image = tile(120, theme=theme)
        board.paste(image, (x, 501), image)
        draw.text((x+60, 641), label, anchor="mt", font=font(16), fill="#6E5E51")
    draw.text((572, 507), "HOME SCREEN", font=font(15, True), fill="#806D60")
    for x, n in [(578, 48), (716, 64), (870, 80)]:
        image = tile(n, "circle")
        board.paste(image, (x, 545+(80-n)//2), image)
        draw.text((x+n/2, 641), f"{n} px", anchor="mt", font=font(16), fill="#6E5E51")
    board.save(OUT / "adaptive-preview.png")

    # Show that the independently moving foreground stays over an opaque landscape.
    motion = Image.new("RGB", (780, 280), "#F6F1EA")
    for i, dx in enumerate([-40, 0, 40]):
        image = compose(bg, fg, inset, (dx, 0)).resize((212, 212), Image.Resampling.LANCZOS)
        image.putalpha(shape_mask(212, "circle"))
        motion.paste(image, (24 + i*260, 20), image)
        ImageDraw.Draw(motion).text((130+i*260, 248), f"Foreground {dx/10:+.0f} dp", anchor="mt", font=font(16), fill="#6E5E51")
    motion.save(OUT / "parallax-preview.png")
    print("Exported Android layers, Play icon, mask/size/theme previews, and motion preview.")
    print("Foreground alpha:", fg.getchannel("A").getextrema())
    print("Play icon:", store.size, "alpha", store.getchannel("A").getextrema())
    print("Android bitmap bytes:", sum(p.stat().st_size for p in target.glob("ic_launcher_*_art.webp")))


if __name__ == "__main__":
    main()
