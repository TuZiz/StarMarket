"""Subset the actual Minecraft 26.2 Unifont without modifying any glyph strokes."""
from pathlib import Path
import hashlib
import zipfile
from PIL import Image


def export(characters: str, assets: Path, filename: str = 'item_names', proportional: bool = False) -> Image.Image:
    source = Path('C:/Users/32633/Desktop/leaf/cache/font-pack-minecraft-26.2-unifont.zip')
    # Verified against Mojang's asset index for release 26.2 (not inferred from 26.1).
    assert hashlib.sha1(source.read_bytes()).hexdigest() == 'ccd5ac4767ce0a9c71d1dd62f2dc25449789b5dd'
    wanted = {ord(c) for c in characters}
    glyphs = {}
    with zipfile.ZipFile(source) as archive:
        for entry in archive.namelist():
            if not entry.endswith('.hex'):
                continue
            for line in archive.read(entry).decode().splitlines():
                code, _, bits = line.partition(':')
                if int(code, 16) not in wanted:
                    continue
                width = len(bits) // 4
                glyph = Image.new('RGBA', (16, 16))
                for y in range(16):
                    row = int(bits[y * width // 4:(y + 1) * width // 4], 16)
                    for x in range(width):
                        if row & (1 << (width - x - 1)):
                            glyph.putpixel((x + (0 if proportional else (16 - width) // 2), y), (255, 255, 255, 255))
                glyphs[int(code, 16)] = (glyph, width)
        license_path = assets.parents[1] / 'licenses/Unifont.txt'
        license_path.parent.mkdir(parents=True, exist_ok=True)
        license_path.write_bytes(archive.read('LICENSE.txt'))
    atlas = Image.new('RGBA', (16 * len(characters), 16))
    for index, character in enumerate(characters):
        glyph, width = glyphs[ord(character)]
        atlas.paste(glyph, (index * 16, 0))
        # Vanilla CJK size override is full 16px -> 8 logical px + 1px advance.
        atlas.putpixel((index * 16 + (width - 1 if proportional else 15), 15), (255, 255, 255, 1))
    atlas.save(assets / f'textures/font/{filename}.png')
    return atlas
