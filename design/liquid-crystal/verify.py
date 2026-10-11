"""Structural checks on the rendered mockups (no eyeballs required)."""
import glob, os
import numpy as np
from PIL import Image

def lum(a):
    return (0.2126*a[...,0] + 0.7152*a[...,1] + 0.0722*a[...,2])

for p in sorted(glob.glob('/home/user/mockups/[01]*.png')):
    im = Image.open(p).convert('RGB')
    a = np.asarray(im).astype(np.float32)
    L = lum(a)
    h, w = L.shape
    # regions
    status = L[0:96, :]
    header = L[120:280, 40:1040]
    content = L[300:2196, 40:1040]
    strip_above_dock = L[2130:2170, 40:1040]
    dock = L[2230:2360, 40:1040]
    edge = np.abs(np.diff(L, axis=1)).mean()
    bright = (L > 225).mean() * 100
    dark = (L < 26).mean() * 100
    # ink: bright pixels in the content region (text / icons)
    ink_content = (content > 200).mean() * 100
    print(f"{os.path.basename(p):16s} mean={L.mean():5.1f} std={L.std():5.1f} "
          f"edge={edge:5.2f} bright%={bright:5.2f} dark%={dark:5.2f} "
          f"ink%={ink_content:5.2f} dockL={dock.mean():5.1f} "
          f"aboveL={strip_above_dock.mean():5.1f} headerInk={(header>200).mean()*100:4.1f}")
