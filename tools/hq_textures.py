"""Draws the Ranger HQ textures: the terminal block (16x16 faces) and the Ranger villager's uniform (64x64 overlay).

Run from the repository root: python3 tools/hq_textures.py
"""
import os

from PIL import Image

ASSETS = "src/main/resources/assets/cobblemonranger/textures"

OUTLINE = (26, 28, 44)


def hexrgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def save(pixels, path, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for (x, y), color in pixels.items():
        if 0 <= x < size and 0 <= y < size:
            img.putpixel((x, y), color + (255,) if len(color) == 3 else color)
    full = os.path.join(ASSETS, path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


# ---------------------------------------------------------------- terminal block
# A Ranger Union terminal: grey metal casing with a red Ranger stripe, a screen showing a styler loop on the front.

METAL_LIGHT, METAL, METAL_DARK = hexrgb(0xC9CED8), hexrgb(0xA2A9B8), hexrgb(0x6F7789)
RED_LIGHT, RED, RED_DARK = hexrgb(0xFF6B57), hexrgb(0xE03A30), hexrgb(0x9E2A2F)
SCREEN_DARK, SCREEN_MID, LOOP, LOOP_LIGHT = hexrgb(0x1E2F4A), hexrgb(0x2B4C73), hexrgb(0x3FCBFF), hexrgb(0xA8F0FF)
GOLD = hexrgb(0xFFD23F)


def casing():
    """Metal panel with a bevelled border; shared by every face."""
    px = {}
    for x in range(16):
        for y in range(16):
            if x == 0 or y == 0:
                px[(x, y)] = METAL_LIGHT
            elif x == 15 or y == 15:
                px[(x, y)] = METAL_DARK
            else:
                px[(x, y)] = METAL
    # Rivets in the corners.
    for p in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[p] = METAL_DARK
    return px


def stripe(px, top_row):
    for x in range(16):
        px[(x, top_row)] = RED_LIGHT
        px[(x, top_row + 1)] = RED
        px[(x, top_row + 2)] = RED_DARK


def front():
    px = casing()
    # Screen with a bezel.
    for x in range(2, 14):
        for y in range(2, 11):
            if x in (2, 13) or y in (2, 10):
                px[(x, y)] = OUTLINE
            else:
                px[(x, y)] = SCREEN_MID if x + y < 13 else SCREEN_DARK
    # A styler loop drawn on the screen.
    loop = [(6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (10, 5), (5, 6), (10, 6), (5, 7), (10, 7), (6, 8), (7, 8),
            (8, 8), (9, 8)]
    for p in loop:
        px[p] = LOOP
    px[(6, 4)] = LOOP_LIGHT
    px[(5, 5)] = LOOP_LIGHT
    px[(7, 6)] = GOLD
    px[(8, 6)] = GOLD
    # Glint.
    px[(3, 3)] = (255, 255, 255)
    # Keyboard / control strip.
    stripe(px, 11)
    for x in (4, 6, 9, 11):
        px[(x, 12)] = (255, 255, 255) if x != 9 else GOLD
    return px


def side():
    px = casing()
    stripe(px, 11)
    # Vents.
    for y in (4, 6, 8):
        for x in range(4, 12):
            px[(x, y)] = METAL_DARK
            px[(x, y - 1)] = METAL_LIGHT if x in (4, 11) else px[(x, y - 1)]
    return px


def top():
    px = casing()
    # A red Ranger emblem: a ring around a gold star.
    ring = [(6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (10, 5), (4, 6), (11, 6), (4, 7), (11, 7), (4, 8), (11, 8),
            (4, 9), (11, 9), (5, 10), (10, 10), (6, 11), (7, 11), (8, 11), (9, 11)]
    for p in ring:
        px[p] = RED
    for p in [(7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (6, 9), (9, 9)]:
        px[p] = GOLD
    return px


def bottom():
    px = casing()
    for x in range(1, 15):
        for y in range(1, 15):
            px[(x, y)] = METAL_DARK if (x + y) % 4 == 0 else METAL
    return px


# ---------------------------------------------------------------- villager uniform
# Painted over the villager (and zombie villager) model: a red Ranger jacket with white trim on the body and arms, and a
# red cap. UVs follow the vanilla villager model (64x64).

def box(px, u, v, w, h, d, faces, rows=None):
    """Fills faces of a box unwrap. faces maps face name -> function(x, y, fw, fh) returning a colour or None."""
    layout = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for name, paint in faces.items():
        fu, fv, fw, fh = layout[name]
        for x in range(fw):
            for y in range(fh):
                if rows is not None and name not in ("top", "bottom") and y >= rows:
                    continue
                color = paint(x, y, fw, fh)
                if color is not None:
                    px[(fu + x, fv + y)] = color


def uniform():
    px = {}
    white, collar = hexrgb(0xF4F4F4), hexrgb(0xD8DCE6)

    def jacket_front(x, y, w, h):
        if y == 0:
            return collar
        if x in (3, 4):
            return white if y < 12 else None  # zip
        if y == 3 and x == 1:
            return GOLD  # Ranger badge
        return (RED if y % 5 else RED_DARK) if y < 12 else None

    def jacket_side(x, y, w, h):
        if y >= 12:
            return None
        return RED_DARK if y == 11 else RED

    def jacket_back(x, y, w, h):
        if y >= 12:
            return None
        if 2 <= x <= 5 and 3 <= y <= 5:
            return white  # back patch
        return RED_DARK if y == 11 else RED

    # Jacket layer (slightly larger than the body), only the top 12 rows so the robe shows below.
    box(px, 0, 38, 8, 20, 6, {"front": jacket_front, "right": jacket_side, "left": jacket_side,
                              "back": jacket_back, "top": lambda x, y, w, h: RED})

    def sleeve(x, y, w, h):
        return white if y == h - 2 else RED

    # Crossed arms: the two upper arms and the forearm bar.
    box(px, 44, 22, 4, 8, 4, {f: sleeve for f in ("front", "back", "left", "right")})
    box(px, 44, 22, 4, 8, 4, {"top": lambda x, y, w, h: RED, "bottom": lambda x, y, w, h: RED})
    box(px, 40, 38, 8, 4, 4, {f: (lambda x, y, w, h: RED if y < 3 else white) for f in
                              ("front", "back", "top", "bottom", "left", "right")})

    # Cap on the hat layer: crown on top and the upper three rows of each side, with a white front panel.
    def cap_side(x, y, w, h):
        return RED_DARK if y == 2 else RED

    def cap_front(x, y, w, h):
        if y == 2:
            return OUTLINE  # brim shadow
        return white if 2 <= x <= 5 and y < 2 else RED

    box(px, 32, 0, 8, 10, 8, {"top": lambda x, y, w, h: RED, "front": cap_front, "back": cap_side,
                              "left": cap_side, "right": cap_side}, rows=3)
    return px


if __name__ == "__main__":
    save(front(), "block/ranger_hq_front")
    save(side(), "block/ranger_hq_side")
    save(top(), "block/ranger_hq_top")
    save(bottom(), "block/ranger_hq_bottom")
    suit = uniform()
    save(suit, "entity/villager/profession/ranger", 64)
    save(suit, "entity/zombie_villager/profession/ranger", 64)
