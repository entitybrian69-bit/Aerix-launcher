package net.kdt.pojavlaunch.aerix.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun ownerAndCoOwnerEmailBadgesAndAntiSpoof() {
        assertEquals(SocialRole.OWNER, SocialRole.fromEmail("entitybrian69@gmail.com"))
        assertEquals(SocialRole.OWNER, SocialRole.fromEmail("  EntityBrian69@Gmail.com "))
        assertEquals("(OWNER)", SocialRole.OWNER.tag)
        assertEquals(0xFFEF233CL, SocialRole.OWNER.colorArgb)

        assertEquals(SocialRole.CO_OWNER, SocialRole.fromEmail("kushaladhikari982648@gmail.com"))
        assertEquals(SocialRole.CO_OWNER, SocialRole.fromEmail("KushalAdhikari982648@gmail.com"))
        assertEquals("(CO-OWNER)", SocialRole.CO_OWNER.tag)
        assertEquals(0xFF38BDF8L, SocialRole.CO_OWNER.colorArgb)

        assertNull(SocialRole.fromEmail("someoneelse@gmail.com"))

        // Any custom name chosen by OWNER / CO-OWNER gets tagged cleanly and extracts cleanly for others
        val ownerWire = SocialRole.encodeTaggedName("ShadowBrian", SocialRole.OWNER, "Brian")
        assertEquals("ShadowBrian (OWNER)", ownerWire)
        val (ownerClean, ownerExtracted) = SocialRole.extract(ownerWire)
        assertEquals("ShadowBrian", ownerClean)
        assertEquals(SocialRole.OWNER, ownerExtracted)

        val coOwnerWire = SocialRole.encodeTaggedName("KushalX", SocialRole.CO_OWNER, "Kushal")
        assertEquals("KushalX (CO-OWNER)", coOwnerWire)
        val (coClean, coExtracted) = SocialRole.extract(coOwnerWire)
        assertEquals("KushalX", coClean)
        assertEquals(SocialRole.CO_OWNER, coExtracted)

        // Non-owners trying to type (OWNER) or (CO-OWNER) have the tag stripped on save
        assertEquals("Impostor", SocialRole.encodeTaggedName("Impostor (OWNER)", null))
        assertEquals("Impostor", SocialRole.encodeTaggedName("Impostor (CO-OWNER)", null))
    }

    @Test
    fun presenceModesAndCustomStatusRoundTrip() {
        assertEquals("[DND] Studying", SocialPresenceCodec.encodeForWire(SocialPresenceMode.DND, "Studying"))
        assertEquals("[DND]", SocialPresenceCodec.encodeForWire(SocialPresenceMode.DND, ""))
        assertEquals("[Idle] AFK / BRB", SocialPresenceCodec.encodeForWire(SocialPresenceMode.IDLE, "AFK / BRB"))
        assertEquals("Down to play", SocialPresenceCodec.encodeForWire(SocialPresenceMode.ONLINE, "Down to play"))
        assertNull(SocialPresenceCodec.encodeForWire(SocialPresenceMode.INVISIBLE, "Secret"))

        val parsedDnd = SocialPresenceCodec.parse("[DND] Studying")
        assertEquals(SocialPresenceMode.DND, parsedDnd.mode)
        assertEquals("Studying", parsedDnd.customText)

        val parsedIdle = SocialPresenceCodec.parse("[Idle] AFK / BRB")
        assertEquals(SocialPresenceMode.IDLE, parsedIdle.mode)
        assertEquals("AFK / BRB", parsedIdle.customText)

        val now = 1_800_000_000_000L
        val dndFriend = SocialFriend(
            id = "u2",
            mcUsername = "Kushal",
            displayName = "Kushal",
            lastSeenMs = now - 10_000L,
            statusLine = parsedDnd.customText,
            presenceMode = parsedDnd.mode,
            role = SocialRole.CO_OWNER
        )
        assertTrue(dndFriend.isOnlineAt(now))
        assertEquals("Do Not Disturb · Studying", dndFriend.presenceSummary(now))

        val invisibleFriend = dndFriend.copy(presenceMode = SocialPresenceMode.INVISIBLE)
        assertFalse(invisibleFriend.isOnlineAt(now))
    }

    @Test
    fun smartAutomaticPresenceAndManualOverride() {
        // 1. Auto mode + Launcher in foreground + Wi-Fi on -> Green (ONLINE)
        assertEquals(
            SocialPresenceMode.ONLINE,
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = true,
                isAutoPresence = true,
                isInForeground = true,
                isPlayingGame = false,
                manualMode = SocialPresenceMode.DND
            )
        )

        // 2. Auto mode + Launcher in background + Wi-Fi on -> Yellow (IDLE)
        assertEquals(
            SocialPresenceMode.IDLE,
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = true,
                isAutoPresence = true,
                isInForeground = false,
                isPlayingGame = false,
                manualMode = SocialPresenceMode.ONLINE
            )
        )

        // 3. Auto mode + Launcher in background while actively playing Minecraft (:game) -> Green (ONLINE)
        assertEquals(
            SocialPresenceMode.ONLINE,
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = true,
                isAutoPresence = true,
                isInForeground = false,
                isPlayingGame = true,
                manualMode = SocialPresenceMode.IDLE
            )
        )

        // 4. Wi-Fi turned off -> Offline immediately (null) in both Auto and Manual modes
        assertNull(
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = false,
                isAutoPresence = true,
                isInForeground = true,
                isPlayingGame = false,
                manualMode = SocialPresenceMode.ONLINE
            )
        )
        assertNull(
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = false,
                isAutoPresence = false,
                isInForeground = true,
                isPlayingGame = false,
                manualMode = SocialPresenceMode.DND
            )
        )

        // 5. Manual mode with Wi-Fi on -> respects user's chosen manual status
        assertEquals(
            SocialPresenceMode.DND,
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = true,
                isAutoPresence = false,
                isInForeground = true,
                isPlayingGame = false,
                manualMode = SocialPresenceMode.DND
            )
        )
        assertEquals(
            SocialPresenceMode.INVISIBLE,
            SocialPresenceCodec.resolveEffectiveMode(
                isNetworkAvailable = true,
                isAutoPresence = false,
                isInForeground = true,
                isPlayingGame = false,
                manualMode = SocialPresenceMode.INVISIBLE
            )
        )
    }

    @Test
    fun optimisticMessageAndSeenReceiptTransitions() {
        val optimistic = SocialMessage(
            id = -1_000_001L,
            sender = "me",
            recipient = "peer",
            body = "yo hop on",
            atMs = 1_800_000_000_000L,
            localTempId = "tmp-1"
        )
        assertFalse(optimistic.isRead)
        assertFalse(optimistic.pendingOffline)

        val confirmed = optimistic.copy(id = 42L)
        assertEquals("tmp-1", confirmed.localTempId)
        assertFalse(confirmed.isRead)

        val seen = confirmed.copy(readAtMs = 1_800_000_001_500L)
        assertTrue(seen.isRead)

        assertEquals("dm:peer", SocialRealtime.dmTypingKey("peer"))
        assertEquals("party:p1", SocialRealtime.partyTypingKey("p1"))
    }
}
