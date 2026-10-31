package net.kdt.pojavlaunch.customcontrols.keyboard;

/** Mirrors the IME's logical text, not its replacement ranges or the hidden deletion padding. */
final class ChatTextMirror {
    static final class Edit {
        final int backspaces;
        final String insertion;
        final int moveLeft, moveRight, forwardDeletes;
        Edit(int backspaces, String insertion, int moveLeft, int moveRight, int forwardDeletes) {
            this.backspaces = backspaces;
            this.insertion = insertion;
            this.moveLeft = moveLeft;
            this.moveRight = moveRight;
            this.forwardDeletes = forwardDeletes;
        }
    }

    private final String filler;
    private int padding;
    private int recoverablePadding;
    private String text = "";
    private String sentText = "";
    private int historyDeletes;
    private int sentCursor;

    ChatTextMirror(String filler) {
        this.filler = filler;
        reset();
    }

    void reset() {
        padding = recoverablePadding = filler.length();
        text = sentText = "";
        historyDeletes = sentCursor = 0;
    }

    void changed(CharSequence buffer, int start, int removed, int inserted) {
        String value = buffer.toString();
        int oldPadding = padding;
        boolean wasEmpty = text.isEmpty();
        if (start < padding) {
            if (inserted == 0) {
                padding -= Math.min(removed, padding - start);
            } else if (!value.startsWith(filler.substring(0, padding))) {
                // Some IMEs replace the entire editor, dropping our virtual prefix.
                padding = start;
            }
        }
        if (inserted > 0 && padding < recoverablePadding &&
                value.startsWith(filler.substring(0, recoverablePadding))) {
            // A batched IME rewrite may first delete, then restore the virtual prefix.
            padding = recoverablePadding;
        }
        padding = Math.min(padding, value.length());
        text = value.substring(padding);
        if (wasEmpty && text.isEmpty() && inserted == 0 && oldPadding - padding == 1) {
            // A normal Backspace can edit earlier chat after reopening the keyboard.
            // Bulk removal of virtual padding is an IME reset, not thirty real deletions.
            historyDeletes++;
        }
        // A delete-and-reinsert IME transaction must never erase unknown game chat history.
        if (!text.isEmpty()) historyDeletes = 0;
    }

    Edit drain() {
        int prefix = 0;
        while (prefix < sentText.length() && prefix < text.length()) {
            int previous = sentText.codePointAt(prefix);
            if (previous != text.codePointAt(prefix)) break;
            prefix += Character.charCount(previous);
        }
        // Keep the unchanged tail in Minecraft. Rebuilding it with Backspace can
        // erase the rest of the word/message if reinsertion is interrupted, and
        // turns a single-letter correction into many destructive key events.
        int previousEnd = sentText.length();
        int nextEnd = text.length();
        while (previousEnd > prefix && nextEnd > prefix) {
            int previous = sentText.codePointBefore(previousEnd);
            if (previous != text.codePointBefore(nextEnd)) break;
            previousEnd -= Character.charCount(previous);
            nextEnd -= Character.charCount(previous);
        }
        String insertion = text.substring(prefix, nextEnd);
        int left = 0, right = 0, backspaces = 0, forwards = 0;
        if (previousEnd != prefix || !insertion.isEmpty()) {
            // Track the game caret instead of returning over the entire suffix.
            // Besides matching middle edits, this avoids doubling the key burst
            // into GLFW's bounded native input queue for long messages.
            if (sentCursor <= prefix) {
                right = sentText.codePointCount(sentCursor, prefix);
                forwards = sentText.codePointCount(prefix, previousEnd);
            } else if (sentCursor >= previousEnd) {
                left = sentText.codePointCount(previousEnd, sentCursor);
                backspaces = sentText.codePointCount(prefix, previousEnd);
            } else {
                backspaces = sentText.codePointCount(prefix, sentCursor);
                forwards = sentText.codePointCount(sentCursor, previousEnd);
            }
            sentCursor = nextEnd;
        }
        if (sentText.isEmpty() && text.isEmpty()) backspaces += historyDeletes;
        Edit edit = new Edit(backspaces, insertion, left, right, forwards);
        sentText = text;
        recoverablePadding = padding;
        historyDeletes = 0;
        return edit;
    }
}
