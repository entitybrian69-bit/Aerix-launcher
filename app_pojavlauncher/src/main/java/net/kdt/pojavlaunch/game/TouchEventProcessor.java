package net.kdt.pojavlaunch.game;

import android.view.MotionEvent;
import android.view.View;

import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public abstract class TouchEventProcessor {
    private final GameView mHostView;
    public TouchEventProcessor(GameView hostView) {
        mHostView = hostView;
    }

    protected void sendTouchCoordinates(float x, float y) {
        Platform.cursorX = x / mHostView.cursorRatioX;
        Platform.cursorY = y / mHostView.cursorRatioY;
        Platform.sendCursorPosition();
    }

    protected void applyMoveVector(float[] vector) {
        applyMoveVector(vector[0], vector[1]);
    }

    protected void applyMoveVector(float x, float y) {
        float clientAimMultiplier = net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge.touchAimMultiplier();
        // "Mouse speed" is a launcher-cursor setting. While the game holds the pointer it is the
        // game's own sensitivity that turns the mouse, so multiplying here would silently change
        // in-game aim sensitivity (and stacking it with a raised slider looked like a bug).
        float mouseSpeed = Platform.isGrabbing() ? 1f : LauncherPreferences.PREF_MOUSESPEED;
        Platform.cursorX += x * mouseSpeed * clientAimMultiplier;
        Platform.cursorY += y * mouseSpeed * clientAimMultiplier;
        Platform.sendCursorPosition();
    }

    abstract public boolean processTouchEvent(MotionEvent motionEvent);
    abstract public void cancelPendingActions();
}
