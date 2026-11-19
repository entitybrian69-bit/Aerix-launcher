#!/usr/bin/env python3
"""The FPS readout must stay readable, and must be switchable from the in-game A menu.

Two complaints are locked down here:

* The counter sometimes showed "weird text". The messages it can print are now short enough for the
  pill it measures for itself, a hard cap keeps any future message inside that pill, and the text is
  drawn centred instead of riding the border. The long "reason" sentence moved to the content
  description, where screen readers can still reach it but the game screen cannot clip it.
* There was no way to turn the counter on or off from where a player notices it. The A menu grid now
  carries an "FPS Counter" tile bound to the same switch the launch screen and video options own.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"
view = (JAVA / "game/FpsView.java").read_text()
strings = (ROOT / "app_pojavlauncher/src/main/res/values/strings.xml").read_text()
overlay = (JAVA / "aerix/client/AerixClientOverlayView.kt").read_text()
store = (JAVA / "aerix/client/AerixClientStore.kt").read_text()
activity = (JAVA / "game/GameActivity.java").read_text()

problems = []


def check(condition, message):
    if not condition:
        problems.append(message)


# 1. Nothing the pill can print may exceed the width it measures for itself.
values = dict(
    line.split(">", 1) for line in strings.splitlines()
    if line.strip().startswith("<string name=\"fps_overlay_")
)
limits = {"fps_overlay_waiting": 30, "fps_overlay_unavailable": 30,
          "fps_overlay_stalled": 30, "fps_overlay_value": 30,
          "fps_overlay_value_hz": 30, "fps_overlay_unavailable_reason": 200}
for name, limit in limits.items():
    text = next(v for k, v in values.items() if f'"{name}"' in k).split("</string>")[0]
    # A format string counts as its longest plausible filled-in form.
    rendered = (text.replace("%1$d", "1000").replace("%2$d", "1000")
                    .replace("%3$d", "144"))
    if name != "fps_overlay_unavailable_reason":
        check(len(rendered) <= limit, f"{name} is {len(rendered)} characters; keep it under the cap")
check("the game is not presenting frames" not in strings,
      "the long explanatory sentence must not be drawn in the pill")

# 2. The pill enforces a cap and says where the reason went.
check("private static final int MAX_TEXT_CHARS = 30;" in view,
      "FpsView must cap its text, so a bad measurement cannot draw off screen")
check("private String fit(String value)" in view, "the cap must be applied by one helper")
check("value = fit(value);" in view, "updateText must run every message through the cap")
check("offsetByCodePoints" in view,
      "the truncation must not split a surrogate pair, which draws as a box")
check("\\u2026" in view, "truncation must be visible as an ellipsis, not silent")
reason_at = view.index("setContentDescription(getContext().getString(R.string.fps_overlay_unavailable_reason)")
reason_text = view[reason_at:view.index(";", reason_at)]
check("R.string.fps_overlay_unavailable_reason" in reason_text,
      "the full reason must still be reachable, via the content description")
# updateText rewrites the description, so the reason must be set after it or it is lost.
unavailable_at = view.index("updateText(getContext().getString(R.string.fps_overlay_unavailable));")
check(unavailable_at < reason_at,
      "the reason must be set after updateText, which would otherwise overwrite it")
check("updateText(getContext().getString(R.string.fps_overlay_unavailable))" in view,
      "the pill itself must show the short message")

# 3. Drawing must match the measured size: centred, with no left-padding offset.
check("textPaint.setTextAlign(Paint.Align.CENTER);" in view,
      "the text must be centred, not left-aligned inside a pill sized to the text")
check("canvas.drawText(text, getWidth() / 2f," in view,
      "the draw x must be the centre the alignment expects")
check("getPaddingLeft(), -textPaint" not in view,
      "the old left-aligned draw, which rode the border, must be gone")

# 4. The A menu grid carries an FPS Counter tile bound to the real switch.
tile = overlay[overlay.index('id = "fps_counter"'):]
tile = tile[:tile.index("ClientSquareModule(")]
check('title = "FPS overlay"' in tile, "the tile must be called FPS overlay")
check("FPS overlay" in overlay, "the grid must list it")
check("AerixClientStore.fpsOverlayEnabled" in tile,
      "the tile must read the same switch the launcher shows")
check("AerixClientStore.fpsOverlayEnabled = !AerixClientStore.fpsOverlayEnabled" in tile,
      "tapping it must toggle the counter on and off")
check("AerixClientStore.persist()" in tile, "the choice must survive the session")
# Flipping the flag is not the whole job. FpsView is a plain View whose visibility is only decided
# when the activity resumes or the menu closes, so without an explicit refresh the tile read ENABLED
# while the counter stayed gone until the player left the game.
check("refreshFpsViewVisibility()" in tile,
      "the tile must re-evaluate the readout, or the switch does nothing until the next resume")
check("fun gameActivityOf(context: Context?)" in overlay,
      "the grid page only has a Context; it needs the activity behind it to reach the readout")
check('id = "fps_counter"' in overlay and '"fps"' not in tile,
      "the new tile must not be confused with the in-HUD performance pill")

# 5. That switch must be what actually shows or hides the readout.
check("AerixClientStore.INSTANCE.getFpsOverlayEnabled()" in activity,
      "GameActivity must show the readout according to the switch the tile toggles")
check("var fpsOverlayEnabled by mutableStateOf(" in store,
      "the switch must be Compose state, so the tile updates when it flips")
check('putBoolean("fpsOverlayEnabled", fpsOverlayEnabled)' in store,
      "the switch must be saved")

# 5. The pause menu the player is actually looking at must carry the switch too.
#    The A-overlay tile only exists on Aerix Client instances; everyone else opens this menu and
#    found no FPS control in it at all.
screen = (JAVA / "aerix/ui/game/GameMenuScreen.kt").read_text()
menu_view = (JAVA / "aerix/ui/game/GameMenuOverlayView.kt").read_text()
check("fun isFpsCounterOn(): Boolean" in screen and "fun onToggleFpsCounter(enabled: Boolean)" in screen,
      "the pause menu must expose the readout switch through its callbacks")
# Assert on the rendered row, not on a mention in a comment: the mutation that replaced the
# callback with a no-op still passed while these were plain substring checks.
row_at = screen.find("SwitchRow(")
check(row_at >= 0, "the switch must be a row the player can actually tap")
row = screen[row_at:row_at + 700]
check("onToggle = onToggleFpsOverlay" in row,
      "the row must call the toggle it was handed, not nothing")
check("callbacks.onToggleFpsCounter(enabled)" in screen,
      "and that toggle must reach the real activity, not stop at the page")
# This used to read `checked = callbacks.isFpsCounterOn()`, which is an ordinary call on a plain
# object: Compose cannot observe it, so the switch changed the game's readout while it went on
# drawing its old position. A switch that does not move reads as one that does not work.
check("checked = callbacks.isFpsCounterOn()" not in row,
      "the row must not read the setting through a call Compose cannot observe")
check("checked = fpsOverlayOn" in row,
      "the row must draw state, so flipping it visibly moves the switch")
# The row lives in GameMenuMainPage, which is a different composable from the one that declares the
# state: reading it directly there is an unresolved reference and the build fails. It has to be a
# parameter of that function.
page_start = screen.index("private fun GameMenuMainPage(")
page = screen[page_start:]
page_signature = page[:page.index(") {")]
check("fpsOverlayOn: Boolean" in page_signature,
      "GameMenuMainPage must take the value as a parameter, or the row cannot see it")
check("onToggleFpsOverlay: (Boolean) -> Unit" in page_signature,
      "and it must take the way to change it, or the switch cannot move")
check("onToggle = onToggleFpsOverlay" in page,
      "the row must call the parameter it was given")
check("var fpsOverlayOn by remember { mutableStateOf(callbacks.isFpsCounterOn()) }" in screen,
      "that state must start from the real setting, not from a guess")
# The state is updated where it is declared - in GameMenuScreen, by the lambda handed to the page -
# so the row changing it and the setting changing happen as one action, not two that can drift.
check("onToggleFpsOverlay = { enabled ->" in screen and "fpsOverlayOn = enabled" in screen
      and "callbacks.onToggleFpsCounter(enabled)" in screen,
      "flipping the row must update the state and the setting together, or they disagree")
# The A overlay has its own switch for the same readout, so an old value would be a stale answer.
check("LaunchedEffect(revision) { fpsOverlayOn = callbacks.isFpsCounterOn() }" in screen,
      "the value must be re-read whenever the menu opens")
# One setting, one name: the pause menu, the A menu tile and the information page all drive the
# same readout, so three different names for it just made the third look like something else.
check("FPS pill on screen" not in overlay,
      "the same setting must not be offered under a third name")
check(overlay.count('"FPS overlay"') >= 2,
      "the tile and the information page must both call it FPS overlay")
check("label = stringResource(R.string.aerix_ingame_menu_fps_counter)" in row,
      "the row must be labelled from resources")
check("aerix_ingame_menu_fps_counter" in screen,
      "the row must have a label string, not a hardcoded title")
check("override fun isFpsCounterOn()" in menu_view and "override fun onToggleFpsCounter(" in menu_view,
      "the placeholder callbacks must satisfy the interface, or the menu cannot compile")

activity = (JAVA / "game/GameActivity.java").read_text()
check("public boolean isFpsCounterOn()" in activity,
      "the activity is where the per-instance answer lives")
# The row must report the setting the readout follows, not the menu it is drawn in: asking for the
# visibility while the menu is open would always answer "off" and the switch would lie, so the second
# argument is the one that matters: true would subtract "the menu is open" from the
# answer, so the switch would always read off while the player is looking at it.
getter = activity[activity.index("public boolean isFpsCounterOn()"):]
getter = getter[:getter.index("}")]
check("updateFpsViewVisibility(true, false)" in getter,
      "the row must ask for the setting without the menu-open discount")
check("PREF_SHOW_FPS = enabled" in activity and '"show_fps"' in activity,
      "toggling it must save the same preference the video options own")
check("setFpsOverlayEnabled(enabled)" in activity and "AerixClientStore.INSTANCE.persist()" in activity,
      "an Aerix Client instance keeps its own switch, and it must be the one written")

if problems:
    print("fps counter check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("fps counter check: the readout stays inside its pill, and the A menu can turn it on and off")

