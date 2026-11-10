package net.kdt.pojavlaunch.game.platform.backend;


import android.view.MotionEvent;
import android.view.Surface;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.game.platform.Platform;

import git.artdeell.dnbootstrap.glfw.GLFW;

/**
 * GLFW Platform implementation
 */
public class GLFWBackend implements PlatformBackend {
    /** Code points that fit the native queue even while the game is mid-frame. */
    private static final int TEXT_CHUNK_CODEPOINTS = 12;
    /** Slightly above a 60 Hz frame, so the pump is sure to have run. */
    private static final int TEXT_CHUNK_INTERVAL_MS = 18;
    private final android.os.Handler textPump = new android.os.Handler(android.os.Looper.getMainLooper());

    public GLFWBackend() {
        // GLFW on the main game we support does not send events to center the cursor on grab loss
        GLFW.setGrabListener(isGrabbing ->  {
            if(!isGrabbing) Platform.resetCursorPosition();
            Platform.grabStateChanged(isGrabbing);
        });
        GLFW.setPositionCallback(Platform::setCursorPosition);
        GLFW.setCursorCallback(cursor -> {
            if (cursor != null)
                Platform.setCursor(cursor.getBitmap(), cursor.getXhot(), cursor.getYhot());
            else Platform.setCursor(null, 0, 0);
        });
    }

    @Override
    public void surfaceCreated(Surface surface) {
        GLFW.nativeSurfaceCreated(surface);
    }

    @Override
    public void surfaceUpdated() {
        GLFW.nativeSurfaceUpdated();
    }

    @Override
    public void surfaceDestroyed() {
        GLFW.nativeSurfaceDestroyed();
    }

    @Override
    public void sendMousePosition(double x, double y, boolean relative) {
        net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge.onCursorPos(x, y);
        GLFW.sendMousePosition0(x, y);
    }

    @Override
    public void sendMouseEvent(int button, int action, int mods, double x, double y, boolean relative) {
        net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge.onMouseButton(button, action != 0);
        int glfwButton;
        switch (button) {
            case MotionEvent.BUTTON_PRIMARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
                break;
            case MotionEvent.BUTTON_SECONDARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
                break;
            case MotionEvent.BUTTON_TERTIARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
                break;
            case MotionEvent.BUTTON_BACK:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4;
                break;
            case MotionEvent.BUTTON_FORWARD:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5;
                break;
            default:
                glfwButton = 0;
        }
        GLFW.sendMouseEvent(glfwButton, action, mods);
    }

    @Override
    public boolean sendKeyEvent(int key, int state, int mods, char codepoint) {
        net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge.onKeyEvent(key, state != 0);
        return GLFW.sendRawKeyEvent(key, state, mods, codepoint);
    }

    @Override
    public boolean sendKeyEvent(int key, int state, int mods) {
        net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge.onKeyEvent(key, state != 0);
        return GLFW.sendRawKeyEvent(key, state, mods, (char) 0);
    }

    @Override
    public boolean sendKeyEvent(int key, boolean state, int mods) {
        net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge.onKeyEvent(key, state);
        return GLFW.sendRawKeyEvent(key, state ? 1 : 0, mods, (char) 0);
    }

    @Override
    public void sendScrollEvent(double x, double y) {
        GLFW.sendScrollEvent(x, y);
    }

    /**
     * Characters the game has not consumed yet, delivered in bursts of
     * {@link #TEXT_CHUNK_CODEPOINTS} one frame apart.
     *
     * The native text queue behind GLFW is bounded, and its only consumer is the window event pump,
     * which runs once per rendered frame. A long message handed over in one call - typed quickly on
     * a mid-range phone, or pasted - overflows the queue, and the characters past its capacity are
     * dropped without a word. That is the reported "he typed a sentence and only half of every word
     * came through": not a lost keystroke, a full buffer. Pacing the same characters one frame apart
     * gives the pump time to empty between bursts, so nothing is thrown away.
     */
    @Override
    public void sendBulkUnicodeEvent(String text, int mods) {
        if (text == null || text.isEmpty()) return;
        if (text.codePointCount(0, text.length()) <= TEXT_CHUNK_CODEPOINTS) {
            GLFW.sendBulkUnicodeEvent(text, mods);
            return;
        }
        String pending = text;
        int burst = 0;
        while (!pending.isEmpty()) {
            int cut = pending.offsetByCodePoints(0, Math.min(TEXT_CHUNK_CODEPOINTS,
                    pending.codePointCount(0, pending.length())));
            String chunk = pending.substring(0, cut);
            pending = pending.substring(cut);
            // The first burst goes now; the rest follow at roughly one frame each.
            if (burst == 0) GLFW.sendBulkUnicodeEvent(chunk, mods);
            else textPump.postDelayed(() -> GLFW.sendBulkUnicodeEvent(chunk, mods),
                    (long) TEXT_CHUNK_INTERVAL_MS * burst);
            burst++;
        }
    }

    @Override
    public String backendName() {
        return "GLFW";
    }

    @Override
    public void setHovered(boolean hovered) {
        GLFW.nativeSetWindowAttribs(GLFW.GLFW_HOVERED, hovered);
    }

    @Override
    public void setVisible(boolean visible) {
        GLFW.nativeSetWindowAttribs(GLFW.GLFW_VISIBLE, visible);
    }
}
