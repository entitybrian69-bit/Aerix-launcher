package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = android.app.Application.class)
public class MenuButtonSizeTest {
    private Context context;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0).edit().clear().commit();
    }
    @Test public void existingUsersKeepTheOriginalSize() { assertEquals(46, MenuButtonSize.read(context)); }
    @Test public void acceptsVerySmallThroughBigAndPersistsValues() {
        for (int size : new int[]{20, 28, 46, 64, 96}) {
            MenuButtonSize.save(context, size);
            assertEquals(size, MenuButtonSize.read(context));
        }
    }
    @Test public void clampsOutOfRangeInputAndHandlesCorruptPreferenceTypes() {
        MenuButtonSize.save(context, -100); assertEquals(20, MenuButtonSize.read(context));
        MenuButtonSize.save(context, 9999); assertEquals(96, MenuButtonSize.read(context));
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0).edit().putString("menuButtonSizeDp", "bad").commit();
        assertEquals(46, MenuButtonSize.read(context));
    }
    @Test public void changingSizeDoesNotEraseDraggedPosition() {
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0).edit().putFloat("menuButtonX", 123).putFloat("menuButtonY", 45).commit();
        MenuButtonSize.save(context, 64);
        assertEquals(123, context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0).getFloat("menuButtonX", 0), 0);
    }
    @Test public void logoScalesWithTheButtonInsteadOfClippingAtTinySizes() {
        assertEquals(26, MenuButtonSize.iconPixels(46));
        assertTrue(MenuButtonSize.iconPixels(20) < 20);
        assertTrue(MenuButtonSize.iconPixels(96) > 26);
    }
    @Test public void resettingPositionDoesNotEraseSavedSize() {
        MenuButtonSize.save(context, 28);
        DrawerPullButton.resetPosition(context);
        assertEquals(28, MenuButtonSize.read(context));
    }
}
