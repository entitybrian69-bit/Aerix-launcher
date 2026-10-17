/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Preference adapter: every Aerix settings row is mapped onto a PojavLauncher preference key.
 * Reads come from the static LauncherPreferences.PREF_* fields (which already carry Pojav's
 * device-dependent defaults); writes go to LauncherPreferences.DEFAULT_PREF with the same key,
 * followed by LauncherPreferences.loadPreferences(), exactly like LauncherPreferenceFragment does.
 *
 * Aerix (Zalith) setting sources mapped here, see mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/setting/AllSettings.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/(all .kt files)
 * Pojav sources mirrored here:
 *   app_pojavlauncher/src/main/res/xml/pref_{video,control,java,misc,experimental,main}.xml
 *   app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/screens/(all .java files)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import fr.spse.gamepad_remapper.Remapper
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.fragments.GamepadMapperFragment
import net.kdt.pojavlaunch.game.renderer.RendererCache
import net.kdt.pojavlaunch.game.renderer.def.Renderers
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider
import net.kdt.pojavlaunch.mobileglues.MainActivity as MobileGluesActivity
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceExperimentalFragment
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceMiscellaneousFragment
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.utils.GpuUtils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

/** Rows of the Aerix settings page. Every row names the Pojav key it owns. */
sealed class PojavSetting(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int?,
    val visible: (FragmentActivity) -> Boolean
) {
    class Toggle(
        key: String, titleRes: Int, summaryRes: Int?,
        val current: () -> Boolean,
        visible: (FragmentActivity) -> Boolean = { true }
    ) : PojavSetting(key, titleRes, summaryRes, visible)

    class Slider(
        key: String, titleRes: Int, summaryRes: Int?,
        val min: (Context) -> Int,
        val max: (Context) -> Int,
        val step: Int,
        val suffix: String,
        val current: () -> Int,
        visible: (FragmentActivity) -> Boolean = { true }
    ) : PojavSetting(key, titleRes, summaryRes, visible)

    class Text(
        key: String, titleRes: Int, summaryRes: Int?,
        val current: () -> String,
        visible: (FragmentActivity) -> Boolean = { true }
    ) : PojavSetting(key, titleRes, summaryRes, visible)

    class Choice(
        key: String, titleRes: Int, summaryRes: Int?,
        val entries: (Context) -> List<Pair<String, String>>,
        val current: () -> String,
        visible: (FragmentActivity) -> Boolean = { true }
    ) : PojavSetting(key, titleRes, summaryRes, visible)

    /** Rows that run an action instead of storing a value (permission requests, tools, sub-pages). */
    class Action(
        key: String, titleRes: Int, summaryRes: Int?,
        val run: (FragmentActivity) -> Unit,
        visible: (FragmentActivity) -> Boolean = { true }
    ) : PojavSetting(key, titleRes, summaryRes, visible)
}

class PojavSettingCategory(
    val id: String,
    @StringRes val titleRes: Int,
    val iconRes: Int,
    val settings: List<PojavSetting>,
    /** Original Pojav preference screen, reachable from the category with every option it carries. */
    val classicScreen: Class<out androidx.fragment.app.Fragment>
)

object PojavSettingsBridge {
    /** Bumped after every write so Compose re-reads the PREF_* values. */
    var revision by mutableIntStateOf(0)
        private set

    private var hasAngleCache: Boolean? = null

    /** Re-evaluates visibility rules (permission rows, cache row) after the launcher resumes. */
    fun touch() {
        revision++
    }

    // ---- writes ----------------------------------------------------------------------------

    private fun commit(context: Context) {
        LauncherPreferences.loadPreferences(context)
        revision++
    }

    fun writeBoolean(context: Context, key: String, value: Boolean) {
        val editor = LauncherPreferences.DEFAULT_PREF.edit().putBoolean(key, value)
        // use_angle and use_system_angle are mutually exclusive (LauncherPreferenceVideoFragment).
        if (value && key == "use_angle") editor.putBoolean("use_system_angle", false)
        if (value && key == "use_system_angle") editor.putBoolean("use_angle", false)
        editor.apply()
        commit(context)
    }

    fun writeInt(context: Context, key: String, value: Int) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt(key, value).apply()
        commit(context)
    }

    fun writeString(context: Context, key: String, value: String) {
        LauncherPreferences.DEFAULT_PREF.edit().putString(key, value).apply()
        commit(context)
    }

    /**
     * Current value of [setting] as display text, computed exactly like the Aerix settings row draws it
     * (choice entries resolved to their label, sliders with their suffix, allocation with its summary).
     */
    fun describe(context: Context, setting: PojavSetting): String = when (setting) {
        is PojavSetting.Toggle -> context.getString(if (setting.current()) R.string.aerix_settings_value_on else R.string.aerix_settings_value_off)
        is PojavSetting.Slider -> setting.current().toString() + setting.suffix
        is PojavSetting.Text -> setting.current().ifEmpty { context.getString(R.string.aerix_settings_value_none) }
        is PojavSetting.Choice -> {
            val current = setting.current()
            setting.entries(context).firstOrNull { it.first == current }?.second ?: current
        }
        is PojavSetting.Action -> ""
    }

    /** Writes [value] into [setting] through the same keys and the same rules the settings page uses. */
    fun writeInto(activity: FragmentActivity, setting: PojavSetting, value: Any) {
        when (setting) {
            is PojavSetting.Toggle -> writeBoolean(activity, setting.key, value as Boolean)
            is PojavSetting.Slider -> writeInt(activity, setting.key, (value as Number).toInt())
            is PojavSetting.Text -> writeString(activity, setting.key, value as String)
            is PojavSetting.Choice -> writeString(activity, setting.key, value as String)
            is PojavSetting.Action -> setting.run(activity)
        }
    }

    /** The Java category's "allocation" row, shared by the settings page and the instance page. */
    val memoryAllocation: PojavSetting.Slider
        get() = java.settings.filterIsInstance<PojavSetting.Slider>().first { it.key == "allocation" }

    /** The Java category's "javaArgs" row, shared by the settings page and the instance page. */
    val javaArguments: PojavSetting.Text
        get() = java.settings.filterIsInstance<PojavSetting.Text>().first { it.key == "javaArgs" }

    // ---- helpers mirrored from the Pojav preference fragments -----------------------------

    /** LauncherPreferenceJavaFragment max RAM computation. */
    fun maxRam(context: Context): Int {
        val deviceRam = Tools.getTotalDeviceMemory(context)
        return if (Architecture.is32BitsDevice() || deviceRam < 2048) minOf(1024, deviceRam)
        else deviceRam - (if (deviceRam < 3064) 800 else 1024)
    }

    /** LauncherPreferenceVideoFragment "#724" fix: values under 25 show as 100. */
    fun resolutionPercent(): Int {
        val value = (LauncherPreferences.PREF_SCALE_FACTOR * 100).toInt()
        return if (value < 25) 100 else value
    }

    private fun hasAngle(context: Context): Boolean {
        val cached = hasAngleCache
        if (cached != null) return cached
        val result = runCatching { GLESProvider.ExternalAngleProvider(context).supported() }.getOrDefault(false)
        hasAngleCache = result
        return result
    }

    private fun supportsTurnip(context: Context): Boolean = runCatching {
        GpuUtils.checkVulkanSupport(context.packageManager) && GpuUtils.getGlInfo().isAdreno()
    }.getOrDefault(false)

    fun renderers(context: Context): List<Pair<String, String>> {
        PojavPluginBridge.loadPlugins(context)
        val cache = RendererCache.getCompatibleRenderers(context)
        return cache.rendererIds.mapIndexed { i, id -> id to (cache.rendererDisplayNames.getOrNull(i) ?: id) }
    }

    private fun downloadSources(context: Context): List<Pair<String, String>> {
        val names = context.resources.getStringArray(R.array.download_source_names)
        val values = context.resources.getStringArray(R.array.download_source_values)
        return values.mapIndexed { i, v -> v to (names.getOrNull(i) ?: v) }
    }

    private fun launcher(activity: FragmentActivity): LauncherActivity? = activity as? LauncherActivity

    fun freeMemoryMb(context: Context): Int =
        (net.kdt.pojavlaunch.utils.MemoryUtils.getFreeDeviceMemory(context) / (1024L * 1024L)).toInt()

    fun clearMetadataCache(activity: FragmentActivity) {
        if (ProgressKeeper.getTaskCount() > 0) {
            Toast.makeText(activity, R.string.tasks_ongoing, Toast.LENGTH_SHORT).show()
            return
        }
        PojavApplication.sExecutorService.submit {
            try {
                FileUtils.deleteDirectory(File(Tools.DIR_CACHE, "string_cache"))
            } catch (e: IOException) {
                Tools.showErrorRemote(activity, R.string.preference_metadata_clear_fail, e)
                return@submit
            }
            Tools.runOnUiThread {
                Toast.makeText(activity, R.string.preference_metadata_clear_complete, Toast.LENGTH_LONG).show()
                revision++
            }
        }
    }

    private fun open(activity: FragmentActivity, screen: Class<out androidx.fragment.app.Fragment>) {
        Tools.swapFragment(activity, screen, LauncherActivity.SETTING_FRAGMENT_TAG, null)
    }

    private fun integer(context: Context, id: Int) = context.resources.getInteger(id)

    // ---- categories ------------------------------------------------------------------------

    val video = PojavSettingCategory(
        id = "video",
        titleRes = R.string.aerix_settings_cat_video,
        iconRes = R.drawable.aerix_ic_video_settings,
        classicScreen = LauncherPreferenceVideoFragment::class.java,
        settings = listOf(
            PojavSetting.Choice("renderer", R.string.mcl_setting_category_renderer, null,
                entries = { renderers(it) }, current = { LauncherPreferences.PREF_RENDERER }),
            PojavSetting.Action("renderer_settings", R.string.mcl_setting_title_renderer_settings, R.string.aerix_settings_renderer_settings_sub,
                run = { it.startActivity(Intent(it, MobileGluesActivity::class.java)) },
                visible = { Renderers.MOBILEGLUES_RENDERER == LauncherPreferences.DEFAULT_PREF.getString("renderer", LauncherPreferences.defaultRendererId(it)) }),
            PojavSetting.Toggle("ignoreNotch", R.string.mcl_setting_title_ignore_notch, R.string.mcl_setting_subtitle_ignore_notch,
                current = { LauncherPreferences.PREF_IGNORE_NOTCH }, visible = { LauncherPreferences.hasNotch(it) }),
            PojavSetting.Slider("resolutionRatio", R.string.mcl_setting_title_resolution_scaler, R.string.mcl_setting_subtitle_resolution_scaler,
                min = { integer(it, R.integer.resolution_seekbar_min) }, max = { 100 },
                step = 5, suffix = " %", current = { resolutionPercent() }),
            PojavSetting.Toggle("sustainedPerformance", R.string.preference_sustained_performance_title, R.string.preference_sustained_performance_description,
                current = { LauncherPreferences.PREF_SUSTAINED_PERFORMANCE }, visible = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.N }),
            PojavSetting.Toggle("alternate_surface", R.string.mcl_setting_title_use_surface_view, R.string.mcl_setting_subtitle_use_surface_view,
                current = { LauncherPreferences.PREF_USE_ALTERNATE_SURFACE }),
            PojavSetting.Toggle("force_vsync", R.string.preference_force_vsync_title, R.string.preference_force_vsync_description,
                current = { LauncherPreferences.PREF_FORCE_VSYNC }, visible = { LauncherPreferences.PREF_USE_ALTERNATE_SURFACE }),
            PojavSetting.Toggle("use_angle", R.string.preference_use_angle_title, R.string.preference_use_angle_description,
                current = { LauncherPreferences.PREF_USE_ANGLE }, visible = { hasAngle(it) }),
            PojavSetting.Toggle("use_system_angle", R.string.preference_use_system_angle_title, R.string.preference_use_angle_description,
                current = { LauncherPreferences.PREF_USE_SYSTEM_ANGLE }, visible = { Build.VERSION.SDK_INT >= 35 }),
            PojavSetting.Toggle("zinkPreferSystemDriver", R.string.preference_vulkan_driver_system_title, R.string.preference_vulkan_driver_system_description,
                current = { LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER }, visible = { supportsTurnip(it) })
        )
    )

    val controls = PojavSettingCategory(
        id = "controls",
        titleRes = R.string.aerix_settings_cat_controls,
        iconRes = R.drawable.aerix_ic_sports_esports_outlined,
        classicScreen = LauncherPreferenceControlFragment::class.java,
        settings = listOf(
            PojavSetting.Action("edit_controls", R.string.preference_edit_controls_title, R.string.preference_edit_controls_summary,
                run = { it.startActivity(Intent(it, CustomControlsActivity::class.java)) }),
            PojavSetting.Toggle("disableGestures", R.string.mcl_disable_gestures, R.string.mcl_disable_gestures_subtitle,
                current = { LauncherPreferences.PREF_DISABLE_GESTURES }),
            PojavSetting.Toggle("disableDoubleTap", R.string.mcl_disable_swap_hand, R.string.mcl_disable_swap_hand_subtitle,
                current = { LauncherPreferences.PREF_DISABLE_SWAP_HAND }),
            PojavSetting.Slider("timeLongPressTrigger", R.string.mcl_setting_title_longpresstrigger, R.string.mcl_setting_subtitle_longpresstrigger,
                min = { integer(it, R.integer.gesture_delay_seekbar_min) }, max = { integer(it, R.integer.gesture_delay_seekbar_max) },
                step = 10, suffix = " ms", current = { LauncherPreferences.PREF_LONGPRESS_TRIGGER }),
            PojavSetting.Slider("buttonscale", R.string.mcl_setting_title_buttonscale, R.string.mcl_setting_subtitle_buttonscale,
                min = { integer(it, R.integer.button_scale_seekbar_min) }, max = { integer(it, R.integer.button_scale_seekbar_max) },
                step = 5, suffix = " %", current = { LauncherPreferences.PREF_BUTTONSIZE.toInt() }),
            PojavSetting.Slider("buttonTransparency", R.string.preference_button_transparency, R.string.preference_button_transparency_summary,
                min = { 0 }, max = { 100 }, step = 1, suffix = " %", current = { LauncherPreferences.PREF_BUTTON_TRANSPARENCY.toInt() }),
            PojavSetting.Toggle("buttonAllCaps", R.string.mcl_setting_title_buttonallcaps, R.string.mcl_setting_subtitle_buttonallcaps,
                current = { LauncherPreferences.PREF_BUTTON_ALL_CAPS }),
            PojavSetting.Slider("mousescale", R.string.mcl_setting_title_mousescale, R.string.mcl_setting_subtitle_mousescale,
                min = { integer(it, R.integer.mouse_scale_seekbar_min) }, max = { integer(it, R.integer.mouse_scale_seekbar_max) },
                step = 5, suffix = " %", current = { Math.round(LauncherPreferences.PREF_MOUSESCALE * 100) }),
            PojavSetting.Slider("mousespeed", R.string.mcl_setting_title_mousespeed, R.string.mcl_setting_subtitle_mousespeed,
                min = { integer(it, R.integer.mouse_speed_seekbar_min) }, max = { integer(it, R.integer.mouse_speed_seekbar_max) },
                step = 5, suffix = " %", current = { Math.round(LauncherPreferences.PREF_MOUSESPEED * 100) }),
            PojavSetting.Toggle("mouse_start", R.string.preference_mouse_start_title, R.string.preference_mouse_start_description,
                current = { LauncherPreferences.PREF_VIRTUAL_MOUSE_START }),
            PojavSetting.Toggle("enableGyro", R.string.preference_enable_gyro_title, R.string.preference_enable_gyro_description,
                current = { LauncherPreferences.PREF_ENABLE_GYRO }),
            PojavSetting.Slider("gyroSensitivity", R.string.preference_gyro_sensitivity_title, R.string.preference_gyro_sensitivity_description,
                min = { integer(it, R.integer.gyro_speed_seekbar_min) }, max = { integer(it, R.integer.gyro_speed_seekbar_max) },
                step = 5, suffix = " %", current = { Math.round(LauncherPreferences.PREF_GYRO_SENSITIVITY * 100) },
                visible = { LauncherPreferences.PREF_ENABLE_GYRO }),
            PojavSetting.Slider("gyroSampleRate", R.string.preference_gyro_sample_rate_title, R.string.preference_gyro_sample_rate_description,
                min = { integer(it, R.integer.gyro_rate_seekbar_min) }, max = { integer(it, R.integer.gyro_rate_seekbar_max) },
                step = 1, suffix = " ms", current = { LauncherPreferences.PREF_GYRO_SAMPLE_RATE },
                visible = { LauncherPreferences.PREF_ENABLE_GYRO }),
            PojavSetting.Toggle("gyroSmoothing", R.string.preference_gyro_smoothing_title, R.string.preference_gyro_smoothing_description,
                current = { LauncherPreferences.PREF_GYRO_SMOOTHING }, visible = { LauncherPreferences.PREF_ENABLE_GYRO }),
            PojavSetting.Toggle("gyroInvertX", R.string.preference_gyro_invert_x_axis, R.string.preference_gyro_invert_x_axis_description,
                current = { LauncherPreferences.PREF_GYRO_INVERT_X }, visible = { LauncherPreferences.PREF_ENABLE_GYRO }),
            PojavSetting.Toggle("gyroInvertY", R.string.preference_gyro_invert_y_axis, R.string.preference_gyro_invert_y_axis_description,
                current = { LauncherPreferences.PREF_GYRO_INVERT_Y }, visible = { LauncherPreferences.PREF_ENABLE_GYRO }),
            PojavSetting.Action("remap_controller", R.string.preference_remap_controller_title, R.string.preference_remap_controller_description,
                run = { Tools.swapFragment(it, GamepadMapperFragment::class.java, GamepadMapperFragment.TAG, null) }),
            PojavSetting.Action("wipe_controller", R.string.preference_wipe_controller_title, R.string.preference_wipe_controller_description,
                run = {
                    Remapper.wipePreferences(it)
                    Toast.makeText(it, R.string.preference_controller_map_wiped, Toast.LENGTH_SHORT).show()
                }),
            PojavSetting.Slider("gamepad_deadzone_scale", R.string.preference_deadzone_scale_title, R.string.preference_deadzone_scale_description,
                min = { integer(it, R.integer.gamepad_deadzone_seekbar_min) }, max = { integer(it, R.integer.gamepad_deadzone_seekbar_max) },
                step = 5, suffix = " %", current = { Math.round(LauncherPreferences.PREF_DEADZONE_SCALE * 100) }),
            PojavSetting.Toggle("keyboardAutoPanning", R.string.preference_keyboard_autopan_title, R.string.preference_keyboard_autopan_summary,
                current = { LauncherPreferences.PREF_KEYBOARD_AUTOPANNING })
        )
    )

    val java = PojavSettingCategory(
        id = "java",
        titleRes = R.string.aerix_settings_cat_java,
        iconRes = R.drawable.aerix_ic_java,
        classicScreen = LauncherPreferenceJavaFragment::class.java,
        settings = listOf(
            PojavSetting.Action("install_jre", R.string.multirt_title, R.string.multirt_subtitle,
                run = { open(it, LauncherPreferenceJavaFragment::class.java) }),
            PojavSetting.Slider("allocation", R.string.mcl_memory_allocation, R.string.mcl_memory_allocation_subtitle,
                min = { integer(it, R.integer.memory_seekbar_min) }, max = { maxRam(it) },
                step = 8, suffix = " MB", current = { LauncherPreferences.PREF_RAM_ALLOCATION }),
            PojavSetting.Text("javaArgs", R.string.mcl_setting_title_javaargs, R.string.mcl_setting_subtitle_javaargs,
                current = { LauncherPreferences.PREF_CUSTOM_JAVA_ARGS ?: "" }),
            PojavSetting.Toggle("java_sandbox", R.string.mcl_setting_java_sandbox, R.string.mcl_setting_java_sandbox_subtitle,
                current = { LauncherPreferences.PREF_JAVA_SANDBOX })
        )
    )

    val misc = PojavSettingCategory(
        id = "misc",
        titleRes = R.string.aerix_settings_cat_misc,
        iconRes = R.drawable.aerix_ic_build_outlined,
        classicScreen = LauncherPreferenceMiscellaneousFragment::class.java,
        settings = listOf(
            PojavSetting.Toggle("force_english", R.string.preference_force_english_title, R.string.preference_force_english_description,
                current = { LauncherPreferences.PREF_FORCE_ENGLISH }),
            PojavSetting.Toggle("checkGameFiles", R.string.preference_verify_game_files_title, R.string.preference_verify_game_files_description,
                current = { LauncherPreferences.PREF_VERIFY_FILES }),
            PojavSetting.Toggle("fastStartupCheck", R.string.preference_go_vroom_title, R.string.preference_go_vroom_description,
                current = { LauncherPreferences.PREF_RAPID_START }),
            PojavSetting.Choice("downloadSource", R.string.preference_download_source_title, R.string.preference_download_source_description,
                entries = { downloadSources(it) }, current = { LauncherPreferences.PREF_DOWNLOAD_SOURCE ?: "default" }),
            PojavSetting.Toggle("verifyManifest", R.string.preference_verify_manifest_title, R.string.preference_verify_manifest_description,
                current = { LauncherPreferences.PREF_VERIFY_MANIFEST }),
            PojavSetting.Toggle("showMemoryWarning", R.string.preference_show_memory_warning_title, R.string.preference_show_memory_warning_summary,
                current = { LauncherPreferences.PREF_SHOW_MEMORY_WARNING_DIALOG }),
            PojavSetting.Action("microphoneAccessRequest", R.string.preference_microphone_access_title, R.string.preference_microphone_access_description,
                run = { launcher(it)?.askForPermission(23, Manifest.permission.RECORD_AUDIO) },
                visible = { act -> launcher(act)?.checkForPermissionRationale(33, Manifest.permission.RECORD_AUDIO) == false }),
            PojavSetting.Action("notification_permission_request", R.string.preference_ask_for_notification_title, R.string.preference_ask_for_notification_description,
                run = { launcher(it)?.askForPermission(33, Manifest.permission.POST_NOTIFICATIONS) },
                visible = { act -> launcher(act)?.checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS) == false }),
            PojavSetting.Action("runDataMigration", R.string.preference_data_migration_title, R.string.preference_data_migration_summary,
                run = {
                    if (ProgressKeeper.getTaskCount() > 0) Toast.makeText(it, R.string.tasks_ongoing, Toast.LENGTH_SHORT).show()
                    else open(it, LauncherPreferenceMiscellaneousFragment::class.java)
                }),
            PojavSetting.Action("clearMetadataCache", R.string.preference_metadata_clear, R.string.preference_metadata_clear_summary,
                run = { clearMetadataCache(it) },
                visible = { File(Tools.DIR_CACHE, "string_cache").exists() })
        )
    )

    val experimental = PojavSettingCategory(
        id = "experimental",
        titleRes = R.string.aerix_settings_cat_experimental,
        iconRes = R.drawable.aerix_ic_warning_outlined,
        classicScreen = LauncherPreferenceExperimentalFragment::class.java,
        settings = listOf(
            PojavSetting.Toggle("dump_shaders", R.string.preference_shader_dump_title, R.string.preference_shader_dump_description,
                current = { LauncherPreferences.PREF_DUMP_SHADERS }),
            PojavSetting.Toggle("bigCoreAffinity", R.string.preference_force_big_core_title, R.string.preference_force_big_core_desc,
                current = { LauncherPreferences.PREF_BIG_CORE_AFFINITY }),
            PojavSetting.Toggle("freedrenoSysmem", R.string.preference_sysmem_title, R.string.preference_sysmem_summary,
                current = { LauncherPreferences.PREF_FREEDRENO_SYSMEM }),
            PojavSetting.Toggle("alsoftForceOpenSL", R.string.preference_alsoft_opensl_title, R.string.preference_alsoft_opensl_summary,
                current = { LauncherPreferences.PREF_ALSOFT_FORCE_OPENSL }),
            PojavSetting.Toggle("ubwcWorkaround", R.string.preference_ubwc_title, R.string.preference_ubwc_summary,
                current = { LauncherPreferences.PREF_UBWC_WORKAROUND })
        )
    )

    val categories: List<PojavSettingCategory> = listOf(video, controls, java, misc, experimental)

    /** Pojav's root preference screen (all categories + force English + notification request). */
    val classicRoot: Class<out androidx.fragment.app.Fragment> = LauncherPreferenceFragment::class.java

    fun openClassic(activity: FragmentActivity, category: PojavSettingCategory) = open(activity, category.classicScreen)
}
