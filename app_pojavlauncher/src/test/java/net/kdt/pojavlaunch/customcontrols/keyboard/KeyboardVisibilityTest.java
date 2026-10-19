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
        boolean active = true;
        ResultReceiver lastResult;
        @Implementation public boolean showSoftInput(View view, int flags, ResultReceiver result) {
            shows++;
            lastResult = result;
            return false; // Simulate a slow/busy IME, not an instantly available keyboard.
        }
        @Implementation public boolean isActive(View view) { return active; }
        @Implementation public void restartInput(View view) { restarts++; }
    }
    private static class Editor extends TouchCharInput {
        boolean focusedWindow = true;
        Editor(Context context) { super(context); }
        @Override public boolean hasWindowFocus() { return focusedWindow; }
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
    private void idle(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }
    private void focus(boolean focused) {
        input.focusedWindow = focused;
        input.onWindowFocusChanged(focused);
    }

    @Test public void openingWaitsForPostedFocusAndRetriesBusyIme() {
        input.setKeyboardState(true);
        assertTrue(input.isFocusableInTouchMode());
        assertEquals(0, ime.shows);
        idle(1);
        assertEquals(1, ime.shows);
        idle(200);
        assertEquals(2, ime.shows);
        assertTrue(input.isEnabled());
    }
    @Test public void transientWindowFocusLossDoesNotDisableOrClearEditor() {
        input.setKeyboardState(true);
        idle(1);
        String text = input.getText().toString();
        focus(false);
        idle(500);
        assertTrue(input.isEnabled());
        assertEquals(text, input.getText().toString());
        assertEquals(1, ime.shows);
        focus(true);
        idle(1);
        assertEquals(2, ime.shows);
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
    @Test public void explicitCloseCancelsPendingAndFutureRetries() {
        input.setKeyboardState(true);
        input.setKeyboardState(false);
        idle(3000);
        focus(true);
        idle(3000);
        assertEquals(0, ime.shows);
        assertFalse(input.isEnabled());
    }
    @Test public void toggleDuringOpeningClosesInsteadOfRequestingAgain() {
        input.switchKeyboardState();
        input.clearFocus();
        input.switchKeyboardState();
        idle(3000);
        assertEquals(0, ime.shows);
        assertFalse(input.isEnabled());
    }
    @Test public void confirmedShownStopsRetrying() {
        input.setKeyboardState(true);
        idle(1);
        ime.lastResult.send(InputMethodManager.RESULT_SHOWN, null);
        idle(3000);
        assertEquals(1, ime.shows);
    }
    @Test public void repeatedOpenRequestsShareOnePendingAttempt() {
        input.setKeyboardState(true);
        input.setKeyboardState(true);
        input.setKeyboardState(true);
        idle(1);
        assertEquals(1, ime.shows);
    }
    @Test public void busyImeRetriesAreBoundedAndDoNotRestartActiveInput() {
        input.setKeyboardState(true);
        idle(1);
        // TextView may restart input itself when enabling/focusing the editor.
        int initialRestarts = ime.restarts;
        idle(5000);
        assertEquals(10, ime.shows);
        assertEquals(initialRestarts, ime.restarts);
        idle(5000);
        assertEquals(10, ime.shows);
        input.setKeyboardState(true);
        idle(1);
        assertEquals(11, ime.shows);
    }
    @Test public void inactiveImeIsRestartedOnceNotOnEveryAttempt() {
        ime.active = false;
        input.setKeyboardState(true);
        int frameworkRestarts = ime.restarts;
        idle(1);
        assertEquals(frameworkRestarts + 1, ime.restarts);
        int firstAttemptRestarts = ime.restarts;
        idle(5000);
        assertEquals(10, ime.shows);
        assertEquals(firstAttemptRestarts, ime.restarts);
    }
    @Test public void detachCancelsPendingOpen() {
        input.setKeyboardState(true);
        activity.setContentView(new FrameLayout(activity));
        idle(3000);
        assertEquals(0, ime.shows);
    }
    @Test public void oldShownCallbackCannotCancelANewRequest() {
        input.setKeyboardState(true);
        idle(1);
        ResultReceiver old = ime.lastResult;
        input.setKeyboardState(false);
        input.setKeyboardState(true);
        idle(1);
        old.send(InputMethodManager.RESULT_SHOWN, null);
        idle(200);
        assertEquals(3, ime.shows);
    }
    @Test public void backgroundThreadRequestIsMarshalledToUiThread() throws Exception {
        Thread request = new Thread(() -> input.setKeyboardState(true));
        request.start();
        request.join();
        idle(1);
        assertTrue(input.isEnabled());
        assertEquals(1, ime.shows);
    }
    @Test public void repeatedFocusBouncesCannotResetTheRetryBudget() {
        input.setKeyboardState(true);
        idle(1);
        for (int i = 0; i < 20; i++) {
            focus(false);
            idle(1);
            focus(true);
            idle(1);
        }
        assertEquals(1, ime.shows); // Focus bounce cannot bypass the retry cooldown either.
        idle(5000);
        assertEquals(10, ime.shows);
    }

}
