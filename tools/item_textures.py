"""Draws the item textures (styler layers and upgrade modules) and the Surfing effect icon as pixel art.

Run from the repository root: python3 tools/item_textures.py
"""
import os

from PIL import Image

ROOT = "src/main/resources/assets/cobblemonranger/textures"
OUT = ROOT + "/item"

OUTLINE = (26, 28, 44)


def hexrgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def save(pixels, name, size=16, out=OUT):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for (x, y), color in pixels.items():
        if 0 <= x < size and 0 <= y < size:
            img.putpixel((x, y), color + (255,) if len(color) == 3 else color)
    img.save(os.path.join(out, name + ".png"))


def paint(rows, palette, ox=0, oy=0):
    """ASCII sprite to pixels: each character is a key into palette, '.' is transparent."""
    px = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[(ox + x, oy + y)] = palette[ch]
    return px


# ---------------------------------------------------------------- styler
# A red handheld after the first Ranger games' Capture Styler: rounded body, yellow antenna on the top right, an oval
# emblem with a blue gem and a dark grip down the right side. Digits are the dyeable shell (layer 0), drawn in greys so
# the item tint gives the colour; letters keep their own colour (layer 1).

STYLER = [
    "...........oo...",
    "..........oYdo..",
    "....oooooooydo..",
    "...o44443oyyo...",
    "...o43333ooo3o..",
    "...o3ooooo33ho..",
    "...o3oSSso32ho..",
    "...o3oBbso32ho..",
    "...o3ooooo22ho..",
    "...o33333322ho..",
    "...o33222222ho..",
    "...o22111122ho..",
    "...o22222222go..",
    "....o2222221o...",
    ".....oooooooo...",
    "................",
]
SHELL = {"4": (255, 255, 255), "3": (226, 226, 226), "2": (190, 190, 190), "1": (150, 150, 150)}
FIXED = {"o": OUTLINE, "y": hexrgb(0xFFD23F), "Y": hexrgb(0xFFF08A), "d": hexrgb(0xD89A12), "g": hexrgb(0x1E1E28),
         "h": hexrgb(0x3A3A4C), "S": hexrgb(0xE8ECF2), "s": hexrgb(0xA8B0BE), "B": hexrgb(0x9AD4FF), "b": hexrgb(0x2A6AE0)}


def styler():
    shell = paint([row.translate(str.maketrans({c: "." for c in FIXED})) for row in STYLER], SHELL)
    top = paint([row.translate(str.maketrans({c: "." for c in SHELL})) for row in STYLER], FIXED)
    return shell, top


# ---------------------------------------------------------------- modules
# A circuit chip that slots into the styler: grey metal body, gold pins down both sides and a coloured core showing
# the upgrade's icon. '#' marks the 6x6 core, filled from CORES.

CHIP = [
    "................",
    "...aaaaaaaaaa...",
    "..abcbbbbbbbba..",
    ".dabbbbbbbbbead.",
    "..abaaaaaaaaea..",
    ".daba######aead.",
    "..aba######aea..",
    ".daba######aead.",
    "..aea######aja..",
    ".daea######ajad.",
    "..aea######aja..",
    ".daeaaaaaaaajad.",
    "..aeejjjjjjjja..",
    "..ajjjjjjjjjja..",
    "...aaaaaaaaaa...",
    "................",
]
# f, g, i: light, mid and dark shades of the module's colour; h: the icon; c: a glint.
CORES = {
    "energy": ["cffffg", "fffhhg", "fghhgg", "ghhhhi", "gghhii", "ghhiii"],  # lightning bolt
    "line": ["cffffg", "ffhhgg", "fhgghg", "ghghhi", "gghhii", "ggiiii"],  # a drawn loop
    "power": ["cffffg", "fffhgg", "fghhhg", "ggghgi", "gghihi", "ggiiii"],  # burst
}
CHIP_COLORS = {"a": hexrgb(0x1A1C2C), "b": hexrgb(0xC9CED8), "c": (255, 255, 255), "d": hexrgb(0xFFD23F),
               "e": hexrgb(0xA2A9B8), "j": hexrgb(0x6F7789)}
CASES = {
    "energy": {"f": hexrgb(0x8EE55A), "g": hexrgb(0x4CB83A), "i": hexrgb(0x2D7A2E), "h": hexrgb(0xFFE14D)},
    "line": {"f": hexrgb(0x7CC8FF), "g": hexrgb(0x3B8FE0), "i": hexrgb(0x2459A8), "h": hexrgb(0x7DF3FF)},
    "power": {"f": hexrgb(0xFF8A6B), "g": hexrgb(0xE5483A), "i": hexrgb(0x9E2A2F), "h": hexrgb(0xFFF1A8)},
}


def module(kind):
    core = iter(CORES[kind])
    rows = [row.replace("######", next(core)) if "#" in row else row for row in CHIP]
    return paint(rows, {**CHIP_COLORS, **CASES[kind]})


# ---------------------------------------------------------------- Surfing effect icon (18x18)
# A curling blue wave with foam on the crest.

WAVE = [
    "......aaaaaa......",
    "....aaddddeeaa....",
    "...addccccddeea...",
    "..adccaaaaaacdea..",
    "..accaa.....aaea..",
    ".adca.........a...",
    ".acca.............",
    ".acccaa...........",
    "adccccdaa.........",
    "acccccccdaaa......",
    "abccccccccddaaa...",
    "abbcccccccccccdaa.",
    "abbbbcccccccccccda",
    "aabbbbbbbbbbbbbbba",
    ".aaaaaaaaaaaaaaaa.",
]
WAVE_COLORS = {"a": hexrgb(0x0E2A5A), "b": hexrgb(0x1F6FD0), "c": hexrgb(0x3FA0F0), "d": hexrgb(0x9AD4FF),
               "e": (255, 255, 255)}


if __name__ == "__main__":
    shell, screen = styler()
    save(shell, "styler_shell")
    save(screen, "styler_screen")
    for kind in CORES:
        save(module(kind), "styler_%s_module" % kind)
    save(paint(WAVE, WAVE_COLORS, 0, 2), "surfing", 18, ROOT + "/mob_effect")
