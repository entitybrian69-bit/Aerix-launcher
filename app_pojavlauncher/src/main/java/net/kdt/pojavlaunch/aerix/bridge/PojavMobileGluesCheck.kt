/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Tells the user which option of the MobileGlues settings keeps Minecraft 26.3 and newer from
 * starting. The file is only read, never written: the defaults of MobileGlues belong to
 * MobileGlues and the launcher does not change them.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.os.Environment
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.game.renderer.def.Renderers
import java.io.File

/**
 * Reads the settings of the MobileGlues library and names the options that a version of
 * Minecraft needs. Nothing is written: the file belongs to MobileGlues and the launcher only
 * tells the user what is missing.
 */
object PojavMobileGluesCheck {

    /** One thing the user has to change in the MobileGlues settings. */
    class Problem(val title: String, val detail: String)

    /** Result of the check; [problems] is empty when the renderer can run the version. */
    class Report(
        val versionId: String,
        val problems: List<Problem>,
        /** False when the MobileGlues app never wrote a configuration file. */
        val configFound: Boolean,
        /** Short description of the settings, written to the game log. */
        val summary: String
    )

    /** `enableNoError` values of the MobileGlues settings. */
    private const val NO_ERROR_AUTO = 0
    private const val NO_ERROR_IGNORE_PROGRAM = 2
    private const val NO_ERROR_IGNORE_PROGRAM_FRAMEBUFFER = 3

    /** The first Minecraft version that needs OpenGL 3.3 and the option that ignores shader errors. */
    private const val MODERN_MAJOR = 26
    private const val MODERN_MINOR = 3

    /** True when the renderer runs on the MobileGlues library. */
    @JvmStatic
    fun usesMobileGlues(rendererTag: String?): Boolean =
        rendererTag == Renderers.MOBILEGLUES_RENDERER || rendererTag == Renderers.SFPEW_RENDERER

    /**
     * Reads the MobileGlues settings and names the options that keep the game from starting.
     * Returns null when the renderer does not use MobileGlues.
     */
    @JvmStatic
    fun check(context: Context, versionId: String, rendererTag: String?): Report? {
        if (!usesMobileGlues(rendererTag)) return null

        val config = readConfig()
        val problems = ArrayList<Problem>()
        val version = PojavAssetBridge.mcVersionOf(versionId) ?: versionId
        val major = version.substringBefore('.').toIntOrNull() ?: 0
        val minor = version.substringAfter('.', "").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val needsModern = major > MODERN_MAJOR || (major == MODERN_MAJOR && minor >= MODERN_MINOR)

        if (needsModern) {
            val noError = config.intOrNull("enableNoError") ?: NO_ERROR_AUTO
            if (noError != NO_ERROR_IGNORE_PROGRAM && noError != NO_ERROR_IGNORE_PROGRAM_FRAMEBUFFER) {
                problems.add(
                    Problem(
                        title = context.getString(R.string.aerix_mobileglues_ignore_error_title),
                        detail = context.getString(R.string.aerix_mobileglues_ignore_error_detail, version)
                    )
                )
            }
            val glVersion = config.intOrNull("customGLVersion") ?: 0
            if (glVersion in 1..32) {
                // The target version is set to 3.2 or lower, Minecraft 26.3 asks for 3.3.
                problems.add(
                    Problem(
                        title = context.getString(R.string.aerix_mobileglues_gl_version_title),
                        detail = context.getString(R.string.aerix_mobileglues_gl_version_detail, version)
                    )
                )
            } else if (glVersion == 0) {
                // The target version is left on automatic, then the version of the device decides
                // and that one is often 3.2.
                problems.add(
                    Problem(
                        title = context.getString(R.string.aerix_mobileglues_gl_auto_title),
                        detail = context.getString(R.string.aerix_mobileglues_gl_auto_detail, version)
                    )
                )
            }
        }

        val summary = buildString {
            append("MobileGlues settings: ")
            if (config == null) {
                append(context.getString(R.string.aerix_mobileglues_config_missing))
            } else {
                append("customGLVersion=")
                append(config.intOrNull("customGLVersion") ?: 0)
                append(", enableNoError=")
                append(config.intOrNull("enableNoError") ?: 0)
            }
        }

        return Report(versionId, problems, config != null, summary)
    }

    /** The configuration file the MobileGlues library reads when the game starts. */
    private fun readConfig(): JsonObject? {
        for (file in configFiles()) {
            val config = readObject(file)
            if (config != null) return config
        }
        return null
    }

    /** The places the configuration file can be, the private one first. */
    private fun configFiles(): List<File> {
        val files = ArrayList<File>()
        files.add(File(Tools.DIR_DATA, "MobileGlues/config.json"))
        try {
            // The folder the user gave access to, when the settings use the shared storage.
            val external = Environment.getExternalStorageDirectory()
            if (external != null) files.add(File(external, "MG/config.json"))
        } catch (_: Exception) {
            // Without the permission the folder cannot be read, the private one is enough.
        }
        return files
    }

    private fun readObject(file: File): JsonObject? {
        return try {
            if (!file.isFile) return null
            JsonParser.parseString(file.readText()).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    /** Number of a key of the configuration file, null when the key is missing or not a number. */
    private fun JsonObject?.intOrNull(key: String): Int? {
        val element = this?.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asInt }.getOrNull()
    }
}
