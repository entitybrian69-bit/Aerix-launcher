#!/usr/bin/env python3
"""The refresh-rate display query has to survive a Context that has no display.

`DisplayRefreshMode` answers three questions about the panel - is a faster mode available, what is the
display serving now, what is its ceiling. It used to answer the first of those by reading
`Context.getDisplay()` directly on API 30+, and Android does not return null from that getter for a
non-visual Context: it throws `UnsupportedOperationException`. The one place that question is asked is
`LauncherPreferences.loadPreferences`, which runs from `PojavApplication.onCreate` with the *Application*
context - so on Android 11 and newer the launcher died in application creation, on every start, before a
window existed to show anything. Reported on Android 13 against both 2.1.0-noruntime and 2.3.0.

These checks are structural on purpose: there is no Android in this sandbox, and the bug was not a wrong
answer but an unguarded platform call, so what has to be pinned is that the call is guarded, that a
background context still gets a real display through `DisplayManager`, and that the preference loader
treats the whole question as optional.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DISPLAY = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/DisplayRefreshMode.kt"
PREFS = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java"

display_src = DISPLAY.read_text()
prefs_src = PREFS.read_text()


failures: list[str] = []


def run_checks() -> list[str]:
    def block_of(source: str, signature: str) -> str:
        """The body of the function whose declaration contains `signature`, brace-matched."""
        if signature not in source:
            raise AssertionError(f"{signature!r} is not in the source at all - the test is chasing a symbol "
                                f"that no longer exists")
        start = source.index(signature)
        open_brace = source.index("{", start)
        depth = 0
        for index in range(open_brace, len(source)):
            if source[index] == "{":
                depth += 1
            elif source[index] == "}":
                depth -= 1
                if depth == 0:
                    return source[open_brace:index + 1]
        raise AssertionError(f"unbalanced body for {signature!r}")


    def check(condition: bool, message: str) -> None:
        if not condition:
            failures.append(message)


    display_of = block_of(display_src, "private fun displayOf(")

    # 1. `context.display` may only be read where a throw is caught.
    for match in re.finditer(r"^\s*(?!//).*?\.display\b", display_of, re.MULTILINE):
        line = match.group(0)
        if "context.display" not in line and "fromContext" not in line:
            continue
        check("runCatching" in line, f"DisplayRefreshMode: `{line.strip()}` reads getDisplay() outside runCatching")
    check("runCatching { context.display }" in display_of,
          "DisplayRefreshMode.displayOf must read `context.display` inside runCatching - Android throws "
          "UnsupportedOperationException for a Context that is not associated with a display")

    # 2. a background Context still gets an answer, so the default stays meaningful instead of always off.
    check("DisplayManager" in display_of and "Display.DEFAULT_DISPLAY" in display_of,
          "DisplayRefreshMode.displayOf must fall back to DisplayManager.getDisplay(Display.DEFAULT_DISPLAY), "
          "which a non-visual Context is allowed to ask")
    check("import android.hardware.display.DisplayManager" in display_src,
          "DisplayRefreshMode is missing the DisplayManager import")

    # 3. no Android call on a Display anywhere in the file may be unguarded: an OEM ROM that will not bind
    # these members, or a display that vanished between frames, must not become a crash in the game overlay.
    for index, line in enumerate(display_src.splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith(("*", "//", "/*")) or "runCatching" in line:
            continue
        read = re.search(r"\bdisplay\.(mode|refreshRate|supportedModes|getRealSize)\b", line)
        if read:
            check(False, f"DisplayRefreshMode.kt:{index} reads display.{read.group(1)} outside runCatching")

    # 4. the preference loader never lets the question take the process down.
    check("maxRefreshDefault(ctx)" in prefs_src,
          "LauncherPreferences.loadPreferences must ask through maxRefreshDefault(ctx), not DisplayRefreshMode "
          "directly - a display query is optional data at Application.onCreate, not a precondition of starting")
    check("DisplayRefreshMode.isAvailable(ctx));" not in prefs_src.split("maxRefreshDefault(Context ctx)")[0],
          "LauncherPreferences.loadPreferences still calls DisplayRefreshMode.isAvailable(ctx) bare")
    guard = block_of(prefs_src, "private static boolean maxRefreshDefault(")
    check("try {" in guard and "catch (Throwable" in guard,
          "maxRefreshDefault must catch Throwable around the display query")
    check("return false;" in guard,
          "maxRefreshDefault must fall back to off (false), never on, when the display cannot be read")

    # 5. nothing else in the loader reads a display straight off the Application context.
    if "static void loadPreferences(" not in prefs_src or "private static int clampPercent" not in prefs_src:
        raise AssertionError("LauncherPreferences no longer has the loadPreferences/clampPercent pair "
                             "this test measures between")
    load_prefs = prefs_src[prefs_src.index("static void loadPreferences("):prefs_src.index("private static int clampPercent")]
    check(".getDisplay(" not in load_prefs and ".display" not in load_prefs,
          "LauncherPreferences.loadPreferences must not read a display itself - that Context has none")

    # 6. the game surface reads its rate from a display it does not own either.
    surface = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/GameView.java").read_text()
    check("windowRate = mSurface.getDisplay().getRefreshRate();" not in surface,
          "GameView reads a refresh rate straight off Surface.getDisplay(), which may be null while a "
          "window is being torn down or re-attached")
    check("surfaceDisplay != null" in surface,
          "GameView must ask the surface's display for a rate only when it answered")

    return failures


failures = []
try:
    failures = run_checks()
except AssertionError as problem:  # a symbol this test measures is gone
    failures.append(str(problem))  # report it, keep the rest


if failures:
    for failure in failures:
        print(f"  FAIL {failure}")
    sys.exit(1)

print("background display: getDisplay guarded, DisplayManager fallback in place, preference loader "
      "treats the question as optional")
