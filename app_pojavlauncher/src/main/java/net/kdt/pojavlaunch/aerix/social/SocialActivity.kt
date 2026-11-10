package net.kdt.pojavlaunch.aerix.social

import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.AssetProject

/**
 * The wording of the launcher side of a friend's status line.
 *
 * One place on purpose: "Reading Sodium" and "Viewing Sodium (Fabric)" are two strings nobody
 * keeps in step, and a friend reads these without asking - so they are written here, in past-tense
 * free, plain-language form, and the screens only say what happened.
 */
object SocialActivity {
    /** A project card is open: the one state worth naming, because it says what is on the screen. */
    fun reading(project: AssetProject) {
        val kind = when (project.type) {
            AssetType.MODPACK -> "modpack"
            AssetType.MOD -> "mod"
            AssetType.RESOURCE_PACK -> "texture pack"
            AssetType.SHADER_PACK -> "shader"
            AssetType.WORLD -> "world"
            else -> "download"
        }
        val title = project.title.trim().take(26).ifEmpty { return }
        SocialBackend.reportLauncherActivity("Reading $title · $kind")
    }

    /** A search for content, with the words the player typed: a friend can answer with a link. */
    fun searching(type: AssetType, query: String) {
        val clean = query.trim().take(20)
        val what = if (type == AssetType.MODPACK) "modpack" else "content"
        SocialBackend.reportLauncherActivity(
            if (clean.isEmpty()) "Hunting for a $what" else "Hunting for $what: $clean"
        )
    }

    /** An instance is being built or repaired, which is worth knowing before you invite someone. */
    fun building(name: String) {
        val clean = name.trim().take(28).ifEmpty { return }
        SocialBackend.reportLauncherActivity("Building $clean")
    }

    /** The launcher is open and doing nothing that a friend needs to read about. */
    fun idle() = SocialBackend.reportLauncherActivity(null)
}
