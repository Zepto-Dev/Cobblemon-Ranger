"""Sprites for the legendary bosses' signature attacks, in the style of tools/attack_textures.py.

Writes textures/gui/capture/signatures.png, one row of 16 px cells:
  0  Mist Ball orb (Psychic pinks)
  1  burst at a beam's mouth, drawn in greys and tinted with the beam's colour at runtime

Run from the repository root: python3 tools/signature_textures.py
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import attack_textures as at  # noqa: E402

OUT = "src/main/resources/assets/cobblemonranger/textures/gui/capture/"
GREYS = [at.rgb(v) for v in (0x3A3A44, 0x9A9AA4, 0xC8C8D0, 0xE8E8EE, 0xFFFFFF)]


def cell(fn, pal, shade=None, glossy=True):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    at.Mask(16, fn).draw(img, 0, 0, pal, shade=shade, glossy=glossy)
    return img


def main():
    sheet = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    mist = cell(at.orb(5.5), at.palette("psychic"), shade=at.radial([(4, 1.5), (3, 3), (2, 4.5)], 6.5, 6.5))
    burst = cell(at.star(8, 5.0, 7.8), GREYS, shade=at.radial([(4, 2), (3, 4), (2, 6)]), glossy=False)
    for i, img in enumerate((mist, burst)):
        sheet.paste(img, (i * 16, 0), img)
    sheet.save(os.path.join(OUT, "signatures.png"))


if __name__ == "__main__":
    main()
