#!/usr/bin/env python3
"""Export Corus Blue launcher assets from the existing vector mark.

Requires Pillow and CairoSVG. Run from any directory. The 108dp adaptive canvas
keeps the full mark inside the central 66dp safe zone; previews use its 72dp
launcher viewport. Android selects the launcher mask, so adaptive layers have
no baked corners or shadows.
"""
from pathlib import Path
import io
import os
import sys
import xml.etree.ElementTree as ET
# Apple's system Python strips DYLD variables at launch; restore the usual
# Homebrew library lookup before CairoSVG asks ctypes to locate Cairo.
if sys.platform == 'darwin' and Path('/opt/homebrew/lib/libcairo.dylib').exists():
    os.environ.setdefault('DYLD_FALLBACK_LIBRARY_PATH', '/opt/homebrew/lib')
import cairosvg
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
OUT = ROOT / 'design/app-icons'
BLUE = '#5E91F0'  # Solid counterpart of the iOS Corus Blue background.
NS = '{http://schemas.android.com/apk/res/android}'
PATH = ' '.join(p.attrib[NS + 'pathData'] for p in ET.parse(RES / 'drawable/logo_no_background.xml').findall('.//path'))
SVG = f'''<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 10240 10240">
<g transform="translate(1394 1536) scale(0.7)"><g transform="translate(0 10240) scale(1 -1)"><path fill="white" d="{PATH}"/></g></g></svg>'''
VECTOR = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="10240" android:viewportHeight="10240">
    <group android:scaleX="0.7" android:scaleY="0.7" android:translateX="1394" android:translateY="1536">
        <group android:translateY="10240" android:scaleY="-1">
            <path android:fillColor="#FFFFFF" android:pathData="{PATH}" />
        </group>
    </group>
</vector>
'''
OUT.mkdir(parents=True, exist_ok=True)
(OUT / 'corus-blue-foreground.svg').write_text(SVG + '\n')
(RES / 'drawable/ic_launcher_blue_foreground.xml').write_text(VECTOR)
(RES / 'drawable/ic_launcher_blue_monochrome.xml').write_text(VECTOR)
(RES / 'values/ic_launcher_blue_background.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<resources><color name="ic_launcher_blue_background">{BLUE}</color></resources>
''')
for name in ['ic_launcher_blue', 'ic_launcher_blue_round']:
    (RES / f'mipmap-anydpi-v26/{name}.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_blue_background" />
    <foreground android:drawable="@drawable/ic_launcher_blue_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_blue_monochrome" />
</adaptive-icon>
''')
foreground = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=SVG.encode(), output_width=1296, output_height=1296))).convert('RGBA')
canvas = Image.new('RGBA', foreground.size, BLUE)
canvas.alpha_composite(foreground)
# 108dp canvas, central 72dp visible viewport.
viewport = canvas.crop((216, 216, 1080, 1080))
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder=RES / f'mipmap-{density}'
    square=viewport.resize((size,size),Image.Resampling.LANCZOS)
    square.convert('RGB').save(folder / 'ic_launcher_blue.png')
    mask=Image.new('L',(size*4,size*4));ImageDraw.Draw(mask).ellipse((0,0,size*4-1,size*4-1),fill=255)
    rounded=square.copy();rounded.putalpha(mask.resize((size,size),Image.Resampling.LANCZOS))
    rounded.save(folder / 'ic_launcher_blue_round.png')
preview=RES/'drawable-nodpi';preview.mkdir(exist_ok=True)
viewport.resize((320,320),Image.Resampling.LANCZOS).convert('RGB').save(preview/'app_icon_blue_preview.png')
# Placement correction for the existing Default artwork. Copy original pixels
# horizontally; never scale, redraw, recolor, or alter the mark. Use immutable
# sources so rerunning this exporter cannot apply the shift more than once.
from collections import deque
def hole_center(image):
    width, height = image.size
    pixels = image.convert('RGBA').load()
    mono = pixels[0, 0][3] == 0
    def is_hole(x, y):
        rgba = pixels[x, y]
        return rgba[3] < 128 if mono else min(rgba[:3]) > 180
    row = round(height * .48)
    candidates = [x for x in range(round(width*.48), round(width*.59)) if is_hole(x, row)]
    seed = (candidates[len(candidates)//2], row)
    queue = deque([seed]); visited = {seed}
    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
            if 0 <= nx < width and 0 <= ny < height and (nx,ny) not in visited and is_hole(nx,ny):
                visited.add((nx,ny));queue.append((nx,ny))
    xs = [x for x,y in visited]
    assert len(visited) < width * height * .03, 'Expected the enclosed vinyl hole'
    return (min(xs)+max(xs))/2
for source in sorted((OUT/'source/default').glob('mipmap-*/*.png')):
    original = Image.open(source).convert('RGBA')
    # Optical compromise: 1dp right of exact vinyl-center alignment. The
    # adaptive layers use a 108dp canvas, legacy PNGs the 72dp masked viewport.
    canvas_dp = 108 if 'foreground' in source.name or 'monochrome' in source.name else 72
    offset = round((original.width-1)/2-hole_center(original) + original.width/canvas_dp)
    shifted = Image.new('RGBA', original.size, original.getpixel((0,0)))
    shifted.paste(original, (offset,0))
    shifted.save(RES/source.parent.name/source.name)
default=Image.open(RES/'mipmap-xxxhdpi/ic_launcher_foreground.png').convert('RGBA')
default.crop((72,72,360,360)).resize((320,320),Image.Resampling.LANCZOS).save(preview/'app_icon_default_preview.png')
viewport.resize((512,512),Image.Resampling.LANCZOS).convert('RGBA').save(OUT/'corus-blue-play-store-512.png')
print('Exported vector adaptive/themed layers, five square/round PNG densities, previews, SVG and optional 512px store artwork.')
