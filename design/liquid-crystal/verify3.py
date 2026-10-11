"""Layout validation: is every label sitting on glass, and is anything colliding?"""
import os, sys
os.environ["AERIX_RECORD"] = "1"
sys.path.insert(0, '/home/user/design/liquid-crystal')
import kit as K, screens as SC

def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    if ix <= 0 or iy <= 0:
        return 0.0
    inter = ix * iy
    area = (a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - inter
    return inter / max(area, 1e-6)

def inside(inner, outer, tol=8.0):
    return (inner[0] >= outer[0]-tol and inner[1] >= outer[1]-tol and
            inner[2] <= outer[2]+tol and inner[3] <= outer[3]+tol)

for name, fn, shift in SC.SCREENS:
    K.REC.clear(); K.WARN.clear()
    im = K.new_screen(seed=3, shift=shift)
    K.status_bar(im); fn(im)
    panels = [r[1] for r in K.REC if r[0] == "glass"]
    texts = [r for r in K.REC if r[0] == "text"]
    icons = [r for r in K.REC if r[0] == "icon"]
    stray, hits, small = [], [], []
    for kind, box, s, size, weight in texts:
        if box[3] < 300 or box[1] > 2218 or size >= 40 or (s.isupper() and size <= 20):
            continue
        if not any(inside(box, p) for p in panels):
            stray.append((s[:34], [round(v) for v in box]))
    for i in range(len(texts)):
        for j in range(i + 1, len(texts)):
            a, b = texts[i], texts[j]
            if a[1][3] < 300 or b[1][3] < 300:
                continue
            v = iou(a[1], b[1])
            if v > 0.30:
                hits.append((round(v, 2), a[2][:22], b[2][:22]))
    for kind, box, s, size, w in icons:
        if box[3] < 300 or box[1] > 2218:
            continue
        if not any(inside(box, p, 10) for p in panels):
            small.append((s, [round(v) for v in box]))
    print(f"{name:14s} panels={len(panels):3d} texts={len(texts):3d} icons={len(icons):3d} "
          f"stray={len(stray)} collide={len(hits)} icon-stray={len(small)}")
    for x in stray[:6]:
        print("     stray text:", x)
    for x in hits[:6]:
        print("     overlap:", x)
    for x in small[:6]:
        print("     stray icon:", x)
