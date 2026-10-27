"""Draws the item textures (styler layers and upgrade modules) as 16x16 pixel art.

Run from the repository root: python3 tools/item_textures.py
"""
import math
import os

from PIL import Image

OUT = "src/main/resources/assets/cobblemonranger/textures/item"

OUTLINE = (26, 28, 44)


def hexrgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def save(pixels, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), color in pixels.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), color + (255,) if len(color) == 3 else color)
    img.save(os.path.join(OUT, name + ".png"))


def neighbours(x, y):
    return [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]


# ---------------------------------------------------------------- styler
# Layer 0 is the dyeable shell, drawn in greys so the item tint gives the colour. Layer 1 is everything that keeps its
# own colour: outline, screen, lens, highlight and the status light.

def styler():
    """A round styler on a wrist band, seen from the front."""
    cx, cy, radius = 8.0, 6.6, 6.4
    disc = {(x, y) for x in range(16) for y in range(16) if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= radius}
    band = set()
    for x in range(16):
        for y in range(16):
            e = ((x + 0.5 - 8.0) / 7.9) ** 2 + ((y + 0.5 - 10.4) / 5.0) ** 2
            if 0.3 <= e <= 1.0 and y >= 8:
                band.add((x, y))
    body = disc | band
    disc_outline = {p for p in disc if any(n not in disc for n in neighbours(*p))}
    band_outline = {p for p in band - disc if any(n not in band for n in neighbours(*p))}
    outline = disc_outline | band_outline

    shell, top = {}, {}
    for (x, y) in body - outline:
        if (x, y) in disc:
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < radius - 2.6:
                continue  # screen, drawn on layer 1
            nx, ny = (x + 0.5 - cx) / d, (y + 0.5 - cy) / d
            light = -(nx * 0.8 + ny) / 1.28
        else:
            light = 0.3 if y <= 11 else -0.3 if y <= 13 else -0.8
            if x <= 2 or x >= 13:
                light -= 0.4
        shade = 255 if light > 0.5 else 222 if light > 0.0 else 186 if light > -0.55 else 146
        shell[(x, y)] = (shade, shade, shade)

    for p in outline:
        top[p] = OUTLINE

    screen_dark, screen_mid = hexrgb(0x1E2F4A), hexrgb(0x2B4C73)
    lens, lens_light, white = hexrgb(0x3FCBFF), hexrgb(0xA8F0FF), (255, 255, 255)
    for (x, y) in disc - outline:
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if d >= radius - 2.6:
            continue
        if d >= radius - 3.3:
            top[(x, y)] = OUTLINE  # bezel between shell and glass
        elif d < 1.8:
            top[(x, y)] = lens_light if (x + 0.5 < cx and y + 0.5 < cy) else lens
        else:
            top[(x, y)] = screen_mid if (x + y) < cx + cy - 1 else screen_dark
    # Glass glint, capture lights on the rim and a clasp on the band.
    top[(6, 4)] = white
    top[(7, 4)] = white
    top[(6, 5)] = white
    top[(8, 1)] = hexrgb(0xFFD23F)
    top[(13, 6)] = hexrgb(0xFF5A3C)
    top[(2, 6)] = hexrgb(0x5BE07A)
    for x in (7, 8):
        top[(x, 14)] = hexrgb(0x8B93A8)
    return shell, top


# ---------------------------------------------------------------- modules
# A cartridge that slots into the styler: coloured case, gold contacts and an icon window.

ICONS = {
    "energy": [  # lightning bolt
        "...YY.",
        "..YY..",
        ".YYYY.",
        "...YY.",
        "..YY..",
        ".Y....",
    ],
    "line": [  # a drawn loop
        "..CCC.",
        ".C...C",
        "C..C.C",
        "C...C.",
        ".CCC..",
        "...C..",
    ],
    "power": [  # burst star
        "..W...",
        "..W...",
        "WWWWW.",
        ".WWW..",
        ".W.W..",
        "W...W.",
    ],
}
ICON_COLORS = {"Y": hexrgb(0xFFE14D), "C": hexrgb(0x7DF3FF), "W": hexrgb(0xFFF1A8)}

CASES = {
    "energy": (hexrgb(0x8EE55A), hexrgb(0x4CB83A), hexrgb(0x2D7A2E)),
    "line": (hexrgb(0x7CC8FF), hexrgb(0x3B8FE0), hexrgb(0x2459A8)),
    "power": (hexrgb(0xFF8A6B), hexrgb(0xE5483A), hexrgb(0x9E2A2F)),
}


def module(kind):
    light, mid, dark = CASES[kind]
    px = {}
    left, right, top_row, bottom = 2, 13, 1, 12
    case = {(x, y) for x in range(left, right + 1) for y in range(top_row, bottom + 1)}
    for corner in [(left, top_row), (right, top_row), (left, bottom), (right, bottom)]:
        case.discard(corner)
    for p in case:
        x, y = p
        if any(n not in case for n in neighbours(x, y)):
            px[p] = OUTLINE
        elif x == left + 1 or y == top_row + 1:
            px[p] = light
        elif x == right - 1 or y == bottom - 1:
            px[p] = dark
        else:
            px[p] = mid
    # Gold contacts under the case.
    for x in range(4, 12):
        px[(x, 13)] = OUTLINE
        px[(x, 14)] = OUTLINE if x in (4, 11) else (hexrgb(0xFFD23F) if x % 2 == 1 else hexrgb(0xB07A1E))
    for x in range(5, 11):
        px[(x, 15)] = OUTLINE
    # Icon window.
    for x in range(4, 12):
        for y in range(3, 11):
            px[(x, y)] = OUTLINE if x in (4, 11) or y in (3, 10) else hexrgb(0x1E2F4A)
    for row, line in enumerate(ICONS[kind]):
        for col, ch in enumerate(line):
            if ch != ".":
                px[(5 + col, 4 + row)] = ICON_COLORS[ch]
    # Shine on the case corner.
    px[(3, 2)] = (255, 255, 255)
    return px


if __name__ == "__main__":
    shell, screen = styler()
    save(shell, "styler_shell")
    save(screen, "styler_screen")
    for kind in ICONS:
        save(module(kind), "styler_%s_module" % kind)
