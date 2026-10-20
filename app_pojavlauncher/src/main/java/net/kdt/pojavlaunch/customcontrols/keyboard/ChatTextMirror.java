package net.kdt.pojavlaunch.customcontrols.keyboard;

/** Mirrors the IME's logical text, not its replacement ranges or the hidden deletion padding. */
final class ChatTextMirror {
    static final class Edit {
        final int backspaces;
        final String insertion;
        final int suffixCodePoints;
        Edit(int backspaces, String insertion, int suffixCodePoints) {
            this.backspaces = backspaces;
            this.insertion = insertion;
            this.suffixCodePoints = suffixCodePoints;
        }
    }

    private final String filler;
    private int padding;
    private int recoverablePadding;
    private String text = "";
    private String sentText = "";
    private int historyDeletes;

    ChatTextMirror(String filler) {
        this.filler = filler;
        reset();
    }

    void reset() {
        padding = recoverablePadding = filler.length();
        text = sentText = "";
        historyDeletes = 0;
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
        int suffix = sentText.codePointCount(previousEnd, sentText.length());
        int deletes = sentText.codePointCount(prefix, previousEnd);
        if (sentText.isEmpty() && text.isEmpty()) deletes += historyDeletes;
        Edit edit = new Edit(deletes, text.substring(prefix, nextEnd), suffix);
        sentText = text;
        recoverablePadding = padding;
        historyDeletes = 0;
        return edit;
    }
}
