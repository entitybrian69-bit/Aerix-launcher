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

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import net.kdt.pojavlaunch.R

/**
 * A real, recorded Minecraft animation that ships inside the APK.
 *
 * These are not drawn - they are footage. Each one is a ten second loop with the end folded back
 * into the start, so it plays continuously with no jump.
 *
 * @param poster a real frame of the clip, shipped small so a card can show the scene the moment the
 *        grid is drawn, without waiting for a video frame to be decoded out of the whole file
 */
data class BundledClip(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val rawRes: Int,
    @DrawableRes val poster: Int
)

/**
 * The twelve animations that ship with the launcher.
 *
 * Encoded at 960x540, 24 fps, H.264 Main profile - which every device has hardware decoding for, so
 * playback costs almost no CPU and the interface stays smooth while a clip plays. The whole set is
 * a little over 5 MB.
 */
val BundledClips: List<BundledClip> = listOf(
    BundledClip("fog_rain", "Misty Rain", "Rainy landscape · fog", Color(0xFF7E9DB8), R.raw.aerix_clip_fog_rain, R.drawable.aerix_clip_poster_fog_rain),
    BundledClip("raindrops", "Raindrops", "Rain on the lens · close", Color(0xFF6E9BC0), R.raw.aerix_clip_raindrops, R.drawable.aerix_clip_poster_raindrops),
    BundledClip("sakura_forest", "Sakura Forest", "Cherry blossom · petals", Color(0xFFF2A6C0), R.raw.aerix_clip_sakura_forest, R.drawable.aerix_clip_poster_sakura_forest),
    BundledClip("dog_wander", "Dog Wander", "Companion · golden hour", Color(0xFFD8A05A), R.raw.aerix_clip_dog_wander, R.drawable.aerix_clip_poster_dog_wander),
    BundledClip("fancy_center", "Fancy Center", "Grand build · warm light", Color(0xFFC8A46E), R.raw.aerix_clip_fancy_center, R.drawable.aerix_clip_poster_fancy_center),
    BundledClip("sunset_farm", "Sunset Farm", "Farmland · evening", Color(0xFFFFA257), R.raw.aerix_clip_sunset_farm, R.drawable.aerix_clip_poster_sunset_farm),
    BundledClip("snowy_campfire", "Snowy Campfire", "Winter camp · firelight", Color(0xFFFF8F4D), R.raw.aerix_clip_snowy_campfire, R.drawable.aerix_clip_poster_snowy_campfire),
    BundledClip("falling_snow", "Falling Snow", "Snowy village · lanterns", Color(0xFFCFE3F5), R.raw.aerix_clip_falling_snow, R.drawable.aerix_clip_poster_falling_snow),
    BundledClip("northern_light", "Northern Light", "Aurora · night sky", Color(0xFF6FE0B8), R.raw.aerix_clip_northern_light, R.drawable.aerix_clip_poster_northern_light),
    BundledClip("portal", "Nether Portal", "Portal glow · night", Color(0xFFB57CFF), R.raw.aerix_clip_portal, R.drawable.aerix_clip_poster_portal),
    BundledClip("nature_spring", "Nature in Spring", "Green landscape · daylight", Color(0xFF7BC96A), R.raw.aerix_clip_nature_spring, R.drawable.aerix_clip_poster_nature_spring),
    BundledClip("sunset_lake", "Sunset Lake", "Golden hour · water", Color(0xFFFFB066), R.raw.aerix_clip_sunset_lake, R.drawable.aerix_clip_poster_sunset_lake)
)

fun bundledClipById(id: String?): BundledClip? = BundledClips.firstOrNull { it.id == id }
