/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The real Minecraft footage that ships with the launcher.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.components

import androidx.compose.ui.graphics.Color
import net.kdt.pojavlaunch.R

/**
 * A real, recorded Minecraft animation that ships inside the APK.
 *
 * These are not drawn - they are footage: a lakeside world rendered at 720p, filmed through a full
 * day and its weather. Each clip is a ten second loop, muted, about a megabyte on average.
 */
data class BundledClip(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val rawRes: Int
)

/** Ten loops of real footage: one world, every light and weather. */
val BundledClips: List<BundledClip> = listOf(
    BundledClip("clear_day", "Clear Day", "Real footage · sunny lake", Color(0xFF63C6F2), R.raw.aerix_clip_day_clear),
    BundledClip("cloudy_day", "Drifting Cloud", "Real footage · overcast", Color(0xFF9FB6C4), R.raw.aerix_clip_day_cloudy),
    BundledClip("rain_day", "Rain on the Lake", "Real footage · rainfall", Color(0xFF6E9BC0), R.raw.aerix_clip_day_rain),
    BundledClip("snow_day", "First Snow", "Real footage · snowfall", Color(0xFFCFE3F5), R.raw.aerix_clip_day_snow),
    BundledClip("clear_noon", "High Noon", "Real footage · midday", Color(0xFFFFD27A), R.raw.aerix_clip_noon_clear),
    BundledClip("clear_night", "Blue Hour", "Real footage · dusk", Color(0xFF7C8BE0), R.raw.aerix_clip_night_clear),
    BundledClip("clear_midnight", "Lantern Glow", "Real footage · midnight", Color(0xFFFF9A4D), R.raw.aerix_clip_midnight_clear),
    BundledClip("cloudy_midnight", "Still Night", "Real footage · calm", Color(0xFF5C6B96), R.raw.aerix_clip_midnight_cloudy),
    BundledClip("rain_midnight", "Night Rain", "Real footage · downpour", Color(0xFF54688F), R.raw.aerix_clip_midnight_rain),
    BundledClip("snow_midnight", "Silent Snowfall", "Real footage · midwinter", Color(0xFFA9C4E0), R.raw.aerix_clip_midnight_snow)
)

fun bundledClipById(id: String?): BundledClip? = BundledClips.firstOrNull { it.id == id }

/**
 * Credit for the footage. It is CC BY-SA 4.0, which requires the author to be named wherever the
 * work is used, so the wallpapers page shows this line.
 */
const val BUNDLED_CLIPS_CREDIT = "Footage: Blaž Jerman · CC BY-SA 4.0"
