package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DiscoveryCategoryFacetTest {
    @Test fun multipleCategoriesShareOneAlternativeGroup() {
        val facet = JsonParser().parse(discoveryCategoryFacet(listOf("adventure", "technology"))).asJsonArray
        assertEquals(listOf("categories:adventure", "categories:technology"), facet.map { it.asString })
    }
    @Test fun clearSelectionProducesNoCategoryTerms() {
        assertEquals("[]", discoveryCategoryFacet(emptyList()))
    }
    @Test fun duplicateAndBlankCategoriesAreNotSent() {
        assertEquals("[\"categories:magic\"]", discoveryCategoryFacet(listOf("magic", "", "magic")))
    }
    @Test fun sourceCategoryIdsAreJsonEscaped() {
        val raw = "quoted\"category\\name"
        assertEquals("categories:$raw", JsonParser().parse(discoveryCategoryFacet(listOf(raw))).asJsonArray[0].asString)
    }
}
