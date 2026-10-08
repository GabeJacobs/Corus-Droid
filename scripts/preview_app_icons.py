#!/usr/bin/env python3
"""Build review sheets from the actual exported Android launcher assets."""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'app/src/main/res'
OUT = ROOT/'design/app-icons'
FONT = RES/'font/nunito.ttf'
BG = '#101114'
def font(size, weight=500):
    f = ImageFont.truetype(str(FONT), size)
    f.set_variation_by_axes([weight])
    return f
TITLE, TEXT, SMALL = font(34,800), font(23,600), font(20)
def label(draw, x, y, text, f=TEXT, fill='#DEE1E9', centered=False):
    if centered: x -= draw.textbbox((0,0),text,font=f)[2]/2
    draw.text((x,y),text,font=f,fill=fill)
def mask(shape, size):
    m=Image.new('L',(size*4,size*4));d=ImageDraw.Draw(m);edge=size*4-1
    if shape in ['Circle','Themed light','Themed dark']:
        d.ellipse((0,0,edge,edge),fill=255)
    elif shape=='Square': d.rectangle((0,0,edge,edge),fill=255)
    elif shape=='Rounded square': d.rounded_rectangle((0,0,edge,edge),radius=size*.8,fill=255)
    elif shape=='Teardrop':
        d.rounded_rectangle((0,0,edge,edge),radius=size*2,fill=255)
        d.rectangle((size*2,0,edge,size*2),fill=255)
    else:
        r=edge/2;points=[]
        for i in range(1000):
            a=i*math.tau/1000;c,s=math.cos(a),math.sin(a)
            points.append((r+r*math.copysign(abs(c)**.5,c),r+r*math.copysign(abs(s)**.5,s)))
        d.polygon(points,fill=255)
    return m.resize((size,size),Image.Resampling.LANCZOS)

blue_fg=Image.open(RES/'mipmap-xxxhdpi/ic_launcher_blue_foreground.png').convert('RGBA')
default_mono=Image.open(RES/'mipmap-xxxhdpi/ic_launcher_monochrome.png').crop((72,72,360,360)).getchannel('A')
blue_mono=default_mono
previews=[Image.open(RES/'drawable-nodpi/app_icon_blue_preview.png').convert('RGBA'),Image.open(RES/'drawable-nodpi/app_icon_default_preview.png').convert('RGBA')]
shapes=['Circle','Squircle','Rounded square','Square','Teardrop','Themed light','Themed dark']
sheet=Image.new('RGB',(1420,640),BG);d=ImageDraw.Draw(sheet)
label(d,40,24,'Android app icons',TITLE,fill='white')
label(d,40,75,'Matching silhouettes and sizes · approved horizontal placement preserved',SMALL,fill='#9EA5B3')
for i,shape in enumerate(shapes):label(d,140+i*190,132,shape,SMALL,centered=True)
for row,(name,preview,mono) in enumerate(zip(['Corus Blue','Default'],previews,[blue_mono,default_mono])):
    y=190+row*228
    for col,shape in enumerate(shapes):
        x=72+col*190;size=136
        tile=preview.resize((size,size),Image.Resampling.LANCZOS)
        if shape.startswith('Themed'):
            colors=('#D7E3FF','#18335D') if shape=='Themed light' else ('#18335D','#D7E3FF')
            tile=Image.new('RGBA',(size,size),colors[0]);mark=Image.new('RGBA',(size,size),colors[1])
            mark.putalpha(mono.resize((size,size),Image.Resampling.LANCZOS));tile.alpha_composite(mark)
        tile.putalpha(mask(shape,size));sheet.paste(tile,(x,y),tile)
    label(d,40,y+161,name,TEXT,fill='white')
sheet.save(OUT/'android-icon-formats.png')

# The second sheet shows the actual technical exports, including PNGs at 1:1.
sheet=Image.new('RGB',(1420,1060),BG);d=ImageDraw.Draw(sheet)
label(d,40,24,'Corus Blue · Android asset formats',TITLE,fill='white')
label(d,40,77,'Adaptive layers use the same 108dp canvas as Default. PNG samples below are shown at 1:1.',SMALL,fill='#9EA5B3')
label(d,40,132,'Adaptive layers',TEXT,fill='white')
for i,name in enumerate(['Foreground · transparent','Background · opaque','Monochrome · transparent']):
    x=90+i*360;y=182;size=150
    tile=Image.new('RGBA',(size,size))
    td=ImageDraw.Draw(tile)
    for yy in range(0,size,10):
        for xx in range(0,size,10):td.rectangle((xx,yy,xx+9,yy+9),fill='#292D35' if (xx//10+yy//10)%2 else '#22262D')
    if i==1:tile=Image.new('RGBA',(size,size),'#5E91F0')
    else:
        layer=blue_fg if i==0 else Image.open(RES/'mipmap-xxxhdpi/ic_launcher_monochrome.png').convert('RGBA')
        tile.alpha_composite(layer.resize((size,size),Image.Resampling.LANCZOS))
    sheet.paste(tile,(x,y));label(d,x,y+163,name,SMALL)
label(d,40,400,'Legacy square PNGs',TEXT,fill='white')
label(d,40,710,'Legacy circular PNGs',TEXT,fill='white')
for i,(density,size) in enumerate([('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]):
    cx=155+i*275
    for row,name in enumerate(['ic_launcher_blue','ic_launcher_blue_round']):
        cy=535+row*330
        art=Image.open(RES/f'mipmap-{density}/{name}.png').convert('RGBA')
        assert art.size==(size,size)
        sheet.paste(art,(int(cx-size/2),int(cy-size/2)),art)
        label(d,cx,cy+110,f'{density} · {size} × {size}px',SMALL,centered=True)
label(d,40,1026,'Also included: transparent foreground PNG, 320px picker preview and optional 512px store artwork.',SMALL,fill='#9EA5B3')
sheet.save(OUT/'android-icon-layers-and-densities.png')
print('Created final launcher-mask and layer/density review sheets.')
