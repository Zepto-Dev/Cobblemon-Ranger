"""Draws the 400x400 CurseForge project logo: the red styler on a green backdrop with a soft glow and sparkles.

Run from the repository root: python3 tools/logo.py
"""
import math

from PIL import Image, ImageDraw, ImageFilter

from item_textures import styler

OUT = "curseforge/logo.png"
RED = 0xB02E26  # the red dye's item tint
SPARKLE, WHITE = (168, 240, 255, 255), (255, 255, 255, 255)


def background():
    """Green radial gradient, lighter in the middle, with a pale glow behind the styler."""
    img = Image.new("RGBA", (400, 400))
    px = img.load()
    for y in range(400):
        for x in range(400):
            t = min(1, math.hypot(x - 200, y - 200) / 283)
            px[x, y] = (int(92 - 45 * t), int(172 - 46 * t), int(70 - 20 * t), 255)
    glow = Image.new("RGBA", (400, 400), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([50, 50, 350, 350], fill=(168, 240, 255, 120))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(28)))
    return img


def styler_sprite():
    """The item as it looks dyed red: tinted shell under the fixed-colour layer, cropped to a square."""
    shell, top = styler()
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    r, g, b = (RED >> 16) & 255, (RED >> 8) & 255, RED & 255
    for (x, y), c in shell.items():
        img.putpixel((x, y), (c[0] * r // 255, c[1] * g // 255, c[2] * b // 255, 255))
    for (x, y), c in top.items():
        img.putpixel((x, y), c + (255,))
    img = img.crop(img.getbbox())
    side = max(img.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return square


def sparkles(img, g, spots):
    """Plus-shaped sparkles made of g-sized pixels, white in the middle."""
    d = ImageDraw.Draw(img)
    h = g // 2
    for x, y, n in spots:
        d.rectangle([x - n * g - h, y - h, x + n * g + g - h - 1, y + g - h - 1], fill=SPARKLE)
        d.rectangle([x - h, y - n * g - h, x + g - h - 1, y + n * g + g - h - 1], fill=SPARKLE)
        d.rectangle([x - h, y - h, x + g - h - 1, y + g - h - 1], fill=WHITE)


def logo():
    img = background()
    sprite = styler_sprite()
    k = 250 // sprite.width
    big = sprite.resize((sprite.width * k, sprite.height * k), Image.NEAREST)
    ox, oy = (400 - big.width) // 2, (400 - big.height) // 2
    shadow = Image.new("RGBA", big.size, (0, 0, 0, 0))
    shadow.paste((20, 60, 24, 130), mask=big.getchannel("A"))
    img.alpha_composite(shadow, (ox + k // 2, oy + k // 2 + 3))
    img.alpha_composite(big, (ox, oy))
    sparkles(img, 17, ((70, 80, 3), (330, 110, 2), (320, 320, 3), (84, 320, 2)))
    return img.convert("RGB")


if __name__ == "__main__":
    logo().save(OUT)
