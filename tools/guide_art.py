"""Draws the Ranger Guide's page illustrations (96x56 pixel art, one per page) in the Browser's navy and light blue.

Run from the repository root: python3 tools/guide_art.py
Output: src/main/resources/assets/cobblemonranger/textures/gui/guide/<page>.png
"""
import math
import os

from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/cobblemonranger/textures/gui/guide"
W, H = 96, 56


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, 255)


NAVY, CELL, CELL_HI, INSET = rgb(0x101820), rgb(0x1C2A36), rgb(0x29404F), rgb(0x0B1118)
ACCENT, ACCENT_LIGHT, ACCENT_DARK = rgb(0x5BC0EB), rgb(0xA8F0FF), rgb(0x2F7FA3)
GOLD, GOLD_DARK = rgb(0xFFC94A), rgb(0xC98A1E)
WHITE, DIM = rgb(0xE0E6EB), rgb(0x6A7C8C)
RED, RED_LIGHT, RED_DARK = rgb(0xE03A30), rgb(0xFF6B57), rgb(0x9E2A2F)
GRASS, GRASS_LIGHT, GRASS_DARK = rgb(0x4F9A3A), rgb(0x7CC45A), rgb(0x356B2A)
OUTLINE = rgb(0x1A1C2C)
FIRE, FIRE_LIGHT = rgb(0xF08030), rgb(0xFFD23F)
PINK = rgb(0xFF7FA8)
STONE, STONE_LIGHT, STONE_DARK = rgb(0x8C8C94), rgb(0xB4B4BC), rgb(0x5A5A66)
LEAF, LEAF_DARK = rgb(0x3E8E3A), rgb(0x2A5E28)


def canvas(bg=CELL):
    img = Image.new("RGBA", (W, H), bg)
    return img, ImageDraw.Draw(img)


def ground(d, top=40, light=GRASS_LIGHT, mid=GRASS, dark=GRASS_DARK):
    d.rectangle([0, top, W - 1, H - 1], fill=mid)
    d.line([0, top, W - 1, top], fill=light)
    for i in range(0, W, 7):
        d.point((i + (i // 7) % 3, top + 4 + (i * 5) % 9), fill=dark)
        d.point((i + 3, top + 2 + (i * 3) % 7), fill=light)


def critter(d, cx, cy, body=rgb(0xF2C14E), shade=rgb(0xC9922E), facing=1):
    """An original round critter with ear tufts: the stand-in Pokémon of every picture."""
    d.ellipse([cx - 7, cy - 6, cx + 7, cy + 6], fill=OUTLINE)
    d.ellipse([cx - 6, cy - 5, cx + 6, cy + 5], fill=body)
    d.chord([cx - 6, cy - 5, cx + 6, cy + 5], 20, 160, fill=shade)
    for ex in (-4, 3):
        d.polygon([(cx + ex, cy - 4), (cx + ex + 1, cy - 9), (cx + ex + 3, cy - 4)], fill=OUTLINE)
        d.point((cx + ex + 1, cy - 6), fill=body)
    eye = cx + 2 * facing
    d.point((eye, cy - 1), fill=OUTLINE)
    d.point((eye + 3 * facing, cy - 1), fill=OUTLINE)
    d.point((eye + facing, cy + 2), fill=RED_DARK)
    d.line([cx - 4, cy + 6, cx - 2, cy + 6], fill=OUTLINE)
    d.line([cx + 2, cy + 6, cx + 4, cy + 6], fill=OUTLINE)


def big_critter(img, cx, cy, body=rgb(0xF2C14E), shade=rgb(0xC9922E), facing=1):
    """The critter at double size, pasted centred on (cx, cy)."""
    tile = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    critter(ImageDraw.Draw(tile), 9, 10, body, shade, facing)
    tile = tile.resize((36, 36), Image.NEAREST)
    img.alpha_composite(tile, (cx - 18, cy - 20))


def styler(d, x, y, scale=1):
    """The capture styler seen from the front: red shell, light blue screen."""
    s = scale
    d.rounded_rectangle([x, y, x + 13 * s, y + 21 * s], radius=2 * s, fill=OUTLINE)
    d.rounded_rectangle([x + s, y + s, x + 12 * s, y + 20 * s], radius=2 * s, fill=RED)
    d.line([x + s, y + 2 * s, x + s, y + 18 * s], fill=RED_LIGHT, width=s)
    d.rectangle([x + 3 * s, y + 3 * s, x + 10 * s, y + 10 * s], fill=OUTLINE)
    d.rectangle([x + 4 * s, y + 4 * s, x + 9 * s, y + 9 * s], fill=ACCENT)
    d.point((x + 5 * s, y + 5 * s), fill=ACCENT_LIGHT)
    d.ellipse([x + 5 * s, y + 13 * s, x + 8 * s, y + 16 * s], fill=GOLD)
    d.line([x + 3 * s, y + 18 * s, x + 10 * s, y + 18 * s], fill=RED_DARK, width=s)


def loop(d, cx, cy, rx, ry, color=ACCENT, gap=None, width=1):
    points = []
    for i in range(0, 361, 6):
        if gap and gap[0] <= i <= gap[1]:
            if len(points) > 1:
                d.line(points, fill=color, width=width)
            points = []
            continue
        a = math.radians(i)
        points.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    if len(points) > 1:
        d.line(points, fill=color, width=width)


def sparkle(d, x, y, color=ACCENT_LIGHT, size=2):
    d.line([x - size, y, x + size, y], fill=color)
    d.line([x, y - size, x, y + size], fill=color)


def heart(d, x, y, color=PINK):
    d.point((x - 1, y - 1), fill=color)
    d.point((x + 1, y - 1), fill=color)
    d.line([x - 2, y, x + 2, y], fill=color)
    d.line([x - 1, y + 1, x + 1, y + 1], fill=color)
    d.point((x, y + 2), fill=color)


def bar(d, x, y, w, segments, filled, color=ACCENT, empty=INSET):
    seg = w // segments
    for i in range(segments):
        d.rectangle([x + i * seg, y, x + i * seg + seg - 2, y + 3], fill=color if i < filled else empty)


def frame_border(img):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], outline=INSET)
    return img


# ------------------------------------------------------------------ pages

def page_styler():
    img, d = canvas()
    styler(d, 12, 8, 2)
    # Energy bar refilling.
    d.text((48, 9), "ENERGY", fill=DIM)
    d.rectangle([48, 20, 88, 27], fill=OUTLINE)
    d.rectangle([49, 21, 76, 26], fill=GOLD)
    d.line([49, 21, 76, 21], fill=FIRE_LIGHT)
    for i, x in enumerate(range(52, 88, 6)):
        if x > 76:
            sparkle(d, x, 33 + (i % 2) * 3, ACCENT_LIGHT, 1)
    d.text((70, 38), "+1", fill=ACCENT)
    return img


def page_capturing():
    img, d = canvas(rgb(0x6DB34F))
    ground(d, 0)
    loop(d, 48, 28, 32, 22, ACCENT_LIGHT, width=2)
    loop(d, 48, 28, 26, 17, ACCENT, gap=(200, 260))
    big_critter(img, 48, 30)
    # A spark from the critter hitting the line.
    d.line([62, 18, 70, 10], fill=FIRE_LIGHT)
    d.line([70, 10, 68, 14], fill=FIRE_LIGHT)
    sparkle(d, 73, 8, WHITE, 2)
    # Stylus tip drawing the loop.
    d.line([80, 30, 88, 40], fill=OUTLINE, width=2)
    d.point((80, 30), fill=WHITE)
    return img


def page_browser():
    img, d = canvas()
    colors = [rgb(0x78C850), None, rgb(0xF08030), None, rgb(0x6890F0), None, None, rgb(0xF8D030), None]
    for i, c in enumerate(colors):
        x, y = 6 + (i % 3) * 18, 6 + (i // 3) * 15
        d.rectangle([x, y, x + 15, y + 12], fill=CELL_HI if c else INSET)
        if c:
            d.line([x, y, x, y + 12], fill=c)
            d.line([x + 3, y + 8, x + 11, y + 8], fill=WHITE)
        else:
            d.text((x + 5, y + 1), "?", fill=DIM)
    d.rectangle([5, 5, 22, 19], outline=ACCENT)
    d.rectangle([62, 6, 90, 50], fill=INSET)
    d.ellipse([66, 36, 86, 40], outline=CELL_HI)
    big_critter(img, 76, 28, rgb(0x78C850), rgb(0x4E9A38))
    d.line([66, 44, 86, 44], fill=WHITE)
    return img


def page_friends():
    img, d = canvas(rgb(0x6DB34F))
    ground(d, 0)
    # Ranger: simple figure in red and navy.
    d.rectangle([24, 18, 31, 25], fill=rgb(0xE8B48A))
    d.rectangle([24, 16, 31, 18], fill=OUTLINE)
    d.point((26, 21), fill=OUTLINE)
    d.point((29, 21), fill=OUTLINE)
    d.rectangle([23, 26, 32, 37], fill=RED)
    d.rectangle([23, 26, 24, 37], fill=RED_DARK)
    d.rectangle([24, 38, 27, 46], fill=NAVY)
    d.rectangle([28, 38, 31, 46], fill=NAVY)
    big_critter(img, 58, 36, facing=-1)
    heart(d, 44, 16)
    heart(d, 52, 10)
    heart(d, 60, 20)
    return img


def page_assists():
    img, d = canvas(rgb(0x6DB34F))
    ground(d, 0)
    loop(d, 66, 27, 26, 19, ACCENT_LIGHT, width=1)
    big_critter(img, 68, 29, rgb(0x9C7AE8), rgb(0x6B4FB8), facing=-1)
    critter(d, 14, 40, rgb(0xF08030), rgb(0xB85A1E))
    # A fire wall burst from the helper.
    for i, x in enumerate(range(26, 46, 4)):
        h = 6 + (i % 2) * 4
        d.polygon([(x, 44), (x + 2, 44 - h), (x + 4, 44)], fill=FIRE)
        d.point((x + 2, 44 - h + 3), fill=FIRE_LIGHT)
    sparkle(d, 40, 22, FIRE_LIGHT, 2)
    return img


def page_bosses():
    img, d = canvas(rgb(0x5E9E46))
    ground(d, 0)
    # A glowing crack erupting across the arena.
    crack = [(4, 50), (16, 44), (24, 47), (36, 40), (44, 43)]
    d.line(crack, fill=OUTLINE, width=3)
    d.line(crack[:3], fill=FIRE, width=2)
    d.line(crack[:2], fill=FIRE_LIGHT)
    sparkle(d, 26, 44, FIRE_LIGHT, 2)
    # An Alpha's red aura and the shield ring round a big critter.
    for r, c in ((22, rgb(0x9E2A2F)), (19, RED), (17, rgb(0x5E9E46))):
        d.ellipse([60 - r, 24 - r * 0.8, 60 + r, 24 + r * 0.8], outline=c)
    big_critter(img, 60, 26, rgb(0x9C7AE8), rgb(0x6B4FB8), facing=-1)
    # A small pixel alpha sign.
    for y, row in enumerate([".##.#", "#..#.", "#..#.", ".##.#"]):
        for x, c in enumerate(row):
            if c == "#":
                d.point((84 + x, 4 + y), fill=RED_LIGHT)
    # Friendship gauge with two stage notches.
    d.rectangle([8, 6, 41, 11], fill=OUTLINE)
    bar(d, 9, 7, 33, 10, 5, GOLD)
    for x in (19, 30):
        d.line([x, 3, x, 14], fill=OUTLINE, width=2)
        d.line([x, 4, x, 13], fill=GOLD if x < 25 else RED_LIGHT)
    return img


def page_partner():
    img, d = canvas()
    big_critter(img, 24, 36)
    # Partner star badge.
    star = [(24 + 6 * math.cos(math.radians(a)) * (1 if k % 2 == 0 else 0.45),
             12 + 6 * math.sin(math.radians(a)) * (1 if k % 2 == 0 else 0.45))
            for k, a in enumerate(range(-90, 270, 36))]
    d.polygon(star, fill=GOLD, outline=GOLD_DARK)
    d.text((46, 10), "CHARGE", fill=DIM)
    bar(d, 46, 22, 44, 10, 7, ACCENT)
    loop(d, 68, 40, 10, 6, ACCENT_LIGHT)
    d.text((63, 35), "x7", fill=ACCENT)
    return img


def page_field_moves():
    img, d = canvas(rgb(0x6DB34F))
    ground(d, 0)
    # Bush being cut.
    d.ellipse([10, 18, 34, 42], fill=LEAF_DARK)
    d.ellipse([12, 19, 32, 39], fill=LEAF)
    d.line([8, 40, 36, 16], fill=WHITE, width=2)
    d.line([12, 42, 38, 20], fill=ACCENT_LIGHT)
    # Cracked rock.
    d.polygon([(56, 42), (60, 24), (74, 20), (84, 30), (82, 42)], fill=STONE, outline=STONE_DARK)
    d.line([61, 26, 72, 22], fill=STONE_LIGHT)
    d.line([70, 22, 67, 30, 72, 34, 69, 42], fill=OUTLINE)
    sparkle(d, 86, 18, WHITE, 2)
    sparkle(d, 52, 20, WHITE, 1)
    return img


def page_field_move_picker():
    """The move picker: field move icons (from tools/field_move_art.py) in a ring around the crosshair."""
    img, d = canvas(rgb(0x6DB34F))
    d.rectangle([0, 0, W - 1, 30], fill=rgb(0x8FD3F5))
    atlas = Image.open("src/main/resources/assets/cobblemonranger/textures/gui/field_moves.png").convert("RGBA")
    order = ["headbutt", "burn", "surf", "cut", "flash", "freeze", "rock_smash", "decompose", "dig", "tailwind",
             "telekinesis", "string_shot", "stone_edge", "spook", "outrage", "snatch", "magnet", "charm"]
    cx, cy = W // 2, H // 2 + 1
    shown = ["flash", "tailwind", "charm", "string_shot", "magnet", "spook"]
    for i, name in enumerate(shown):
        a = -math.pi / 2 + i * 2 * math.pi / len(shown)
        x = int(round(cx + 30 * math.cos(a))) - 10
        y = int(round(cy + 17 * math.sin(a))) - 10
        k = order.index(name)
        tile = atlas.crop(((k % 6) * 20, (k // 6) * 20, (k % 6) * 20 + 20, (k // 6) * 20 + 20))
        if i == 0:
            d.rectangle([x - 1, y - 1, x + 20, y + 20], outline=GOLD)
        img.alpha_composite(tile, (x, y))
    d.line([cx - 2, cy, cx + 2, cy], fill=WHITE)
    d.line([cx, cy - 2, cx, cy + 2], fill=WHITE)
    return img


def page_missions():
    img, d = canvas()
    # The HQ terminal.
    d.rectangle([8, 16, 33, 45], fill=rgb(0xA2A9B8), outline=rgb(0x6F7789))
    d.rectangle([8, 16, 33, 19], fill=RED)
    d.rectangle([12, 23, 29, 36], fill=rgb(0x1E2F4A))
    loop(d, 20, 29, 5, 4, ACCENT)
    # Ranger villager with a mission bubble.
    d.rectangle([52, 22, 61, 31], fill=rgb(0xBD8B72))
    d.rectangle([55, 26, 56, 33], fill=rgb(0x9C6E58))
    d.rectangle([50, 32, 63, 47], fill=RED)
    d.rectangle([50, 32, 63, 34], fill=GOLD)
    d.rounded_rectangle([66, 6, 88, 20], radius=3, fill=WHITE)
    d.polygon([(68, 20), (72, 20), (64, 25)], fill=WHITE)
    d.rectangle([76, 8, 77, 14], fill=RED)
    d.rectangle([76, 16, 77, 17], fill=RED)
    return img


def page_upgrades():
    img, d = canvas()
    styler(d, 8, 10, 2)
    d.line([40, 30, 52, 30], fill=ACCENT, width=2)
    d.polygon([(52, 26), (57, 30), (52, 34)], fill=ACCENT)
    # Module chip.
    d.rectangle([62, 20, 84, 40], fill=OUTLINE)
    d.rectangle([63, 21, 83, 39], fill=ACCENT_DARK)
    d.rectangle([67, 25, 79, 35], fill=ACCENT)
    for k in range(4):
        d.line([65 + k * 5, 17, 65 + k * 5, 19], fill=GOLD)
        d.line([65 + k * 5, 41, 65 + k * 5, 43], fill=GOLD)
    d.text((70, 25), "+", fill=WHITE)
    return img


def page_signs():
    img, d = canvas(rgb(0x14203A))
    for x, y in [(8, 6), (22, 14), (84, 8), (70, 46), (12, 44), (90, 30)]:
        d.point((x, y), fill=DIM)
    pts = [(20, 40), (34, 14), (48, 34), (62, 12), (76, 40)]
    d.line(pts, fill=ACCENT_DARK, width=5)
    d.line(pts, fill=ACCENT, width=3)
    d.line(pts, fill=ACCENT_LIGHT, width=1)
    for p in pts:
        sparkle(d, p[0], p[1], WHITE, 2)
    return img


PAGES = {
    "styler": page_styler, "capturing": page_capturing, "browser": page_browser, "friends": page_friends,
    "assists": page_assists, "bosses": page_bosses, "partner": page_partner, "field_moves": page_field_moves,
    "field_move_picker": page_field_move_picker, "missions": page_missions,
    "upgrades": page_upgrades, "signs": page_signs,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, draw in PAGES.items():
        frame_border(draw()).save(os.path.join(OUT, name + ".png"))
    sheet = Image.new("RGBA", (W * 2 * 5 + 40, H * 2 * 3 + 32), NAVY)
    for i, name in enumerate(PAGES):
        tile = Image.open(os.path.join(OUT, name + ".png")).resize((W * 2, H * 2), Image.NEAREST)
        sheet.paste(tile, (8 + (i % 5) * (W * 2 + 8), 8 + (i // 5) * (H * 2 + 8)))
    os.makedirs("build", exist_ok=True)
    sheet.save("build/guide_art_sheet.png")


if __name__ == "__main__":
    main()
