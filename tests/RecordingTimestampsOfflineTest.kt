import net.kdt.pojavlaunch.recording.RecordingTimestamps

private fun check(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun main() {
    val clock = RecordingTimestamps(100_000_000L)
    check(clock.video(100_050_000L) == 50_000L, "video should share session epoch")
    check(clock.audio(50_000L) == 50_000L, "audio should share session epoch")

    val neg = RecordingTimestamps(100_000_000L)
    check(neg.audio(-40_000L) == 0L, "negative audio priming clamps to 0")
    check(neg.audio(-20_000L) == 1L, "monotonic audio after clamp")
    check(neg.audio(0L) == 2L, "monotonic audio at 0")
    check(neg.audio(20_000L) == 20_000L, "real audio timestamp resumes cleanly")

    val vendor = RecordingTimestamps(100_000_000L)
    check(vendor.video(1_000L) == 0L, "vendor-relative video clock starts at 0")
    check(vendor.video(2_000L) == 1_000L, "vendor-relative video clock advances")

    val dup = RecordingTimestamps(100_000_000L)
    check(dup.video(100_000_000L) == 0L, "first video frame starts at 0")
    check(dup.video(100_000_000L) == 1L, "repeated video timestamps stay strictly monotonic")

    println("RecordingTimestampsOfflineTest passed")
}
