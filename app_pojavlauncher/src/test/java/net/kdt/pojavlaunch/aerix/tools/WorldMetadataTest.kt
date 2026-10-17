package net.kdt.pojavlaunch.aerix.tools

import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream

class WorldMetadataTest {
    private fun metadata(write: DataOutputStream.() -> Unit): WorldMetadata {
        val file = File.createTempFile("aerix-level", ".dat")
        try {
            DataOutputStream(GZIPOutputStream(file.outputStream())).use { it.write() }
            return WorldMetadata.read(file)
        } finally { file.delete() }
    }
    @Test fun readsRealDisplayFields() {
        val result = metadata {
            writeByte(10); writeUTF("")
            writeByte(10); writeUTF("Data")
            writeByte(8); writeUTF("LevelName"); writeUTF("Mountain home")
            writeByte(4); writeUTF("LastPlayed"); writeLong(123456789)
            writeByte(10); writeUTF("Version")
            writeByte(8); writeUTF("Name"); writeUTF("1.20.1")
            writeByte(0); writeByte(0); writeByte(0)
        }
        assertEquals(WorldMetadata("Mountain home", 123456789, "1.20.1"), result)
    }
    @Test fun emptyCompoundHasNoInventedFields() {
        assertEquals(WorldMetadata(null, 0, null), metadata { writeByte(10); writeUTF(""); writeByte(0) })
    }
    @Test(expected = IOException::class) fun rejectsNonCompoundRoot() {
        metadata { writeByte(8); writeUTF("invalid") }
    }
    @Test(expected = IOException::class) fun rejectsNegativeArrayLength() {
        metadata { writeByte(10); writeUTF(""); writeByte(7); writeUTF("x"); writeInt(-1) }
    }
    @Test(expected = IOException::class) fun rejectsTruncatedArray() {
        metadata { writeByte(10); writeUTF(""); writeByte(11); writeUTF("x"); writeInt(4); writeInt(1) }
    }
    @Test(expected = IOException::class) fun rejectsDeepCompounds() {
        metadata { writeByte(10); writeUTF(""); repeat(70) { writeByte(10); writeUTF("x") }; repeat(71) { writeByte(0) } }
    }
    @Test fun listContentsCannotSpoofRootDisplayFields() {
        val result = metadata {
            writeByte(10); writeUTF("")
            writeByte(9); writeUTF("unrelated"); writeByte(10); writeInt(1)
            writeByte(10); writeUTF("Data"); writeByte(8); writeUTF("LevelName"); writeUTF("spoofed"); writeByte(0); writeByte(0)
            writeByte(0)
        }
        assertNull(result.name)
    }
}
