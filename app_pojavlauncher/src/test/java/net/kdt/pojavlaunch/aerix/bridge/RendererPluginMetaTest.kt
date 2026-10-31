package net.kdt.pojavlaunch.aerix.bridge

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plugin discovery has to accept the manifests real renderer APKs ship.
 *
 * The PowerVR build (`np.proton.renderer.powervr`) declares no launcher activity and no code, only
 * renderer meta-data, so it reaches the scanner through getInstalledApplications and has to be
 * recognised from that meta-data alone. Emesis declares several markers at once.
 */
@RunWith(RobolectricTestRunner::class)
class RendererPluginMetaTest {

    private fun meta(vararg pairs: Pair<String, Any>): Bundle = Bundle().apply {
        pairs.forEach { (key, value) ->
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }
    }

    @Test
    fun `power vr style manifest is a legacy renderer plugin`() {
        val bundle = meta(
            "zalithRendererPlugin" to true,
            "des" to "PowerVR Unified r2 (experimental)",
            "renderer" to "opengles3:libpowervr.so:libEGL.so",
            "pojavEnv" to "LIBGL_ES=3:POJAV_RENDERER=opengles3"
        )
        assertEquals("zalithRendererPlugin", PojavPluginBridge.markerForMeta(bundle, "np.proton.renderer.powervr"))
    }

    @Test
    fun `emesis style manifest is matched by its first marker`() {
        val bundle = meta(
            "fclPlugin" to true,
            "zalithRendererPlugin" to true,
            "pgwRendererPlugin" to true,
            "renderer" to "EmesisRenderer:libemesis_renderer.so:libemesis_renderer.so"
        )
        assertEquals("fclPlugin", PojavPluginBridge.markerForMeta(bundle, "com.entitybrian.emesis"))
    }

    @Test
    fun `marker written as a string still counts`() {
        // Hand-written manifests sometimes store the flag as the string "true"; Bundle.getBoolean
        // reports false for those, which used to hide the plugin.
        val bundle = meta("zalithRendererPlugin" to "true")
        assertEquals("zalithRendererPlugin", PojavPluginBridge.markerForMeta(bundle, "com.example.renderer"))
    }

    @Test
    fun `renderer contract without a marker is still a plugin`() {
        val bundle = meta(
            "renderer" to "opengles3:libfoo.so:libEGL.so",
            "pojavEnv" to "LIBGL_ES=3"
        )
        assertEquals("rendererMetadata", PojavPluginBridge.markerForMeta(bundle, "com.example.renderer"))
    }

    @Test
    fun `an ordinary app with meta-data is not a plugin`() {
        val bundle = meta("com.google.android.gms.version" to 12451000)
        assertNull(PojavPluginBridge.markerForMeta(bundle, "com.example.app"))
    }

    @Test
    fun `fcl v2 config is recognised`() {
        val bundle = meta("fclPlugin_V2" to 2131689500)
        assertEquals("fclPlugin_V2", PojavPluginBridge.markerForMeta(bundle, "com.example.v2"))
    }
}
