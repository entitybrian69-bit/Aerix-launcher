import android.app.Activity
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.refreshstub.FakeScreen
import net.kdt.pojavlaunch.utils.DisplayRefreshMode

/**
 * End-to-end checks for the max-refresh-rate request, run in CI against stub `android.*` classes from
 * `tests/refresh_display_stubs`. The production sources are compiled unmodified, so what is asserted
 * here is the real path: `Display.getSupportedModes()` is read, [RefreshModeChoice] picks, the
 * window's `LayoutParams.preferredDisplayModeId` is written, `Window.setFrameRate` is voted, and the
 * refusal paths log instead of throwing.
 *
 * A device is still the only proof that a given OEM honors the request - which is why the read-back in
 * `GameActivity` exists - but everything the launcher can do on its own is checked here.
 */
private var checks = 0

private fun expect(condition: Boolean, what: String) {
    checks++
    if (!condition) throw AssertionError("display refresh: $what")
}

private fun expectEquals(actual: Any?, expected: Any?, what: String) {
    expect(actual == expected, "$what (expected $expected, got $actual)")
}

private fun logged(containing: String): Boolean = FakeScreen.logs.any { it.contains(containing) }

private fun enabled(activity: Activity = Activity(), sdkInt: Int = 34) {
    LauncherPreferences.PREF_MAX_REFRESH_RATE = true
    FakeScreen.sdkInt = sdkInt
    expect(activity.window.attributes.preferredDisplayModeId == 0, "a fresh window starts with no mode preference")
}

fun main() {
    // 1. A panel that lists 60/90/120 as separate modes: the id has to move, and the rate has to be voted.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enabled()
    val plan = DisplayRefreshMode.planFor(Activity())
    expect(plan != null, "a 120Hz mode at the live resolution must be found, got null")
    expectEquals(plan?.modeId, 3, "the fastest same-resolution mode must be the one requested")
    expectEquals(plan?.currentHz, 60f, "the plan must report the rate being left behind")
    val activity = Activity()
    expect(DisplayRefreshMode.applyTo(activity), "the request must be reported as made")
    expectEquals(FakeScreen.writtenModeId, 3, "the window must have been given the new display-mode id")
    expectEquals(FakeScreen.writtenFrameRate, 120f, "the frame-rate vote must go with the mode switch")
    expect(logged("requested display mode 3"), "the log must name the mode that was asked for")
    expect(DisplayRefreshMode.isAvailable(activity), "availability must agree with the plan")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(activity), 120f, "the ceiling must read 120 for the settings row")
    expectEquals(DisplayRefreshMode.activeRefreshHz(activity), 60f, "the read-back must report the live rate, not the ceiling")

    // 2. An adaptive panel: one mode, idling at 60, top of range 120. Nothing to switch to, so the id
    //    must be left alone and the vote must carry the whole request. This is the case a
    //    "preferredDisplayModeId only" implementation silently misses.
    FakeScreen.reset(); FakeScreen.adaptivePanel(); enabled()
    val voteActivity = Activity()
    expect(DisplayRefreshMode.applyTo(voteActivity), "a vote-only panel must still be asked")
    expect(FakeScreen.writtenModeId == null, "an adaptive panel must not be handed a mode id it already has")
    expectEquals(FakeScreen.writtenFrameRate, 120f, "the rate vote is the only lever on an adaptive panel")
    expectEquals(DisplayRefreshMode.activeRefreshHz(voteActivity), 60f, "the panel is still serving 60 until it complies")
    expect(logged("voted 120.0Hz"), "a vote without a mode switch must be logged as such")

    // 3. The same adaptive panel on Android 11, where no frame-rate vote exists: nothing can be done, so
    //    nothing may be claimed.
    FakeScreen.reset(); FakeScreen.adaptivePanel(); enabled(sdkInt = 30)
    val oldAdaptive = Activity()
    expect(DisplayRefreshMode.planFor(oldAdaptive) == null, "a vote-only plan on API 30 must be dropped")
    expect(!DisplayRefreshMode.applyTo(oldAdaptive), "nothing must be reported as requested on API 30")
    expect(FakeScreen.writtenModeId == null && FakeScreen.writtenFrameRate == null, "API 30 must leave the window untouched")
    expect(logged("nothing was requested"), "the refusal must be explained in the log")

    // 4. A 60Hz panel: the switch exists but must not pretend otherwise.
    FakeScreen.reset(); FakeScreen.sixtyOnlyPanel(); enabled()
    val sixty = Activity()
    expect(DisplayRefreshMode.planFor(sixty) == null, "a 60Hz-only panel must produce no plan")
    expect(!DisplayRefreshMode.applyTo(sixty), "no request may be claimed on a 60Hz-only panel")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(sixty), 60f, "the ceiling must still read 60 for the settings row")
    expect(FakeScreen.writtenModeId == null, "the window must not be touched on a 60Hz-only panel")

    // 5. Turning it off mid-session has to hand the display back, or the mode sticks until reboot.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enabled()
    val toggle = Activity()
    DisplayRefreshMode.applyTo(toggle)
    expectEquals(FakeScreen.writtenModeId, 3, "the switch must first put the faster mode in place")
    LauncherPreferences.PREF_MAX_REFRESH_RATE = false
    expect(!DisplayRefreshMode.applyTo(toggle), "with the switch off, no request may be claimed")
    expectEquals(FakeScreen.writtenModeId, 0, "switching off must clear the window's display-mode preference")
    expectEquals(FakeScreen.writtenFrameRate, 0f, "switching off must withdraw the frame-rate vote")

    // 6. Android 10 and earlier: no public mode id, so the reflective getter is the only way to name a
    //    mode, and there is no vote to fall back on.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enabled(sdkInt = 29)
    val legacy = Activity()
    val legacyPlan = DisplayRefreshMode.planFor(legacy)
    expectEquals(legacyPlan?.modeId, 3, "the mode id must still be found through the pre-API-30 getter")
    expect(DisplayRefreshMode.applyTo(legacy), "a mode switch is still worth asking for on API 29")
    expectEquals(FakeScreen.writtenModeId, 3, "the legacy path must still write the window's mode id")
    expect(FakeScreen.writtenFrameRate == null, "there is no frame-rate vote below API 31")

    // 7. A context with no display - a detached or finishing activity - must degrade to doing nothing.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enabled()
    FakeScreen.hasDisplay = false
    val headless = Activity()
    expect(DisplayRefreshMode.planFor(headless) == null, "no display means no plan")
    expect(!DisplayRefreshMode.applyTo(headless), "no display means no request")
    expectEquals(DisplayRefreshMode.activeRefreshHz(headless), 0f, "no display must read as 0 rather than throwing")
    expect(!DisplayRefreshMode.isAvailable(headless), "no display means nothing is available")
    DisplayRefreshMode.clear(headless)
    expect(!logged("Exception"), "a missing display must never surface as a caught exception in the log")

    // 8. A screen that will not say which mode it is in must not be asked for anything either.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enabled()
    FakeScreen.current = null
    val mute = Activity()
    expect(DisplayRefreshMode.planFor(mute) == null, "an unreadable current mode must stop the request")
    expect(!DisplayRefreshMode.applyTo(mute), "the game must not be told anything changed")
    expectEquals(DisplayRefreshMode.activeRefreshHz(mute), 0f, "an unreadable panel reads as 0")

    println("display refresh: $checks checks passed")
}
