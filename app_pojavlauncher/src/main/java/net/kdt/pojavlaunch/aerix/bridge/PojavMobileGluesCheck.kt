/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Tells the user which option of the MobileGlues settings keeps Minecraft 26.3 and newer from
 * starting, and switches on the option that the version needs.
 *
 * The settings of MobileGlues belong to MobileGlues. Only the one option that a version cannot
 * run without is written, every other key of the file stays as it is.
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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.game.renderer.def.Renderers
import java.io.File

/**
 * Reads the settings of the MobileGlues library and knows which option a version of Minecraft
 * needs. The option that a version cannot run without is switched on, the rest of the file is
 * left alone.
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
        val summary: String,
        /** Settings that the launcher switched on for this version. */
        val appliedSettings: List<String>
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
     * Reads the MobileGlues settings and switches on the option that the version needs. Returns
     * null when the renderer does not use MobileGlues.
     *
     * Minecraft 26.3 and newer use shaders that the shader compiler of MobileGlues cannot build.
     * The game then stops with "Failed to load required shader programs", so the option that
     * ignores such errors has to be on before the game starts. Nothing else is written.
     */
    @JvmStatic
    fun check(context: Context, versionId: String, rendererTag: String?): Report? {
        if (!usesMobileGlues(rendererTag)) return null

        val version = PojavAssetBridge.mcVersionOf(versionId) ?: versionId
        var config = readConfig()
        val problems = ArrayList<Problem>()
        val applied = ArrayList<String>()

        if (needsModern(version)) {
            val noError = config.intOrNull("enableNoError") ?: NO_ERROR_AUTO
            if (noError != NO_ERROR_IGNORE_PROGRAM && noError != NO_ERROR_IGNORE_PROGRAM_FRAMEBUFFER) {
                val written = writeNoError(NO_ERROR_IGNORE_PROGRAM)
                if (written) {
                    applied.add("enableNoError=$NO_ERROR_IGNORE_PROGRAM")
                    config = readConfig()
                } else {
                    problems.add(
                        Problem(
                            title = context.getString(R.string.aerix_mobileglues_ignore_error_title),
                            detail = context.getString(R.string.aerix_mobileglues_ignore_error_detail, version)
                        )
                    )
                }
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

        return Report(versionId, problems, config != null, summary, applied)
    }

    /** True when the version needs the options of the newer Minecraft releases. */
    private fun needsModern(version: String): Boolean {
        val major = version.substringBefore('.').toIntOrNull() ?: 0
        val minor = version.substringAfter('.', "").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        return major > MODERN_MAJOR || (major == MODERN_MAJOR && minor >= MODERN_MINOR)
    }

    /** Switches the option that ignores shader and program errors on. */
    private fun writeNoError(value: Int): Boolean {
        val config = readConfig() ?: JsonObject()
        config.addProperty("enableNoError", value)
        return writeConfig(config)
    }

    /**
     * The configuration file the MobileGlues library reads when the game starts. The path is the
     * one the renderer hands to the library, so this is the file the game sees.
     */
    private fun configFile(): File = File(Tools.DIR_DATA, "MobileGlues/config.json")

    /** The content of the configuration file, null when the library never wrote one. */
    private fun readConfig(): JsonObject? {
        return try {
            val file = configFile()
            if (!file.isFile) return null
            JsonParser.parseString(file.readText()).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes the configuration file the way the MobileGlues app does it: into a temporary file
     * that is renamed afterwards, so the game never reads half a file.
     */
    private fun writeConfig(config: JsonObject): Boolean {
        return try {
            val target = configFile()
            val dir = target.parentFile ?: return false
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val temp = File(dir, "config.json.tmp")
            temp.writeText(config.toString())
            if (temp.renameTo(target)) {
                true
            } else {
                // Some file systems cannot rename onto an existing file, then it is written
                // straight away.
                target.writeText(config.toString())
                temp.delete()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Number of a key of the configuration file, null when the key is missing or not a number. */
    private fun JsonObject?.intOrNull(key: String): Int? {
        val element = this?.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asInt }.getOrNull()
    }
}
