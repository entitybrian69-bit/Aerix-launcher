package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * The floating logo can be hidden during play - and must never be hideable into a dead end.
 *
 * The logo is how the in-game menu opens on a layout with no MENU button, which is the default
 * layout. So the rules are: shown by default, still shown while the menu is open, still shown in the
 * controls editor, and gone when the layout has its own MENU button (as it always was).
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = android.app.Application.class)
public class MenuButtonVisibilityTest {
    private Context context;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0).edit().clear().commit();
    }

    @Test public void everyoneKeepsTheLogoUntilTheyAskOtherwise() {
        assertFalse(MenuButtonVisibility.isHiddenWhilePlaying(context));
    }

    @Test public void theChoiceSurvivesARestart() {
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        assertTrue(MenuButtonVisibility.isHiddenWhilePlaying(context));
        MenuButtonVisibility.setHiddenWhilePlaying(context, false);
        assertFalse(MenuButtonVisibility.isHiddenWhilePlaying(context));
    }

    @Test public void aCorruptPreferenceFallsBackToShowingTheLogo() {
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0)
                .edit().putString("menuButtonHiddenWhilePlaying", "bad").commit();
        assertFalse(MenuButtonVisibility.isHiddenWhilePlaying(context));
    }

    @Test public void hidingTheLogoDoesNotEraseSizeOrPosition() {
        MenuButtonSize.save(context, 64);
        context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0)
                .edit().putFloat("menuButtonX", 123).putFloat("menuButtonY", 45).commit();
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        assertEquals(64, MenuButtonSize.read(context));
        assertEquals(123, context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0)
                .getFloat("menuButtonX", 0), 0);
        assertEquals(45, context.getSharedPreferences(MenuButtonSize.PREFERENCES, 0)
                .getFloat("menuButtonY", 0), 0);
    }

    @Test public void changingTheSizeDoesNotEraseTheVisibilityChoice() {
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        MenuButtonSize.save(context, 28);
        assertTrue(MenuButtonVisibility.isHiddenWhilePlaying(context));
    }

    /** The one rule that matters on a device: hiding cannot cost a player their way into the menu. */
    @Test public void hiddenWhilePlayingButStillReachableFromTheMenu() {
        DrawerPullButton button = new DrawerPullButton(context);
        button.setLayoutHasMenuButton(false);
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        button.refreshVisibility();
        assertEquals("hidden while the player is playing",
                android.view.View.GONE, button.getVisibility());

        button.setMenuShowing(true);
        assertEquals("shown while the menu is open, or it could never be opened again",
                android.view.View.VISIBLE, button.getVisibility());

        button.setMenuShowing(false);
        assertEquals(android.view.View.GONE, button.getVisibility());
    }

    @Test public void aLayoutWithItsOwnMenuButtonStillHidesTheLogo() {
        DrawerPullButton button = new DrawerPullButton(context);
        button.setLayoutHasMenuButton(true);
        assertEquals("the logo is clutter when a MENU button already exists",
                android.view.View.GONE, button.getVisibility());
    }

    /** Without the logo the controls editor cannot be left, so it is shown there regardless. */
    @Test public void theEditorAlwaysHasTheLogoEvenWhenItIsHiddenDuringPlay() {
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        DrawerPullButton button = new DrawerPullButton(context);
        button.refreshVisibility();
        assertEquals(android.view.View.GONE, button.getVisibility());
        button.setEditorMode(true);
        assertEquals("the editor is exited through the logo", android.view.View.VISIBLE,
                button.getVisibility());
        button.setEditorMode(false);
        assertEquals("leaving the editor returns the player's choice, not a forced logo",
                android.view.View.GONE, button.getVisibility());
    }

    /** Safety first: until the activity has loaded a layout, the way into the menu is on screen. */
    @Test public void aFreshButtonIsVisibleUntilTheActivitySaysOtherwise() {
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        DrawerPullButton button = new DrawerPullButton(context);
        assertEquals("a logo missing at startup is a player with no way into the menu",
                android.view.View.VISIBLE, button.getVisibility());
    }

    @Test public void hiddenMeansGoneAndThereforeUntouchable() {
        DrawerPullButton button = new DrawerPullButton(context);
        button.setLayoutHasMenuButton(false);
        MenuButtonVisibility.setHiddenWhilePlaying(context, true);
        button.refreshVisibility();
        // INVISIBLE would still swallow touches meant for the game.
        assertNotEquals(android.view.View.INVISIBLE, button.getVisibility());
    }
}
