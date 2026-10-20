package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.app.Activity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.CallbackBridge;
import net.kdt.pojavlaunch.game.platform.backend.DummyBackend;
import net.kdt.pojavlaunch.game.platform.backend.PlatformBackend;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class, shadows = TouchCharInputTest.ShadowCallbackBridge.class)
public class TouchCharInputTest {
    // Keep the real Java key/modifier forwarding; only omit Android-only JNI loading on the JVM.
    @Implements(CallbackBridge.class)
    public static class ShadowCallbackBridge {
        @Implementation protected static void __staticInitializer__() { }
    }

    private TouchCharInput input;
    private InputConnection connection;
    private PlatformBackend previousBackend;
    private final RecordingBackend backend = new RecordingBackend();
    private static final String HISTORY = "previous chat ";

    private static class RecordingBackend extends DummyBackend {
        final StringBuilder chat = new StringBuilder(HISTORY);
        int deletes;
        int enters;
        int cursor = chat.length();
        int unsafeMods;
        @Override public void sendBulkUnicodeEvent(String text, int mods) {
            unsafeMods |= mods;
            chat.insert(cursor, text);
            cursor += text.length();
        }
        @Override public boolean sendKeyEvent(int key, boolean down, int mods) {
            unsafeMods |= mods;
            if (!down) return true;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (cursor > 0) cursor = chat.offsetByCodePoints(cursor, -1);
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (cursor < chat.length()) cursor = chat.offsetByCodePoints(cursor, 1);
            } else if (key == KeyEvent.KEYCODE_FORWARD_DEL) {
                deletes++;
                if (cursor < chat.length()) chat.delete(cursor, chat.offsetByCodePoints(cursor, 1));
            } else if (key == KeyEvent.KEYCODE_DEL) {
                deletes++;
                if (cursor > 0) {
                    int start = chat.offsetByCodePoints(cursor, -1);
                    chat.delete(start, cursor);
                    cursor = start;
                }
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

    @Test public void temporaryWindowFocusLossKeepsTypedMessageAndEditorEnabled() {
        connection.commitText("hello friends", 1);
        String editorText = input.getText().toString();
        input.onWindowFocusChanged(false);
        assertTrue(input.isEnabled());
        assertEquals(editorText, input.getText().toString());
        input.onWindowFocusChanged(true);
        connection.commitText(" ", 1);
        assertEquals(HISTORY + "hello friends ", backend.chat.toString());
        assertEquals(0, backend.deletes);
    }

    @Test public void singleLetterDeletionInMiddlePreservesEntireSentence() {
        connection.commitText("hello world and friends", 1);
        int position = TouchCharInput.TEXT_FILLER.length() + 4;
        connection.setSelection(position, position);
        connection.deleteSurroundingText(1, 0);
        assertEquals(HISTORY + "helo world and friends", backend.chat.toString());
        assertEquals(1, backend.deletes);
        // A second edit must retain the same logical caret.
        connection.commitText("l", 1);
        assertEquals(HISTORY + "hello world and friends", backend.chat.toString());
        assertEquals(1, backend.deletes);
        assertEquals(HISTORY.length() + 4, backend.cursor);
    }

    @Test public void selectingOneWrongLetterOnlyReplacesThatLetter() {
        connection.commitText("hello wurld and friends", 1);
        int start = TouchCharInput.TEXT_FILLER.length() + 7;
        connection.setSelection(start, start + 1);
        connection.commitText("o", 1);
        assertEquals(HISTORY + "hello world and friends", backend.chat.toString());
        assertEquals(1, backend.deletes);
    }

    @Test public void deletingInsideComposingWordPreservesRestOfMessage() {
        connection.commitText("say ", 1);
        connection.setComposingText("helllo", 1);
        connection.setComposingText("hello", 1);
        assertEquals(HISTORY + "say hello", backend.chat.toString());
        assertEquals(1, backend.deletes);
        connection.commitText("hello ", 1);
        assertEquals(HISTORY + "say hello ", backend.chat.toString());
    }

    @Test public void codePointDeletionPreservesFollowingEmojiAndWords() {
        connection.commitText("hi \uD83D\uDE00 friends", 1);
        int position = TouchCharInput.TEXT_FILLER.length() + 5;
        connection.setSelection(position, position);
        connection.deleteSurroundingTextInCodePoints(1, 0);
        assertEquals(HISTORY + "hi  friends", backend.chat.toString());
        assertEquals(1, backend.deletes);
    }

    @Test public void forwardDeletePreservesTheRestOfTheWord() {
        connection.commitText("hello world", 1);
        int position = TouchCharInput.TEXT_FILLER.length() + 3;
        connection.setSelection(position, position);
        connection.deleteSurroundingText(0, 1);
        assertEquals(HISTORY + "helo world", backend.chat.toString());
        assertEquals(1, backend.deletes);
    }

    @Test public void imeEditsDoNotInheritShortcutModifierFlags() {
        boolean ctrl = CallbackBridge.holdingCtrl;
        boolean shift = CallbackBridge.holdingShift;
        boolean alt = CallbackBridge.holdingAlt;
        try {
            CallbackBridge.holdingCtrl = CallbackBridge.holdingShift = CallbackBridge.holdingAlt = true;
            connection.commitText("hello world", 1);
            int position = TouchCharInput.TEXT_FILLER.length() + 4;
            connection.setSelection(position, position);
            connection.deleteSurroundingText(1, 0);
            assertEquals(HISTORY + "helo world", backend.chat.toString());
            assertEquals(1, backend.deletes);
            assertEquals(0, backend.unsafeMods);
            // Do not mutate the held controls; real shortcut handling remains separate.
            assertTrue(CallbackBridge.holdingCtrl);
            assertTrue(CallbackBridge.holdingShift);
            assertTrue(CallbackBridge.holdingAlt);
        } finally {
            CallbackBridge.holdingCtrl = ctrl;
            CallbackBridge.holdingShift = shift;
            CallbackBridge.holdingAlt = alt;
        }
    }

    @Test public void movingToEndAfterMiddleCorrectionAppendsAtEnd() {
        connection.commitText("hello world", 1);
        int position = TouchCharInput.TEXT_FILLER.length() + 4;
        connection.setSelection(position, position);
        connection.deleteSurroundingText(1, 0);
        connection.setSelection(input.length(), input.length());
        connection.commitText("!", 1);
        assertEquals(HISTORY + "helo world!", backend.chat.toString());
        assertEquals(1, backend.deletes);
        assertEquals(backend.chat.length(), backend.cursor);
    }

}
