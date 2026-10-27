"""Draws the capture arena art: one tileable 64x64 ground texture per environment and a 16x16 decoration atlas.

Grass and water tiles (and the first decoration row) are drawn in greys; the game tints them with the biome's own
grass or water colour. Run from the repository root: python3 tools/arena_textures.py
"""
import os
import random

from PIL import Image

OUT = "src/main/resources/assets/cobblemonranger/textures/gui/capture"
SIZE = 64


def hexrgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, 255)


def grey(v):
    return (v, v, v, 255)


class Tile:
    def __init__(self, base, seed):
        self.img = Image.new("RGBA", (SIZE, SIZE), base)
        self.rng = random.Random(seed)

    def put(self, x, y, color):
        self.img.putpixel((x % SIZE, y % SIZE), color)

    def get(self, x, y):
        return self.img.getpixel((x % SIZE, y % SIZE))

    def speckle(self, colors, count):
        for _ in range(count):
            self.put(self.rng.randrange(SIZE), self.rng.randrange(SIZE), self.rng.choice(colors))

    def blobs(self, color, count, rmin, rmax):
        for _ in range(count):
            cx, cy = self.rng.randrange(SIZE), self.rng.randrange(SIZE)
            r = self.rng.uniform(rmin, rmax)
            ir = int(r) + 1
            for dx in range(-ir, ir + 1):
                for dy in range(-ir, ir + 1):
                    if dx * dx + dy * dy * 1.4 <= r * r and self.rng.random() > 0.12:
                        self.put(cx + dx, cy + dy, color)

    def marks(self, pattern, colors, count):
        """Stamps a small ASCII pattern ('a', 'b'... index into colors) at random places."""
        for _ in range(count):
            ox, oy = self.rng.randrange(SIZE), self.rng.randrange(SIZE)
            for y, row in enumerate(pattern):
                for x, ch in enumerate(row):
                    if ch != ".":
                        self.put(ox + x, oy + y, colors[ord(ch) - ord("a")])

    def save(self, name):
        os.makedirs(os.path.join(OUT, "terrain"), exist_ok=True)
        self.img.save(os.path.join(OUT, "terrain", name + ".png"))


BLADE = ["a.a", ".a."]
BLADE_TALL = ["a..", "a.a", ".a."]
DOT2 = ["aa"]


def grass():
    t = Tile(grey(196), 1)
    t.blobs(grey(206), 14, 3, 7)
    t.blobs(grey(184), 10, 2, 5)
    t.marks(BLADE, [grey(160)], 70)
    t.marks(BLADE_TALL, [grey(152)], 24)
    t.speckle([grey(224), grey(232)], 90)
    t.save("grass")


def moss():
    t = Tile(hexrgb(0x5E8F32), 2)
    t.blobs(hexrgb(0x6FA23A), 16, 3, 6)
    t.blobs(hexrgb(0x4D7A2A), 10, 2, 4)
    t.marks(BLADE, [hexrgb(0x3F6A24)], 60)
    t.speckle([hexrgb(0x8CC04B)], 70)
    t.save("moss")


def sand(name, base, light, dark, seed):
    t = Tile(hexrgb(base), seed)
    for row in range(0, SIZE, 8):
        offset = t.rng.randrange(SIZE)
        for x in range(SIZE):
            wave = (x + offset) % 16
            y = row + (1 if wave in range(4, 12) else 0)
            if (x + offset) % 22 < 15:
                t.put(x, y, hexrgb(light))
                t.put(x, y + 1, hexrgb(dark))
    t.speckle([hexrgb(dark), hexrgb(light)], 120)
    t.save(name)


def badlands():
    t = Tile(hexrgb(0xC9703A), 4)
    for row in range(0, SIZE, 16):
        for x in range(SIZE):
            for y in range(row, row + 3):
                t.put(x, y + (x // 9) % 2, hexrgb(0xB45A2F))
            t.put(x, row + 8 + (x // 13) % 2, hexrgb(0xDE8A4E))
    t.speckle([hexrgb(0xE39A5C), hexrgb(0x9E4A26)], 120)
    t.save("red_sand")


def snow():
    t = Tile(hexrgb(0xEEF6FB), 5)
    t.blobs(hexrgb(0xDCEAF4), 14, 3, 8)
    t.blobs(hexrgb(0xFFFFFF), 10, 2, 5)
    t.marks(["a.", ".b"], [hexrgb(0xC4D8E8), hexrgb(0xFFFFFF)], 40)
    t.speckle([hexrgb(0xBFD6E8)], 60)
    t.save("snow")


def stone(name, base, light, dark, seed, cobble=True):
    t = Tile(hexrgb(base), seed)
    t.blobs(hexrgb(light), 12, 3, 7)
    t.blobs(hexrgb(dark), 8, 2, 5)
    if cobble:
        # Cracks between flagstones.
        for _ in range(16):
            x, y = t.rng.randrange(SIZE), t.rng.randrange(SIZE)
            for _ in range(t.rng.randrange(6, 14)):
                t.put(x, y, hexrgb(dark))
                t.put(x, y + 1, hexrgb(light))
                x += t.rng.choice([1, 1, 0])
                y += t.rng.choice([1, 0, -1])
    t.speckle([hexrgb(dark), hexrgb(light)], 140)
    t.save(name)


def water():
    t = Tile(grey(170), 7)
    t.blobs(grey(160), 10, 4, 9)
    t.blobs(grey(184), 8, 3, 6)
    for _ in range(26):
        x, y = t.rng.randrange(SIZE), t.rng.randrange(SIZE)
        length = t.rng.randrange(4, 9)
        for i in range(length):
            t.put(x + i, y + (1 if i in (0, length - 1) else 0), grey(236))
            t.put(x + i, y + 2 - (1 if i in (0, length - 1) else 0), grey(140))
    t.save("water")


def netherrack():
    t = Tile(hexrgb(0x8E3434), 8)
    t.blobs(hexrgb(0xA34040), 14, 3, 6)
    t.blobs(hexrgb(0x6E2427), 12, 2, 5)
    t.marks(["ab", "b."], [hexrgb(0xC0574A), hexrgb(0x5A1C20)], 70)
    t.save("netherrack")


def end_stone():
    t = Tile(hexrgb(0xE6E7AA), 9)
    t.blobs(hexrgb(0xF2F3BE), 12, 3, 6)
    t.marks(["aa", "ab"], [hexrgb(0xC9C98C), hexrgb(0xB3B37A)], 60)
    t.speckle([hexrgb(0xD4D49A)], 80)
    t.save("end_stone")


def mycelium():
    t = Tile(hexrgb(0x7D6A7E), 10)
    t.blobs(hexrgb(0x8E7A8F), 14, 3, 6)
    t.blobs(hexrgb(0x685869), 8, 2, 4)
    t.speckle([hexrgb(0xB8A4B8), hexrgb(0xA08CA6), hexrgb(0x5C4C5E)], 200)
    t.save("mycelium")


# ---------------------------------------------------------------- decorations
# 16x16 cells in an 8x4 atlas. Letters index into each sprite's palette; '.' is transparent. Sprites sit on the
# bottom-centre of their cell so they can be placed by their feet.

O = (30, 34, 40, 255)
G = [grey(120), grey(160), grey(200), grey(232)]  # tinted at runtime

SPRITES = [
    # row 0: tinted plants
    ("tuft", G, ["..b..c..", ".bb.cb..", "abbbbba.", ".aaaaa.."]),
    ("tuft_tall", G, ["...c....", "..cb..c.", ".bcb.cb.", ".bbbbbb.", "abbbbbba", ".aaaaaa."]),
    ("fern", G, ["...c...", "..cbc..", ".cb.bc.", "cb.b.bc", "b..b..b", "...a..."]),
    ("clover", G, [".cc.cc.", "cddcddc", ".cbbbc.", "cddbddc", ".cc.cc.", "...a..."]),
    ("bush", G, ["..cddc..", ".cdddbc.", "cddbbbbc", "cdbbbbba", ".bbbbaa.", "..aaaa.."]),
    ("leaves", G, ["..c.....", ".cbc..c.", "cbbbccbc", ".cbbbbc.", "..ab.ba.", "........"]),
    ("reeds", G, ["c..c..c", "b.cb.cb", "bcb.cb.", "bb.bb.b", "ab.ab.a"]),
    ("lily", [hexrgb(0x2E6B2A), hexrgb(0x3F8F38), hexrgb(0x5DB04F), hexrgb(0xF4A6C8)],
     ["..bbbb..", ".bccbbb.", "bccbbd.b", "bcbbbb.b", ".abbbba.", "..aaaa.."]),
    # row 1: flowers and stones
    ("flower_red", [hexrgb(0xC0303A), hexrgb(0xF2545B), hexrgb(0xFFE07A), hexrgb(0x3E8A3A)],
     [".bb.", "bccb", ".aa.", "..d.", ".dd."]),
    ("flower_yellow", [hexrgb(0xD99A1E), hexrgb(0xFFD23F), hexrgb(0xFFF6C0), hexrgb(0x3E8A3A)],
     [".bb.", "bccb", ".aa.", "..d.", ".dd."]),
    ("flower_blue", [hexrgb(0x3B6FD8), hexrgb(0x7FB2FF), hexrgb(0xFFFFFF), hexrgb(0x3E8A3A)],
     [".b.b.", "bbcbb", ".aba.", "..d..", ".dd.."]),
    ("flower_white", [hexrgb(0xC8D0DC), hexrgb(0xFFFFFF), hexrgb(0xFFD23F), hexrgb(0x3E8A3A)],
     [".bb..", "bcbb.", ".bb..", "..d..", ".dd.."]),
    ("mushroom_red", [O, hexrgb(0xD8303A), hexrgb(0xFFFFFF), hexrgb(0xEADBC4)],
     [".aaaa.", "abcbba", "abbbca", ".aaaa.", "..dd..", "..dd.."]),
    ("mushroom_brown", [O, hexrgb(0x9C6A44), hexrgb(0xC08C5E), hexrgb(0xEADBC4)],
     [".aaaa.", "acbbba", "abbbba", ".aaaa.", "..dd..", "..dd.."]),
    ("pebbles", [hexrgb(0x5E636E), hexrgb(0x8C929E), hexrgb(0xC0C6D0)], [".c....", "bba.c.", ".a.bba", "....a."]),
    ("rock", [O, hexrgb(0x6F7582), hexrgb(0x989FAC), hexrgb(0xC4CAD4)],
     ["..aaa...", ".adcca..", "adccbba.", "acbbbbba", "abbbbbba", ".aaaaaa."]),
    # row 2: dry, cold and coastal things
    ("cactus", [O, hexrgb(0x2F7A3A), hexrgb(0x4FA84E), hexrgb(0x8EDB7A)],
     ["..aa..", ".acba.", ".acba.a", "aacbaaba", "abcbbbba", "aacbaaa.", ".acba...", ".acba..."]),
    ("dead_bush", [hexrgb(0x6B4423), hexrgb(0x8E6033)], ["a..b..a", ".a.b.a.", "..aba..", "b..a..b", ".b.a.b.", "..bab.."]),
    ("shell", [O, hexrgb(0xF2A7B5), hexrgb(0xFFE0E6), hexrgb(0xD27A8E)],
     ["..aaa..", ".acbca.", "abcbcba", "adbdbda", ".aaaaa."]),
    ("starfish", [hexrgb(0xC4502E), hexrgb(0xF07A45)], ["..b..", "bbbbb", ".bab.", "b...b"]),
    ("snow_tuft", [hexrgb(0xA9C4DA), hexrgb(0xDDEBF6), hexrgb(0xFFFFFF)], ["..c..c.", ".cbc.cb", "cbbbcbb", "aaaaaaa"]),
    ("ice", [O, hexrgb(0x7FC7F0), hexrgb(0xC4ECFF), hexrgb(0xFFFFFF)],
     ["...a...", "..aca..", ".acdba.", ".acbba.", "abcbbba", "aaaaaaa"]),
    ("crystal", [O, hexrgb(0x8A4FD8), hexrgb(0xC08CFF), hexrgb(0xF0DCFF)],
     ["..a.....", ".aca..a.", ".acba.aca", "aacbaacba", "abcbbacba", "aaaaaaaaa"]),
    ("bone", [O, hexrgb(0xE8E2CC)], ["ab....ba", ".abbbba.", "ab....ba"]),
    # row 3: water, nether, end and caves
    ("ripple", [hexrgb(0xFFFFFF), (255, 255, 255, 120)], [".bbbbb.", "b.....b", ".aaaaa."]),
    ("ember", [hexrgb(0xFF7A1A), hexrgb(0xFFD23F), hexrgb(0xFFF6C0)], ["..a..", ".aba.", "abcba", ".aba."]),
    ("fungus", [O, hexrgb(0xB02A2A), hexrgb(0xE04848), hexrgb(0xF0C878)],
     [".aaaa.", "acbbca", "abbbba", ".aaaa.", "..dd..", ".dddd."]),
    ("chorus", [O, hexrgb(0x7A4C8A), hexrgb(0xA77AB8), hexrgb(0xE4D2EE)],
     ["..ad...", ".acca.a", "..bb.ad", "a.bbabb", "abbbbb.", ".abba..", "..bb..."]),
    ("glow_berries", [hexrgb(0x3E8A3A), hexrgb(0xFFB02E), hexrgb(0xFFE28A)], ["a.a.a", "bab.a", "cbabb", ".b.cb", "...b."]),
    ("sculk", [hexrgb(0x0C2A33), hexrgb(0x13505E), hexrgb(0x2BC4CF)], [".aabaa.", "abcbbba", "aabbcba", ".aaaaa."]),
    ("crack", [hexrgb(0x2A2E36), hexrgb(0x5A606C)], ["a......", ".a..a..", "..aa.a.", "..a...a", ".a.....", "a......"]),
    ("puddle", [hexrgb(0x3C4F6A), hexrgb(0x5E7898), hexrgb(0xA8C4E0)], [".aaaaa..", "abbcbbba", "abbbbba.", ".aaaa..."]),
]


def decorations():
    atlas = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    for index, (name, palette, rows) in enumerate(SPRITES):
        cx, cy = (index % 8) * 16, (index // 8) * 16
        w = max(len(r) for r in rows)
        ox = cx + (16 - w) // 2
        oy = cy + 16 - len(rows)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    atlas.putpixel((ox + x, oy + y), palette[ord(ch) - ord("a")])
    atlas.save(os.path.join(OUT, "decorations.png"))
    return [name for name, _, _ in SPRITES]


if __name__ == "__main__":
    grass()
    moss()
    sand("sand", 0xE8D49A, 0xF4E4B4, 0xCDB57A, 3)
    badlands()
    snow()
    stone("stone", 0x8A8F98, 0x9EA3AC, 0x6C717B, 6)
    stone("gravel", 0x8C8682, 0xA8A29C, 0x6A6460, 11, cobble=False)
    stone("deepslate", 0x4A4E5A, 0x5A5F6C, 0x34374A, 12)
    water()
    netherrack()
    end_stone()
    mycelium()
    for i, name in enumerate(decorations()):
        print(i, name)
