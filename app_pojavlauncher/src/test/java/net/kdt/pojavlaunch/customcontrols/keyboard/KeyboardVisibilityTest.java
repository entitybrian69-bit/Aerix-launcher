package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowInputMethodManager;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class,
        shadows = {KeyboardVisibilityTest.KeyboardIme.class, TouchCharInputTest.ShadowCallbackBridge.class})
public class KeyboardVisibilityTest {
    @Implements(InputMethodManager.class)
    public static class KeyboardIme extends ShadowInputMethodManager {
        int shows;
        int restarts;
        int lastFlags = -1;
        @Implementation public boolean showSoftInput(View view, int flags, ResultReceiver result) {
            shows++;
            lastFlags = flags;
            return false; // Rejected/busy IME: never claim this means the keyboard is visible.
        }
        @Implementation public void restartInput(View view) { restarts++; }
    }
    private static class Editor extends TouchCharInput {
        boolean focusedWindow = true;
        boolean keyboardVisible;
        Editor(Context context) { super(context); }
        @Override public boolean hasWindowFocus() { return focusedWindow; }
        @Override protected boolean isKeyboardVisible() { return keyboardVisible; }
    }
    private Activity activity;
    private Editor input;
    private KeyboardIme ime;
    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        input = new Editor(activity);
        activity.setContentView(input);
        ime = Shadow.extract(activity.getSystemService(Context.INPUT_METHOD_SERVICE));
    }
    private void idle(long millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    private void focus(boolean value) { input.focusedWindow = value; input.onWindowFocusChanged(value); }

    @Test public void openingWaitsForPostedEditorFocusAndIsExplicit() {
        input.setKeyboardState(true);
        assertTrue(input.isFocusableInTouchMode());
        assertEquals(0, ime.shows);
        idle(1);
        assertEquals(1, ime.shows);
        assertEquals(0, ime.lastFlags);
    }
    @Test public void rejectedShowDoesNotPollOrRestartTheIme() {
        input.setKeyboardState(true);
        int frameworkRestarts = ime.restarts;
        idle(10000);
        assertEquals(1, ime.shows);
        assertEquals(frameworkRestarts, ime.restarts);
    }
    @Test public void nextToggleRetriesRejectedOpenRatherThanClosingEditor() {
        input.switchKeyboardState();
        idle(1);
        input.switchKeyboardState();
        idle(1);
        assertEquals(2, ime.shows);
        assertTrue(input.isEnabled());
    }
    @Test public void toggleClosesOnlyWhenKeyboardIsActuallyVisible() {
        input.switchKeyboardState();
        idle(1);
        input.keyboardVisible = true;
        input.switchKeyboardState();
        idle(1000);
        assertEquals(1, ime.shows);
        assertFalse(input.isEnabled());
    }
    @Test public void staleVisibleInsetsCannotBlockAnExplicitOpen() {
        input.keyboardVisible = true;
        input.setKeyboardState(true);
        idle(1);
        assertEquals(1, ime.shows);
    }
    @Test public void requestBeforeWindowFocusOpensWhenFocusArrives() {
        focus(false);
        input.setKeyboardState(true);
        idle(500);
        assertEquals(0, ime.shows);
        focus(true);
        idle(1);
        assertEquals(1, ime.shows);
    }
    @Test public void focusBouncesDoNotCreateRepeatedImeWork() {
        input.setKeyboardState(true);
        idle(1);
        String text = input.getText().toString();
        for (int i = 0; i < 20; i++) { focus(false); focus(true); idle(1); }
        idle(5000);
        assertEquals(1, ime.shows);
        assertTrue(input.isEnabled());
        assertEquals(text, input.getText().toString());
    }
    @Test public void explicitCloseCancelsQueuedOpen() {
        input.setKeyboardState(true);
        input.setKeyboardState(false);
        focus(true);
        idle(3000);
        assertEquals(0, ime.shows);
        assertFalse(input.isEnabled());
    }
    @Test public void repeatedNativeOpenRequestsDoNotSpamTheIme() {
        input.setKeyboardState(true);
        idle(1);
        for (int i = 0; i < 100; i++) input.setKeyboardState(true);
        idle(5000);
        assertEquals(1, ime.shows);
    }
    @Test public void detachCancelsPendingOpen() {
        input.setKeyboardState(true);
        activity.setContentView(new FrameLayout(activity));
        idle(3000);
        assertEquals(0, ime.shows);
    }
    @Test public void backgroundThreadRequestIsMarshalledToUiThread() throws Exception {
        Thread thread = new Thread(() -> input.setKeyboardState(true));
        thread.start(); thread.join(); idle(1);
        assertTrue(input.isEnabled());
        assertEquals(1, ime.shows);
    }
}
