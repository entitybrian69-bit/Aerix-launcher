package net.kdt.pojavlaunch.aerix.bridge

import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.authenticator.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SkinBridgeTest {
    @Test
    fun offlineAccountReplacesNilUuidAndZeroToken() {
        val legacyOffline = Account().apply {
            username = "sahil"
            authType = AuthType.LOCAL
            profileId = "00000000-0000-0000-0000-000000000000"
            accessToken = "0"
        }

        val expectedUuid = UUID.nameUUIDFromBytes("OfflinePlayer:sahil".toByteArray(Charsets.UTF_8)).toString()
        assertEquals(expectedUuid, PojavAccountBridge.effectiveProfileId(legacyOffline))
        assertNotEquals("00000000-0000-0000-0000-000000000000", PojavAccountBridge.effectiveProfileId(legacyOffline))

        val token = PojavAccountBridge.effectiveAccessToken(legacyOffline)
        assertNotEquals("0", token)
        assertEquals(32, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun microsoftAccountPreservesOfficialUuidAndToken() {
        val msa = Account().apply {
            username = "Notch"
            authType = AuthType.MICROSOFT
            profileId = "069a79f4-44e9-4726-a5be-fca90e38aaf5"
            accessToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9"
        }

        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", PojavAccountBridge.effectiveProfileId(msa))
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9", PojavAccountBridge.effectiveAccessToken(msa))
    }

    @Test
    fun skinServerStripsRedirectedServicePrefixesCleanly() {
        assertEquals(
            "https://api.minecraftservices.com" to "/minecraft/profile/lookup/name/sahil",
            AerixSkinServer.resolveUpstreamTarget("/minecraftservices/minecraft/profile/lookup/name/sahil")
        )
        assertEquals(
            "https://sessionserver.mojang.com" to "/session/minecraft/profile/069a79f444e94726a5befca90e38aaf5",
            AerixSkinServer.resolveUpstreamTarget("/sessionserver/session/minecraft/profile/069a79f444e94726a5befca90e38aaf5")
        )
        assertEquals(
            "https://api.mojang.com" to "/profiles/minecraft",
            AerixSkinServer.resolveUpstreamTarget("/api/profiles/minecraft")
        )
        assertEquals(
            "https://authserver.mojang.com" to "/validate",
            AerixSkinServer.resolveUpstreamTarget("/authserver/validate")
        )
    }
}
