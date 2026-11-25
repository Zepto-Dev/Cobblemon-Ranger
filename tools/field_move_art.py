"""Field move art: wheel icons, String Shot silk and field move particles, in the mod's outlined pixel style.

Run from the repository root:
  python3 tools/field_move_art.py                writes the chosen art into the mod's assets
  python3 tools/field_move_art.py previews DIR   renders every option sheet into DIR
"""
import os
import sys

from PIL import Image, ImageDraw

OUTLINE = (26, 28, 44, 255)


def hexa(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


def paint(rows, palette, size=16):
    assert len(rows) == size, (len(rows), rows)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == size, (y, len(row), row)
        for x, ch in enumerate(row):
            if ch == "." or ch == " ":
                continue
            img.putpixel((x, y), OUTLINE if ch == "o" else palette[ch])
    return img


# ---------------------------------------------------------------- icons (16x16, full colour, dark outline)

ICONS = {}

ICONS["headbutt"] = ([
    "....oooooooo....",
    "..ooggggGGggoo..",
    ".ogggGGGGgggggo.",
    "ogggGGgggggrrgdo",
    "ogggggggdggrRrdo",
    "ogdggggggggrrgdo",
    ".oddggdgggddgdo.",
    "..ooddddddddoo..",
    "....oooTToo.....",
    "y.y...oTto......",
    ".yyy..oTto...ro.",
    "yyYyy.oTto..rRro",
    ".yyy..oTto..orro",
    "y.y...oTto...oo.",
    "....ooottooo....",
    "...oooooooooo...",
], {"g": hexa(0x4F9A3A), "G": hexa(0x7CC45A), "d": hexa(0x356B2A), "T": hexa(0x9C6B3E), "t": hexa(0x6E4A2A),
    "r": hexa(0xE03A30), "R": hexa(0xFF8A70), "y": hexa(0xFFD23F), "Y": hexa(0xFFFFFF)})

ICONS["burn"] = ([
    ".......o........",
    "......oyo.......",
    "......oyo.......",
    ".....ofyfo......",
    ".....ofyyfo.....",
    "....offyyfo..o..",
    "...offfyyyfoofo.",
    "...offyyyyyffofo",
    "..offyyywyyyffo.",
    "..ofyyywwwyyyfo.",
    "..ofyyywwwwyyfo.",
    "..offyywwwwyffo.",
    "...offyywwyyfo..",
    "....offyyyyffo..",
    ".....offfffoo...",
    "......ooooo.....",
], {"f": hexa(0xE0562A), "y": hexa(0xFFA030), "w": hexa(0xFFE070)})

ICONS["cut"] = ([
    "................",
    "............ooo.",
    "..........oollo.",
    ".........ollllo.",
    "........ollgllo.",
    ".......ollglllo.",
    "......ollgllllo.",
    ".....ollgllllo..",
    "....ollglllloo..",
    "...ollglllloo...",
    "...olgllllo.....",
    "..olglllooo.....",
    "..ogllloo.......",
    ".ogooooo........",
    ".oo.............",
    "o...............",
], {"l": hexa(0x5DB84A), "g": hexa(0x2F7A2C), "w": hexa(0xFFFFFF)})

ICONS["flash"] = ([
    ".y.....oo.....y.",
    "..y..oowwoo..y..",
    "....owwyyyyo....",
    "...owwyyyyyyo...",
    "...owyyyyyyyo...",
    "y..oyyyyyyyyo..y",
    "...oyyyyyyyyo...",
    "...oyyyyyyydo...",
    "....oyyyyydo....",
    ".....oyyydo.....",
    ".....oggggo.....",
    ".....oddddo.....",
    ".....oggggo.....",
    "......oddo......",
    ".......oo.......",
    "................",
], {"y": hexa(0xFFE14A), "w": hexa(0xFFFBD0), "d": hexa(0xD8A81E), "g": hexa(0xB4B8C4)})

ICONS["freeze"] = ([
    ".......oo.......",
    "....o.owwo.o....",
    "...owoowwoowo...",
    "....owowwowo....",
    ".o...owwwwo...o.",
    "owoo..owwo..oowo",
    ".owwoowwwwoowwo.",
    "..owwwwbbwwwwo..",
    "..owwwwbbwwwwo..",
    ".owwoowwwwoowwo.",
    "owoo..owwo..oowo",
    ".o...owwwwo...o.",
    "....owowwowo....",
    "...owoowwoowo...",
    "....o.owwo.o....",
    ".......oo.......",
], {"w": hexa(0xBFEFFF), "b": hexa(0x4FA8E0)})

ICONS["rock_smash"] = ([
    "................",
    "................",
    "....oo.oo.oo....",
    "...osso.so.sso..",
    "..osssssssssso..",
    "..ossososssosso.",
    "..oksskssksksso.",
    "..ooooooooooosso",
    ".osssssssssosso.",
    ".osssssssssosso.",
    ".oksssssssskso..",
    "..okkkkkkkkkso..",
    "...oooooooooo...",
    "....oRRRRRRo....",
    "....oooooooo....",
    "................",
], {"s": hexa(0xF2C08A), "k": hexa(0xC98A5A), "R": hexa(0xC22E28)})

ICONS["decompose"] = ([
    "...........o....",
    "..........opo...",
    "...........o.o..",
    "..oo.......oPo..",
    ".owwo.......o...",
    ".owwwo..........",
    "..owwwo.........",
    "...owwwo........",
    "....owwwo...o...",
    ".....owwwo.opo..",
    "......owwwo.o...",
    ".......owwwo....",
    "......oooowwo...",
    ".....omMmMmoo...",
    "....omMmmmMmo...",
    "...oooooooooo...",
], {"w": hexa(0xEDE6D2), "p": hexa(0xB45AD0), "P": hexa(0xE0A0FF), "m": hexa(0xE0E0E0), "M": hexa(0xB8B8B8)})

ICONS["dig"] = ([
    "................",
    "......o..o..o...",
    ".....ow.ow.ow...",
    "....ow.ow.ow....",
    "...ow.ow.ow.....",
    "...o..o..o......",
    "................",
    "......oooo......",
    "....oobbbboo....",
    "...obbBBBBbbo...",
    "..obbBoooobbbo..",
    ".obbBokkkkobbbo.",
    ".obbBokkkkobbbo.",
    "obbbbBoooobbbbbo",
    "obbbbbbbbbbbbbdo",
    "oooooooooooooooo",
], {"b": hexa(0x9C6B3E), "B": hexa(0xC4935A), "d": hexa(0x6E4A2A), "k": hexa(0x2A1E14), "w": hexa(0xFFFFFF)})

ICONS["tailwind"] = ([
    "................",
    "........oooo....",
    "......oowwwwo...",
    ".....owwoooowo..",
    "....owoo....owo.",
    "...owo..ooo.owo.",
    "oooowo.owwwoowo.",
    "owwwwwwwwoowwo..",
    "oooooooooowwo...",
    "..oowwwwwwwoo...",
    ".owwoooooooo....",
    ".owo............",
    ".owwoooooooooo..",
    "..oowwwwwwwwwwo.",
    "....oooooooooo..",
    "................",
], {"w": hexa(0xC8F4EC)})

ICONS["telekinesis"] = ([
    "..p.........p...",
    ".pPp..oo...pPp..",
    "..p..oPPo...p...",
    "....oPwwPo......",
    "....oPwwPo..p...",
    ".....oPPo..pPp..",
    "......oso...p...",
    ".......oso......",
    "........oso.....",
    ".........oso....",
    "....oooooooooo..",
    "...oggggggggggo.",
    "...oGGGGGGGGGGo.",
    "...oddddddddddo.",
    "....oooooooooo..",
    "................",
], {"p": hexa(0xF95587), "P": hexa(0xFF9AC0), "w": hexa(0xFFFFFF), "s": hexa(0x9C6B3E),
    "g": hexa(0xB4B4BC), "G": hexa(0x8C8C94), "d": hexa(0x5A5A66)})

ICONS["string_shot"] = ([
    "oo..............",
    "oWoo............",
    ".oWWoo..........",
    "..ooWWoo........",
    "....ooWWo.......",
    "......oWWo......",
    ".......oWo......",
    "...ooo.oWo.ooo..",
    "..oWWWooWooWWWo.",
    "..ooooWWWWWoooo.",
    "....oWWoWoWWo...",
    "...oWo.oWo.oWo..",
    "..oWo..oWo..oWo.",
    "..oo...oWo...oo.",
    ".......oWo......",
    "........o.......",
], {"W": hexa(0xF4F1E6)})

ICONS["stone_edge"] = ([
    "................",
    "..........o.....",
    ".........oWo....",
    "........oWlo....",
    "........oWlso...",
    ".......oWllso...",
    ".oooooooWlllsoo.",
    ".olllllWllllsso.",
    ".olllllWlllllso.",
    ".osssssosssssso.",
    ".olllsolllllsoo.",
    ".olllsolllllso..",
    ".osssssssssssso.",
    ".ollllllsollllo.",
    ".oddddddddddddo.",
    ".oooooooooooooo.",
], {"l": hexa(0xB4B4BC), "s": hexa(0x8C8C94), "d": hexa(0x5A5A66), "W": hexa(0xFFFFFF)})

ICONS["spook"] = ([
    "................",
    ".....oooooo.....",
    "....oppppppo....",
    "...oppPpppppo...",
    "..opPpppppppo...",
    "..oppoopoopppo..",
    "..oppoopoopppo..",
    "..opppppppppppo.",
    "..oppppoooppppo.",
    "..opppppppppppo.",
    "..oppppppppppo..",
    "..oppppppppppo..",
    "..opdpppdppdpo..",
    "..oodpodppodoo..",
    "...oo.oo.oo.o...",
    "................",
], {"p": hexa(0xA48ADC), "P": hexa(0xD8C8FF), "d": hexa(0x735797)})

ICONS["outrage"] = ([
    "................",
    ".oooooooooooooo.",
    ".okkkkkkkkkkkko.",
    ".okpkkkkkkfkkko.",
    ".okkkkkkkfFkkpo.",
    ".okkkkkkfFkkkko.",
    ".okkkkfkFkkfkko.",
    ".okkkfFkkkfFkko.",
    ".okkfFkkkfFkkko.",
    ".okkFkkfkFkkkko.",
    ".okkkkfFkkkkpko.",
    ".okkkfFkkkkkkko.",
    ".okpkFkkkkkkkko.",
    ".okkkkkkkkkkkko.",
    ".odddddddddddddo",
    ".oooooooooooooo.",
], {"k": hexa(0x2A1E3E), "p": hexa(0x6A4A9A), "d": hexa(0x160F22), "f": hexa(0xFF8A30), "F": hexa(0xFFE070)})

ICONS["snatch"] = ([
    "................",
    "..oo.oo.oo......",
    ".okko.kko.kko...",
    ".okkokkkokkko...",
    ".okkkkkkkkkko...",
    ".okkkkkkkkkko.o.",
    "..okkkkkkkkkokko",
    "..okkkkkkkkkkko.",
    "...okkkkkkkkko..",
    "....okkkkkkko...",
    ".....oKKKKKo....",
    "......oyyyo.....",
    ".....oyYyyyo....",
    ".....oyyyyyo....",
    "......oyyyo.....",
    ".......ooo......",
], {"k": hexa(0x3A3448), "K": hexa(0x5A5070), "y": hexa(0xFFC94A), "Y": hexa(0xFFF2B0)})

ICONS["magnet"] = ([
    "................",
    "....oooooooo....",
    "...orrrrrrrro...",
    "..orRrrrrrrrro..",
    ".orRrooooooorro.",
    ".orRro....orrro.",
    ".orrro....orrro.",
    ".orrro....orrro.",
    ".orrro....orrro.",
    ".orrro....orrro.",
    ".oooooo..oooooo.",
    ".osssso..osssso.",
    ".oSSSSo..oSSSSo.",
    ".osssso..osssso.",
    ".oooooo..oooooo.",
    "................",
], {"r": hexa(0xE03A30), "R": hexa(0xFF7A66), "s": hexa(0xD8DCE6), "S": hexa(0xA0A6B4)})

ICONS["charm"] = ([
    "..............w.",
    ".............www",
    "..oooo..oooo..w.",
    ".oppppoopppPpo..",
    "oppWWppppppppPo.",
    "opWWpppppppppPo.",
    "oppppppppppppPo.",
    "oppppppppppppPo.",
    ".oppppppppppPo..",
    "..oppppppppPo...",
    "...oppppppPo....",
    "....oppppPo.....",
    ".w...oppPo......",
    "www...ooo.......",
    ".w..............",
    "................",
], {"p": hexa(0xFF7FA8), "P": hexa(0xD0507E), "W": hexa(0xFFFFFF), "w": hexa(0xFFFFFF)})

TYPE_OF = {
    "headbutt": ("normal", 0xA8A77A), "burn": ("fire", 0xEE8130), "surf": ("water", 0x6390F0),
    "cut": ("grass", 0x7AC74C), "flash": ("electric", 0xF7D02C), "freeze": ("ice", 0x96D9D6),
    "rock_smash": ("fighting", 0xC22E28), "decompose": ("poison", 0xA33EA1), "dig": ("ground", 0xE2BF65),
    "tailwind": ("flying", 0xA98FF3), "telekinesis": ("psychic", 0xF95587), "string_shot": ("bug", 0xA6B91A),
    "stone_edge": ("rock", 0xB6A136), "spook": ("ghost", 0x735797), "outrage": ("dragon", 0x6F35FC),
    "snatch": ("dark", 0x705746), "magnet": ("steel", 0xB7B7CE), "charm": ("fairy", 0xD685AD),
}
ORDER = list(TYPE_OF)


def icon(name):
    if name == "surf":
        src = Image.open("src/main/resources/assets/cobblemonranger/textures/mob_effect/surfing.png").convert("RGBA")
        box = src.getbbox()
        crop = src.crop(box)
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        img.alpha_composite(crop, ((16 - crop.width) // 2, (16 - crop.height) // 2))
        return img
    rows, pal = ICONS[name]
    img = paint(rows, pal)
    if name == "cut":
        # A white slash across the leaf, splitting it in two.
        for i in range(1, 15):
            img.putpixel((i, i), (255, 255, 255, 255))
            if img.getpixel((i + 1, i))[3] and i < 14:
                img.putpixel((i + 1, i), OUTLINE)
    return img


def lum(c):
    return (c[0] * 299 + c[1] * 587 + c[2] * 114) / 1000


def mono(img, shades):
    """Recolours an icon's fill by brightness into the given shades, keeping the outline."""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.height):
        for x in range(img.width):
            c = img.getpixel((x, y))
            if c[3] == 0:
                continue
            if c[:3] == OUTLINE[:3]:
                out.putpixel((x, y), shades[0])
                continue
            l = lum(c)
            idx = 1 if l < 110 else 2 if l < 200 else 3
            out.putpixel((x, y), shades[min(idx, len(shades) - 1)])
    return out


def style_a(name):
    """A: the icon alone, full colour."""
    cell = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    cell.alpha_composite(icon(name), (2, 2))
    return cell


def style_b(name):
    """B: the icon on a disc in the Pokémon type's colour."""
    cell = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(cell)
    col = hexa(TYPE_OF[name][1])
    dark = tuple(int(v * 0.65) for v in col[:3]) + (255,)
    d.ellipse([0, 0, 19, 19], fill=OUTLINE)
    d.ellipse([1, 1, 18, 18], fill=dark)
    d.ellipse([1, 1, 18, 17], fill=col)
    cell.alpha_composite(icon(name), (2, 2))
    return cell


NAVY, CELLC, ACCENT, ACCENT_LIGHT, ACCENT_DARK = (hexa(0x101820), hexa(0x1C2A36), hexa(0x5BC0EB), hexa(0xA8F0FF),
                                                  hexa(0x2F7FA3))


def style_c(name):
    """C: a light blue glyph on the Browser's navy screen tile."""
    cell = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(cell)
    d.rounded_rectangle([0, 0, 19, 19], radius=3, fill=hexa(0x0B1118))
    d.rounded_rectangle([1, 1, 18, 18], radius=3, fill=CELLC)
    d.line([3, 1, 16, 1], fill=hexa(0x29404F))
    glyph = mono(icon(name), [hexa(0x0B1118), ACCENT_DARK, ACCENT, ACCENT_LIGHT])
    cell.alpha_composite(glyph, (2, 2))
    return cell


def style_d(name):
    """D: full-colour icon on the navy tile with a type-coloured rim."""
    cell = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(cell)
    col = hexa(TYPE_OF[name][1])
    d.rounded_rectangle([0, 0, 19, 19], radius=3, fill=hexa(0x0B1118))
    d.rounded_rectangle([1, 1, 18, 18], radius=3, fill=col)
    d.rounded_rectangle([2, 2, 17, 17], radius=2, fill=CELLC)
    cell.alpha_composite(icon(name), (2, 2))
    return cell


ICON_STYLES = {"A": ("Plain icon", style_a), "B": ("Type disc", style_b), "C": ("Browser screen", style_c),
               "D": ("Navy tile, type rim", style_d)}


# ---------------------------------------------------------------- String Shot silk (16x16 block textures)

def silk_a():
    """A: woven silk mat, opaque."""
    img = Image.new("RGBA", (16, 16))
    base, hi, lo, edge = hexa(0xEDE8D8), hexa(0xFFFFFF), hexa(0xCFC8B0), hexa(0xB0A88C)
    for y in range(16):
        for x in range(16):
            over = ((x // 4) + (y // 4)) % 2 == 0
            c = base
            if over:
                c = hi if y % 4 == 0 else base if y % 4 in (1, 2) else lo
            else:
                c = hi if x % 4 == 0 else base if x % 4 in (1, 2) else lo
            img.putpixel((x, y), c)
    for i in range(16):
        img.putpixel((i, 15), edge)
        img.putpixel((15, i), edge)
    return img


def silk_b():
    """B: a net of thick strands with see-through gaps (cutout, like cobweb)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    strand, hi, shade = hexa(0xF4F1E6), hexa(0xFFFFFF), hexa(0xC8C2AA)
    for i in range(16):
        for (x, y) in [(i, i), (i, 15 - i)]:
            img.putpixel((x, y), strand)
        img.putpixel((i, 0), shade)
        img.putpixel((0, i), shade)
        img.putpixel((i, 8), strand)
        img.putpixel((8, i), strand)
    for (x, y) in [(8, 8), (4, 4), (11, 11), (4, 11), (11, 4)]:
        img.putpixel((x, y), hi)
    for (x, y) in [(3, 8), (12, 8), (8, 3), (8, 12)]:
        img.putpixel((x, y), hexa(0xBFEFFF))
    return img


def silk_c():
    """C: cocoon bands, pale green-gold like Bug-type silk, opaque."""
    img = Image.new("RGBA", (16, 16))
    bands = [hexa(0xF2F0D6), hexa(0xE6E4B8), hexa(0xD2D09A), hexa(0xF8F8E8)]
    for y in range(16):
        for x in range(16):
            k = (y + (x // 5)) % 4
            img.putpixel((x, y), bands[[3, 0, 1, 2][k]])
    for x in range(16):
        img.putpixel((x, (x * 7) % 16), hexa(0xFFFFFF))
    return img


def silk_d():
    """D: rope-ladder silk: two side ropes with rungs, see-through between."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rope, hi, shade = hexa(0xF4F1E6), hexa(0xFFFFFF), hexa(0xC8C2AA)
    for y in range(16):
        for x in (1, 2, 13, 14):
            img.putpixel((x, y), rope if x in (1, 13) else shade)
    for y in (3, 4, 11, 12):
        for x in range(3, 13):
            img.putpixel((x, y), hi if y in (3, 11) else shade)
    for (x, y) in [(2, 8), (14, 1), (1, 14)]:
        img.putpixel((x, y), hexa(0xBFEFFF))
    return img


SILK_STYLES = {"A": ("Woven mat", silk_a), "B": ("Strand net (see-through)", silk_b),
               "C": ("Cocoon bands", silk_c), "D": ("Silk rope ladder (see-through)", silk_d)}


# ---------------------------------------------------------------- particles (8x8, 4 frames)

PARTICLES = ["flash", "tailwind", "spook", "charm", "magnet", "decompose", "string_shot", "telekinesis"]


def particle_b(kind, frame):
    """B: outlined, like the assist sprites."""
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = [3, 2, 1, 0][frame]  # shrink as it fades
    if kind == "flash":
        c = [hexa(0xFFFBD0), hexa(0xFFE14A), hexa(0xFFE14A), hexa(0xD8A81E)][frame]
        r = 3 - frame // 2
        d.line([4 - r, 4, 3 + r, 4], fill=c)
        d.line([4, 4 - r, 4, 3 + r], fill=c)
        d.point((4, 4), fill=hexa(0xFFFFFF))
        d.point((3, 3), fill=c)
    elif kind == "tailwind":
        c = hexa(0xC8F4EC)
        x0 = frame
        d.line([x0, 3, x0 + 5 - frame, 3], fill=OUTLINE)
        d.line([x0, 4, x0 + 5 - frame, 4], fill=c)
        d.point((x0 + 5 - frame, 3), fill=c)
    elif kind == "spook":
        rows = ["..ooo...", ".opppo..", "opPpppo.", "opoppoo.", "oppppo..", "opppppo.", ".opopo..", "..o.o..."]
        im = paint(rows, {"p": hexa(0xA48ADC), "P": hexa(0xD8C8FF)}, 8)
        im.putalpha(im.getchannel("A").point(lambda a: a * (4 - frame) // 4))
        return im
    elif kind == "charm":
        rows = [".oo.oo..", "oppopPo.", "opppppo.", "opppppo.", ".opppo..", "..opo...", "...o....", "........"]
        im = paint(rows, {"p": hexa(0xFF7FA8), "P": hexa(0xFFFFFF)}, 8)
        return im.transform(im.size, Image.AFFINE, (1, 0, 0, 0, 1, -frame + 1), Image.NEAREST)
    elif kind == "magnet":
        c = [hexa(0xFFFFFF), hexa(0xD8DCE6), hexa(0xA0A6B4), hexa(0xA0A6B4)][frame]
        d.rectangle([2, 2, 5, 5], fill=OUTLINE)
        d.rectangle([3, 3, 4, 4], fill=c)
        if frame < 2:
            d.point((1, 1), fill=hexa(0xE03A30))
            d.point((6, 6), fill=hexa(0xE03A30))
    elif kind == "decompose":
        r = [1, 2, 2, 3][frame]
        d.ellipse([4 - r - 1, 4 - r - 1, 4 + r, 4 + r], fill=OUTLINE)
        d.ellipse([4 - r, 4 - r, 4 + r - 1, 4 + r - 1], fill=hexa(0xB45AD0) if frame < 3 else hexa(0xE0E0E0))
        d.point((4 - r, 4 - r), fill=hexa(0xE0A0FF))
    elif kind == "string_shot":
        c = hexa(0xF4F1E6)
        for i in range(7 - frame * 2):
            d.point((i, 6 - i), fill=c)
            d.point((i + 1, 6 - i), fill=OUTLINE if i % 2 else c)
    elif kind == "telekinesis":
        r = [1, 2, 3, 3][frame]
        d.ellipse([4 - r, 4 - r, 3 + r, 3 + r], outline=hexa(0xF95587) if frame < 3 else hexa(0xFF9AC0))
    return img


def particle_c(kind, frame):
    """C: soft glow, no outline, brighter centre that fades out."""
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    base = {"flash": 0xFFE14A, "tailwind": 0xC8F4EC, "spook": 0xA48ADC, "charm": 0xFF7FA8, "magnet": 0xD8DCE6,
            "decompose": 0xB45AD0, "string_shot": 0xF4F1E6, "telekinesis": 0xF95587}[kind]
    a = [255, 220, 160, 90][frame]
    col, core, glow = hexa(base, a), hexa(0xFFFFFF, a), hexa(base, a // 2)
    d = ImageDraw.Draw(img)
    if kind in ("flash", "magnet"):
        r = 3 - frame // 2
        d.line([4 - r, 4, 3 + r, 4], fill=col)
        d.line([4, 4 - r, 4, 3 + r], fill=col)
        d.rectangle([3, 3, 4, 4], fill=core)
    elif kind == "tailwind":
        d.line([frame, 4, 7, 4], fill=glow)
        d.line([frame + 2, 4, 6, 4], fill=col)
        d.point((6, 3), fill=col)
    elif kind == "spook":
        d.ellipse([2, 1 + frame // 2, 5, 5 + frame // 2], fill=col)
        d.point((3, 3 + frame // 2), fill=hexa(0x2A1E3E, a))
        d.point((5, 6), fill=glow)
        d.point((2, 6), fill=glow)
    elif kind == "charm":
        rows = ["........", ".pp.pp..", "pWpppppp", "pppppppp", ".pppppp.", "..pppp..", "...pp...", "........"]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    img.putpixel((x, max(0, y - frame // 2)), core if ch == "W" else col)
    elif kind == "decompose":
        r = [1, 1, 2, 2][frame]
        d.ellipse([4 - r, 4 - r, 4 + r, 4 + r], fill=glow)
        d.point((4, 4), fill=col)
        d.point((3, 3), fill=core)
    elif kind == "string_shot":
        for i in range(8 - frame * 2):
            d.point((i, 7 - i), fill=col if i % 3 else core)
    elif kind == "telekinesis":
        r = [1, 2, 3, 3][frame]
        d.ellipse([4 - r, 4 - r, 3 + r, 3 + r], outline=col)
        d.point((4, 4), fill=glow)
    return img


PARTICLE_STYLES = {"B": ("Outlined, like the assist sprites", particle_b), "C": ("Soft glow, no outline", particle_c)}
VANILLA = {"flash": "end_rod + flash", "tailwind": "cloud + sweep", "spook": "soul", "charm": "heart",
           "magnet": "electric_spark", "decompose": "witch + spore_blossom", "string_shot": "white_ash",
           "telekinesis": "portal (pink tint impossible)"}

# ---------------------------------------------------------------- preview sheets

BG, PANEL, TEXT, DIMC = hexa(0x101820), hexa(0x1C2A36), hexa(0xE0E6EB), hexa(0x6A7C8C)


def label(d, xy, text, fill=TEXT):
    d.text(xy, text, fill=fill)


def sheet_icons(out):
    scale = 4
    cw = 20 * scale + 16
    w = 110 + cw * 6
    img = Image.new("RGBA", (w, 40 + len(ICON_STYLES) * 3 * (20 * scale + 34)), BG)
    d = ImageDraw.Draw(img)
    label(d, (12, 10), "FIELD MOVE ICONS - one per type, shown at 4x. Pick one letter.", TEXT)
    y = 40
    for key, (title, fn) in ICON_STYLES.items():
        label(d, (12, y + 4), key, hexa(0xFFC94A))
        label(d, (12, y + 20), title, DIMC)
        for i, name in enumerate(ORDER):
            row, col = divmod(i, 6)
            x = 110 + col * cw
            yy = y + row * (20 * scale + 34)
            tile = fn(name).resize((20 * scale, 20 * scale), Image.NEAREST)
            img.alpha_composite(tile, (x, yy))
            label(d, (x, yy + 20 * scale + 4), name.replace("_", " ").title(), TEXT)
        y += 3 * (20 * scale + 34)
    img.save(os.path.join(out, "icons.png"))


def sheet_icons_actual(out):
    """Every style at the size it shows in game (GUI scale 2 = 2x) on a game-like backdrop."""
    img = Image.new("RGBA", (40 + 18 * 44, 40 + len(ICON_STYLES) * 56), hexa(0x6DB34F))
    d = ImageDraw.Draw(img)
    for key_i, (key, (_, fn)) in enumerate(ICON_STYLES.items()):
        y = 20 + key_i * 56
        d.text((8, y + 14), key, fill=OUTLINE)
        for i, name in enumerate(ORDER):
            img.alpha_composite(fn(name).resize((40, 40), Image.NEAREST), (28 + i * 44, y))
    img.save(os.path.join(out, "icons-in-game-size.png"))


def bridge_scene(tex, scale=4):
    """Side view: two grass ledges with a silk bridge, and a silk ladder up a stone wall."""
    t = 16
    cols, rows = 14, 8
    scene = Image.new("RGBA", (cols * t, rows * t), hexa(0x8FD3F5))
    grass = Image.new("RGBA", (t, t), hexa(0x8B5A2B))
    gd = ImageDraw.Draw(grass)
    gd.rectangle([0, 0, 15, 3], fill=hexa(0x5DB84A))
    gd.line([0, 4, 15, 4], fill=hexa(0x3E8E3A))
    dirt = Image.new("RGBA", (t, t), hexa(0x8B5A2B))
    stone = Image.new("RGBA", (t, t), hexa(0x8C8C94))
    sd = ImageDraw.Draw(stone)
    sd.line([0, 7, 15, 7], fill=hexa(0x6E6E78))
    sd.line([7, 0, 7, 7], fill=hexa(0x6E6E78))
    sd.line([3, 8, 3, 15], fill=hexa(0x6E6E78))
    for c in range(cols):
        for r in range(rows):
            tile = None
            if c < 4 and r >= 5:
                tile = grass if r == 5 else dirt
            elif 4 <= c < 9 and r == 5:
                tile = tex
            elif 9 <= c < 11 and r >= 5:
                tile = grass if r == 5 else dirt
            elif c >= 12 and r >= 1:
                tile = stone
            elif c == 11 and 1 <= r <= 4:
                tile = tex
            elif c == 11 and r >= 5:
                tile = grass if r == 5 else dirt
            if tile is not None:
                scene.alpha_composite(tile, (c * t, r * t))
    return scene.resize((scene.width * scale // 2, scene.height * scale // 2), Image.NEAREST)


def sheet_silk(out):
    keys = list(SILK_STYLES)
    tw = 3 * 16 * 4
    scene_w = 14 * 16 * 2
    img = Image.new("RGBA", (40 + tw + 30 + scene_w, 40 + len(keys) * 300), BG)
    d = ImageDraw.Draw(img)
    label(d, (12, 10), "STRING SHOT SILK - the one new block. Tiled 3x3 at 4x, and as a bridge + ladder (2x).")
    y = 40
    for key in keys:
        title, fn = SILK_STYLES[key]
        tex = fn()
        tiled = Image.new("RGBA", (48, 48), PANEL)
        for i in range(3):
            for j in range(3):
                tiled.alpha_composite(tex, (i * 16, j * 16))
        img.alpha_composite(tiled.resize((tw, tw), Image.NEAREST), (40, y))
        label(d, (12, y), key, hexa(0xFFC94A))
        label(d, (40, y + tw + 4), title, DIMC)
        img.alpha_composite(bridge_scene(tex), (40 + tw + 30, y))
        y += max(tw, 8 * 16 * 2) + 24
    img = img.crop((0, 0, img.width, y))
    img.save(os.path.join(out, "silk.png"))


def sheet_particles(out):
    scale = 8
    keys = list(PARTICLE_STYLES)
    cw = 4 * 8 * scale + 30
    img = Image.new("RGBA", (150 + 2 * cw, 60 + len(PARTICLES) * (8 * scale + 20)), BG)
    d = ImageDraw.Draw(img)
    label(d, (12, 10), "FIELD MOVE PARTICLES - 4 frames each at 8x. Option A = vanilla particles (no new art).")
    for k, key in enumerate(keys):
        label(d, (150 + k * cw, 34), key + ": " + PARTICLE_STYLES[key][0], hexa(0xFFC94A))
    for i, kind in enumerate(PARTICLES):
        y = 56 + i * (8 * scale + 20)
        label(d, (12, y + 10), kind.replace("_", " ").title(), TEXT)
        label(d, (12, y + 26), "A: " + VANILLA[kind], DIMC)
        for k, key in enumerate(keys):
            fn = PARTICLE_STYLES[key][1]
            for f in range(4):
                tile = Image.new("RGBA", (8, 8), PANEL)
                tile.alpha_composite(fn(kind, f))
                img.alpha_composite(tile.resize((8 * scale, 8 * scale), Image.NEAREST),
                                    (150 + k * cw + f * (8 * scale + 2), y))
    img.save(os.path.join(out, "particles.png"))


def sheet_wheel(out, style="D"):
    """Two layouts for the sneak + right-click picker, using one icon style."""
    fn = ICON_STYLES[style][1]
    W, H = 427, 240
    shots = []
    for layout in ("A", "B"):
        scr = Image.new("RGBA", (W, H), hexa(0x6DB34F))
        sd = ImageDraw.Draw(scr)
        sd.rectangle([0, 0, W, 120], fill=hexa(0x8FD3F5))
        sd.rectangle([0, 120, W, H], fill=hexa(0x5DB84A))
        sd.rectangle([W // 2 - 91, H - 22, W // 2 + 91, H - 2], fill=hexa(0x000000, 140))
        cx, cy = W // 2, H // 2
        sd.line([cx - 4, cy, cx + 4, cy], fill=hexa(0xFFFFFF))
        sd.line([cx, cy - 4, cx, cy + 4], fill=hexa(0xFFFFFF))
        usable = ["flash", "tailwind", "spook", "charm", "magnet", "surf"]
        if layout == "A":
            import math
            for i, name in enumerate(usable):
                a = -math.pi / 2 + i * 2 * math.pi / len(usable)
                x, y = int(cx + 46 * math.cos(a)) - 10, int(cy + 46 * math.sin(a)) - 10
                if i == 0:
                    sd.rounded_rectangle([x - 2, y - 2, x + 21, y + 21], radius=4, outline=hexa(0xFFC94A))
                scr.alpha_composite(fn(name), (x, y))
            sd.text((cx - 14, cy + 66), "Flash", fill=hexa(0xFFFFFF))
            sd.text((cx - 52, cy + 78), "Partner: 3 energy", fill=hexa(0xA8F0FF))
        else:
            x0 = cx - len(usable) * 12
            sd.rounded_rectangle([x0 - 4, H - 58, x0 + len(usable) * 24, H - 30], radius=3, fill=hexa(0x101820, 220))
            for i, name in enumerate(usable):
                x = x0 + i * 24
                if i == 0:
                    sd.rounded_rectangle([x - 2, H - 56, x + 21, H - 33], radius=4, outline=hexa(0xFFC94A))
                scr.alpha_composite(fn(name), (x, H - 54))
            sd.text((x0, H - 72), "Flash - scroll to pick", fill=hexa(0xFFFFFF))
        shots.append(scr.resize((W * 2, H * 2), Image.NEAREST))
    img = Image.new("RGBA", (W * 4 + 60, H * 2 + 60), BG)
    d = ImageDraw.Draw(img)
    label(d, (20, 12), "MOVE PICKER - A: wheel around the crosshair (move mouse)   B: strip above the hotbar (scroll)")
    img.alpha_composite(shots[0], (20, 40))
    img.alpha_composite(shots[1], (40 + W * 2, 40))
    img.save(os.path.join(out, "picker.png"))


def previews(out):
    os.makedirs(out, exist_ok=True)
    sheet_icons(out)
    sheet_icons_actual(out)
    sheet_silk(out)
    sheet_particles(out)
    sheet_wheel(out)


# Dylan's picks (letters from the option sheets).
CHOSEN_ICONS = "D"
CHOSEN_SILK = "B"
TEXTURES = "src/main/resources/assets/cobblemonranger/textures"


def build():
    """Icon atlas (6 x 3 cells of 20x20, FieldMove order) and the two silk textures."""
    fn = ICON_STYLES[CHOSEN_ICONS][1]
    atlas = Image.new("RGBA", (120, 60), (0, 0, 0, 0))
    for i, name in enumerate(ORDER):
        atlas.alpha_composite(fn(name), ((i % 6) * 20, (i // 6) * 20))
    os.makedirs(TEXTURES + "/gui", exist_ok=True)
    atlas.save(TEXTURES + "/gui/field_moves.png")
    os.makedirs(TEXTURES + "/block", exist_ok=True)
    SILK_STYLES[CHOSEN_SILK][1]().save(TEXTURES + "/block/silk.png")
    ladder = silk_d() if CHOSEN_SILK in ("B", "D") else SILK_STYLES[CHOSEN_SILK][1]()
    ladder.save(TEXTURES + "/block/silk_ladder.png")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "previews":
        previews(sys.argv[2])
    else:
        build()
