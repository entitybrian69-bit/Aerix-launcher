package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import org.junit.Test;

public class RendererAutoSelectorTest {
    @Test
    public void usesGl4esForVersionsThatSupportIt() {
        assertEquals(Renderers.GL4ES_RENDERER, RendererAutoSelector.select(true, 2, false));
        assertEquals(Renderers.GL4ES_RENDERER, RendererAutoSelector.select(true, 3, true));
    }

    @Test
    public void selectsLtwForNewerGamesOnlyWhenGlesThreeIsAvailable() {
        assertEquals(Renderers.LTW_RENDERER, RendererAutoSelector.select(false, 3, true));
        assertNull(RendererAutoSelector.select(false, 2, true));
        assertNull(RendererAutoSelector.select(false, 3, false));
    }

    @Test
    public void automaticSelectionNeverChoosesAVulkanOnlyRenderer() {
        String selected = RendererAutoSelector.select(false, 3, true);
        assertEquals(Renderers.LTW_RENDERER, selected);
    }
}
