package net.kdt.pojavlaunch.customcontrols.keyboard;


import static android.content.Context.INPUT_METHOD_SERVICE;

import static net.kdt.pojavlaunch.game.platform.Platform.PLATFORM;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.Bundle;
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
    private int mKeyboardRequestGeneration;
    private int mShowAttempts;
    private boolean mShowScheduled;
    private boolean mImeShown;
    private static final int MAX_SHOW_ATTEMPTS = 10;
    private static final long SHOW_RETRY_MS = 200;
    private final Runnable mShowKeyboard = this::tryShowKeyboard;

    private void cancelShowRequest() {
        removeCallbacks(mShowKeyboard);
        mShowScheduled = false;
    }

    private void scheduleShow(long delay) {
        if (!mKeyboardRequested || mImeShown || mShowScheduled || mShowAttempts >= MAX_SHOW_ATTEMPTS) return;
        mShowScheduled = true;
        postDelayed(mShowKeyboard, delay);
    }

    private void tryShowKeyboard() {
        mShowScheduled = false;
        // Window/attachment callbacks will resume the request. Never poll in the background.
        if (!mKeyboardRequested || !isAttachedToWindow() || !hasWindowFocus() || !isShown()) return;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            mImeShown = true;
            return;
        }
        if (++mShowAttempts > MAX_SHOW_ATTEMPTS) return;
        if (!hasFocus()) requestFocus();
        if (hasFocus()) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                // Do not restart an active connection on every retry: that discards composition.
                if (mShowAttempts == 1 && !imm.isActive(this)) imm.restartInput(this);
                final int generation = mKeyboardRequestGeneration;
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT,
                        new ResultReceiver(new Handler(Looper.getMainLooper())) {
                            @Override protected void onReceiveResult(int resultCode, Bundle data) {
                                if (generation != mKeyboardRequestGeneration || !mKeyboardRequested) return;
                                if (resultCode == InputMethodManager.RESULT_SHOWN ||
                                        resultCode == InputMethodManager.RESULT_UNCHANGED_SHOWN) {
                                    mImeShown = true;
                                    cancelShowRequest();
                                }
                            }
                        });
            }
        }
        scheduleShow(SHOW_RETRY_MS);
    }

    private void requestKeyboard() {
        if (!mKeyboardRequested || mShowAttempts >= MAX_SHOW_ATTEMPTS) {
            mKeyboardRequestGeneration++;
            mShowAttempts = 0;
            mImeShown = false;
        }
        mKeyboardRequested = true;
        enable();
        // Let the visible, touch-focusable editor attach and register with the IME first.
        scheduleShow(0);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleShow(0);
    }

    @Override protected void onDetachedFromWindow() {
        mKeyboardRequested = false;
        mKeyboardRequestGeneration++;
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
        for (int i = 0; i < edit.backspaces; i++) {
            CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_DEL);
        }
        if (!edit.insertion.isEmpty()) {
            PLATFORM.sendBulkUnicodeEvent(edit.insertion, CallbackBridge.getCurrentMods());
        }
    }

    /**
     * Keep the opening intent through transient window focus changes.
     * Real activity pauses and explicit close/Back/Done dismiss the keyboard.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            // IME/window transitions are not a request to erase or disable the editor.
            cancelShowRequest();
            mKeyboardRequestGeneration++;
            mImeShown = false;
        } else if (mKeyboardRequested) {
            // Focus bouncing must not create an unlimited retry loop on a slow device.
            scheduleShow(0);
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
        // Focus may be temporarily missing even though an opening request is pending.
        if (mKeyboardRequested) disable();
        else requestKeyboard();
    }

    /** Force the desired keyboard state; repeated open requests are idempotent. */
    public void setKeyboardState(boolean state){
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(() -> setKeyboardState(state));
            return;
        }
        if (state) requestKeyboard();
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
        mKeyboardRequestGeneration++;
        mImeShown = false;
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
        CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ENTER);
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
