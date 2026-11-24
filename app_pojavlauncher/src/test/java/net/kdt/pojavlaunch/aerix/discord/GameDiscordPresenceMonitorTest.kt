package net.kdt.pojavlaunch.aerix.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

class GameDiscordPresenceMonitorTest {

    @Test
    fun serverIconUrlRejectsLocalAndFormatsPublic() {
        assertEquals(
            "https://api.mcsrvstat.us/icon/mc.hypixel.net",
            GameDiscordPresenceMonitor.serverIconUrlFor("mc.hypixel.net.:25565")
        )
        assertEquals(
            "https://api.mcsrvstat.us/icon/play.example.com:25566",
            GameDiscordPresenceMonitor.serverIconUrlFor("play.example.com:25566")
        )
        assertNull(GameDiscordPresenceMonitor.serverIconUrlFor("localhost:25565"))
        assertNull(GameDiscordPresenceMonitor.serverIconUrlFor("127.0.0.1:25565"))
        assertNull(GameDiscordPresenceMonitor.serverIconUrlFor("192.168.1.15:25565"))
        assertNull(GameDiscordPresenceMonitor.serverIconUrlFor("10.0.0.5"))
        assertNull(GameDiscordPresenceMonitor.serverIconUrlFor("172.16.4.2"))
        assertTrue(GameDiscordPresenceMonitor.isLocalOrLanAddress("192.168.0.10:25565"))
        assertFalse(GameDiscordPresenceMonitor.isLocalOrLanAddress("donutsmp.net"))
    }

    @Test
    fun parsesMinecraftServersDatNbtAndStripsColorCodes() {
        val tempDir = Files.createTempDirectory("aerix-servers-test").toFile()
        try {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { dos ->
                // Root TAG_Compound ""
                dos.writeByte(10)
                dos.writeUTF("")
                // TAG_List "servers" of TAG_Compound (2 entries)
                dos.writeByte(9)
                dos.writeUTF("servers")
                dos.writeByte(10)
                dos.writeInt(2)

                // Entry 1: Hypixel with color codes & default port
                dos.writeByte(8)
                dos.writeUTF("name")
                dos.writeUTF("§6§lHypixel §rNetwork")
                dos.writeByte(8)
                dos.writeUTF("ip")
                dos.writeUTF("mc.hypixel.net:25565")
                dos.writeByte(0)

                // Entry 2: Custom port server
                dos.writeByte(8)
                dos.writeUTF("name")
                dos.writeUTF("My SMP")
                dos.writeByte(8)
                dos.writeUTF("ip")
                dos.writeUTF("play.mysmp.net:25570")
                dos.writeByte(0)

                dos.writeByte(0) // End root
            }
            File(tempDir, "servers.dat").writeBytes(out.toByteArray())

            val names = GameDiscordPresenceMonitor.readServerListNames(tempDir)
            assertEquals("Hypixel Network", names["mc.hypixel.net"])
            assertEquals("My SMP", names["play.mysmp.net:25570"])
            assertEquals("My SMP", names["play.mysmp.net"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun logTrackerTracksMenuServerSingleplayerAndWorldShare() {
        val tracker = GameDiscordPresenceMonitor.LogActivityTracker(
            mapOf("mc.hypixel.net" to "Hypixel")
        )
        assertEquals(GameActivityStage.LAUNCHING, tracker.snapshot(false).stage)

        tracker.onLogLine("[12:00:01] [Render thread/INFO]: OpenAL initialized on device OpenAL Soft")
        assertEquals(GameActivityStage.MAIN_MENU, tracker.snapshot(false).stage)

        tracker.onLogLine("[12:00:10] [Render thread/INFO]: Connecting to mc.hypixel.net., 25565")
        val connecting = tracker.snapshot(false)
        assertEquals(GameActivityStage.CONNECTING_SERVER, connecting.stage)
        assertEquals("Hypixel", connecting.displayServerName())
        assertEquals("https://api.mcsrvstat.us/icon/mc.hypixel.net", connecting.serverIconUrl)

        tracker.onLogLine("[12:00:12] [Render thread/INFO]: [CHAT] Joined the game")
        tracker.onLogLine("[12:00:12] [Render thread/INFO]: Loaded 12 advancements")
        assertEquals(GameActivityStage.IN_SERVER, tracker.snapshot(false).stage)

        tracker.onLogLine("[12:00:25] [Render thread/INFO]: ResourceKey[minecraft:dimension / minecraft:the_nether]")
        assertEquals("The Nether", tracker.snapshot(true).dimension)
        assertTrue(tracker.snapshot(true).isRecording)

        tracker.onLogLine("[12:01:00] [Render thread/INFO]: Disconnected from server")
        val backToMenu = tracker.snapshot(false)
        assertEquals(GameActivityStage.MAIN_MENU, backToMenu.stage)
        assertNull(backToMenu.serverAddress)
        assertNull(backToMenu.serverIconUrl)

        tracker.onLogLine("[12:01:10] [Server thread/INFO]: Starting integrated minecraft server")
        tracker.onLogLine("""[12:01:11] [Server thread/INFO]: Preparing level "Hardcore Base"""")
        tracker.onLogLine("[12:01:12] [Server thread/INFO]: Preparing spawn area: 65%")
        val loading = tracker.snapshot(false)
        assertEquals(GameActivityStage.LOADING_WORLD, loading.stage)
        assertEquals("Hardcore Base", loading.worldName)
        assertEquals(65, loading.loadingPercent)

        tracker.onLogLine("[12:01:15] [Server thread/INFO]: Saving chunks for level 'ServerLevel[Hardcore Base]'/minecraft:overworld")
        val inWorld = tracker.snapshot(false)
        assertEquals(GameActivityStage.IN_WORLD, inWorld.stage)
        assertEquals("Singleplayer: Hardcore Base", inWorld.activityLine())
    }

    @Test
    fun formatsSuperDetailedPresenceLines() {
        val packMeta = GameInstanceMeta(
            instanceName = "Mx Boost",
            mcVersion = "1.21.1",
            loaderName = "Fabric",
            modCount = 48,
            modpackName = "Mx Boost",
            modpackIconUrl = "https://cdn.modrinth.com/mx-boost.png",
            modpackPageUrl = "https://modrinth.com/modpack/mx-boost"
        )
        val onServer = LiveGameActivityState(
            stage = GameActivityStage.IN_SERVER,
            serverAddress = "mc.hypixel.net",
            serverName = "Hypixel",
            serverIconUrl = "https://api.mcsrvstat.us/icon/mc.hypixel.net",
            isRecording = true
        )
        val serverCard = AerixDiscordPresence.formatInGamePresence(packMeta, onServer)
        assertEquals("Playing on Hypixel · Mx Boost", serverCard.details)
        assertEquals("mc.hypixel.net · Fabric 1.21.1 · 48 mods · ● Recording", serverCard.state)

        val singleplayer = LiveGameActivityState(
            stage = GameActivityStage.IN_WORLD,
            worldName = "Survival",
            dimension = "The End",
            isRecording = false
        )
        val spCard = AerixDiscordPresence.formatInGamePresence(packMeta, singleplayer)
        assertEquals("Singleplayer: Survival · Mx Boost", spCard.details)
        assertEquals("Fabric 1.21.1 · 48 mods · The End", spCard.state)
    }
}
