import net.kdt.pojavlaunch.utils.RefreshModeChoice
import net.kdt.pojavlaunch.utils.RefreshModeChoice.Plan
import net.kdt.pojavlaunch.utils.RefreshModeChoice.ScreenMode

/**
 * Offline checks for the refresh-rate chooser, compiled and run by `.github/workflows/aerix-debug.yml`
 * with kotlinc and no Android on the classpath - which is the point of keeping the decision in
 * [RefreshModeChoice]. These are the behaviours a max-refresh switch is judged on, and every one of
 * them has been a real bug in a launcher somewhere:
 *
 *  - an adaptive panel whose mode id never changes must still be asked (via the rate vote),
 *  - a faster mode at a resolution the game is not running at must be refused,
 *  - a panel already at its ceiling must produce no request at all, and
 *  - the rate the panel is *serving* is the number to compare against, not the top of its range.
 */
private var checks = 0

private fun expect(condition: Boolean, what: String) {
    checks++
    if (!condition) throw AssertionError("refresh-rate chooser: $what")
}

private fun planOf(modes: List<ScreenMode>, current: ScreenMode?): Plan? = RefreshModeChoice.choose(modes, current)

fun main() {
    // 1. One adaptive mode: id 0 runs 60 now and tops at 120. The mode cannot be "switched" to, so the
    //    plan must still exist and must say it is a vote, not a mode change.
    val adaptive = ScreenMode(modeId = 0, width = 1080, height = 2400, peakHz = 120f, liveHz = 60f)
    val vote = planOf(listOf(adaptive), adaptive)
    expect(vote != null, "an adaptive mode with a 120Hz ceiling must be requested even though its id is unchanged")
    expect(vote?.requestedHz == 120f, "the vote must carry the top of the range, not the idle rate")
    expect(vote?.currentHz == 60f, "the plan must remember what the panel was serving")
    expect(vote?.needsModeSwitch == false, "voting inside the current mode must not be reported as a mode switch")

    // 2. A panel that lists separate modes per rate: the id has to change.
    val sixty = ScreenMode(modeId = 1, width = 1080, height = 2400, peakHz = 60f)
    val ninety = ScreenMode(modeId = 2, width = 1080, height = 2400, peakHz = 90f)
    val oneTwenty = ScreenMode(modeId = 3, width = 1080, height = 2400, peakHz = 120f)
    val switch = planOf(listOf(sixty, ninety, oneTwenty), sixty)
    expect(switch?.modeId == 3, "the fastest mode at the live resolution must win, got ${switch?.modeId}")
    expect(switch?.needsModeSwitch == true, "moving to another mode must be reported as a mode switch")
    expect(switch?.fasterModes == 2, "both faster candidates must be counted, got ${switch?.fasterModes}")

    // 3. The resolution rule: a 165Hz mode at another size is not on the table, because the swap can
    //    resize the surface under a running GL context.
    val tall = ScreenMode(modeId = 9, width = 1440, height = 3200, peakHz = 165f)
    expect(planOf(listOf(sixty, tall), sixty) == null, "a faster mode at a different resolution must be refused")
    expect(!RefreshModeChoice.canGoFaster(listOf(tall), sixty), "with no same-size candidate there is nothing to ask for")

    // 4. Already at the ceiling: nothing is requested, so the UI cannot claim a change that did not happen.
    expect(planOf(listOf(oneTwenty, ninety, sixty), oneTwenty) == null, "a panel at its ceiling must produce no request")
    expect(RefreshModeChoice.ceilingHz(listOf(oneTwenty, ninety), oneTwenty) == 120f, "the ceiling must still be reported at 120")

    // 4b. The exact trap this design exists for: when the mode's own rate is read as both the current
    //     rate and the range top, nothing ever looks faster and the switch silently does nothing.
    val reportedFixed = ScreenMode(modeId = 0, width = 1080, height = 2400, peakHz = 120f, liveHz = 120f)
    expect(planOf(listOf(reportedFixed), reportedFixed) == null, "a mode that is already at its own top must be left alone")
    expect(RefreshModeChoice.ceilingHz(listOf(adaptive), adaptive) == 120f, "120Hz must be readable while the panel idles at 60")

    // 5. Rounding noise is not an upgrade: 60.05 vs 60 must not burn battery.
    val wobble = ScreenMode(modeId = 4, width = 1080, height = 2400, peakHz = 60.05f)
    expect(planOf(listOf(sixty, wobble), sixty) == null, "sub-hertz differences must be ignored")

    // 6. Two modes at the same peak: the lower id is the panel's native entry, so ties go to it.
    val twin = ScreenMode(modeId = 7, width = 1080, height = 2400, peakHz = 120f)
    expect(planOf(listOf(oneTwenty, twin), sixty)?.modeId == 3, "ties must break towards the lower mode id")

    // 7. A 60Hz-only screen still has something true to say.
    expect(RefreshModeChoice.ceilingHz(listOf(sixty), sixty) == 60f, "a 60Hz panel must report a 60Hz ceiling, not 0")

    // 8. Nothing to compare against - an unreadable display must never produce a request.
    expect(planOf(listOf(oneTwenty), null) == null, "no current mode means no request")
    expect(RefreshModeChoice.ceilingHz(emptyList(), sixty) == 0f, "an empty mode list must report no ceiling")
    expect(planOf(emptyList(), sixty) == null, "an empty mode list must produce no request")

    println("refresh-rate chooser: $checks checks passed")
}
