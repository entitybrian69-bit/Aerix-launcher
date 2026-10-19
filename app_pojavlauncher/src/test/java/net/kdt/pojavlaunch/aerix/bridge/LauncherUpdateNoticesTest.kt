package net.kdt.pojavlaunch.aerix.bridge

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class LauncherUpdateNoticesTest {
    private val context get() = RuntimeEnvironment.getApplication<Application>()
    private fun version(value: String) = LauncherVersion.parse(value)
    private fun release(value: String) = LauncherRelease(value, version(value)!!, "Changes", LauncherUpdateAsset("app.apk", "", 1, null))
    @Before fun clear() { context.getSharedPreferences("aerix_update_notices", 0).edit().clear().commit() }
    @Test fun newerStableReleaseCanOffer() { assertTrue(LauncherUpdateNotices.shouldOffer(version("1.2.5"), version("1.2.6"), null)) }
    @Test fun installedOrOlderNeverOffers() {
        for (available in listOf("1.2.5", "1.2.4", "1.1.9")) assertFalse(LauncherUpdateNotices.shouldOffer(version("1.2.5"), version(available), null))
    }
    @Test fun sameAcknowledgedReleaseNeverOffersAgain() {
        LauncherUpdateNotices.acknowledge(context, release("v1.2.6"))
        assertFalse(LauncherUpdateNotices.shouldOffer(version("1.2.5"), version("1.2.6"), LauncherUpdateNotices.acknowledged(context)))
    }
    @Test fun nextReleaseOffersAfterCancellationOrDownload() {
        LauncherUpdateNotices.acknowledge(context, release("v1.2.6"))
        assertTrue(LauncherUpdateNotices.shouldOffer(version("1.2.5"), version("1.2.7"), LauncherUpdateNotices.acknowledged(context)))
    }
    @Test fun acknowledgementLivesInPreferencesNotComposition() {
        LauncherUpdateNotices.acknowledge(context, release("v1.2.6"))
        assertEquals("1.2.6", context.createConfigurationContext(context.resources.configuration)
            .getSharedPreferences("aerix_update_notices", 0).getString("acknowledged_version", null))
    }
    @Test fun rollbackCannotEraseHigherAcknowledgement() {
        LauncherUpdateNotices.acknowledge(context, release("1.2.8"))
        LauncherUpdateNotices.acknowledge(context, release("1.2.6"))
        assertEquals(version("1.2.8"), LauncherUpdateNotices.acknowledged(context))
        assertFalse(LauncherUpdateNotices.shouldOffer(version("1.2.5"), version("1.2.7"), LauncherUpdateNotices.acknowledged(context)))
    }
    @Test fun noRuntimeAndFullVersionsHaveSameIdentity() {
        assertFalse(LauncherUpdateNotices.shouldOffer(version("1.2.6-noruntime"), version("v1.2.6"), null))
    }
    @Test fun invalidOrMissingVersionsCannotOffer() {
        assertFalse(LauncherUpdateNotices.shouldOffer(null, version("1.2.6"), null))
        assertFalse(LauncherUpdateNotices.shouldOffer(version("1.2.5"), null, null))
        assertFalse(LauncherUpdateNotices.shouldOffer(version("1.2.5"), version("1.2.6-beta"), null))
    }
}
