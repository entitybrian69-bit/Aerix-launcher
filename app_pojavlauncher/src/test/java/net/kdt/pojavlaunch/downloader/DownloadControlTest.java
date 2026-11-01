package net.kdt.pojavlaunch.downloader;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class DownloadControlTest {
    @Test public void registryIsScopedToSession() throws Exception {
        String key = "registry-test";
        DownloadControl c = DownloadControl.begin(key);
        try {
            assertSame(c, DownloadControl.active(key));
            try { DownloadControl.begin(key); fail("Must not replace an active session"); }
            catch (IOException expected) { }
        } finally { DownloadControl.end(key, c); }
        assertNull(DownloadControl.active(key));
    }
    @Test public void oldSessionCannotUnregisterNewOne() throws Exception {
        String key = "identity-test";
        DownloadControl old = DownloadControl.begin(key);
        DownloadControl.end(key, old);
        DownloadControl current = DownloadControl.begin(key);
        try {
            DownloadControl.end(key, old);
            assertSame(current, DownloadControl.active(key));
        } finally { DownloadControl.end(key, current); }
    }
    @Test public void pauseBlocksAndResumeUnblocksWorker() throws Exception {
        DownloadControl c = new DownloadControl(); c.togglePause();
        assertTrue(c.isPaused());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> f = pool.submit(() -> { c.checkpoint(); return null; });
            try { f.get(100, TimeUnit.MILLISECONDS); fail("Paused worker must wait"); }
            catch (TimeoutException expected) { }
            c.togglePause(); f.get(2, TimeUnit.SECONDS); assertFalse(c.isPaused());
        } finally { c.cancel(); pool.shutdownNow(); }
    }
    @Test public void cancellationWakesPausedWorker() throws Exception {
        DownloadControl c = new DownloadControl(); c.togglePause();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> f = pool.submit(() -> { c.checkpoint(); return null; });
            c.cancel();
            try { f.get(2, TimeUnit.SECONDS); fail("Cancelled task must fail"); }
            catch (ExecutionException e) { assertTrue(e.getCause() instanceof DownloadControl.CancelledException); }
            c.togglePause(); assertFalse(c.isPaused());
        } finally { c.cancel(); pool.shutdownNow(); }
    }
    @Test public void cancellationIsIsolated() throws Exception {
        DownloadControl first = new DownloadControl(), second = new DownloadControl();
        first.cancel(); second.checkpoint();
        try { first.checkCancelled(); fail("Cancelled session must not continue"); }
        catch (DownloadControl.CancelledException expected) { }
    }
}
