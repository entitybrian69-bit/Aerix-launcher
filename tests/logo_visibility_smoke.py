#!/usr/bin/env python3
"""The floating Aerix logo can be put away during play - without taking the menu with it.

The logo is how the in-game menu opens on a layout that has no MENU button, and the default layout
has none. So hiding it freely would be a way for a player to lock themselves out of the pause menu
mid-session, with no visible control to get it back. This checks the rules that prevent that.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

button = (JAVA / "customcontrols/handleview/DrawerPullButton.java").read_text()
visibility = (JAVA / "customcontrols/handleview/MenuButtonVisibility.java").read_text()
activity = (JAVA / "game/GameActivity.java").read_text()
settings = (JAVA / "aerix/ui/screens/SettingsScreen.kt").read_text()
strings = (ROOT / "app_pojavlauncher/src/main/res/values/aerix_strings.xml").read_text()

problems = []


def check(condition, message):
    if not condition:
        problems.append(message)


# ── The preference ────────────────────────────────────────────────────────────────────────────
check("isHiddenWhilePlaying" in visibility and "setHiddenWhilePlaying" in visibility,
      "the switch needs a stored preference to survive a restart")
check("DEFAULT_HIDDEN_WHILE_PLAYING = false" in visibility,
      "showing the logo is what every existing player already has; hiding must be opt-in")
check("ClassCastException" in visibility,
      "the logo settings file is shared with the size and position, so a corrupt type must not "
      "crash the game on startup")
check("MenuButtonSize.PREFERENCES" in visibility,
      "it belongs in the same file as the size and position: same decision, same place")

# ── The switch in the long-press card ───────────────────────────────────────────────────────
check("R.string.aerix_logo_hide_while_playing" in button,
      "the switch belongs in the card the player already opens by holding the logo")
check("aerix_logo_hide_while_playing" in strings,
      "a switch needs a label a player can read")
check("hideSwitch.setOnCheckedChangeListener" in button,
      "the switch has to do something")
check("MenuButtonVisibility.setHiddenWhilePlaying(getContext(), mHiddenWhilePlaying)" in button,
      "OK is what saves it, like the size")
check("mHiddenWhilePlaying = originalHidden" in button,
      "Cancel must undo the switch, or a cancelled change silently becomes permanent")
check("final boolean originalHidden = mHiddenWhilePlaying;" in button,
      "Cancel restores it, so the original has to be captured before the panel can change it")

# "Default" resets the size and must not quietly undo a separate decision.
neutral = button[button.index("setNeutralButton"):]
neutral = neutral[:neutral.index("create()")]
check("MenuButtonVisibility" not in neutral,
      "Default means the default size; it must not flip the hide switch behind the player")

# ── The rules that keep the menu reachable ──────────────────────────────────────────────────
check("public void setMenuShowing(boolean showing)" in button,
      "the logo has to return while the menu is open")
menu_showing = button[button.index("public void setMenuShowing(boolean showing)"):]
menu_showing = menu_showing[:menu_showing.index("\n    }")]
# A guard that returns early would leave the flag stale and the logo hidden through the menu.
body = [line.strip() for line in menu_showing.splitlines() if line.strip()]
# body[0] is the signature itself; the guard has to be the first thing after it.
check(body[1] == "if (mMenuShowing == showing) return;"
          and "mMenuShowing = showing;" in body
          and "applyVisibility();" in body,
      "setMenuShowing must skip no-op calls, store the flag, and re-run the rules - a body that "
      "returns early leaves the logo hidden through the menu it is supposed to return for")
check("public void setLayoutHasMenuButton(boolean hasMenuButton)" in button,
      "a layout with its own MENU button does not need a floating logo")
check("public void setEditorMode(boolean editing)" in button,
      "the controls editor is left through the logo, so it cannot be hidden there")
apply = button[button.index("private void applyVisibility()"):]
apply = apply[:apply.index("\n    }")]
check("setVisibility(wanted ? VISIBLE : GONE)" in apply,
      "GONE, not INVISIBLE: an invisible logo still eats the touches meant for the game")
check("mEditorMode" in apply and "mLayoutHasMenuButton" in apply and "mMenuShowing" in apply,
      "every rule has to be decided in one place, or the next call site will disagree with it")

# ── The activity drives it ──────────────────────────────────────────────────────────────────
check("mDrawerPullButton.setMenuShowing(true)" in activity,
      "opening the menu must bring the logo back")
check("mDrawerPullButton.setMenuShowing(false)" in activity,
      "closing it must put the logo away again")
check("mDrawerPullButton.setEditorMode(true)" in activity,
      "the editor must show it")
check("mDrawerPullButton.setEditorMode(false)" in activity,
      "and leaving the editor must return the player's choice")
check("setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE)" not in activity,
      "the old direct setVisibility calls would now fight the new rules")
check("mDrawerPullButton.refreshVisibility()" in activity,
      "a change made in the launcher's settings has to be picked up when the game comes back")
# The field used to be declared as a plain View, so none of the button's methods resolved and the
# whole build failed on eight "cannot find symbol" errors. A missing base class is worth a check.
check("private View mDrawerPullButton;" not in activity
      and "DrawerPullButton mDrawerPullButton;" in activity,
      "the activity must hold the logo as a DrawerPullButton, not a View: as a View, every method "
      "this feature calls on it fails to resolve")

# ── The way back, from outside the game ─────────────────────────────────────────────────────
check("MenuButtonVisibility" in settings,
      "the logo is the only way into the menu on the default layout, so the switch needs a second "
      "home that does not require opening that menu")
check("aerix_logo_hide_while_playing_hint" in strings,
      "the switch has to say what happens if the layout has no MENU button")

if problems:
    print("logo visibility check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("logo visibility check: the logo can be hidden while playing and cannot be hidden out of reach")
