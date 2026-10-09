package net.kdt.pojavlaunch.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MinecraftServerListStoreTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void savesAndReloadsVanillaCompressedServerList() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "servers.dat");
        List<MinecraftServerListStore.ServerEntry> entries = new ArrayList<>();
        entries.add(new MinecraftServerListStore.ServerEntry("Aerix SMP", "play.example.net:25565"));

        MinecraftServerListStore.save(file, entries);

        byte[] header = new byte[2];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            assertEquals(2, input.read(header));
        }
        assertEquals(0x1f, header[0] & 0xff);
        assertEquals(0x8b, header[1] & 0xff);

        List<MinecraftServerListStore.ServerEntry> loaded = MinecraftServerListStore.load(file);
        assertEquals(1, loaded.size());
        assertEquals("Aerix SMP", loaded.get(0).name);
        assertEquals("play.example.net:25565", loaded.get(0).address);

        loaded.get(0).name = "Updated name";
        loaded.add(new MinecraftServerListStore.ServerEntry("Local world", "192.168.1.20"));
        MinecraftServerListStore.save(file, loaded);
        loaded = MinecraftServerListStore.load(file);
        assertEquals(2, loaded.size());
        assertEquals("Updated name", loaded.get(0).name);
        assertEquals("192.168.1.20", loaded.get(1).address);
        assertFalse(new File(file.getPath() + ".aerix.tmp").exists());
        assertFalse(new File(file.getPath() + ".aerix.bak").exists());
    }

    @Test
    public void doesNotReplaceMalformedServerList() throws Exception {
        File file = temporaryFolder.newFile("servers.dat");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        byte[] original = java.nio.file.Files.readAllBytes(file.toPath());

        try {
            MinecraftServerListStore.save(file,
                    java.util.Collections.singletonList(new MinecraftServerListStore.ServerEntry("Test", "example.net")));
            fail("Expected malformed NBT to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("root") || expected.getMessage().contains("truncated"));
        }

        assertTrue(java.util.Arrays.equals(original, java.nio.file.Files.readAllBytes(file.toPath())));
    }
}
