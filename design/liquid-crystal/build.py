"""Render the ten Aerix 'Liquid Crystal' mockups."""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import kit as K  # noqa: E402
import screens as SC  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "/home/user/mockups"
os.makedirs(OUT, exist_ok=True)


def render(name, fn, shift, seed):
    t0 = time.time()
    K.WARN.clear()
    im = K.new_screen(seed=seed, shift=shift)
    K.status_bar(im)
    fn(im)
    path = os.path.join(OUT, f"{name}.png")
    K.finish(im, path)
    warns = list(dict.fromkeys(K.WARN))
    print(f"{name:16s} {time.time() - t0:5.1f}s  {os.path.getsize(path)/1e6:5.2f} MB"
          f"  warnings: {len(warns)}")
    for w in warns[:14]:
        print("    !", w)
    return path


if __name__ == "__main__":
    paths = []
    for i, (name, fn, shift) in enumerate(SC.SCREENS):
        paths.append(render(name, fn, shift, seed=7 + i * 13))
    print("\nrendered", len(paths), "->", OUT)
