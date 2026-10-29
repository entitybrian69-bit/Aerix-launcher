/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The high-resolution clip pack: six 4K/1440p/1080p Minecraft loops that are downloaded on demand
 * instead of shipped inside the APK.
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

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One clip of the high-resolution pack.
 *
 * @param file name it is saved under, which is also its file name in the pack
 * @param sha256 of the exact published file, checked after the download so what lands on the phone
 *        is byte for byte the original. Nothing is re-encoded, here or anywhere else.
 * @param poster a real frame of the clip, shipped in the APK so the card shows the actual scene
 *        before the clip itself has been downloaded
 */
data class HdClip(
    val id: String,
    val title: String,
    val subtitle: String,
    val resolution: String,
    val seconds: String,
    val bytes: Long,
    val sha256: String,
    val accent: Color,
    val file: String,
    @DrawableRes val poster: Int
) {
    val megabytes: String get() = String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}

/**
 * The six clips of the high-resolution pack.
 *
 * Each is a one to two second cut of the artist's original file - 3840x2160, 2560x1440 or
 * 1920x1080, at the original bitrate - taken on keyframes with the container rewritten and the
 * packets copied, so not a single frame is re-encoded. The cut point of each was picked by
 * comparing candidate keyframes and keeping the pair whose first and last frames match most
 * closely, which is what makes the loop look continuous instead of jumping.
 *
 * Bundling the six would add about 21 MB to every download of the launcher, so they are fetched
 * the first time one is picked and then live in the same folder as imported clips.
 */
object AerixHdClips {
    private const val BASE = "https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/clips-hd-v2/"

    val clips: List<HdClip> = listOf(
        HdClip(
            id = "hd_autumn_mountains",
            title = "Autumn Mountains",
            subtitle = "Golden valley · 4K",
            resolution = "3840 × 2160",
            seconds = "1.5 s",
            bytes = 8_066_100L,
            sha256 = "2211b49b7a740ee009cecd9d466db0dada880e8cbf5cc684080a5f87d0322ba4",
            accent = Color(0xFFE8A85C),
            file = "aerix_hd_autumn_mountains.mp4",
            poster = R.drawable.aerix_hd_poster_autumn_mountains
        ),
        HdClip(
            id = "hd_butterfly_garden",
            title = "Butterfly Garden",
            subtitle = "Sunlit garden · 1440p",
            resolution = "2560 × 1440",
            seconds = "2.0 s",
            bytes = 2_085_703L,
            sha256 = "1caf4afe29b81e891f1299cb854b6b6d0d38797cd2044544ef09f9255949f9de",
            accent = Color(0xFF8FD46A),
            file = "aerix_hd_butterfly_garden.mp4",
            poster = R.drawable.aerix_hd_poster_butterfly_garden
        ),
        HdClip(
            id = "hd_sakura_field",
            title = "Sakura Field",
            subtitle = "Blossom field · 1440p",
            resolution = "2560 × 1440",
            seconds = "2.0 s",
            bytes = 5_782_883L,
            sha256 = "b67808d64c0563f3226bc538ed9f8ab6a4aeb98e81ed8c9083dbf8f4a44f389a",
            accent = Color(0xFFF2A6C0),
            file = "aerix_hd_sakura_field.mp4",
            poster = R.drawable.aerix_hd_poster_sakura_field
        ),
        HdClip(
            id = "hd_flower_forest_train",
            title = "Flower Forest Train",
            subtitle = "Train in bloom · 1440p",
            resolution = "2560 × 1440",
            seconds = "1.0 s",
            bytes = 4_017_656L,
            sha256 = "2e62c1b6c49dd2310ad569add0e5d1e7243605f732c88618e6eb7382bac46d27",
            accent = Color(0xFFE3C46A),
            file = "aerix_hd_flower_forest_train.mp4",
            poster = R.drawable.aerix_hd_poster_flower_forest_train
        ),
        HdClip(
            id = "hd_night_sky",
            title = "Night Sky",
            subtitle = "Stars over the hills · 1080p",
            resolution = "1920 × 1080",
            seconds = "2.0 s",
            bytes = 983_130L,
            sha256 = "83598820eaa023f1ce8eece6c04bb3002d5811ff1d506f9e8f3a778360325052",
            accent = Color(0xFF7C8CF8),
            file = "aerix_hd_night_sky.mp4",
            poster = R.drawable.aerix_hd_poster_night_sky
        ),
        HdClip(
            id = "hd_wisteria_lake",
            title = "Wisteria Lake",
            subtitle = "Lake at night · 1080p",
            resolution = "1920 × 1080",
            seconds = "2.0 s",
            bytes = 1_138_012L,
            sha256 = "0ca5ff61234698b2621fc465c266330585c645b215214313ee32380212c23d28",
            accent = Color(0xFFB48CF2),
            file = "aerix_hd_wisteria_lake.mp4",
            poster = R.drawable.aerix_hd_poster_wisteria_lake
        )
    )

    val totalBytes: Long = clips.sumOf { it.bytes }

    /** Which clip is downloading, so its card can show the bar. Null when nothing is. */
    var downloadingId by mutableStateOf<String?>(null)
        private set
    /** 0..1 of the current download. */
    var progress by mutableFloatStateOf(0f)
        private set
    /** Set when the last attempt failed; the card offers to try again. */
    var failure by mutableStateOf<String?>(null)
        private set

    fun file(context: Context, clip: HdClip): File = File(AerixThemeManager.clipDir(context), clip.file)

    fun isDownloaded(context: Context, clip: HdClip): Boolean = file(context, clip).let { it.isFile && it.length() == clip.bytes }

    /**
     * Fetches one clip into the imported-clips folder, verifying the checksum.
     *
     * Downloads to a temporary name first and renames at the end, so a clip that is interrupted
     * never appears in the grid half-written.
     *
     * @return the saved file, or null when it failed
     */
    suspend fun download(context: Context, clip: HdClip): File? = withContext(Dispatchers.IO) {
        val target = file(context, clip)
        if (target.isFile && target.length() == clip.bytes) return@withContext target
        downloadingId = clip.id
        progress = 0f
        failure = null
        val partial = File(target.parentFile, "${clip.file}.part")
        try {
            target.parentFile?.mkdirs()
            val connection = (URL(BASE + clip.file).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AerixLauncher")
            }
            try {
                require(connection.responseCode in 200..299) { "The server answered ${connection.responseCode}" }
                val digest = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            progress = (written.toFloat() / clip.bytes).coerceIn(0f, 1f)
                        }
                        require(written == clip.bytes) { "The download was incomplete" }
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                require(hex == clip.sha256) { "The file did not match its checksum" }
            } finally {
                connection.disconnect()
            }
            if (target.exists()) target.delete()
            require(partial.renameTo(target)) { "The clip could not be saved" }
            progress = 1f
            target
        } catch (e: Exception) {
            partial.delete()
            failure = e.message ?: "The download failed"
            null
        } finally {
            downloadingId = null
        }
    }

    fun clearFailure() {
        failure = null
    }
}
