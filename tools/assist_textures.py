"""Draws the Poké Assist sprites for the capture minigame: rock crags, ice shards, ghost wisps, smoke puffs, poison
bubbles, a heart, a snowflake, a sparkle and the styler battery shown by an Electric assist.

assists.png is a grid of 16x16 cells, one row per sprite kind; the columns are variants or frames. Crags and shards
stand on the bottom edge of their cell, the rest are centred. Bigger effects (the fire wall, the bubble, the clock,
line styles) are drawn in code by client/CaptureScreen.

Row order must match client/AssistArt.java. Run from the repository root: python3 tools/assist_textures.py
"""
import math
import random

from PIL import Image, ImageDraw, ImageFilter

OUT = "src/main/resources/assets/cobblemonranger/textures/gui/capture/assists.png"
CELL = 16


def outline(img, color):
    alpha = img.split()[3].point(lambda v: 255 if v > 40 else 0)
    grown = alpha.filter(ImageFilter.MaxFilter(3))
    out = Image.new("RGBA", img.size)
    out.paste(Image.new("RGBA", img.size, color), (0, 0), grown)
    out.alpha_composite(img)
    return out


def cell():
    return Image.new("RGBA", (CELL, CELL))


def crag(seed):
    rnd = random.Random(seed)
    img = cell()
    d = ImageDraw.Draw(img)
    h, w, lean = rnd.randint(9, 13), rnd.randint(4, 5), rnd.uniform(-2, 2)
    x, y = 8, 14
    d.polygon([(x - w, y), (x + w, y), (x + lean, y - h)], fill=(120, 96, 76, 255))
    d.polygon([(x, y), (x + w - 1, y), (x + lean, y - h + 1)], fill=(92, 72, 58, 255))
    d.line([(x - w + 2, y - 1), (x + lean - 1, y - h + 3)], fill=(170, 144, 116, 255))
    return outline(img, (50, 36, 30, 255))


def shard(seed):
    rnd = random.Random(seed + 10)
    img = cell()
    d = ImageDraw.Draw(img)
    h, w = rnd.randint(9, 13), rnd.randint(2, 4)
    x, y = 8, 14
    d.polygon([(x - w, y), (x, y - h), (x + w, y)], fill=(150, 214, 245, 255))
    d.polygon([(x, y - h), (x + w, y), (x, y)], fill=(100, 170, 220, 255))
    d.line([(x - 1, y - h + 2), (x - 2, y - 2)], fill=(255, 255, 255, 255))
    return outline(img, (30, 60, 100, 255))


def wisp(frame):
    img = cell()
    d = ImageDraw.Draw(img)
    col = (180, 150, 230, 235)
    top = 2 + frame
    d.ellipse([4, top, 11, top + 7], fill=col)
    tail = [(4, top + 4), (11, top + 4), (10, top + 11), (8, top + 9 - frame), (6, top + 11), (5, top + 8 + frame)]
    d.polygon(tail, fill=col)
    d.point([(6, top + 3), (9, top + 3)], fill=(30, 10, 40, 255))
    return outline(img, (40, 20, 60, 255))


def smoke(variant):
    img = cell()
    d = ImageDraw.Draw(img)
    r = 4 + variant
    d.ellipse([8 - r, 8 - r, 8 + r, 8 + r], fill=(50, 40, 60, 225))
    d.ellipse([8 - r + 1, 8 - r + 1, 8 - r + 3, 8 - r + 3], fill=(110, 100, 120, 255))
    return outline(img, (20, 15, 25, 255))


def poison(size):
    img = cell()
    d = ImageDraw.Draw(img)
    r = 2 + size
    d.ellipse([8 - r, 8 - r, 8 + r, 8 + r], fill=(190, 100, 220, 255))
    d.point((8 - r // 2, 8 - r // 2), fill=(240, 200, 255, 255))
    return outline(img, (80, 30, 100, 255))


def heart():
    img = cell()
    d = ImageDraw.Draw(img)
    col = (255, 130, 180, 255)
    d.ellipse([3, 4, 8, 9], fill=col)
    d.ellipse([8, 4, 13, 9], fill=col)
    d.polygon([(3, 7), (13, 7), (8, 12)], fill=col)
    d.point((5, 5), fill=(255, 255, 255, 255))
    return outline(img, (120, 30, 70, 255))


def snowflake():
    img = cell()
    d = ImageDraw.Draw(img)
    d.line([(5, 8), (11, 8)], fill=(255, 255, 255, 255))
    d.line([(8, 5), (8, 11)], fill=(255, 255, 255, 255))
    d.point([(6, 6), (10, 10), (6, 10), (10, 6)], fill=(200, 230, 255, 255))
    return outline(img, (70, 130, 200, 200))


def sparkle():
    img = cell()
    d = ImageDraw.Draw(img)
    d.line([(6, 8), (10, 8)], fill=(255, 250, 230, 255))
    d.line([(8, 6), (8, 10)], fill=(255, 250, 230, 255))
    return img


def battery(level):
    img = cell()
    d = ImageDraw.Draw(img)
    d.rectangle([1, 4, 13, 11], fill=(40, 40, 50, 235), outline=(20, 20, 20, 255))
    d.rectangle([14, 6, 15, 9], fill=(200, 200, 200, 255))
    if level:
        d.rectangle([3, 6, 2 + level * 2 + (level == 5), 9], fill=(120, 230, 90, 255))
    d.polygon([(8, 5), (5, 8), (8, 8), (7, 11), (10, 7), (8, 7)], fill=(255, 240, 90, 255))
    return img


ROWS = [
    [crag(i) for i in range(4)],
    [shard(i) for i in range(4)],
    [wisp(i) for i in range(2)],
    [smoke(i) for i in range(3)],
    [poison(i) for i in range(3)],
    [heart(), snowflake(), sparkle()],
    [battery(i) for i in range(6)],
]

atlas = Image.new("RGBA", (8 * CELL, len(ROWS) * CELL))
for r, row in enumerate(ROWS):
    for c, sprite in enumerate(row):
        atlas.alpha_composite(sprite, (c * CELL, r * CELL))
atlas.save(OUT)
print("wrote", OUT, atlas.size)
