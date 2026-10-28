"""Draws the capture minigame's attack art: projectiles, particles and ground blasts for every Pokémon type.

attacks.png is a grid of 16x16 cells with one row per element (the 18 types, then a few environment variants such
as bubbles underwater). Columns 0-3 are the projectile's animation frames, 4-5 a particle used for trails, charge
effects and shockwave rings, and 6-7 smaller copies of that particle for when it fades out.

blasts.png is a grid of 32x32 cells with one row per eruption style. Columns 0-1 are the warning marker shown before
the blast, 2-5 the blast itself.

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


def comet(phase):
    def inside(x, y):
        if (x - 2) ** 2 + y * y <= 3.4 ** 2:
            return True
        if -6.5 <= x <= 2:
            flicker = 0.6 * math.sin(x * 1.7 + phase * 1.9)
            return abs(y) <= 3.4 * (x + 6.5) / 8.5 + flicker
        return False
    return inside


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


# ---------------------------------------------------------------- projectiles (columns 0-3)
# kind: "orient" sprites point along +x and are rotated by the game to face where they fly; "spin" sprites turn
# through the frames; "pulse" sprites throb.

def missile_frames(name):
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


MISSILES = {
    "normal": lambda i: (rotate(star(4, 2.2, 6.2), i * math.pi / 8), True),
    "fire": lambda i: (comet(i), True),
    "water": lambda i: (scaled(teardrop, 1 + 0.05 * (i % 2)), True),
    "grass": lambda i: (rotate(leaf, i * math.pi / 4), True),
    "electric": lambda i: (orb(3.6 + 0.4 * (i % 2)), True),
    "ice": lambda i: (shard, True),
    "fighting": lambda i: (lumpy(4.6, 6, 0.12, i * 0.8), True),
    "poison": lambda i: (lumpy(4.4 + 0.3 * math.sin(i * math.pi / 2), 5, 0.13, i * 0.5), True),
    "ground": lambda i: (rotate(rock(8), i * math.pi / 2), True),
    "flying": lambda i: (scaled(crescent, 1 + 0.04 * (i % 2)), True),
    "psychic": lambda i: (ring(1.8 + 0.5 * (i % 2), 5 + 0.4 * (i % 2)), True),
    "bug": lambda i: (needle, True),
    "rock": lambda i: (rotate(rock(3), i * math.pi / 2), True),
    "ghost": lambda i: (comet(i + 2), True),
    "dragon": lambda i: (comet(i + 4), True),
    "dark": lambda i: (rotate(crescent, math.pi + i * math.pi / 2), True),
    "steel": lambda i: (rotate(star(4, 1.6, 6.4), i * math.pi / 8), True),
    "fairy": lambda i: (rotate(star(5, 2.4, 6), i * math.pi / 10), True),
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
    elif name == "psychic":
        for k in range(4):
            angle = k * math.pi / 2 + frame * math.pi / 4
            put(int(8 + math.cos(angle) * 6.2), int(8 + math.sin(angle) * 6.2), pal[4])
    elif name == "lava":
        for x, y in ((6, 7), (10, 9), (8, 11)):
            put(x + frame % 2, y, pal[1])


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
    # name, palette, warning style, burst style, lobes, amplitude
    ("fire", "fire", "cracks", "flame", 7, 0.32),
    ("ground", "ground", "cracks", "spike", 6, 0.38),
    ("rock", "rock", "cracks", "spike", 5, 0.45),
    ("lava", "lava", "cracks", "flame", 6, 0.25),
    ("sand", "sand", "spiral", "lumpy", 9, 0.22),
    ("steam", "steam", "ripples", "lumpy", 7, 0.18),
    ("generic", "dust", "cracks", "lumpy", 8, 0.2),
    ("rockfall", "rock", "shadow", "shatter", 0, 0),
]


def wave(style, k, theta, phase):
    if style == "flame":
        # Two sets of tongues at different counts so the flames look uneven rather than like petals.
        tongues = 0.6 * abs(math.sin(k * theta / 2 + phase)) ** 0.5 + 0.4 * abs(math.sin((k + 3) * theta / 2 - phase * 1.7)) ** 0.5
        return tongues * 2 - 1
    if style == "spike":
        return abs(math.cos(k * theta / 2 + phase)) ** 2.5 * 2 - 1
    return math.sin(k * theta + phase)


def blast_frame(name, pal_name, style, k, amp, t, seed):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    pal = palette(pal_name, 225 if pal_name == "steam" else 255)
    rng = random.Random(seed)
    if style == "shatter":
        # A boulder hits the ground and breaks into chunks flying outwards.
        Mask(32, lumpy(4 + 5 * t, 6, 0.2)).draw(img, 0, 0, palette("dust", 150), outline=False, glossy=False)
        for c in range(7):
            angle = c * 2 * math.pi / 7 + 0.4
            dist = 3 + t * 11
            size = 3.2 - t * 1.6
            fn = rock(c + 20)
            cx, cy = math.cos(angle) * dist, math.sin(angle) * dist
            Mask(32, lambda x, y, fn=fn, cx=cx, cy=cy, s=size / 3.6: fn((x - cx) / s, (y - cy) / s)).draw(
                img, 0, 0, pal)
        return img
    radius = 5 + 9.5 * (1 - (1 - t) ** 2)
    phase = t * 1.3
    hollow = max(0.0, (t - 0.45) * 1.6) * radius
    if hollow < 2:
        hollow = 0.0

    def inside(x, y):
        d = math.hypot(x, y)
        return hollow <= d <= radius * (1 + amp * wave(style, k, math.atan2(y, x), phase))

    def shade(x, y):
        dx, dy = x + 0.5 - 16, y + 0.5 - 16
        d = math.hypot(dx, dy)
        edge = radius * (1 + amp * wave(style, k, math.atan2(dy, dx), phase))
        rel = (d - hollow) / max(0.1, edge - hollow)
        hot = 1 - t * 0.6
        if rel < 0.3 * hot:
            return 4
        if rel < 0.55 * hot + 0.1:
            return 3
        if rel < 0.85:
            return 2
        return 1

    mask = Mask(32, inside)
    if t >= 0.99:
        # Last frame breaks up into scattered bits.
        mask.pixels = {p for p in mask.pixels if rng.random() < 0.45}
    mask.draw(img, 0, 0, pal, outline=pal_name not in ("steam", "dust"), glossy=False, shade=shade)
    # Flying debris around the blast.
    for _ in range(int(6 + 10 * t)):
        angle = rng.uniform(0, 2 * math.pi)
        d = radius * rng.uniform(0.9, 1.25)
        x, y = int(16 + math.cos(angle) * d), int(16 + math.sin(angle) * d)
        if 0 <= x < 32 and 0 <= y < 32:
            img.putpixel((x, y), pal[rng.choice((1, 2, 3))])
    return img


def warning_frame(pal_name, style, frame, seed):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    pal = palette(pal_name)
    rng = random.Random(seed)
    if style == "shadow":
        r = 8 + frame * 3
        Mask(32, lambda x, y: (x / r) ** 2 + (y / (r * 0.7)) ** 2 <= 1).draw(
            img, 0, 0, [(0, 0, 0, 150)] * 2 + [(0, 0, 0, 180)] * 3, outline=False, glossy=False,
            shade=lambda x, y: 2 if math.hypot(x - 15.5, (y - 15.5) / 0.7) < r * 0.6 else 1)
        return img
    # Every other marker covers the danger area with a faint disc in the attack's colour.
    Mask(32, orb(14.5)).draw(img, 0, 0, [pal[1][:3] + (80,)] * 5, outline=False, glossy=False)
    if style == "ripples":
        for r in (5 + frame * 2, 10 + frame * 2):
            Mask(32, ring(r - 0.6, r + 0.6)).draw(img, 0, 0, [pal[3]] * 5, outline=False, glossy=False)
        for _ in range(5):
            x, y = rng.randrange(8, 24), rng.randrange(8, 24)
            img.putpixel((x, y), pal[4])
        return img
    if style == "spiral":
        for i in range(40):
            angle = i * 0.55 + frame * 0.5
            d = 2 + i * 0.32
            x, y = int(16 + math.cos(angle) * d), int(16 + math.sin(angle) * d)
            if 0 <= x < 32 and 0 <= y < 32:
                img.putpixel((x, y), pal[1 if i % 3 else 0])
        return img
    # A ring around the danger area and the ground cracking open from the middle, the cracks glowing brighter on
    # the second frame.
    for i in range(48):
        angle = i * 2 * math.pi / 48
        if (i + frame * 2) % 6 < 4:
            img.putpixel((round(15.5 + math.cos(angle) * 14), round(15.5 + math.sin(angle) * 14)), pal[3 if frame else 2])
    for c in range(5):
        angle = c * 2 * math.pi / 5 + rng.uniform(-0.3, 0.3)
        heading = angle
        x, y = 15.5, 15.5
        length = 8 + frame * 3 + rng.randrange(2)
        for step in range(length):
            heading = angle + rng.uniform(-0.45, 0.45)
            x += math.cos(heading)
            y += math.sin(heading)
            xi, yi = round(x), round(y)
            if 0 <= xi < 32 and 0 <= yi < 32:
                img.putpixel((xi, yi), pal[4] if step < 3 + frame * 2 else pal[3] if step < length - 2 else pal[1])
    for x, y in ((15, 15), (16, 15), (15, 16), (16, 16)):
        img.putpixel((x, y), pal[4])
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
    for row, (name, pal_name, warning, style, k, amp) in enumerate(BLASTS):
        for f in range(2):
            frame = warning_frame(pal_name, warning, f, row * 7 + f)
            atlas.paste(frame, (f * 32, row * 32), frame)
        for f in range(4):
            frame = blast_frame(name, pal_name, style, k, amp, (f + 1) / 4, row * 13 + f)
            atlas.paste(frame, ((2 + f) * 32, row * 32), frame)
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
