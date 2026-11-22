"""Draws the capture minigame's attack art: projectiles, particles and ground blasts for every Pokémon type.

attacks.png is a grid of 16x16 cells with one row per element (the 18 types, then a few environment variants such
as bubbles underwater). Columns 0-3 are the projectile's animation frames, 4-5 a particle used for trails, charge
effects and shockwave rings, and 6-7 smaller copies of that particle for when it fades out.

blasts.png is a grid of 32x32 cells with one row per eruption style. Columns 0-1 are the warning marker shown before
the blast, 2-5 the blast itself: a bold cartoon burst for every row except the cave rockfall.

Row order must match client/AttackArt.java. Run from the repository root: python3 tools/attack_textures.py
"""
import math
import os
import random

from PIL import Image

OUT = "src/main/resources/assets/cobblemonranger/textures/gui/capture"


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


# Five shades per element: outline, shadow, mid, light, highlight ('a' to 'e' in the ASCII sprites).
PALETTES = {
    "normal": [0x4A4038, 0x9A9384, 0xC8C2B0, 0xEDE8DA, 0xFFFFFF],
    "fire": [0x6A1A0E, 0xC8361A, 0xF07A24, 0xFFC23E, 0xFFF4B0],
    "water": [0x1A3A7A, 0x2E6AD0, 0x4FA0F0, 0x9AD4FF, 0xFFFFFF],
    "grass": [0x1C4418, 0x3C9A30, 0x6CD04A, 0xB8F080, 0xF0FFD0],
    "electric": [0x6A4A0A, 0xD89A12, 0xFFD23F, 0xFFF08A, 0xFFFFFF],
    "ice": [0x2A5A7A, 0x5AA8D0, 0x9ADCF0, 0xD6F6FF, 0xFFFFFF],
    "fighting": [0x5A140E, 0xA8281E, 0xE0503A, 0xFF9A6A, 0xFFE6C8],
    "poison": [0x3A1248, 0x6E2A8A, 0xA048C0, 0xD08AF0, 0xF6E0FF],
    "ground": [0x4A3014, 0x8A5E2C, 0xC09050, 0xE4C27E, 0xFFF0C8],
    "flying": [0x3A3A7A, 0x7A84D0, 0xB4BEF4, 0xE2E8FF, 0xFFFFFF],
    "psychic": [0x6A1440, 0xC0306E, 0xF0609A, 0xFFA8CA, 0xFFF0F6],
    "bug": [0x3A4A0A, 0x6E8A12, 0xA8C02A, 0xD8EC70, 0xFAFFD0],
    "rock": [0x3A3020, 0x7A6A48, 0xA8966A, 0xD0C094, 0xF2E8C8],
    "ghost": [0x22143A, 0x4A2E7A, 0x7A58B8, 0xB494F0, 0xEADCFF],
    "dragon": [0x1E0E5A, 0x4A26B0, 0x7A5AF0, 0xB09AFF, 0x7AF5E6],
    "dark": [0x100C10, 0x2E2430, 0x4E4050, 0x7A6880, 0xC8A0E0],
    "steel": [0x3A404A, 0x7A8494, 0xB0BAC8, 0xDCE4EE, 0xFFFFFF],
    "fairy": [0x7A2A5A, 0xD0609A, 0xF59AC4, 0xFFD0E4, 0xFFFFFF],
    "bubble": [0xE4F6FF, 0xB8E4FF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF],
    "steam": [0x8A949E, 0xB8C2CC, 0xDCE2E8, 0xF2F6FA, 0xFFFFFF],
    "lava": [0x3A0A06, 0xA0200C, 0xE85A10, 0xFFA020, 0xFFE070],
    "sand": [0x7A5A2A, 0xC09A58, 0xE0C080, 0xF4DEA8, 0xFFF6DC],
    # Extra colours for the drawn sprites below (not rows of their own).
    "normal_warm": [0x5A4228, 0xC09A62, 0xEED7A4, 0xFFF3D2, 0xFFFFFF],
    "dragon_flame": [0x0A3A4A, 0x1A8A9A, 0x3AD0D0, 0x9AF8EE, 0xFFFFFF],
    "meteor": [0x1A1030, 0x3A2A5A, 0x5A4A80, 0x8A7AB0, 0xC0B0E0],
    "wisp": [0x0A1A4A, 0x2A4AB0, 0x4A8AF0, 0x9AC8FF, 0xFFFFFF],
    # Drawn in greys and tinted at runtime with the colour of the arena's ground.
    "dust": [0x6E6E6E, 0x9A9A9A, 0xC4C4C4, 0xE2E2E2, 0xFFFFFF],
}


def palette(name, alpha=255):
    return [rgb(c, alpha) for c in PALETTES[name]]


# ---------------------------------------------------------------- shaded shapes

class Mask:
    """A set of pixels drawn by sampling a shape function at pixel centres, then shaded and outlined."""

    def __init__(self, size, inside):
        self.size = size
        half = size / 2
        self.pixels = {(x, y) for x in range(size) for y in range(size)
                       if inside(x + 0.5 - half, y + 0.5 - half)}

    def edge(self, x, y):
        return any((x + dx, y + dy) not in self.pixels for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    def draw(self, img, ox, oy, pal, outline=True, light=(-0.7, -0.7), glossy=True, shade=None):
        if not self.pixels:
            return
        cx = sum(p[0] for p in self.pixels) / len(self.pixels)
        cy = sum(p[1] for p in self.pixels) / len(self.pixels)
        reach = max(math.hypot(x - cx, y - cy) for x, y in self.pixels) or 1
        best, best_score = None, -9
        for x, y in self.pixels:
            if outline and self.edge(x, y):
                img.putpixel((ox + x, oy + y), pal[0])
                continue
            if shade is not None:
                level = shade(x, y)
            else:
                s = ((x - cx) * light[0] + (y - cy) * light[1]) / reach
                level = 3 if s > 0.3 else 2 if s > -0.35 else 1
            img.putpixel((ox + x, oy + y), pal[level])
            score = ((x - cx) * light[0] + (y - cy) * light[1]) / reach
            if score > best_score and not self.edge(x, y):
                best, best_score = (x, y), score
        if glossy and best is not None:
            img.putpixel((ox + best[0], oy + best[1]), pal[4])


def rotate(fn, angle):
    c, s = math.cos(-angle), math.sin(-angle)
    return lambda x, y: fn(x * c - y * s, x * s + y * c)


def scaled(fn, k):
    return lambda x, y: fn(x / k, y / k)


def orb(r):
    return lambda x, y: x * x + y * y <= r * r


def lumpy(r, k, amp, phase=0.0):
    return lambda x, y: math.hypot(x, y) <= r * (1 + amp * math.sin(k * math.atan2(y, x) + phase))


def star(points, inner, outer):
    def inside(x, y):
        theta = math.atan2(y, x)
        spike = abs(math.cos(points * theta / 2)) ** 3
        return math.hypot(x, y) <= inner + (outer - inner) * spike
    return inside


def teardrop(x, y):
    # Round front towards +x, pointed tail behind.
    if (x - 1.5) ** 2 + y * y <= 3.6 ** 2:
        return True
    return -5.5 <= x <= 1.5 and abs(y) <= 3.6 * (x + 5.5) / 7


def leaf(x, y):
    return abs(x) <= 6 and abs(y) <= 2.9 * (1 - (x / 6) ** 2)


def needle(x, y):
    # A stinger: a fat round back tapering to a sharp point at +x.
    if (x + 3) ** 2 + y * y <= 2.6 ** 2:
        return True
    return -3 <= x <= 6 and abs(y) <= 2.6 * (6 - x) / 9


def shard(x, y):
    return abs(x) / 6 + abs(y) / 2.8 <= 1


def crescent(x, y):
    return x * x + y * y <= 5.5 ** 2 and (x + 2.6) ** 2 + y * y > 4.6 ** 2


def ring(inner, outer):
    return lambda x, y: inner * inner <= x * x + y * y <= outer * outer


def rock(seed):
    rng = random.Random(seed)
    radii = [3.6 + rng.uniform(-0.7, 0.9) for _ in range(7)]

    def inside(x, y):
        theta = (math.atan2(y, x) + math.pi) / (2 * math.pi) * 7
        i = int(theta) % 7
        t = theta - int(theta)
        return math.hypot(x, y) <= radii[i] * (1 - t) + radii[(i + 1) % 7] * t
    return inside


def poly(pts):
    def inside(x, y):
        c = False
        for i in range(len(pts)):
            (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        return c
    return inside


def shift(fn, dx, dy):
    return lambda x, y: fn(x - dx, y - dy)


def radial(levels, cx=7.5, cy=7.5):
    """Shade by distance from a pixel: levels is [(shade, max distance), ...], brightest first."""
    def shade(x, y):
        d = math.hypot(x - cx, y - cy)
        for level, edge in levels:
            if d <= edge:
                return level
        return 1
    return shade


def flame(i, length=6.5, r=3.4, tongues=3):
    """A flame pointing right: round head and a flickering forked tail."""
    def inside(x, y):
        if (x - 2) ** 2 + y * y <= r * r:
            return True
        if -length <= x <= 2:
            k = (x + length) / (length + 2)
            fork = 0.9 * math.sin((y / max(0.5, r * k)) * tongues * 1.5 + i * 1.8) * (1 - k)
            return abs(y) <= r * k + fork
        return False
    return inside


# ---------------------------------------------------------------- projectiles (columns 0-3)
# kind: "orient" sprites point along +x and are rotated by the game to face where they fly; "spin" sprites turn
# through the frames; "pulse" sprites throb.

def missile_frames(name):
    if name in DRAWN:
        return [DRAWN[name](i) for i in range(4)]
    pal = palette(name)
    frames = []
    for i in range(4):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        fn, outline = MISSILES[name](i)
        Mask(16, fn).draw(img, 0, 0, pal, outline=outline)
        decorate(name, img, i, pal)
        frames.append(img)
    return frames


def pulse(fn_for_scale):
    return lambda i: fn_for_scale(1 + 0.08 * math.sin(i * math.pi / 2))


# Projectiles drawn from a single shaded mask; the layered ones are in DRAWN below.
MISSILES = {
    "water": lambda i: (scaled(teardrop, 1 + 0.05 * (i % 2)), True),
    "grass": lambda i: (rotate(leaf, i * math.pi / 4), True),
    "electric": lambda i: (orb(3.6 + 0.4 * (i % 2)), True),
    "ice": lambda i: (shard, True),
    "poison": lambda i: (lumpy(4.4 + 0.3 * math.sin(i * math.pi / 2), 5, 0.13, i * 0.5), True),
    "ground": lambda i: (rotate(rock(8), i * math.pi / 2), True),
    "flying": lambda i: (scaled(crescent, 1 + 0.04 * (i % 2)), True),
    "bug": lambda i: (needle, True),
    "rock": lambda i: (rotate(rock(3), i * math.pi / 2), True),
    "dark": lambda i: (rotate(crescent, math.pi + i * math.pi / 2), True),
    "steel": lambda i: (rotate(star(4, 1.6, 6.4), i * math.pi / 8), True),
    "bubble": lambda i: (orb(4.6 + 0.3 * math.sin(i * math.pi / 2)), True),
    "steam": lambda i: (lumpy(4.6, 6, 0.15, i), False),
    "lava": lambda i: (lumpy(4.4, 5, 0.14, i * 0.7), True),
    "sand": lambda i: (lumpy(4.2, 7, 0.18, i * 0.9), False),
    "dust": lambda i: (lumpy(4.4, 6, 0.16, i), False),
}

MISSILE_KIND = {
    "fire": "orient", "water": "orient", "ice": "orient", "flying": "orient", "bug": "orient", "ghost": "orient",
    "dragon": "orient",
}


def decorate(name, img, frame, pal):
    """Details the generic shapes cannot draw: sparks, bubbles in sludge, a midrib on leaves and so on."""
    put = lambda x, y, c: img.putpixel((x, y), c) if 0 <= x < 16 and 0 <= y < 16 else None
    if name == "electric":
        rng = random.Random(40 + frame)
        for k in range(4):
            angle = k * math.pi / 2 + rng.uniform(-0.5, 0.5) + frame * 0.4
            x, y = 8 + math.cos(angle) * 4, 8 + math.sin(angle) * 4
            for step in range(3):
                x += math.cos(angle) + rng.uniform(-0.8, 0.8)
                y += math.sin(angle) + rng.uniform(-0.8, 0.8)
                put(int(x), int(y), pal[3 if step < 2 else 4])
    elif name == "grass":
        for t in range(-4, 5):
            angle = frame * math.pi / 4
            put(int(8 + t * math.cos(angle)), int(8 + t * math.sin(angle)), pal[1])
    elif name == "poison":
        for x, y in ((6, 6), (9, 9)) if frame % 2 == 0 else ((9, 6), (6, 9)):
            put(x, y, pal[4])
    elif name == "bubble":
        # Clear bubble: only the rim, a glint and a faint film.
        for x in range(16):
            for y in range(16):
                p = img.getpixel((x, y))
                if p[3] and p[:3] != pal[0][:3]:
                    img.putpixel((x, y), pal[2][:3] + (70,))
        put(6, 5, pal[4])
        put(5, 6, pal[4])
        put(6, 6, pal[3])
    elif name == "ice":
        for x in range(4, 13):
            if img.getpixel((x, 8))[3] and img.getpixel((x, 8))[:3] != pal[0][:3]:
                put(x, 8, pal[4] if x == 9 + frame % 2 else pal[3])
    elif name == "flying":
        put(10 + frame % 2, 6, pal[4])
    elif name == "lava":
        for x, y in ((6, 7), (10, 9), (8, 11)):
            put(x + frame % 2, y, pal[1])


# Projectiles that are layered from several shapes rather than one shaded mask. Each returns frame i of 4.

def new(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def normal_burst(i):
    """A comic impact burst in warm cream that throbs; kept well away from Steel's grey shuriken."""
    img = new()
    k = 1.1 if i % 2 else 0.9
    points = [(j * math.pi / 8 + 0.1, (6.9 if j % 2 == 0 else 3.6) * (1.0 if j % 4 else 0.92)) for j in range(16)]
    burst = poly([(math.cos(a) * r, math.sin(a) * r) for a, r in points])
    Mask(16, scaled(burst, k)).draw(img, 0, 0, palette("normal_warm"), glossy=False,
                                     shade=radial([(4, 1.6), (3, 3.2), (2, 5)]))
    return img


def fighting_aura(i):
    """Focus energy: a hot core with flame tongues licking round it as it spins."""
    img = new()
    pal = palette("fighting")
    tongues = lambda x, y: math.hypot(x, y) <= 4.6 + 2.4 * max(0, math.sin(5 * math.atan2(y, x) + i * math.pi / 2.5)) ** 2
    Mask(16, tongues).draw(img, 0, 0, pal, glossy=False, shade=lambda x, y: 1)
    Mask(16, orb(4.2)).draw(img, 0, 0, pal, outline=False, glossy=False, shade=radial([(4, 1.4), (3, 2.6), (2, 4.4)]))
    return img


def dragon_meteor(i):
    """Draco Meteor: a dark rock wreathed in purple and teal fire."""
    img = new()
    outer = lambda x, y: (x - 2) ** 2 + y * y <= 4.6 ** 2 or (
        -7.5 <= x <= 2 and abs(y) <= 4.6 * (x + 7.5) / 9.5 + 0.7 * math.sin(x * 1.5 + i * 1.9))
    inner = lambda x, y: (x - 2) ** 2 + y * y <= 3.4 ** 2 or (
        -4.5 <= x <= 2 and abs(y) <= 3.0 * (x + 4.5) / 6.5 + 0.5 * math.sin(x * 2 + i * 2.3))
    Mask(16, outer).draw(img, 0, 0, palette("dragon"), glossy=False,
                         shade=lambda x, y: 3 if x > 9 else 2 if abs(y - 7.5) < 2.5 else 1)
    Mask(16, inner).draw(img, 0, 0, palette("dragon_flame"), outline=False, glossy=False, shade=lambda x, y: 3)
    Mask(16, shift(scaled(rotate(rock(5), i * math.pi / 2), 0.75), 3, 0)).draw(img, 0, 0, palette("meteor"))
    return img


def fire_wisp(i):
    """A flame streaming behind its bright head."""
    img = new()
    Mask(16, flame(i, 7.0, 3.6)).draw(img, 0, 0, palette("fire"), glossy=False, shade=lambda x, y: (
        4 if x > 9 and abs(y - 7.5) < 1.6 else 3 if x > 6 and abs(y - 7.5) < 2.6 else 2))
    return img


def psychic_orb(i):
    """A glowing orb inside a pulsing halo."""
    img = new()
    pal = palette("psychic")
    r = 6.0 + 0.6 * (i % 2)
    Mask(16, ring(r - 1.0, r)).draw(img, 0, 0, pal, outline=False, glossy=False, shade=lambda x, y: 3)
    Mask(16, orb(3.6)).draw(img, 0, 0, pal, shade=radial([(4, 0.6), (3, 2.0), (2, 3.4)], 7, 7))
    return img


def ghost_wisp(i):
    """Will-o-wisp: a cold blue flame."""
    img = new()
    Mask(16, flame(i, 6.0, 3.6, 2)).draw(img, 0, 0, palette("wisp"), glossy=False, shade=lambda x, y: (
        4 if x > 9 and abs(y - 7.5) < 1.4 else 3 if x > 6 else 2))
    return img


def fairy_sparkles(i):
    """A cluster of twinkling sparkles, each shrinking and growing in turn."""
    img = new()
    pal = palette("fairy")
    for k, (x, y, size) in enumerate(((7, 7, 3), (2, 3, 2), (12, 4, 2), (11, 12, 2), (3, 12, 1))):
        size = size if (k + i) % 2 == 0 else max(1, size - 1)
        for d in range(-size, size + 1):
            img.putpixel((x + d, y), pal[3] if d else pal[4])
            img.putpixel((x, y + d), pal[3] if d else pal[4])
    return img


DRAWN = {"normal": normal_burst, "fighting": fighting_aura, "dragon": dragon_meteor, "fire": fire_wisp,
         "psychic": psychic_orb, "ghost": ghost_wisp, "fairy": fairy_sparkles}


# ---------------------------------------------------------------- particles (columns 4-7)
# Hand-drawn; letters index the element's palette. Two frames each, flickering while the particle lives.

PARTICLES = {
    "normal": (["...e...", "...d...", "..ded..", "edeeede", "..ded..", "...d...", "...e..."],
               ["e.....e", ".d...d.", "..ded..", "..eee..", "..ded..", ".d...d.", "e.....e"]),
    "fire": (["...e...", "..ed...", "..dd.e.", ".dcdd..", ".cdedc.", ".bcdcb.", "..bbb.."],
             ["....e..", "...de..", ".e.dd..", "..ddcd.", ".cdedc.", ".bcdcb.", "..bbb.."]),
    "water": (["...a...", "..aca..", ".abcda.", "abccdea", "abcccda", ".abbca.", "..aaa.."],
              [".a.....", "ada..a.", ".a..ada", "....aca", ".a...a.", "aca....", ".a....."]),
    "grass": (["....aaa", "..aacda", ".acdca.", "acdca..", "abca...", ".aa....", "a......"],
              ["aa.....", "adcaa..", ".acdca.", "..acdca", "...acba", "....aa.", "......a"]),
    "electric": (["...ee.", "..ed..", ".edde.", "..dde.", "..ed..", ".ed...", ".e...."],
                 [".ee....", "..de...", ".eddde.", "....ed.", "...ed..", "....e.."]),
    "ice": (["...e...", ".e.d.e.", "..ded..", "eddcdde", "..ded..", ".e.d.e.", "...e..."],
            ["...e...", "..ede..", ".edcde.", "..ede..", "...e..."]),
    "fighting": (["d..e..d", ".d.e.d.", "..ede..", "eeeceee", "..ede..", ".d.e.d.", "d..e..d"],
                 [".d.d.", "..e..", "deced", "..e..", ".d.d."]),
    "poison": (["..aaa..", ".acdea.", "acccdea", "accccda", "abcccca", ".abbca.", "..aaa.."],
               [".aa.....", "aeca....", "acca.aa.", ".aa.aeca", "....acca", ".....aa."]),
    "ground": ([".aaa....", "acdca...", "abcccaa.", "abbccdca", ".aabbca.", "...aaa.."],
               ["..aa...", ".adca..", ".abca..", "..aa.aa", "....aca", ".....a."]),
    "flying": ([".....aa", "...aaea", "..adea.", ".adca..", ".aca...", ".aa....", "a......"],
               [".eddd..", ".......", "...eddd", ".......", ".eddd.."]),
    "psychic": (["..ddd..", ".d...d.", "d.....d", "d..e..d", "d.....d", ".d...d.", "..ddd.."],
                [".ddd.", "d...d", "d.e.d", "d...d", ".ddd."]),
    "bug": (["..e....", ".ede...", "..e..e.", "....ede", ".e...e.", "ede....", ".e....."],
            ["....e..", "...ede.", "e...e..", ".d.....", "...e...", "..ede..", "...e..."]),
    "rock": (["..aaaa.", ".acdcda", "abcccca", "abbccca", ".abbba.", "..aaa.."],
             [".aaa.", "acdca", "abcca", ".aaa."]),
    "ghost": (["...d...", "..ded..", ".dcecd.", ".ccdcc.", "..cdc..", ".c.c.c.", "c...c.."],
              ["...d..", "..ded.", ".ccdc.", ".cdec.", "..cc..", ".c.c..", "...c.."]),
    "dragon": (["...e...", "..ed...", "..dd.e.", ".dcdd..", ".cdedc.", ".bcdcb.", "..bbb.."],
               ["....e..", "...de..", ".e.dd..", "..ddcd.", ".cdedc.", ".bcdcb.", "..bbb.."]),
    "dark": (["..cce..", ".cd....", "cd.....", "cd.....", "cc.....", ".cc....", "..ccb.."],
             [".bbb.", "bcccb", "bccdb", ".bbb."]),
    "steel": (["...e...", "...d...", "..ded..", "edeeede", "..ded..", "...d...", "...e..."],
              ["e.....e", ".d...d.", "..ded..", "..eee..", "..ded..", ".d...d.", "e.....e"]),
    "fairy": (["...e...", "...d...", "..dcd..", "edcecde", "..dcd..", "...d...", "...e..."],
              [".cc.cc.", "cdecddc", "cddddcc", ".cdddc.", "..cdc..", "...c..."]),
    "bubble": (["..aaa..", ".a..ea.", "a....ea", "a.....a", "a.....a", ".a...a.", "..aaa.."],
               [".aa.", "a.ea", "a..a", ".aa."]),
    "steam": (["..cdd..", ".cdeedc", "cddeddc", ".cdddc.", "..ccc.."],
              [".cd.", "cded", ".cc."]),
    "lava": (["..aaaa.", ".acdeda", "abcddca", "abbccca", ".abbba.", "..aaa.."],
             [".aaa.", "aceca", "abcda", ".aaa."]),
    "sand": ([".d...c.", "...c...", "c.d...d", "...d.c.", ".c.....", "...c.d."],
             ["..c....", "d...c..", "...d..c", ".c.....", "....d.."]),
    "dust": (["..ccc..", ".cdddc.", "cddeddc", "cdddddc", ".ccddc.", "..ccc.."],
             [".ccc.", "cddec", "cdddc", ".ccc."]),
}


def paste_ascii(img, ox, oy, rows, pal):
    w = max(len(r) for r in rows)
    x0 = ox + (16 - w) // 2
    y0 = oy + (16 - len(rows)) // 2
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x0 + x, y0 + y), pal[ord(ch) - ord("a")])


def shrink(rows):
    """Peels the outer layer of pixels off an ASCII sprite, for the fading frames."""
    grid = {(x, y): ch for y, row in enumerate(rows) for x, ch in enumerate(row) if ch != "."}
    keep = {p: ch for p, ch in grid.items()
            if sum((p[0] + dx, p[1] + dy) in grid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) >= 3}
    if not keep:
        # Too thin to erode (sparks, rings): keep the brightest pixels instead.
        brightest = max(grid.values())
        keep = {p: ch for p, ch in grid.items() if ch == brightest}
    xs = [p[0] for p in keep]
    ys = [p[1] for p in keep]
    out = []
    for y in range(min(ys), max(ys) + 1):
        out.append("".join(keep.get((x, y), ".") for x in range(min(xs), max(xs) + 1)))
    return out


# ---------------------------------------------------------------- blasts (32x32)

BLASTS = [
    # name, palette, lobes, pointy
    ("fire", "fire", 6, False),
    ("ground", "ground", 5, True),
    ("rock", "rock", 5, True),
    ("lava", "lava", 6, False),
    ("sand", "sand", 8, False),
    ("steam", "steam", 7, False),
    ("generic", "dust", 7, False),
    ("rockfall", "rock", 0, False),
]


def blast_frame(pal_name, lobes, pointy, f):
    """A bold cartoon burst: three flat bands, a puffy (or spiky) edge and a thick outline. Hollows out and breaks up."""
    img = new(32)
    pal = palette(pal_name)
    t = (f + 1) / 4
    r = 6 + 8.5 * (1 - (1 - t) ** 2)

    def edge(a):
        if pointy:
            return r * (0.72 + 0.4 * abs(math.cos(lobes * a / 2 + t)) ** 3)
        return r * (0.86 + 0.14 * abs(math.sin(lobes * a / 2 + t * 2)))
    hollow = 0 if f < 2 else r * (0.25 if f == 2 else 0.6)

    def shade(x, y):
        dx, dy = x + 0.5 - 16, y + 0.5 - 16
        rel = math.hypot(dx, dy) / max(0.1, edge(math.atan2(dy, dx)))
        return 4 if rel < 0.42 else 3 if rel < 0.72 else 2
    mask = Mask(32, lambda x, y: hollow <= math.hypot(x, y) <= edge(math.atan2(y, x)))
    if f == 3:
        rng = random.Random(3)
        mask.pixels = {q for q in mask.pixels if (q[0] // 3 + q[1] // 3 + rng.randrange(2)) % 3 != 0}
    mask.draw(img, 0, 0, pal, glossy=False, shade=shade)
    # A second, softer outline ring outside the first makes the edge read as thick.
    for (x, y) in [p for p in mask.pixels if mask.edge(*p)]:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in mask.pixels and 0 <= q[0] < 32 and 0 <= q[1] < 32 and math.hypot(q[0] - 15.5, q[1] - 15.5) > hollow + 1:
                img.putpixel(q, pal[0][:3] + (160,))
    return img


def warning_frame(pal_name, frame):
    """A dashed target ring that tightens on the second frame, over a faint disc, with a cross in the middle."""
    img = new(32)
    pal = palette(pal_name)
    Mask(32, orb(14.5)).draw(img, 0, 0, [pal[1][:3] + (70,)] * 5, outline=False, glossy=False)
    r = 13.6 - frame * 1.2
    for n in range(64):
        a = n * 2 * math.pi / 64 + frame * 0.2
        if n % 8 < 5:
            for rr, c in ((r, pal[0]), (r - 1, pal[3])):
                x, y = round(15.5 + math.cos(a) * rr), round(15.5 + math.sin(a) * rr)
                if 0 <= x < 32 and 0 <= y < 32:
                    img.putpixel((x, y), c)
    for d in range(-2 - frame, 3 + frame):
        img.putpixel((15 + d, 15), pal[4] if abs(d) < 2 else pal[3])
        img.putpixel((15, 15 + d), pal[4] if abs(d) < 2 else pal[3])
    return img


def rockfall_frame(t):
    """A boulder hits the ground and breaks into chunks flying outwards."""
    img = new(32)
    Mask(32, lumpy(4 + 5 * t, 6, 0.2)).draw(img, 0, 0, palette("dust", 150), outline=False, glossy=False)
    for c in range(7):
        angle = c * 2 * math.pi / 7 + 0.4
        dist = 3 + t * 11
        size = 3.2 - t * 1.6
        fn = rock(c + 20)
        cx, cy = math.cos(angle) * dist, math.sin(angle) * dist
        Mask(32, lambda x, y, fn=fn, cx=cx, cy=cy, s=size / 3.6: fn((x - cx) / s, (y - cy) / s)).draw(
            img, 0, 0, palette("rock"))
    return img


def rockfall_warning(frame):
    """The boulder's shadow, growing as it falls."""
    img = new(32)
    r = 8 + frame * 3
    Mask(32, lambda x, y: (x / r) ** 2 + (y / (r * 0.7)) ** 2 <= 1).draw(
        img, 0, 0, [(0, 0, 0, 150)] * 2 + [(0, 0, 0, 180)] * 3, outline=False, glossy=False,
        shade=lambda x, y: 2 if math.hypot(x - 15.5, (y - 15.5) / 0.7) < r * 0.6 else 1)
    return img


# ---------------------------------------------------------------- output

ELEMENTS = ["normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying",
            "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy", "bubble", "steam", "lava",
            "sand", "dust"]


def attacks():
    atlas = Image.new("RGBA", (128, 16 * len(ELEMENTS)), (0, 0, 0, 0))
    for row, name in enumerate(ELEMENTS):
        for i, frame in enumerate(missile_frames(name)):
            atlas.paste(frame, (i * 16, row * 16), frame)
        pal = palette(name)
        a, b = PARTICLES[name]
        for col, rows in enumerate((a, b, shrink(a), shrink(b))):
            paste_ascii(atlas, (4 + col) * 16, row * 16, rows, pal)
    atlas.save(os.path.join(OUT, "attacks.png"))


def blasts():
    atlas = Image.new("RGBA", (192, 32 * len(BLASTS)), (0, 0, 0, 0))
    for row, (name, pal_name, lobes, pointy) in enumerate(BLASTS):
        if name == "rockfall":
            frames = [rockfall_warning(f) for f in range(2)] + [rockfall_frame((f + 1) / 4) for f in range(4)]
        else:
            frames = [warning_frame(pal_name, f) for f in range(2)] + [blast_frame(pal_name, lobes, pointy, f)
                                                                     for f in range(4)]
        for col, frame in enumerate(frames):
            atlas.paste(frame, (col * 32, row * 32), frame)
    atlas.save(os.path.join(OUT, "blasts.png"))


def preview(path):
    """Both atlases side by side, blown up 4x on a grass-coloured background, for reviewing the art."""
    a = Image.open(os.path.join(OUT, "attacks.png"))
    b = Image.open(os.path.join(OUT, "blasts.png"))
    sheet = Image.new("RGBA", (a.width + b.width + 8, max(a.height, b.height)), rgb(0x6DA046))
    sheet.paste(a, (0, 0), a)
    sheet.paste(b, (a.width + 8, 0), b)
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(path)


if __name__ == "__main__":
    import sys
    attacks()
    blasts()
    if len(sys.argv) > 1:
        preview(sys.argv[1])
    for row, name in enumerate(ELEMENTS):
        print(row, name, MISSILE_KIND.get(name, "spin"))
