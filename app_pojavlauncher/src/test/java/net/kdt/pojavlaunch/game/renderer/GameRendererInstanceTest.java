package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import java.util.Map;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import net.kdt.pojavlaunch.Logger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = android.app.Application.class, shadows = GameRendererInstanceTest.ShadowLogger.class)
public class GameRendererInstanceTest {
    @Implements(Logger.class)
    public static class ShadowLogger {
        @Implementation public static void appendToLog(String text) { }
    }
    private static class FakeRenderer implements RenderSpec {
        private final String path;
        FakeRenderer(String path) { this.path = path; }
        public boolean compatibleDevice(Context context) { return true; }
        public String name() { return "test"; }
        public int displayName() { return 0; }
        public String tag() { return "test"; }
        public String library() { return "test"; }
        public String glLibraryPath() { return path; }
        public void setupEnvironment(Context context, Map<String, String> env) { }
        public boolean setupRenderer() { return true; }
    }

    @Test public void explicitRendererIsNotReplacedWhenLibraryIsMissing() {
        GameRenderer manager = new GameRenderer(Renderers.GL4ES_RENDERER, true);
        RenderSpec chosen = new FakeRenderer("");
        manager.setCurrentRenderer(chosen);
        assertNull(manager.getLaunchableRenderer());
        assertSame(chosen, manager.getCurrentRenderer());
    }

    @Test public void explicitRendererIsUsedWhenLibraryIsPresent() {
        GameRenderer manager = new GameRenderer(Renderers.GL4ES_RENDERER, true);
        RenderSpec chosen = new FakeRenderer("libGLMojo.so");
        manager.setCurrentRenderer(chosen);
        assertSame(chosen, manager.getLaunchableRenderer());
        assertSame(chosen, manager.getCurrentRenderer());
    }
}
