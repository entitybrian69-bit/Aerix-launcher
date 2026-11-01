package net.kdt.pojavlaunch.aerix.client;

/** Bounded hysteresis: lower after two slow windows, raise after three fast windows. */
public final class AdaptiveDistanceController {
    private final int original;
    private int current, minimum, targetFps, slow, fast;
    public AdaptiveDistanceController(int initial, int minimum, int targetFps) {
        if (initial < 2 || initial > 64) throw new IllegalArgumentException("Invalid render distance");
        this.original = initial;
        this.current = initial;
        configure(minimum, targetFps);
    }
    public void configure(int minimum, int targetFps) {
        this.minimum = Math.min(original, Math.max(2, Math.min(16, minimum)));
        this.targetFps = Math.max(20, Math.min(120, targetFps));
        this.current = Math.max(this.minimum, this.current);
    }
    public int current() { return current; }
    public int sample(int fps) {
        if (fps <= 0) { slow = fast = 0; return current; }
        if (fps < targetFps * 0.8) { slow++; fast = 0; }
        else if (fps > targetFps * 1.1) { fast++; slow = 0; }
        else { slow = fast = 0; }
        if (slow >= 2) { current = Math.max(minimum, current - 1); slow = 0; }
        if (fast >= 3) { current = Math.min(original, current + 1); fast = 0; }
        return current;
    }
}
