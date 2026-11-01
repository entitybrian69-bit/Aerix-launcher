package net.kdt.pojavlaunch.aerix.bridge

/**
 * Rendering identity, NOT selection/storage identity. The slot prefix makes keys unique even
 * when an upstream snapshot repeats an instance folder ID. Keep selection keyed by folder ID.
 * Reordering can recreate a row's local UI state, but cannot move the selected game instance.
 */
internal fun instanceRenderKey(index: Int, instanceId: String): String = "instance-slot:$index:$instanceId"
