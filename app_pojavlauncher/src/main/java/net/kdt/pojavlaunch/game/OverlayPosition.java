package net.kdt.pojavlaunch.game;

/** Geometry shared by dragging, saved-position restoration and screen resizing. */
final class OverlayPosition {
    static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(Math.max(min, max), value));
    }
    static float fraction(float value, float min, float max) {
        return max <= min ? 0 : clamp((value - min) / (max - min), 0, 1);
    }
    static float restore(float fraction, float min, float max) {
        return min + clamp(fraction, 0, 1) * Math.max(0, max - min);
    }
}
