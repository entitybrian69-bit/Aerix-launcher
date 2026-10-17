package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AssetCategoryQueryTest {
    @Test
    fun noCategoriesLeavesSearchUnfiltered() {
        assertEquals(0, AssetCategoryQuery.modrinthFacet(emptyList()).size())
        assertNull(AssetCategoryQuery.curseForgeIds(emptyList()))
    }

    @Test
    fun multipleModrinthCategoriesShareOneOrFacet() {
        val facet = AssetCategoryQuery.modrinthFacet(listOf("adventure", "magic"))
        assertEquals(JsonParser.parseString("[\"categories:adventure\",\"categories:magic\"]"), facet)
    }

    @Test
    fun duplicateAndBlankModrinthCategoriesAreRemoved() {
        val facet = AssetCategoryQuery.modrinthFacet(listOf("magic", "", "magic", " "))
        assertEquals(JsonParser.parseString("[\"categories:magic\"]"), facet)
    }

    @Test
    fun facetValuesAreJsonEscaped() {
        val facet = AssetCategoryQuery.modrinthFacet(listOf("a\"b"))
        assertEquals("categories:a\"b", JsonParser.parseString(facet.toString()).asJsonArray[0].asString)
    }

    @Test
    fun multipleCurseForgeCategoriesUseAnIdArray() {
        assertEquals("[422,419]", AssetCategoryQuery.curseForgeIds(listOf(422, 419)))
    }

    @Test
    fun categoryAliasesDoNotDuplicateCurseForgeIds() {
        assertEquals("[4481]", AssetCategoryQuery.curseForgeIds(listOf(4481, 4481)))
    }
}
