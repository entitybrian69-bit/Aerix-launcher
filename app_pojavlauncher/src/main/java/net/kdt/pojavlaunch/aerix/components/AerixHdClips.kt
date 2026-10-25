/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The high-resolution clip pack: five 4K/1440p/1080p Minecraft loops that are downloaded on demand
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 */
data class HdClip(
    val id: String,
    val title: String,
    val subtitle: String,
    val resolution: String,
    val bytes: Long,
    val sha256: String,
    val accent: Color,
    val file: String
) {
    val megabytes: String get() = String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}

/**
 * The five clips of the high-resolution pack.
 *
 * These are the original files from the artist - 3840x2160, 2560x1440 and 1920x1080, at 11 to 38
 * Mbps. Bundling them would add about 50 MB to every download of the launcher, so they are fetched
 * the first time they are picked and then live in the same folder as imported clips. Full quality,
 * just not on everybody's disk by default.
 */
object AerixHdClips {
    private const val BASE = "https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/clips-hd-v1/"

    val clips: List<HdClip> = listOf(
        HdClip(
            id = "hd_autumn_mountains",
            title = "Autumn Mountains",
            subtitle = "Golden valley · 4K",
            resolution = "3840 × 2160",
            bytes = 19_454_441L,
            sha256 = "a5b02b28d5c66bca5fe0698c8b337cb9190021b8661b3a219fe0563f6d099183",
            accent = Color(0xFFE8A85C),
            file = "aerix_hd_autumn_mountains.mp4"
        ),
        HdClip(
            id = "hd_butterfly_garden",
            title = "Butterfly Garden",
            subtitle = "Sunlit garden · 1440p",
            resolution = "2560 × 1440",
            bytes = 11_283_759L,
            sha256 = "091d59ec92847292af5189a7a56f7af2d04eda4d3914ca9b82f2fdd26cb711ce",
            accent = Color(0xFF8FD46A),
            file = "aerix_hd_butterfly_garden.mp4"
        ),
        HdClip(
            id = "hd_sakura_field",
            title = "Sakura Field",
            subtitle = "Blossom field · 1440p",
            resolution = "2560 × 1440",
            bytes = 9_647_475L,
            sha256 = "9a7ea94afa614dcff898c26ab93bd079d5706c7953584473416ed550c5471763",
            accent = Color(0xFFF2A6C0),
            file = "aerix_hd_sakura_field.mp4"
        ),
        HdClip(
            id = "hd_night_sky",
            title = "Night Sky",
            subtitle = "Stars over the hills · 1080p",
            resolution = "1920 × 1080",
            bytes = 6_193_350L,
            sha256 = "baf0704f32fad699e420a8ff005f2133db19425d3f8d31291da5006f938f4297",
            accent = Color(0xFF7C8CF8),
            file = "aerix_hd_night_sky.mp4"
        ),
        HdClip(
            id = "hd_wisteria_lake",
            title = "Wisteria Lake",
            subtitle = "Lake at night · 1080p",
            resolution = "1920 × 1080",
            bytes = 5_457_526L,
            sha256 = "1df295eb0d88e053bbb8d3aa6273d6732b79e0cbd1374699b406bdcf3825441f",
            accent = Color(0xFFB48CF2),
            file = "aerix_hd_wisteria_lake.mp4"
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
