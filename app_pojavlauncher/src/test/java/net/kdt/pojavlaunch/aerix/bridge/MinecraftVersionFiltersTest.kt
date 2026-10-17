package net.kdt.pojavlaunch.aerix.bridge

import org.junit.Assert.*
import org.junit.Test

class MinecraftVersionFiltersTest {
    @Test fun releasesAreNotLimitedToAnOldShortlist() {
        for (id in listOf("1.7.10", "1.21.11", "26.3", "27.1")) assertEquals(MinecraftVersionKind.RELEASE, MinecraftVersionFilters.classify(id))
    }
    @Test fun weeklySnapshotsArePreserved() {
        assertEquals(MinecraftVersionKind.SNAPSHOT, MinecraftVersionFilters.classify("24w14a"))
        assertEquals(MinecraftVersionKind.SNAPSHOT, MinecraftVersionFilters.classify("20w14∞"))
    }
    @Test fun newSnapshotNamesAreRecognized() {
        assertEquals(MinecraftVersionKind.SNAPSHOT, MinecraftVersionFilters.classify("26.3-snapshot-2"))
    }
    @Test fun prereleasesHaveTheirOwnCategory() {
        for (id in listOf("1.21-pre1", "26.3-pre-1", "1.21 Pre-Release 1")) assertEquals(MinecraftVersionKind.PRE_RELEASE, MinecraftVersionFilters.classify(id))
    }
    @Test fun releaseCandidatesHaveTheirOwnCategory() {
        for (id in listOf("1.21-rc1", "26.3-rc-2")) assertEquals(MinecraftVersionKind.RELEASE_CANDIDATE, MinecraftVersionFilters.classify(id))
    }
    @Test fun oldBetaAndAlphaRemainSelectable() {
        assertEquals(MinecraftVersionKind.OLD_BETA, MinecraftVersionFilters.classify("b1.7.3"))
        assertEquals(MinecraftVersionKind.OLD_ALPHA, MinecraftVersionFilters.classify("a1.2.6"))
    }
    @Test fun providerMetadataHandlesHistoricalNames() {
        assertEquals(MinecraftVersionKind.OLD_ALPHA, MinecraftVersionFilters.classify("c0.30_01c", "old_alpha"))
        assertEquals(MinecraftVersionKind.OLD_BETA, MinecraftVersionFilters.classify("unusual", "beta"))
    }
    @Test fun unknownVersionsAreNotSilentlyDiscarded() {
        val kind = MinecraftVersionFilters.classify("experimental-combat-test")
        assertEquals(MinecraftVersionKind.OTHER, kind)
        assertTrue(MinecraftVersionFilters.matches(MinecraftVersionKind.ALL, kind))
    }
    @Test fun snapshotFilterIncludesPreAndRcButNotReleases() {
        assertTrue(MinecraftVersionFilters.matches(MinecraftVersionKind.SNAPSHOT, MinecraftVersionKind.PRE_RELEASE))
        assertTrue(MinecraftVersionFilters.matches(MinecraftVersionKind.SNAPSHOT, MinecraftVersionKind.RELEASE_CANDIDATE))
        assertFalse(MinecraftVersionFilters.matches(MinecraftVersionKind.SNAPSHOT, MinecraftVersionKind.RELEASE))
    }
    @Test fun releaseFilterDoesNotIncludeSnapshots() {
        assertFalse(MinecraftVersionFilters.matches(MinecraftVersionKind.RELEASE, MinecraftVersionKind.SNAPSHOT))
        assertTrue(MinecraftVersionFilters.matches(MinecraftVersionKind.RELEASE, MinecraftVersionKind.RELEASE))
    }
}
