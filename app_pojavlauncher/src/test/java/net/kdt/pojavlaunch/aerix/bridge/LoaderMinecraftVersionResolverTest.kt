package net.kdt.pojavlaunch.aerix.bridge

import org.junit.Assert.*
import org.junit.Test

class LoaderMinecraftVersionResolverTest {
    @Test fun resolvesInheritedMinecraftRatherThanLoaderNumber() {
        val id = "fabric-loader-0.13.3-1.18.2"
        assertEquals("1.18.2", LoaderMinecraftVersionResolver.resolve(id, setOf("1.18.2", "26.3")) { "1.18.2" })
    }
    @Test fun missingMetadataStillRecognizesKnownProfileSuffix() {
        assertEquals("1.18.2", LoaderMinecraftVersionResolver.resolve("fabric-loader-0.13.3-1.18.2", setOf("1.18", "1.18.2", "26.3")) { null })
    }
    @Test fun unknownProfileNeverSelectsLatestGame() {
        assertNull(LoaderMinecraftVersionResolver.resolve("custom-modpack", setOf("26.3")) { null })
    }
    @Test fun handlesInheritanceCycles() {
        assertNull(LoaderMinecraftVersionResolver.resolve("a", setOf("26.3")) { if (it == "a") "b" else "a" })
    }
    @Test fun followsMultipleInheritanceLevelsAndSnapshots() {
        assertEquals("26.4-snapshot-4", LoaderMinecraftVersionResolver.resolve("custom", setOf("26.4-snapshot-4")) {
            if (it == "custom") "fabric-custom" else "26.4-snapshot-4"
        })
    }
}
