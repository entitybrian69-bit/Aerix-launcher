package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonArray

/** Categories in one Modrinth facet are alternatives; version and loader remain separate facets. */
internal object AssetCategoryQuery {
    fun modrinthFacet(categories: List<String>): JsonArray {
        val facet = JsonArray()
        categories.filter { it.isNotBlank() }.distinct().forEach { facet.add("categories:$it") }
        return facet
    }

    fun curseForgeIds(categoryIds: List<Int>): String? {
        val unique = categoryIds.distinct()
        if (unique.isEmpty()) return null
        val ids = JsonArray()
        unique.forEach { ids.add(it) }
        return ids.toString()
    }
}
