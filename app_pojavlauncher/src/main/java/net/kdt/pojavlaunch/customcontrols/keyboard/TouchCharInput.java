package net.kdt.pojavlaunch.customcontrols.keyboard;


import static android.content.Context.INPUT_METHOD_SERVICE;

import static net.kdt.pojavlaunch.game.platform.Platform.PLATFORM;

import android.content.Context;
import android.os.Looper;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.CallbackBridge;

/**
 * This class is intended for sending characters used in chat via the virtual keyboard
 */
public class TouchCharInput extends androidx.appcompat.widget.AppCompatEditText {
    public static final String TEXT_FILLER = "                              ";
    public TouchCharInput(@NonNull Context context) {
        this(context, null);
    }
    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.editTextStyle);
    }
    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }


    private boolean mIsDoingInternalChanges = false;
    private final ChatTextMirror mTextMirror = new ChatTextMirror(TEXT_FILLER);
    private int mImeBatchDepth;
    private int mInputConnectionGeneration;
    private boolean mKeyboardRequested;
    private boolean mOpenPending;
    private boolean mShowScheduled;
    private final Runnable mShowKeyboard = this::tryShowKeyboard;

    private void cancelShowRequest() {
        removeCallbacks(mShowKeyboard);
        mShowScheduled = false;
    }

    private void scheduleShow() {
        if (!mOpenPending || mShowScheduled) return;
        mShowScheduled = true;
        post(mShowKeyboard);
    }

    private void tryShowKeyboard() {
        mShowScheduled = false;
        // Wait for attachment/window focus through callbacks, never a timed polling loop.
        if (!mOpenPending || !isAttachedToWindow() || !hasWindowFocus() || !isShown()) return;
        if (!hasFocus() && !requestFocus()) return;
        mOpenPending = false;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            // Explicit user request, not SHOW_IMPLICIT. Do not gate on old IME insets,
            // restart the connection, or trust a ResultReceiver from another window.
            imm.showSoftInput(this, 0, null);
        }
    }

    protected boolean isKeyboardVisible() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
    }

    private void requestKeyboard(boolean userToggle) {
        // Native repeated "open" notifications must not issue IME work every game frame.
        if (mKeyboardRequested && !userToggle) return;
        mKeyboardRequested = true;
        mOpenPending = true;
        enable();
        scheduleShow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleShow();
    }

    @Override protected void onDetachedFromWindow() {
        mKeyboardRequested = false;
        mOpenPending = false;
        cancelShowRequest();
        super.onDetachedFromWindow();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        flushTextChanges();
        mImeBatchDepth = 0;
        final int generation = ++mInputConnectionGeneration;
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, false) {
            @Override public boolean beginBatchEdit() {
                boolean accepted = super.beginBatchEdit();
                if (accepted && generation == mInputConnectionGeneration) mImeBatchDepth++;
                return accepted;
            }
            @Override public boolean endBatchEdit() {
                try {
                    return super.endBatchEdit();
                } finally {
                    if (generation == mInputConnectionGeneration) {
                        if (mImeBatchDepth > 0) mImeBatchDepth--;
                        if (mImeBatchDepth == 0) {
                            flushTextChanges();
                            if (getEditableText().length() == 0) clear();
                        }
                    }
                }
            }
            @Override public void closeConnection() {
                try {
                    super.closeConnection();
                } finally {
                    if (generation == mInputConnectionGeneration) {
                        mImeBatchDepth = 0;
                        flushTextChanges();
                        mInputConnectionGeneration++;
                    }
                }
            }
        };
    }

    private void flushTextChanges() {
        ChatTextMirror.Edit edit = mTextMirror.drain();
        if (edit.backspaces == 0 && edit.insertion.isEmpty()) return;
        // Game cursor stays at the mirrored session's end between transactions.
        // Move around an unchanged suffix instead of deleting/retyping it.
        for (int i = 0; i < edit.suffixCodePoints; i++) sendImeKey(KeyEvent.KEYCODE_DPAD_LEFT);
        for (int i = 0; i < edit.backspaces; i++) sendImeKey(KeyEvent.KEYCODE_DEL);
        if (!edit.insertion.isEmpty()) PLATFORM.sendBulkUnicodeEvent(edit.insertion, 0);
        for (int i = 0; i < edit.suffixCodePoints; i++) sendImeKey(KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    private void sendImeKey(int keyCode) {
        // These are text-editor operations, not hardware/control shortcuts.
        // Inheriting Ctrl/Shift/Alt can turn Backspace into word deletion or
        // cursor movement into selection. Physical-key forwarding is unchanged.
        PLATFORM.sendKeyEvent(keyCode, true, 0);
        PLATFORM.sendKeyEvent(keyCode, false, 0);
    }

    /**
     * Keep the opening intent through transient window focus changes.
     * Real activity pauses and explicit close/Back/Done dismiss the keyboard.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            // Keep text and a genuinely pending open, but do not reopen an already
            // served editor on every window focus bounce.
            cancelShowRequest();
        } else {
            scheduleShow();
        }
    }

    /**
     * Intercepts the back key to disable focus
     * Does not affect the rest of the activity.
     */
    @Override
    public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            disable();
            return true;
        }
        return super.onKeyPreIme(keyCode, event);
    }


    /**
     * Toggle on and off the soft keyboard, depending of the state
     */
    public void switchKeyboardState(){
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(this::switchKeyboardState);
            return;
        }
        // A request is not proof that the keyboard opened. A second tap must be
        // able to recover a rejected/hidden IME rather than disabling the editor.
        if (hasWindowFocus() && hasFocus() && isKeyboardVisible()) disable();
        else requestKeyboard(true);
    }

    /** Force the desired keyboard state; repeated open requests are idempotent. */
    public void setKeyboardState(boolean state){
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(() -> setKeyboardState(state));
            return;
        }
        if (state) requestKeyboard(false);
        else disable();
    }


    /**
     * Clear the EditText from any leftover inputs
     * It does not affect the in-game input
     */
    public void clear(){
        mIsDoingInternalChanges = true;
        // Edit the Editable directly as it doesn't affect the state
        // of the TextView.
        Editable editable = getEditableText();
        editable.clear();
        // Virtual deletion padding; this is never part of the typed game message.
        editable.append(TEXT_FILLER);
        Selection.setSelection(editable, TEXT_FILLER.length());
        mTextMirror.reset();
        mIsDoingInternalChanges = false;
    }

    /** Regain ability to exist, take focus and have some text being input */
    public void enable(){
        setEnabled(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setVisibility(VISIBLE);
        requestFocus();
    }

    /** Lose ability to exist, take focus and have some text being input */
    public void disable(){
        mKeyboardRequested = false;
        mOpenPending = false;
        cancelShowRequest();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getWindowToken() != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        flushTextChanges();
        mImeBatchDepth = 0;
        mInputConnectionGeneration++;
        clear();
        setVisibility(GONE);
        clearFocus();
        setEnabled(false);
        //setFocusable(false);
    }

    /** Send the enter key. */
    private void sendEnter(){
        flushTextChanges();
        sendImeKey(KeyEvent.KEYCODE_ENTER);
        clear();
    }

    /** This function deals with anything that has to be executed when the constructor is called */
    private void setup(){
        // Using TextWatcher instead of overriding onTextChanged because some Huawei firmware
        // calls setText in constructor, causing havoc for our listener
        addTextChangedListener(new InputTextWatcher());
        setOnEditorActionListener((textView, i, keyEvent) -> {
            sendEnter();
            clear();
            disable();
            return true;
        });
        clear();
        disable();
    }
    private class InputTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        /**
         * IMEs may replace an unchanged composing word (or the whole editor) on Space.
         * Mirror the logical before/after text instead of interpreting the replacement
         * length as a number of game Backspace presses. Batch edits flush atomically.
         */
        @Override
        public void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
            if(mIsDoingInternalChanges) return;
            mTextMirror.changed(text, start, lengthBefore, lengthAfter);
            if (mImeBatchDepth == 0) flushTextChanges();
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if(mIsDoingInternalChanges) return;
            // Moved from onTextChanged because "It is an error to attempt to make changes to s from this callback."
            // reference: https://developer.android.com/reference/android/text/TextWatcher#onTextChanged(java.lang.CharSequence,%20int,%20int,%20int)
            if (editable.length() < 1 && mImeBatchDepth == 0) clear();
        }
    }
}
