package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.junit.Assert.*
import org.junit.Test

class CurseForgeProjectLinkTest {
    // Fixture IDs are deliberately synthetic; no real project ID is hardcoded in the catalogue.
    private fun mod(slug: String = "aetherium-fabric", game: Int = 432, kind: Int = 6, id: String = "123") = JsonObject().apply {
        addProperty("slug", slug); addProperty("gameId", game); addProperty("classId", kind); addProperty("id", id)
        addProperty("name", "Aetherium (Fabric)"); addProperty("summary", "Fixture")
    }
    private fun response(vararg entries: JsonObject) = JsonObject().apply { add("data", JsonArray().apply { entries.forEach { add(it) } }) }
    @Test fun exactFabricProjectWinsOverFirstUnrelatedResult() {
        val result = CurseForgeProjectLink.resolve(response(mod("aetherium-neoforge"), mod(id = "456")), AssetType.MOD, "aetherium-fabric")
        assertEquals("456", result?.id); assertEquals(AssetPlatform.CURSEFORGE, result?.platform); assertEquals(AssetType.MOD, result?.type)
    }
    @Test fun wrongEditionOrSimilarNameNeverOpens() {
        assertNull(CurseForgeProjectLink.resolve(response(mod("aetherium-neoforge"), mod("aetherium-fabric-plus")), AssetType.MOD, "aetherium-fabric"))
    }
    @Test fun wrongGameOrContentTypeNeverOpens() {
        assertNull(CurseForgeProjectLink.resolve(response(mod(game = 1), mod(kind = 4471)), AssetType.MOD, "aetherium-fabric"))
    }
    @Test fun invalidProjectIdNeverOpens() {
        for (id in listOf("", "0", "-1", "not-an-id")) assertNull(CurseForgeProjectLink.resolve(response(mod(id = id)), AssetType.MOD, "aetherium-fabric"))
    }
    @Test fun absentDataAndInvalidSlugFailSafely() {
        assertNull(CurseForgeProjectLink.resolve(JsonObject(), AssetType.MOD, "aetherium-fabric"))
        assertNull(CurseForgeProjectLink.resolve(response(mod()), AssetType.MOD, "../aetherium-fabric"))
    }
    @Test fun malformedEntryDoesNotHideTheExactMatch() {
        assertEquals("123", CurseForgeProjectLink.resolve(response(JsonObject(), mod()), AssetType.MOD, "aetherium-fabric")?.id)
    }
}
