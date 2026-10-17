package net.kdt.pojavlaunch.aerix.tools

import java.io.DataInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Reads only the small display fields needed by world cards. */
internal data class WorldMetadata(val name: String?, val lastPlayed: Long, val version: String?) {
    companion object {
        fun read(file: File): WorldMetadata {
            var name: String? = null
            var played = 0L
            var version: String? = null
            GZIPInputStream(file.inputStream().buffered()).use { compressed ->
                val limited = object : FilterInputStream(compressed) {
                    var remaining = 16L * 1024 * 1024
                    override fun read(): Int {
                        if (--remaining < 0) throw IOException("World metadata exceeds 16 MB")
                        return super.read()
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (remaining <= 0) throw IOException("World metadata exceeds 16 MB")
                        val n = `in`.read(b, off, minOf(len.toLong(), remaining).toInt())
                        if (n > 0) remaining -= n
                        return n
                    }
                    override fun skip(n: Long): Long {
                        val buffer = ByteArray(minOf(n, 8192L).toInt())
                        var count = 0L
                        while (count < n) {
                            val read = read(buffer, 0, minOf(buffer.size.toLong(), n - count).toInt())
                            if (read < 0) break
                            count += read
                        }
                        return count
                    }
                }
                val input = DataInputStream(limited)
                fun length(): Int = input.readInt().also { if (it !in 0..1_000_000) throw IOException("Invalid NBT length") }
                fun skipFully(bytes: Long) {
                    var remaining = bytes
                    while (remaining > 0) {
                        val skipped = input.skip(remaining)
                        if (skipped <= 0) throw IOException("Truncated world metadata")
                        remaining -= skipped
                    }
                }
                fun tag(type: Int, path: String, depth: Int) {
                    if (depth > 64) throw IOException("World metadata is nested too deeply")
                    when (type) {
                        1 -> input.readByte()
                        2 -> input.readShort()
                        3 -> input.readInt()
                        4 -> input.readLong().also { if (path == "Data.LastPlayed") played = it }
                        5 -> input.readFloat()
                        6 -> input.readDouble()
                        7 -> skipFully(length().toLong())
                        8 -> input.readUTF().also {
                            if (path == "Data.LevelName") name = it.take(128)
                            if (path == "Data.Version.Name") version = it.take(80)
                        }
                        9 -> {
                            val element = input.readUnsignedByte()
                            val count = length()
                            if (element == 0 && count != 0) throw IOException("Invalid NBT list")
                            repeat(count) { tag(element, "#", depth + 1) }
                        }
                        10 -> {
                            var count = 0
                            while (true) {
                                val child = input.readUnsignedByte()
                                if (child == 0) break
                                if (++count > 100_000) throw IOException("Too many NBT fields")
                                val key = input.readUTF()
                                val next = when {
                                    path.isEmpty() && key == "Data" -> "Data"
                                    path == "Data" && key in setOf("LevelName", "LastPlayed", "Version") -> "Data.$key"
                                    path == "Data.Version" && key == "Name" -> "Data.Version.Name"
                                    else -> "#"
                                }
                                tag(child, next, depth + 1)
                            }
                        }
                        11 -> skipFully(length().toLong() * 4)
                        12 -> skipFully(length().toLong() * 8)
                        else -> throw IOException("Unknown NBT tag $type")
                    }
                }
                if (input.readUnsignedByte() != 10) throw IOException("World metadata has no root compound")
                input.readUTF()
                tag(10, "", 0)
            }
            return WorldMetadata(name, played, version)
        }
    }
}
