package net.kdt.pojavlaunch.progresskeeper;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class ProgressKeeperTest {
    @Test public void firstEventIncludesProgressAndTaskEndsCleanly() {
        String key = "dock-first-event";
        List<String> events = new ArrayList<>();
        ProgressListener listener = new ProgressListener() {
            public void onProgressStarted() { events.add("start"); }
            public void onProgressUpdated(int p, int r, Object... args) { events.add(p + ":" + args[0]); }
            public void onProgressEnded() { events.add("end"); }
        };
        ProgressKeeper.addListener(key, listener); events.clear();
        try {
            ProgressKeeper.submitProgress(key, 42, -1, "files");
            assertEquals(Arrays.asList("start", "42:files"), events);
            ProgressKeeper.submitProgress(key, -1, -1);
            assertFalse(ProgressKeeper.hasProgressKey(key)); assertEquals("end", events.get(2));
        } finally { ProgressKeeper.removeListener(key, listener); ProgressKeeper.submitProgress(key, -1, -1); }
    }
    @Test public void observerReattachmentRestoresCurrentState() {
        String key = "dock-reattach";
        List<Integer> values = new ArrayList<>();
        ProgressListener listener = new ProgressListener() {
            public void onProgressStarted() { }
            public void onProgressUpdated(int p, int r, Object... args) { values.add(p); }
            public void onProgressEnded() { }
        };
        try {
            ProgressKeeper.submitProgress(key, 67, -1, "restored");
            ProgressKeeper.addListener(key, listener);
            assertEquals(Arrays.asList(67), values);
        } finally { ProgressKeeper.removeListener(key, listener); ProgressKeeper.submitProgress(key, -1, -1); }
    }
}
