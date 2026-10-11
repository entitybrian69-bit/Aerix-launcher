"""Targeted probes: are the bespoke widgets actually drawn where intended?"""
import numpy as np
from PIL import Image

def load(n):
    return np.asarray(Image.open(f'/home/user/mockups/{n}.png').convert('RGB')).astype(np.float32)

def region(a, cx, cy, r=6):
    return a[int(cy-r):int(cy+r), int(cx-r):int(cx+r)]

def stat(a, box):
    x0,y0,x1,y1 = [int(v) for v in box]
    s = a[y0:y1, x0:x1]
    return s.mean(), s.std(), s.max()

def ink(a, box, thr=200):
    x0,y0,x1,y1 = [int(v) for v in box]
    s = a[y0:y1, x0:x1]
    L = 0.2126*s[...,0]+0.7152*s[...,1]+0.0722*s[...,2]
    return (L > thr).mean()*100

a = load('01-launch')
print('--- 01 launch ---')
print('orb core       ', ['%.0f'%v for v in stat(a,(440,650,640,850))])
print('orb centre px  ', a[750, 540], ' ring px', a[650, 540])
print('launch label   ', 'ink %.1f%%' % ink(a,(430,890,650,930)))
print('hero title ink ', '%.1f%%' % ink(a,(190,360,700,410)))
print('dock active    ', ['%.0f'%v for v in stat(a,(34,2240,118,2348))])
print('dock inactive  ', ['%.0f'%v for v in stat(a,(548,2240,632,2348))])
print('header title   ', '%.1f%%' % ink(a,(58,150,760,200)))
print('quick action   ', '%.1f%%' % ink(a,(60,1120,380,1180)))

b = load('04-controls')
print('--- 04 controls ---')
print('stage scene    ', ['%.0f'%v for v in stat(b,(100,340,900,860))])
print('joystick       ', ['%.0f'%v for v in stat(b,(150,820,270,940))])
print('inspector row  ', '%.1f%%' % ink(b,(150,1090,700,1140)))
print('toolbar        ', ['%.0f'%v for v in stat(b,(60,1010,1020,1100))])

c = load('06-video')
print('--- 06 video ---')
print('renderer grid  ', ['%.0f'%v for v in stat(c,(60,350,1020,760))])
print('active tile    ', ['%.0f'%v for v in stat(c,(560,352,1020,432))])
print('idle tile      ', ['%.0f'%v for v in stat(c,(560,444,1020,524))])

d = load('07-input')
print('--- 07 input ---')
print('toggle ON      ', ['%.0f'%v for v in stat(d,(940,1310,1030,1400))])
print('last tile      ', ['%.0f'%v for v in stat(d,(40,2072,1040,2160))])
