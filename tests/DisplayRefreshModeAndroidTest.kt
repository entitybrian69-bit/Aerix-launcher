import android.app.Activity
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.refreshstub.FakeScreen
import net.kdt.pojavlaunch.utils.DisplayRefreshMode

/**
 * End-to-end checks for the max-refresh-rate request, run in CI against the stub `android.*` classes
 * in `tests/refresh_display_stubs`. The production sources are compiled unmodified, so what is
 * asserted here is the real path: the display's modes are read, [RefreshModeChoice] picks, the
 * window's `LayoutParams` are written, the frame-rate levers are pulled, and every refusal degrades
 * into a log line instead of an exception.
 *
 * A device remains the only proof that a given OEM honors the request - which is why the game window
 * reads the result back - but everything the launcher can do on its own is checked here, including
 * the parts that exist precisely because the SDK will not bind some of these members by name.
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

/** Turns the switch on for the emulated Android version, starting from a window with no requests. */
private fun enable(sdkInt: Int = 34) {
    LauncherPreferences.PREF_MAX_REFRESH_RATE = true
    FakeScreen.sdkInt = sdkInt
    expect(Activity().window.attributes.preferredDisplayModeId == 0, "a fresh window starts with no mode preference")
    expect(Activity().window.attributes.preferredRefreshRate == 0f, "a fresh window starts with no rate preference")
}

fun main() {
    // 1. A panel that lists 60/90/120 as separate modes: the id has to move and the rate has to be asked.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enable()
    val activity = Activity()
    val plan = DisplayRefreshMode.planFor(activity)
    expect(plan != null, "a 120Hz mode at the live resolution must be found, got null")
    expectEquals(plan?.modeId, 3, "the fastest same-resolution mode must be the one requested")
    expectEquals(plan?.currentHz, 60f, "the plan must report the rate being left behind")
    expect(DisplayRefreshMode.applyTo(activity), "the request must be reported as made")
    expectEquals(FakeScreen.writtenModeId, 3, "the window must have been given the new display-mode id")
    expectEquals(FakeScreen.writtenPreferredRate, 120f, "the window must also carry the rate itself")
    expectEquals(FakeScreen.writtenFrameRate, 120f, "Window.setFrameRate must be reached reflectively on API 31+")
    expectEquals(FakeScreen.writtenFrameRateCategory, 2, "the decor view must ask for the HIGH frame-rate category")
    expect(logged("requested display mode 3"), "the log must name the mode that was asked for")
    expect(DisplayRefreshMode.isAvailable(activity), "availability must agree with the plan")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(activity), 120f, "the ceiling must read 120 for the settings row")
    expectEquals(DisplayRefreshMode.activeRefreshHz(activity), 60f, "the read-back must report the live rate, not the ceiling")

    // 2. An adaptive panel: one mode, idling at 60, top of range 120. There is no id to switch to, so
    //    the id must be left alone and the rate levers must carry the whole request. This is the case
    //    a "preferredDisplayModeId only" implementation silently misses.
    FakeScreen.reset(); FakeScreen.adaptivePanel(); enable()
    val voteActivity = Activity()
    expect(DisplayRefreshMode.applyTo(voteActivity), "a vote-only panel must still be asked")
    expect(FakeScreen.writtenModeId == null, "an adaptive panel must not be handed a mode id it already has")
    expectEquals(FakeScreen.writtenPreferredRate, 120f, "the rate preference is what moves an adaptive panel")
    expectEquals(FakeScreen.writtenFrameRate, 120f, "and the frame-rate vote goes with it")
    expectEquals(DisplayRefreshMode.activeRefreshHz(voteActivity), 60f, "the panel still serves 60 until it complies")
    expect(logged("asked for 120.0Hz"), "a request without a mode switch must be logged as one")

    // 3. The same adaptive panel on Android 11, where neither API 31 lever exists: the deprecated rate
    //    preference is still a real request, so this must not give up.
    FakeScreen.reset(); FakeScreen.adaptivePanel(); enable(sdkInt = 30)
    val legacyAdaptive = Activity()
    expect(DisplayRefreshMode.applyTo(legacyAdaptive), "API 30 must still be able to ask for the rate")
    expectEquals(FakeScreen.writtenPreferredRate, 120f, "the rate preference works back to API 17")
    expect(FakeScreen.writtenFrameRate == null, "there is no Window.setFrameRate below API 31")
    expect(FakeScreen.writtenFrameRateCategory == null, "there is no frame-rate category below API 31")
    expect(FakeScreen.writtenModeId == null, "a vote-only plan must not write a mode id")

    // 4. Android 10 and earlier: no public mode id, so the reflective getter is the only way to name a
    //    mode - and if that fails, the rate preference still stands on its own.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enable(sdkInt = 29)
    val legacy = Activity()
    expectEquals(DisplayRefreshMode.planFor(legacy)?.modeId, 3, "the mode id must be found through the pre-30 getter")
    expect(DisplayRefreshMode.applyTo(legacy), "a mode switch is worth asking for on API 29")
    expectEquals(FakeScreen.writtenModeId, 3, "the legacy path must still write the window's mode id")
    expectEquals(FakeScreen.writtenPreferredRate, 120f, "and the rate alongside it")
    expect(FakeScreen.writtenFrameRate == null, "there is no frame-rate vote below API 31")

    // 5. A 60Hz panel: the switch exists but must not pretend otherwise, and must not touch the window.
    FakeScreen.reset(); FakeScreen.sixtyOnlyPanel(); enable()
    val sixty = Activity()
    expect(DisplayRefreshMode.planFor(sixty) == null, "a 60Hz-only panel must produce no plan")
    expect(!DisplayRefreshMode.applyTo(sixty), "no request may be claimed on a 60Hz-only panel")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(sixty), 60f, "the ceiling must still read 60 for the settings row")
    expect(FakeScreen.writtenModeId == null && FakeScreen.writtenPreferredRate == null,
        "the window must be left alone when there was nothing to gain")
    expect(FakeScreen.logs.isEmpty(), "a panel with nothing to offer must not be reported as a failure")

    // 6. Turning it off mid-session has to hand the display back, or the mode sticks until reboot.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enable()
    val toggle = Activity()
    DisplayRefreshMode.applyTo(toggle)
    expectEquals(FakeScreen.writtenModeId, 3, "the switch must first put the faster mode in place")
    LauncherPreferences.PREF_MAX_REFRESH_RATE = false
    expect(!DisplayRefreshMode.applyTo(toggle), "with the switch off, no request may be claimed")
    expectEquals(FakeScreen.writtenModeId, 0, "switching off must clear the window's display-mode preference")
    expectEquals(FakeScreen.writtenPreferredRate, 0f, "switching off must clear the rate preference")
    expectEquals(FakeScreen.writtenFrameRate, 0f, "switching off must withdraw the frame-rate vote")
    expectEquals(FakeScreen.writtenFrameRateCategory, 0, "switching off must return the frame-rate category to unspecified")

    // 7. A context with no display - a detached or finishing activity - must degrade to doing nothing.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enable()
    FakeScreen.hasDisplay = false
    val headless = Activity()
    expect(DisplayRefreshMode.planFor(headless) == null, "no display means no plan")
    expect(!DisplayRefreshMode.applyTo(headless), "no display means no request")
    expectEquals(DisplayRefreshMode.activeRefreshHz(headless), 0f, "no display must read as 0 rather than throwing")
    expect(!DisplayRefreshMode.isAvailable(headless), "no display means nothing is available")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(headless), 0f, "no display must report no ceiling")
    DisplayRefreshMode.clear(headless)
    expect(FakeScreen.logs.none { it.contains("Exception") }, "a missing display must not surface as an exception")

    // 8. A screen that will not say which mode it is in must not be asked for anything either.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enable()
    FakeScreen.current = null
    val mute = Activity()
    expect(DisplayRefreshMode.planFor(mute) == null, "an unreadable current mode must stop the request")
    expect(!DisplayRefreshMode.applyTo(mute), "the game must not be told anything changed")
    expectEquals(DisplayRefreshMode.activeRefreshHz(mute), 0f, "an unreadable panel reads as 0")

    // 9. The Application context - which is what `LauncherPreferences.loadPreferences` holds while
    //    `Application.onCreate` is still running - is answered with a throw on Android 11+, not with
    //    null. That throw took the launcher down before a single window existed (2.1.0 and 2.3.0, on
    //    Android 13 among others), so the read path is required to come back answered through the
    //    display service, or unanswered as "nothing", but never as an exception.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); enable()
    val background = object : android.content.Context() {
        override val associatedWithDisplay: Boolean get() = false
    }
    expect(DisplayRefreshMode.isAvailable(background),
        "a context Android refuses to answer must still be told a faster mode exists, via DisplayManager")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(background), 120f,
        "the ceiling of the default display must be readable from a background context")
    expectEquals(DisplayRefreshMode.activeRefreshHz(background), 60f,
        "and the live rate must read back, not throw")
    expectEquals(DisplayRefreshMode.planFor(background)?.modeId, 3,
        "a background context must be handed the same plan an Activity gets")
    expect(FakeScreen.logs.none { it.contains("Exception") },
        "falling back must not be reported as an exception")
    // 10. when even the display service has no panel to name, the answer is "nothing available" - the
    //     preference then simply defaults to off instead of the process dying over it.
    FakeScreen.hasDisplay = false
    expect(!DisplayRefreshMode.isAvailable(background), "an unanswerable device must read as nothing available")
    expectEquals(DisplayRefreshMode.activeRefreshHz(background), 0f, "and as no rate, rather than a crash")
    expectEquals(DisplayRefreshMode.ceilingRefreshHz(background), 0f, "and no ceiling")
    // 11. a platform that answers null instead of throwing takes the same route, unchanged.
    FakeScreen.reset(); FakeScreen.tripleRatePanel(); FakeScreen.refusesDisplaylessContext = false
    expect(DisplayRefreshMode.isAvailable(background), "a null answer must fall through to the display service too")

    println("display refresh: $checks checks passed")
}
