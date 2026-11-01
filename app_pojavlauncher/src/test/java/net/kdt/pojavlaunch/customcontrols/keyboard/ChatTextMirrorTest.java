package net.kdt.pojavlaunch.customcontrols.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatTextMirrorTest {
    private static final String PAD = "                              ";
    private final ChatTextMirror mirror = new ChatTextMirror(PAD);

    private void expect(int deletes, String insertion) {
        ChatTextMirror.Edit edit = mirror.drain();
        assertEquals(deletes, edit.backspaces);
        assertEquals(insertion, edit.insertion);
    }
    private void type(String text) {
        mirror.changed(PAD + text, PAD.length(), 0, text.length());
        expect(0, text);
    }

    @Test public void spaceThatReplacesWholeEditorOnlyInsertsSpace() {
        type("hello");
        mirror.changed(PAD + "hello ", 0, PAD.length() + 5, PAD.length() + 6);
        expect(0, " ");
    }
    @Test public void spaceCommitThatDropsPaddingDoesNotDeleteChatHistory() {
        type("hello");
        mirror.changed("hello ", 0, PAD.length() + 5, 6);
        expect(0, " ");
        mirror.changed("hello world", 6, 0, 5);
        expect(0, "world");
    }
    @Test public void unchangedCompositionCommitEmitsNothing() {
        type("hello");
        mirror.changed(PAD + "hello", PAD.length(), 5, 5);
        expect(0, "");
    }
    @Test public void ordinarySpaceIsNotTrimmed() {
        type("hello");
        mirror.changed(PAD + "hello ", PAD.length() + 5, 0, 1);
        expect(0, " ");
        mirror.changed(PAD + "hello  ", PAD.length() + 6, 0, 1);
        expect(0, " ");
    }
    @Test public void autocorrectReplacesOnlyChangedTail() {
        type("say teh");
        mirror.changed(PAD + "say the ", PAD.length() + 4, 3, 4);
        expect(2, "he ");
    }
    @Test public void growingCompositionOnlySendsNewCharacters() {
        type("h");
        mirror.changed(PAD + "hello", PAD.length(), 1, 5);
        expect(0, "ello");
    }
    @Test public void deletingCurrentTextDoesNotBackspacePadding() {
        type("hello");
        mirror.changed("", 0, PAD.length() + 5, 0);
        expect(5, "");
    }
    @Test public void batchedDeleteAndSpaceCommitDoesNotEraseHistory() {
        type("hello");
        mirror.changed("", 0, PAD.length() + 5, 0);
        mirror.changed("hello ", 0, 0, 6);
        expect(0, " ");
    }
    @Test public void batchedRewriteCanRestorePadding() {
        type("hello");
        mirror.changed("", 0, PAD.length() + 5, 0);
        mirror.changed(PAD + "hello ", 0, 0, PAD.length() + 6);
        expect(0, " ");
    }
    @Test public void firstBatchedSpaceCannotDeleteUnknownHistory() {
        mirror.changed("", 0, PAD.length(), 0);
        mirror.changed(" ", 0, 0, 1);
        expect(0, " ");
    }
    @Test public void deliberateBackspaceStillWorksAfterReopeningKeyboard() {
        mirror.changed(PAD.substring(1), PAD.length() - 1, 1, 0);
        expect(1, "");
    }
    @Test public void deletingEmojiCountsCodePointsNotUtf16Units() {
        type("hi \uD83D\uDE00");
        mirror.changed(PAD + "hi ", PAD.length() + 3, 2, 0);
        expect(1, "");
    }
    @Test public void changingSupplementaryCharacterNeverSplitsSurrogatePair() {
        type("\uD83D\uDE00");
        mirror.changed(PAD + "\uD83D\uDE01", PAD.length(), 2, 2);
        expect(1, "\uD83D\uDE01");
    }
    @Test public void editingInMiddlePreservesUnchangedSuffix() {
        type("hello world");
        mirror.changed(PAD + "hello brave world", PAD.length() + 6, 0, 6);
        ChatTextMirror.Edit edit = mirror.drain();
        assertEquals(0, edit.backspaces);
        assertEquals("brave ", edit.insertion);
        assertEquals(5, edit.moveLeft);
    }
    @Test public void resetDoesNotEmitEditsIntoExistingGameChat() {
        type("hello");
        mirror.reset();
        expect(0, "");
    }
    @Test public void firstCommittedSpaceIsARealSpace() {
        mirror.changed(" ", 0, PAD.length(), 1);
        expect(0, " ");
    }
    @Test public void bulkPaddingRemovalCannotEraseUnknownChatEvenOutsideBatch() {
        mirror.changed("", 0, PAD.length(), 0);
        expect(0, "");
    }

    @Test public void deletingOneMiddleLetterNeverDeletesRemainingWordOrSentence() {
        type("hello world and friends");
        mirror.changed(PAD + "helo world and friends", PAD.length() + 3, 1, 0);
        ChatTextMirror.Edit edit = mirror.drain();
        assertEquals(1, edit.backspaces);
        assertEquals("", edit.insertion);
        assertEquals(19, edit.moveLeft);
    }

    @Test public void replacingMiddleEmojiKeepsTheFollowingText() {
        type("hi \uD83D\uDE00 friends");
        mirror.changed(PAD + "hi \uD83D\uDE01 friends", PAD.length() + 3, 2, 2);
        ChatTextMirror.Edit edit = mirror.drain();
        assertEquals(1, edit.backspaces);
        assertEquals("\uD83D\uDE01", edit.insertion);
        assertEquals(8, edit.moveLeft);
    }

    @Test public void unchangedCommitAfterMiddleEditDoesNotLoseTheGameCaret() {
        type("hello world");
        mirror.changed(PAD + "helo world", PAD.length() + 3, 1, 0);
        ChatTextMirror.Edit deletion = mirror.drain();
        assertEquals(1, deletion.backspaces);
        mirror.changed(PAD + "helo world", 0, PAD.length() + 10, PAD.length() + 10);
        ChatTextMirror.Edit unchanged = mirror.drain();
        assertEquals(0, unchanged.moveLeft + unchanged.moveRight + unchanged.backspaces + unchanged.forwardDeletes);
        mirror.changed(PAD + "helo world!", PAD.length() + 10, 0, 1);
        ChatTextMirror.Edit append = mirror.drain();
        assertEquals(7, append.moveRight);
        assertEquals("!", append.insertion);
    }

}
