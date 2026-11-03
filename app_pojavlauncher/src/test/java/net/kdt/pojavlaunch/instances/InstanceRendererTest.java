package net.kdt.pojavlaunch.instances;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = android.app.Application.class)
public class InstanceRendererTest {
    @Test public void explicitRendererWinsAndFollowGlobalTracksGlobal() {
        String before = LauncherPreferences.PREF_RENDERER;
        try {
            Instance first = new Instance();
            Instance second = new Instance();
            first.renderer = "emesis";
            second.renderer = "opengles_mobileglues";
            LauncherPreferences.PREF_RENDERER = "opengles2";
            assertEquals("emesis", first.getLaunchRenderer());
            assertEquals("opengles_mobileglues", second.getLaunchRenderer());
            LauncherPreferences.PREF_RENDERER = "vulkan_zink";
            assertEquals("emesis", first.getLaunchRenderer());
            assertEquals("opengles_mobileglues", second.getLaunchRenderer());
            second.renderer = null;
            assertEquals("vulkan_zink", second.getLaunchRenderer());
            second.renderer = "";
            assertEquals("vulkan_zink", second.getLaunchRenderer());
        } finally {
            LauncherPreferences.PREF_RENDERER = before;
        }
    }
}
