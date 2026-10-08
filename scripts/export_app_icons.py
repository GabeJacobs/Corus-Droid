#!/usr/bin/env python3
"""Export Blue from Default's exact silhouette, size and placement.

Requires Pillow. Android selects the adaptive launcher mask; the transparent
108dp foregrounds have no baked corners or shadows. Default's immutable
originals ensure its accepted placement correction never accumulates.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
OUT = ROOT / 'design/app-icons'
BLUE = '#5E91F0'  # Solid counterpart of the iOS Corus Blue background.
OUT.mkdir(parents=True, exist_ok=True)
(RES / 'values/ic_launcher_blue_background.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<resources><color name="ic_launcher_blue_background">{BLUE}</color></resources>
''')
for name in ['ic_launcher_blue', 'ic_launcher_blue_round']:
    (RES / f'mipmap-anydpi-v26/{name}.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_blue_background" />
    <foreground android:drawable="@mipmap/ic_launcher_blue_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
''')
preview=RES/'drawable-nodpi';preview.mkdir(exist_ok=True)
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
default_preview=default.crop((72,72,360,360)).resize((320,320),Image.Resampling.LANCZOS)
default_preview.save(preview/'app_icon_default_preview.png')

def white_foreground(default):
    # Preserve every antialiased edge from the black-on-white original.
    foreground = Image.new('RGBA', default.size, 'white')
    foreground.putalpha(ImageOps.invert(default.convert('L')))
    return foreground

def white_on_blue(default):
    canvas = Image.new('RGBA', default.size, BLUE)
    canvas.alpha_composite(white_foreground(default))
    return canvas

for density in ['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi']:
    folder=RES/f'mipmap-{density}'
    adaptive=Image.open(folder/'ic_launcher_foreground.png').convert('RGBA')
    white_foreground(adaptive).save(folder/'ic_launcher_blue_foreground.png')
    legacy=Image.open(folder/'ic_launcher.png').convert('RGBA')
    square=white_on_blue(legacy)
    square.convert('RGB').save(folder/'ic_launcher_blue.png')
    size=square.width
    mask=Image.new('L',(size*4,size*4))
    ImageDraw.Draw(mask).ellipse((0,0,size*4-1,size*4-1),fill=255)
    square.putalpha(mask.resize((size,size),Image.Resampling.LANCZOS))
    square.save(folder/'ic_launcher_blue_round.png')

white_on_blue(default_preview).convert('RGB').save(preview/'app_icon_blue_preview.png')
white_foreground(default).save(OUT/'corus-blue-foreground.png')
store=default.crop((72,72,360,360)).resize((512,512),Image.Resampling.LANCZOS)
white_on_blue(store).save(OUT/'corus-blue-play-store-512.png')
# Remove the superseded vector mark so it cannot be used accidentally.
for obsolete in [OUT/'corus-blue-foreground.svg',
                 RES/'drawable/ic_launcher_blue_foreground.xml',
                 RES/'drawable/ic_launcher_blue_monochrome.xml']:
    obsolete.unlink(missing_ok=True)
print('Exported matching adaptive/themed layers, five square/round PNG densities, previews and optional 512px store artwork.')
