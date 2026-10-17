package net.kdt.pojavlaunch.downloader;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ConcurrentHashMap;

/** Cooperative controls for a single file-transfer session; never interrupts unrelated tasks. */
public final class DownloadControl {
    private static final ConcurrentHashMap<String, DownloadControl> ACTIVE = new ConcurrentHashMap<>();
    private boolean paused;
    private boolean cancelled;

    public static DownloadControl active(String key) { return ACTIVE.get(key); }
    static DownloadControl begin(String key) throws IOException {
        DownloadControl control = new DownloadControl();
        if (ACTIVE.putIfAbsent(key, control) != null) throw new IOException("Download already active: " + key);
        return control;
    }
    static void end(String key, DownloadControl control) { ACTIVE.remove(key, control); }
    public synchronized boolean isCancelled() { return cancelled; }
    public synchronized boolean isPaused() { return paused; }
    public synchronized void togglePause() { if (!cancelled) { paused = !paused; notifyAll(); } }
    public synchronized void cancel() { cancelled = true; paused = false; notifyAll(); }
    public synchronized void checkCancelled() throws IOException {
        if (cancelled) throw new CancelledException();
    }
    synchronized void checkpoint() throws IOException {
        while (paused && !cancelled) {
            try { wait(); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Download interrupted");
            }
        }
        checkCancelled();
    }
    public static final class CancelledException extends IOException {
        public CancelledException() { super("Download cancelled"); }
    }
}
