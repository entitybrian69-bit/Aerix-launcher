package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.app.Activity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.backend.DummyBackend;
import net.kdt.pojavlaunch.game.platform.backend.PlatformBackend;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class TouchCharInputTest {
    private TouchCharInput input;
    private InputConnection connection;
    private PlatformBackend previousBackend;
    private final RecordingBackend backend = new RecordingBackend();
    private static final String HISTORY = "previous chat ";

    private static class RecordingBackend extends DummyBackend {
        final StringBuilder chat = new StringBuilder(HISTORY);
        int deletes;
        int enters;
        @Override public void sendBulkUnicodeEvent(String text, int mods) { chat.append(text); }
        @Override public boolean sendKeyEvent(int key, boolean down, int mods) {
            if (!down) return true;
            if (key == KeyEvent.KEYCODE_DEL) {
                deletes++;
                if (chat.length() > 0) chat.delete(chat.offsetByCodePoints(chat.length(), -1), chat.length());
            } else if (key == KeyEvent.KEYCODE_ENTER) enters++;
            return true;
        }
    }

    @Before public void setup() {
        previousBackend = Platform.PLATFORM;
        Platform.PLATFORM = backend;
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        input = new TouchCharInput(activity);
        activity.setContentView(input);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.enable();
        connection = input.onCreateInputConnection(new EditorInfo());
        assertNotNull(connection);
    }
    @After public void cleanup() { Platform.PLATFORM = previousBackend; }

    @Test public void committingSpaceOverEntireEditorKeepsChat() {
        connection.commitText("hello", 1);
        connection.setSelection(0, input.length());
        connection.commitText(TouchCharInput.TEXT_FILLER + "hello ", 1);
        assertEquals(HISTORY + "hello ", backend.chat.toString());
        assertEquals(0, backend.deletes);
    }
    @Test public void wholeEditorRewriteWithoutFillerCannotDeleteHistory() {
        connection.commitText("hello", 1);
        connection.setSelection(0, input.length());
        connection.commitText("hello ", 1);
        assertEquals(HISTORY + "hello ", backend.chat.toString());
        assertEquals(0, backend.deletes);
    }
    @Test public void composingWordThenSpaceDoesNotDeleteAndReinsertWholeWord() {
        connection.setComposingText("h", 1);
        connection.setComposingText("hello", 1);
        connection.commitText("hello ", 1);
        assertEquals(HISTORY + "hello ", backend.chat.toString());
        assertEquals(0, backend.deletes);
    }
    @Test public void batchedDeleteAndReinsertOnSpaceIsAtomic() {
        connection.commitText("hello", 1);
        assertTrue(connection.beginBatchEdit());
        connection.deleteSurroundingText(input.length(), 0);
        assertEquals(HISTORY + "hello", backend.chat.toString());
        connection.commitText("hello ", 1);
        connection.endBatchEdit();
        assertEquals(HISTORY + "hello ", backend.chat.toString());
        assertEquals(0, backend.deletes);
    }
    @Test public void nestedBatchesWaitForOutermostEnd() {
        connection.commitText("teh", 1);
        connection.beginBatchEdit();
        connection.beginBatchEdit();
        connection.setSelection(TouchCharInput.TEXT_FILLER.length(), input.length());
        connection.commitText("the ", 1);
        connection.endBatchEdit();
        assertEquals(HISTORY + "teh", backend.chat.toString());
        connection.endBatchEdit();
        assertEquals(HISTORY + "the ", backend.chat.toString());
        assertEquals(2, backend.deletes);
    }
    @Test public void normalBackspaceAndRepeatedSpacesStillWork() {
        connection.commitText("hi  ", 1);
        connection.deleteSurroundingText(1, 0);
        assertEquals(HISTORY + "hi ", backend.chat.toString());
        assertEquals(1, backend.deletes);
    }
    @Test public void internalClearDoesNotClearGameText() {
        connection.commitText("hello", 1);
        input.clear();
        assertEquals(HISTORY + "hello", backend.chat.toString());
        assertEquals(0, backend.deletes);
    }
    @Test public void gainingWindowFocusDoesNotDisableKeyboardOrClearText() {
        connection.commitText("hello", 1);
        input.onWindowFocusChanged(true);
        assertTrue(input.isEnabled());
        assertTrue(input.getText().toString().endsWith("hello"));
        assertEquals(HISTORY + "hello", backend.chat.toString());
    }
    @Test public void doneActionIsConsumedAndSendsEnterOnce() {
        connection.commitText("hello", 1);
        assertTrue(connection.performEditorAction(EditorInfo.IME_ACTION_DONE));
        assertEquals(1, backend.enters);
        assertEquals(HISTORY + "hello", backend.chat.toString());
    }
    @Test public void restartingConnectionDoesNotLeaveTypingStuckInOldBatch() {
        connection.beginBatchEdit();
        connection.commitText("hello", 1);
        InputConnection restarted = input.onCreateInputConnection(new EditorInfo());
        assertNotNull(restarted);
        assertEquals(HISTORY + "hello", backend.chat.toString());
        restarted.beginBatchEdit();
        restarted.commitText(" ", 1);
        connection.endBatchEdit();
        assertEquals(HISTORY + "hello", backend.chat.toString());
        restarted.endBatchEdit();
        assertEquals(HISTORY + "hello ", backend.chat.toString());
    }

    @Test public void hidingKeyboardFlushesPendingTextWithoutDeletingIt() {
        connection.beginBatchEdit();
        connection.commitText("hello", 1);
        input.setKeyboardState(false);
        assertEquals(HISTORY + "hello", backend.chat.toString());
        assertFalse(input.isEnabled());
        assertEquals(0, backend.deletes);
        connection.endBatchEdit();
        assertEquals(HISTORY + "hello", backend.chat.toString());
    }

}
