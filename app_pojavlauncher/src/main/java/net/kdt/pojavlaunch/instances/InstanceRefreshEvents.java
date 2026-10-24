package net.kdt.pojavlaunch.instances;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

/** Publish only after instance metadata and selection have been persisted. */
public final class InstanceRefreshEvents {
    private InstanceRefreshEvents() { }

    public static void notifyChanged() {
        // ExtraCore deliberately ignores null values, even for notification-only events.
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, Boolean.TRUE);
    }
}
