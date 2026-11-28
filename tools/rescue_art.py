"""Rescue event art: the five trap blocks (rubble, tangle, fire, ice, sand) and the alert beam particle.

Run from the repository root:
  python3 tools/rescue_art.py                writes the chosen art into the mod's assets
  python3 tools/rescue_art.py previews DIR   renders every option sheet into DIR
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

OUTLINE = (26, 28, 44, 255)
CLEAR = (0, 0, 0, 0)


def hexa(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def blank(size=16):
    return Image.new("RGBA", (size, size), CLEAR)


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def outline_alpha(img, col=OUTLINE):
    """Adds a 1px dark outline around every opaque shape of a cutout texture."""
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] > 200:
                    out.putpixel((x, y), col)
                    break
    return out


# ---------------------------------------------------------------- rubble (Rock Smash)

STONES = [(0, 0, 6, 5), (7, 0, 15, 6), (0, 6, 5, 11), (6, 7, 11, 12), (12, 7, 15, 11), (0, 12, 7, 15),
          (8, 13, 15, 15)]


def stone_pile(base, light, dark, gap, moss=None):
    img = Image.new("RGBA", (16, 16), gap)
    d = ImageDraw.Draw(img)
    for (x0, y0, x1, y1) in STONES:
        d.rounded_rectangle([x0, y0, x1, y1], radius=2, fill=OUTLINE)
        d.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], radius=1, fill=base)
        d.line([x0 + 2, y0 + 1, x1 - 2, y0 + 1], fill=light)
        d.point((x0 + 1, y0 + 2), fill=light)
        d.line([x0 + 2, y1 - 1, x1 - 1, y1 - 1], fill=dark)
        if moss is not None:
            d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=moss[0])
            d.point((x0 + 2, y0 + 2), fill=moss[1])
            d.point((x1 - 2, y0 + 2), fill=moss[1])
    for (x, y) in [(3, 3), (11, 3), (9, 9), (2, 8), (4, 13)]:
        put(img, x, y, dark)
        put(img, x + 1, y + 1, dark)
    return img


def rubble_a():
    """A: grey boulder pile."""
    return stone_pile(hexa(0x8E8E98), hexa(0xC4C4CE), hexa(0x5E5E6A), hexa(0x3A3530))


def rubble_b():
    """B: mossy rocks."""
    return stone_pile(hexa(0x84868C), hexa(0xB8BAC0), hexa(0x55585E), hexa(0x2E3424),
                      moss=(hexa(0x6DB34F), hexa(0x4F8F3A)))


def rubble_c():
    """C: broken stone bricks with cracks and chips."""
    img = Image.new("RGBA", (16, 16), hexa(0x3A3530))
    d = ImageDraw.Draw(img)
    base, light, dark = hexa(0xA49A88), hexa(0xD2C8B4), hexa(0x6E6658)
    rows = [(0, 4, [(0, 6), (7, 15)]), (5, 9, [(-3, 3), (4, 11), (12, 18)]), (10, 15, [(0, 7), (8, 15)])]
    for (y0, y1, bricks) in rows:
        for (x0, x1) in bricks:
            d.rectangle([x0, y0, x1, y1], fill=OUTLINE)
            d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=base)
            d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=light)
            d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=dark)
    for (x, y) in [(3, 1), (4, 2), (4, 3), (9, 6), (10, 7), (2, 12), (3, 13), (12, 11), (11, 12), (11, 13)]:
        put(img, x, y, OUTLINE)
    for (x, y) in [(15, 0), (14, 0), (0, 15), (6, 9), (15, 15)]:
        put(img, x, y, hexa(0x3A3530))
    return img


RUBBLE = {"A": ("Boulder pile", rubble_a), "B": ("Mossy rocks", rubble_b), "C": ("Broken bricks", rubble_c)}


# ---------------------------------------------------------------- tangle (Cut)

def vine_path(img, pts, col, hi):
    for i in range(len(pts) - 1):
        x0, y0 = pts[i]
        x1, y1 = pts[i + 1]
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for s in range(steps + 1):
            x = round(x0 + (x1 - x0) * s / steps)
            y = round(y0 + (y1 - y0) * s / steps)
            put(img, x, y, col)
            if s % 3 == 0:
                put(img, x, y - 1, hi)


def tangle_a():
    """A: green vines with leaves, see-through."""
    img = blank()
    stem, hi, leaf, leaf_hi = hexa(0x3E8E3A), hexa(0x7CC45A), hexa(0x4FA83E), hexa(0x9BDB6A)
    vine_path(img, [(0, 2), (5, 6), (9, 4), (15, 9)], stem, hi)
    vine_path(img, [(0, 12), (4, 9), (10, 13), (15, 11)], stem, hi)
    vine_path(img, [(3, 0), (6, 8), (4, 15)], stem, hi)
    vine_path(img, [(12, 0), (10, 7), (13, 15)], stem, hi)
    for (x, y) in [(2, 4), (8, 5), (13, 8), (5, 10), (11, 12), (6, 2), (11, 3), (4, 13)]:
        put(img, x, y, leaf)
        put(img, x + 1, y, leaf)
        put(img, x, y + 1, leaf)
        put(img, x + 1, y - 1, leaf_hi)
    return outline_alpha(img)


def tangle_b():
    """B: thorny bramble with berries, see-through."""
    img = blank()
    stem, hi, thorn, berry = hexa(0x7A4E2E), hexa(0xA8744A), hexa(0xE8DCC0), hexa(0xD8344A)
    vine_path(img, [(0, 0), (8, 7), (15, 15)], stem, hi)
    vine_path(img, [(15, 1), (7, 8), (0, 14)], stem, hi)
    vine_path(img, [(0, 7), (6, 5), (15, 7)], stem, hi)
    vine_path(img, [(4, 15), (9, 11), (13, 15)], stem, hi)
    for (x, y) in [(3, 2), (12, 4), (5, 11), (10, 10), (2, 6), (13, 6), (8, 12)]:
        put(img, x, y - 1, thorn)
    for (x, y) in [(6, 3), (11, 12), (2, 12), (13, 2)]:
        put(img, x, y, berry)
        put(img, x + 1, y, berry)
        put(img, x, y + 1, shade(berry, 0.7))
        put(img, x + 1, y + 1, berry)
    return outline_alpha(img)


def tangle_c():
    """C: knotted tall grass with flowers, see-through."""
    img = blank()
    blades = [hexa(0x4F9A3A), hexa(0x6DB34F), hexa(0x3E7E30)]
    rnd = random.Random(7)
    for x in range(0, 16):
        h = 6 + rnd.randint(0, 9)
        lean = rnd.choice([-1, 0, 1])
        col = blades[x % 3]
        for k in range(h):
            put(img, x + (lean if k > h // 2 else 0), 15 - k, col)
    for (x, y) in [(1, 3), (2, 4), (3, 5), (4, 6), (5, 7), (6, 8), (14, 2), (13, 3), (12, 4), (11, 5), (10, 6)]:
        put(img, x, y, hexa(0x9BDB6A))
    for (x, y, c) in [(3, 6, 0xFFD23F), (11, 4, 0xFF7FA8), (7, 9, 0xFFFFFF), (14, 8, 0xFFD23F)]:
        put(img, x, y, hexa(c))
        put(img, x - 1, y, hexa(c))
        put(img, x + 1, y, hexa(c))
        put(img, x, y - 1, hexa(c))
        put(img, x, y + 1, hexa(c))
        put(img, x, y, hexa(0xE07020) if c != 0xFFFFFF else hexa(0xFFD23F))
    return outline_alpha(img)


TANGLE = {"A": ("Vines and leaves", tangle_a), "B": ("Thorny bramble", tangle_b), "C": ("Knotted grass + flowers",
                                                                                      tangle_c)}


# ---------------------------------------------------------------- rescue fire (Surf), 8 animation frames

FIRE_FRAMES = 8
FLAME = [hexa(0xB8261C), hexa(0xE0562A), hexa(0xFF8A30), hexa(0xFFC84A), hexa(0xFFF0A0)]


def fire_a(frame):
    """A: classic flame wall, tongues rising and flickering."""
    img = blank()
    for x in range(16):
        h = 9 + 4 * math.sin(x * 0.9 + frame * math.pi / 4) + 2 * math.sin(x * 2.3 - frame * math.pi / 2)
        h = int(max(4, min(15, h)))
        for k in range(h):
            t = k / h
            idx = 0 if t > 0.85 else 1 if t > 0.6 else 2 if t > 0.35 else 3 if t > 0.12 else 4
            if x in (0, 15) and idx > 2:
                idx = 2
            put(img, x, 15 - k, FLAME[idx])
    return img


def fire_b(frame):
    """B: outlined cartoon flames, three tongues swaying (matches the minigame blasts)."""
    img = blank()
    d = ImageDraw.Draw(img)
    sway = [0, 1, 1, 0, 0, -1, -1, 0][frame]
    tongues = [(3, 9 + (frame % 2)), (8, 3 + ((frame + 1) % 3)), (13, 8 + ((frame + 2) % 2))]
    for i, (cx, top) in enumerate(tongues):
        s = sway if i != 1 else -sway
        pts = [(cx - 4, 15), (cx - 3, 11), (cx + s, top), (cx + 3, 11), (cx + 4, 15)]
        d.polygon(pts, fill=FLAME[1])
        d.polygon([(cx - 2, 15), (cx - 2, 12), (cx + s, top + 4), (cx + 2, 12), (cx + 2, 15)], fill=FLAME[2])
        d.polygon([(cx - 1, 15), (cx + s // 2, top + 7), (cx + 1, 15)], fill=FLAME[3])
        put(img, cx, 14, FLAME[4])
    return outline_alpha(img)


def fire_c(frame):
    """C: low ember wall: short flames along the bottom and sparks floating up."""
    img = blank()
    for x in range(16):
        h = 5 + 2 * math.sin(x * 1.3 + frame * math.pi / 2)
        h = int(max(3, h))
        for k in range(h):
            t = k / h
            idx = 1 if t > 0.7 else 2 if t > 0.4 else 3
            put(img, x, 15 - k, FLAME[idx])
        put(img, x, 15, FLAME[4] if x % 3 else FLAME[3])
    rnd = random.Random(3)
    for i in range(6):
        x = rnd.randint(1, 14)
        y0 = rnd.randint(0, 15)
        y = (y0 - frame * 2) % 11
        put(img, x + (1 if (frame + i) % 4 == 0 else 0), y, FLAME[3] if i % 2 else FLAME[4])
    return img


FIRE = {"A": ("Classic flame wall", fire_a), "B": ("Outlined cartoon flames", fire_b), "C": ("Low embers + sparks",
                                                                                          fire_c)}


# ---------------------------------------------------------------- ice (Burn)

def ice_a():
    """A: clear blue ice with cracks (translucent)."""
    img = Image.new("RGBA", (16, 16), hexa(0x9ADCF8, 115))
    edge, crack, shine = hexa(0xDDF6FF, 220), hexa(0xFFFFFF, 200), hexa(0xFFFFFF, 240)
    for i in range(16):
        put(img, i, 0, edge)
        put(img, 0, i, edge)
        put(img, i, 15, hexa(0x5AAAD8, 200))
        put(img, 15, i, hexa(0x5AAAD8, 200))
    for (x, y) in [(3, 3), (4, 4), (5, 4), (6, 5), (7, 7), (8, 8), (8, 9), (10, 10), (11, 12), (7, 6), (9, 5),
                   (10, 4), (12, 3)]:
        put(img, x, y, crack)
    for (x, y) in [(2, 2), (3, 2), (2, 3), (12, 12), (13, 13)]:
        put(img, x, y, shine)
    return img


def ice_b():
    """B: crystal shards, faceted and a little more solid."""
    img = Image.new("RGBA", (16, 16), hexa(0x7CC8F0, 190))
    d = ImageDraw.Draw(img)
    facets = [hexa(0xBFEFFF, 210), hexa(0x8AD4F8, 200), hexa(0x5AAAD8, 210), hexa(0xE8FBFF, 230)]
    for i, x in enumerate(range(0, 16, 4)):
        top = [2, 0, 3, 1][i]
        d.polygon([(x, 15), (x, top + 3), (x + 2, top), (x + 3, top + 3), (x + 3, 15)], fill=facets[i % 4])
        d.line([x + 1, top + 2, x + 1, 13], fill=hexa(0xFFFFFF, 230))
        d.line([x + 3, top + 3, x + 3, 15], fill=hexa(0x3A80B0, 220))
    return img


def ice_c():
    """C: frosty rim with bubbles, clearer in the middle."""
    img = Image.new("RGBA", (16, 16), hexa(0xA8E4FA, 105))
    rnd = random.Random(11)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0 or (edge == 1 and rnd.random() < 0.6) or (edge == 2 and rnd.random() < 0.2):
                put(img, x, y, hexa(0xF4FCFF, 230))
    for (x, y, r) in [(5, 6, 1), (10, 9, 1), (7, 11, 0), (11, 5, 0)]:
        if r:
            for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
                put(img, x + dx, y + dy, hexa(0xFFFFFF, 210))
        else:
            put(img, x, y, hexa(0xFFFFFF, 230))
    return img


ICE = {"A": ("Clear ice with cracks", ice_a), "B": ("Crystal shards", ice_b), "C": ("Frosty rim + bubbles", ice_c)}


# ---------------------------------------------------------------- sand mound (Dig)

SAND = [hexa(0xE8D49A), hexa(0xD8BE7E), hexa(0xC4A666), hexa(0xF6E8BC)]


def sand_a():
    """A: quicksand swirl."""
    img = Image.new("RGBA", (16, 16), SAND[0])
    for t in range(0, 220):
        a = t * 0.12
        r = t * 0.034
        x = int(round(7.5 + r * math.cos(a)))
        y = int(round(7.5 + r * math.sin(a)))
        put(img, x, y, SAND[2])
        put(img, x + 1, y, SAND[1])
    put(img, 7, 7, SAND[2])
    put(img, 8, 8, SAND[2])
    for (x, y) in [(1, 1), (14, 2), (2, 13), (13, 14)]:
        put(img, x, y, SAND[3])
    return img


def sand_b():
    """B: dune ripples."""
    img = Image.new("RGBA", (16, 16), SAND[0])
    for row in range(4):
        for x in range(16):
            y = row * 4 + 1 + int(round(math.sin(x * 0.7 + row) * 1.2))
            put(img, x, y % 16, SAND[3])
            put(img, x, (y + 1) % 16, SAND[1])
            put(img, x, (y + 2) % 16, SAND[2] if x % 5 else SAND[1])
    return img


def sand_c():
    """C: speckled sand with pebbles and a shell."""
    img = Image.new("RGBA", (16, 16), SAND[0])
    rnd = random.Random(5)
    for _ in range(40):
        put(img, rnd.randint(0, 15), rnd.randint(0, 15), rnd.choice(SAND[1:]))
    for (x, y) in [(3, 4), (11, 11)]:
        put(img, x, y, hexa(0x8E8E98))
        put(img, x + 1, y, hexa(0xB8B8C2))
        put(img, x, y + 1, hexa(0x6E6E78))
        put(img, x + 1, y + 1, hexa(0x8E8E98))
    for (x, y, c) in [(10, 3, 0xFFB8A0), (11, 3, 0xFFD8C8), (12, 3, 0xFFB8A0), (10, 4, 0xE89880), (11, 4, 0xFFB8A0),
                      (12, 4, 0xE89880), (11, 5, 0xE89880)]:
        put(img, x, y, hexa(c))
    return img


SANDS = {"A": ("Quicksand swirl", sand_a), "B": ("Dune ripples", sand_b), "C": ("Pebbles + shell", sand_c)}


# ---------------------------------------------------------------- alert beam particle (8x8, 4 frames)

def beam_b(frame):
    """B: light blue styler sparkle (four-point star)."""
    img = Image.new("RGBA", (8, 8), CLEAR)
    a = [255, 230, 180, 110][frame]
    col, core = hexa(0x5BC0EB, a), hexa(0xFFFFFF, a)
    r = [3, 3, 2, 1][frame]
    for i in range(1, r + 1):
        for (dx, dy) in ((i, 0), (-i, 0), (0, i), (0, -i)):
            put(img, 4 + dx, 4 + dy, col if i > 1 else hexa(0xA8F0FF, a))
    put(img, 4, 4, core)
    return img


def beam_c(frame):
    """C: golden rescue ring, a small halo that spins (alternates its bright side)."""
    img = Image.new("RGBA", (8, 8), CLEAR)
    a = [255, 230, 180, 110][frame]
    d = ImageDraw.Draw(img)
    d.ellipse([1, 2, 6, 5], outline=hexa(0xFFC94A, a))
    bright = [(1, 3), (3, 2), (6, 4), (4, 5)][frame]
    put(img, bright[0], bright[1], hexa(0xFFFFFF, a))
    return img


def beam_a(frame):
    """A: stand-in for the vanilla end rod particle (no new art)."""
    img = Image.new("RGBA", (8, 8), CLEAR)
    a = [255, 230, 180, 110][frame]
    put(img, 3, 3, hexa(0xFFFFFF, a))
    put(img, 4, 3, hexa(0xFFFFFF, a))
    put(img, 3, 4, hexa(0xFFFFFF, a))
    put(img, 4, 4, hexa(0xFFFFFF, a))
    for (x, y) in [(2, 3), (5, 4), (3, 2), (4, 5)]:
        put(img, x, y, hexa(0xFFF0D8, a // 2))
    return img


BEAMS = {"A": ("Vanilla end rod (no new art)", beam_a), "B": ("Light blue styler sparkle", beam_b),
         "C": ("Golden rescue halo", beam_c)}


# ---------------------------------------------------------------- scene renderer (oblique view)

T = 16


def top_face(tex):
    """The texture squashed onto a parallelogram 16 wide, 8 tall, leaning right."""
    face = Image.new("RGBA", (T + T // 2, T // 2), CLEAR)
    for y in range(T // 2):
        for x in range(T):
            c = tex.getpixel((x, y * 2))
            if c[3]:
                face.putpixel((x + (T // 2 - 1 - y), y), shade(c, 1.12))
    return face


def side_face(tex):
    """The texture squashed onto the right-hand face, 8 wide, 24 tall, leaning up."""
    face = Image.new("RGBA", (T // 2, T + T // 2), CLEAR)
    for x in range(T // 2):
        for y in range(T):
            c = tex.getpixel((x * 2, y))
            if c[3]:
                face.putpixel((x, y + (T // 2 - 1 - x)), shade(c, 0.72))
    return face


def grass_tex():
    img = Image.new("RGBA", (16, 16), hexa(0x8B5A2B))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 3], fill=hexa(0x5DB84A))
    for x in range(0, 16, 3):
        d.point((x, 4), fill=hexa(0x3E8E3A))
    return img


def grass_top():
    img = Image.new("RGBA", (16, 16), hexa(0x5DB84A))
    rnd = random.Random(2)
    for _ in range(20):
        img.putpixel((rnd.randint(0, 15), rnd.randint(0, 15)), hexa(0x4F9A3A))
    return img


def critter(frame=0):
    """A small round placeholder creature (not any real Pokémon) that looks worried."""
    img = Image.new("RGBA", (20, 20), CLEAR)
    d = ImageDraw.Draw(img)
    bob = frame % 2
    d.ellipse([2, 4 + bob, 17, 19], fill=OUTLINE)
    d.ellipse([3, 5 + bob, 16, 18], fill=hexa(0xF0C060))
    d.ellipse([4, 6 + bob, 9, 10 + bob], fill=hexa(0xFFE0A0))
    d.polygon([(3, 7 + bob), (1, 0), (7, 5 + bob)], fill=OUTLINE)
    d.polygon([(16, 7 + bob), (18, 0), (12, 5 + bob)], fill=OUTLINE)
    d.polygon([(4, 6 + bob), (2, 2), (6, 5 + bob)], fill=hexa(0xF0C060))
    d.polygon([(15, 6 + bob), (17, 2), (13, 5 + bob)], fill=hexa(0xF0C060))
    d.point((7, 11 + bob), fill=OUTLINE)
    d.point((12, 11 + bob), fill=OUTLINE)
    d.line([8, 15 + bob, 11, 15 + bob], fill=OUTLINE)
    d.point((14, 9 + bob), fill=hexa(0x8FD3F5))
    d.point((14, 10 + bob), fill=hexa(0x8FD3F5))
    return img


def render(blocks, cross, pokemon_at, beam=None, frame=0, size=(7, 5, 6)):
    """blocks: {(x, y, z): tex} cubes; cross: {(x, y, z): tex} flat billboards; z grows toward the viewer."""
    W, H, D = size
    width = W * T + D * T // 2 + 8
    height = H * T + D * T // 2 + 20
    img = Image.new("RGBA", (width, height), hexa(0x8FD3F5))
    ox, oy = 4, height - T - 6

    def screen(x, y, z):
        return ox + x * T + (D - 1 - z) * T // 2, oy - y * T - (D - 1 - z) * T // 2

    items = []
    for (x, y, z), tex in blocks.items():
        items.append(((z, y, x, 0), ("cube", x, y, z, tex)))
    for (x, y, z), tex in cross.items():
        items.append(((z, y, x, 1), ("cross", x, y, z, tex)))
    if pokemon_at is not None:
        x, y, z = pokemon_at
        items.append(((z, y, x, 1), ("mon", x, y, z, None)))
    for _, (kind, x, y, z, tex) in sorted(items, key=lambda i: i[0]):
        sx, sy = screen(x, y, z)
        if kind == "cube":
            top_tex = grass_top() if tex == "grass" else tex
            front = grass_tex() if tex == "grass" else tex
            img.alpha_composite(top_face(top_tex), (sx, sy - T // 2))
            img.alpha_composite(side_face(front), (sx + T, sy - T // 2))
            img.alpha_composite(front, (sx, sy))
        elif kind == "cross":
            img.alpha_composite(tex, (sx + 4, sy - 4))
        else:
            img.alpha_composite(critter(frame).resize((28, 28), Image.NEAREST), (sx - 2, sy - 14))
    if beam is not None:
        sx, sy = screen(*pokemon_at)
        rnd = random.Random(frame // 2)
        for k in range(12):
            f = (k + frame) % 4
            y = sy - 22 - k * 9 - (frame % 3) * 3
            if y < 2:
                continue
            img.alpha_composite(beam(f), (sx + 6 + rnd.randint(-2, 2), y))
    return img


def ring(r, h, cap=False, corners_up=False):
    pos = []
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if max(abs(dx), abs(dz)) != r:
                continue
            for y in range(h):
                pos.append((dx, y, dz))
            if corners_up and abs(dx) == r and abs(dz) == r:
                pos.append((dx, h, dz))
    if cap:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                pos.append((dx, h, dz))
    return pos


SHAPES = {
    "rubble": dict(r=1, h=1, corners_up=True),
    "tangle": dict(r=1, h=2),
    "fire": dict(r=2, h=1),
    "ice": dict(r=1, h=2, cap=True),
    "sand": dict(r=1, h=1),
}


def scene(kind, tex_or_fn, frame=0, beam=None):
    cx, cz = 3, 2
    blocks, cross = {}, {}
    for x in range(7):
        for z in range(5):
            blocks[(x, 0, z)] = "grass"
    shape = SHAPES[kind]
    tex = tex_or_fn(frame) if callable(tex_or_fn) else tex_or_fn
    for (dx, y, dz) in ring(shape["r"], shape["h"], shape.get("cap", False), shape.get("corners_up", False)):
        p = (cx + dx, 1 + y, cz + dz)
        if kind in ("fire",):
            cross[p] = tex
        else:
            blocks[p] = tex
    return render(blocks, cross, (cx, 1, cz), beam=beam, frame=frame, size=(7, 4, 5))


# ---------------------------------------------------------------- preview sheets

BG, PANEL, TEXT, DIMC, GOLD = hexa(0x101820), hexa(0x1C2A36), hexa(0xE0E6EB), hexa(0x6A7C8C), hexa(0xFFC94A)

BLOCKS = [
    ("rubble", "RUBBLE - freed with Rock Smash (Fighting)", RUBBLE),
    ("tangle", "TANGLE - freed with Cut (Grass)", TANGLE),
    ("fire", "RESCUE FIRE - freed with Surf (Water). Animated, see fire.gif", FIRE),
    ("ice", "ICE - freed with Burn (Fire). See-through", ICE),
    ("sand", "SAND MOUND - freed with Dig (Ground)", SANDS),
]


def tiled(tex, n=3, bg=PANEL):
    img = Image.new("RGBA", (16 * n, 16 * n), bg)
    for i in range(n):
        for j in range(n):
            img.alpha_composite(tex, (i * 16, j * 16))
    return img


def sheet_blocks(out):
    scale = 3
    sc = scene("rubble", rubble_a())
    cell_w = max(48 * scale, sc.width * 2) + 30
    row_h = 48 * scale + 30 + sc.height * 2 + 30
    img = Image.new("RGBA", (140 + 3 * cell_w, 30 + len(BLOCKS) * row_h), BG)
    d = ImageDraw.Draw(img)
    d.text((12, 8), "RESCUE TRAP BLOCKS - each tiled 3x3 at 3x, and as the trap around a Pokemon (2x). "
                    "Pick one letter per row.", fill=TEXT)
    y = 30
    for kind, title, options in BLOCKS:
        d.text((12, y + 4), title, fill=GOLD)
        for c, (key, (name, fn)) in enumerate(options.items()):
            tex = fn(0) if kind == "fire" else fn()
            x = 140 + c * cell_w
            d.text((x, y + 20), key + "  " + name, fill=TEXT)
            img.alpha_composite(tiled(tex).resize((48 * scale, 48 * scale), Image.NEAREST), (x, y + 36))
            sc = scene(kind, fn if kind == "fire" else tex)
            img.alpha_composite(sc.resize((sc.width * 2, sc.height * 2), Image.NEAREST), (x, y + 36 + 48 * scale + 12))
        y += row_h
    img.save(os.path.join(out, "trap-blocks.png"))


def gif(frames, path, ms=110):
    frames = [f.convert("RGB").convert("P", palette=Image.ADAPTIVE, colors=255) for f in frames]
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=ms, loop=0, disposal=2)


def sheet_fire_gif(out):
    frames = []
    for f in range(FIRE_FRAMES * 2):
        sc0 = scene("fire", fire_a, frame=f % FIRE_FRAMES)
        img = Image.new("RGBA", (30 + 3 * (sc0.width * 2 + 20), sc0.height * 2 + 60), BG)
        d = ImageDraw.Draw(img)
        d.text((12, 8), "RESCUE FIRE - animated (8 frames)", fill=TEXT)
        for c, (key, (name, fn)) in enumerate(FIRE.items()):
            sc = scene("fire", fn, frame=f % FIRE_FRAMES)
            x = 20 + c * (sc.width * 2 + 20)
            d.text((x, 26), key + "  " + name, fill=GOLD)
            img.alpha_composite(sc.resize((sc.width * 2, sc.height * 2), Image.NEAREST), (x, 44))
        frames.append(img)
    gif(frames, os.path.join(out, "fire.gif"))
    frames[0].save(os.path.join(out, "fire.png"))


def sheet_beam(out):
    frames = []
    for f in range(12):
        sc0 = scene("rubble", rubble_a(), frame=f, beam=beam_a)
        img = Image.new("RGBA", (30 + 3 * (sc0.width * 2 + 20), sc0.height * 2 + 60), BG)
        d = ImageDraw.Draw(img)
        d.text((12, 8), "ALERT BEAM over the trapped Pokemon (only its Ranger sees it)", fill=TEXT)
        for c, (key, (name, fn)) in enumerate(BEAMS.items()):
            sc = scene("rubble", rubble_a(), frame=f, beam=fn)
            x = 20 + c * (sc.width * 2 + 20)
            d.text((x, 26), key + "  " + name, fill=GOLD)
            img.alpha_composite(sc.resize((sc.width * 2, sc.height * 2), Image.NEAREST), (x, 44))
        frames.append(img)
    gif(frames, os.path.join(out, "beam.gif"), ms=140)
    frames[0].save(os.path.join(out, "beam.png"))
    # The particle frames themselves at 8x.
    img = Image.new("RGBA", (160 + 4 * 70, 40 + 3 * 80), BG)
    d = ImageDraw.Draw(img)
    d.text((12, 8), "ALERT BEAM PARTICLE - 4 frames at 8x", fill=TEXT)
    for r, (key, (name, fn)) in enumerate(BEAMS.items()):
        y = 34 + r * 80
        d.text((12, y + 24), key + "  " + name[:18], fill=GOLD)
        for f in range(4):
            tile = Image.new("RGBA", (8, 8), PANEL)
            tile.alpha_composite(fn(f))
            img.alpha_composite(tile.resize((64, 64), Image.NEAREST), (160 + f * 70, y))
    img.save(os.path.join(out, "beam-particles.png"))


def previews(out):
    os.makedirs(out, exist_ok=True)
    sheet_blocks(out)
    sheet_fire_gif(out)
    sheet_beam(out)


# Dylan's picks (letters from the option sheets).
CHOSEN = {"rubble": "A", "tangle": "A", "fire": "A", "ice": "A", "sand": "B", "beam": "B"}
TEXTURES = "src/main/resources/assets/cobblemonranger/textures"


def build():
    os.makedirs(TEXTURES + "/block", exist_ok=True)
    RUBBLE[CHOSEN["rubble"]][1]().save(TEXTURES + "/block/rescue_rubble.png")
    TANGLE[CHOSEN["tangle"]][1]().save(TEXTURES + "/block/rescue_tangle.png")
    ICE[CHOSEN["ice"]][1]().save(TEXTURES + "/block/rescue_ice.png")
    SANDS[CHOSEN["sand"]][1]().save(TEXTURES + "/block/rescue_sand.png")
    fire_fn = FIRE[CHOSEN["fire"]][1]
    strip = Image.new("RGBA", (16, 16 * FIRE_FRAMES), CLEAR)
    for f in range(FIRE_FRAMES):
        strip.alpha_composite(fire_fn(f), (0, 16 * f))
    strip.save(TEXTURES + "/block/rescue_fire.png")
    with open(TEXTURES + "/block/rescue_fire.png.mcmeta", "w") as meta:
        meta.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')
    os.makedirs(TEXTURES + "/particle", exist_ok=True)
    defs = "src/main/resources/assets/cobblemonranger/particles"
    beam_fn = BEAMS[CHOSEN["beam"]][1]
    names = []
    for f in range(4):
        beam_fn(f).save(TEXTURES + "/particle/rescue_beam_%d.png" % f)
        names.append('"cobblemonranger:rescue_beam_%d"' % f)
    with open(os.path.join(defs, "rescue_beam.json"), "w") as out:
        out.write('{\n  "textures": [%s]\n}\n' % ", ".join(names))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "previews":
        previews(sys.argv[2])
    else:
        build()
