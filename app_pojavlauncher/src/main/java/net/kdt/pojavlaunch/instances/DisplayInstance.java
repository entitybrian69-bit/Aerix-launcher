package net.kdt.pojavlaunch.instances;

import java.io.File;

public class DisplayInstance {
    protected transient File mInstanceRoot;
    public String name;
    public String versionId;
    public String icon;

    /** Stable directory identity, independent of whichever profile is currently selected. */
    public String getInstanceId() { return mInstanceRoot.getName(); }

    protected void sanitize() {
        sanitizeIcon();
    }

    protected DisplayInstance() {
    }

    protected File getInstanceIconLocation() {
        return new File(mInstanceRoot, "icon.webp");
    }

    private void sanitizeIcon() {
        if(InstanceIconProvider.hasStaticIcon(icon) &&
                !InstanceIconProvider.FALLBACK_ICON_NAME.equals(icon)) return;
        // Either no icon at all or the stock one: the version id says which loader this really is,
        // and the loader's own mark is a better answer than the launcher's.
        String loaderIcon = InstanceIconProvider.loaderIconFor(versionId);
        if(loaderIcon != null) {
            icon = loaderIcon;
            return;
        }
        icon = InstanceIconProvider.FALLBACK_ICON_NAME;
    }
}
