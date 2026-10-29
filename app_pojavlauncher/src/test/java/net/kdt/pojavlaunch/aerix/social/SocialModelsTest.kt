package net.kdt.pojavlaunch.aerix.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialModelsTest {

    @Test
    fun formatLastSeenAndRichPresence() {
        val now = 1_700_000_000_000L
        assertEquals("Last seen 2 h ago", SocialFriend.formatLastSeen(now - 2L * 3600_000L, now))
        assertEquals("Last seen 15 m ago", SocialFriend.formatLastSeen(now - 15L * 60_000L, now))
        assertEquals("Last seen yesterday", SocialFriend.formatLastSeen(now - 26L * 3600_000L, now))

        val richOnline = SocialFriend(
            id = "u1",
            mcUsername = "Marie",
            displayName = "Marie",
            lastSeenMs = System.currentTimeMillis() - 15_000L,
            statusLine = "Playing 1.21.4 · 34 mods",
            mcVersion = "1.21.4",
            joinable = true,
            nickname = "Bestie"
        )
        assertTrue(richOnline.online)
        assertEquals("Bestie", richOnline.effectiveName)
        assertEquals("Playing 1.21.4 · 34 mods", richOnline.presenceSummary())
    }

    @Test
    fun partyInviteAndMetaRoundTrip() {
        val packed = SocialEnvelopeCodec.packVersionWithMeta(
            SocialEnvelopeCodec.PackedVersionMeta(
                versionId = "fabric-loader-0.16.9-1.21.4",
                loader = "Fabric",
                modpackSource = "modrinth",
                modpackId = "fabulously-optimized",
                serverAddress = "play.example.net:25565",
                maxMembers = 6,
                isPublic = true
            )
        )
        val unpacked = SocialEnvelopeCodec.unpackVersionWithMeta(packed)
        assertEquals("fabric-loader-0.16.9-1.21.4", unpacked.versionId)
        assertEquals("Fabric", unpacked.loader)
        assertEquals("modrinth", unpacked.modpackSource)
        assertEquals("fabulously-optimized", unpacked.modpackId)
        assertEquals("play.example.net:25565", unpacked.serverAddress)
        assertEquals(6, unpacked.maxMembers)
        assertTrue(unpacked.isPublic)

        val invite = SocialPartyInvite(
            id = "inv-1",
            partyId = "party-uuid",
            fromUserId = "host-uuid",
            fromName = "Marie",
            partyTitle = "Create Co-op",
            instanceName = "Create Pack",
            versionId = "1.21.4",
            mcVersion = "1.21.4",
            loader = "Fabric",
            serverAddress = "mc.marie.dev",
            maxMembers = 4,
            expiresAtMs = 1_900_000_000_000L,
            createdAtMs = 1_899_995_000_000L
        )
        val wire = SocialEnvelopeCodec.encodePartyInvite(invite)
        val decoded = SocialEnvelopeCodec.decodePartyInvite(wire, rowId = 42L)
        assertNotNull(decoded)
        assertEquals("party-uuid", decoded!!.partyId)
        assertEquals("Create Co-op", decoded.partyTitle)
        assertEquals("1.21.4", decoded.mcVersion)
        assertEquals("Fabric", decoded.loader)
        assertEquals("mc.marie.dev", decoded.serverAddress)
        assertEquals(42L, decoded.messageRowId)
    }

    @Test
    fun partyExpiryCalculation() {
        val now = 1_800_000_000_000L
        val active = SocialParty(
            id = "p1",
            hostId = "h1",
            hostName = "Alex",
            title = "Survival",
            mcVersion = "1.21.4",
            loader = "Fabric",
            expiresAtMs = now + 2 * 3600_000L
        )
        assertFalse(active.isExpired(now))
        assertEquals("2h left", active.remainingText(now))
        assertTrue(active.isExpired(now + 2 * 3600_000L + 1L))
    }
}
