package net.kdt.pojavlaunch.instances;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class InstanceRefreshEventsTest {
    @Test public void completionReachesInstanceListListener() {
        AtomicInteger refreshes = new AtomicInteger();
        ExtraListener<Object> listener = (key, value) -> {
            assertNotNull(value);
            refreshes.incrementAndGet();
            return false;
        };
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_VERSION_SPINNER, listener);
        try {
            InstanceRefreshEvents.notifyChanged();
            assertEquals(1, refreshes.get());
        } finally {
            ExtraCore.removeExtraListenerFromValue(ExtraConstants.REFRESH_VERSION_SPINNER, listener);
            ExtraCore.removeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        }
    }
    @Test public void consecutiveInstallsEachRefreshWithoutRestart() {
        AtomicInteger refreshes = new AtomicInteger();
        ExtraListener<Object> listener = (key, value) -> { refreshes.incrementAndGet(); return false; };
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_VERSION_SPINNER, listener);
        try {
            InstanceRefreshEvents.notifyChanged();
            InstanceRefreshEvents.notifyChanged();
            assertEquals(2, refreshes.get());
        } finally {
            ExtraCore.removeExtraListenerFromValue(ExtraConstants.REFRESH_VERSION_SPINNER, listener);
            ExtraCore.removeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        }
    }
}
