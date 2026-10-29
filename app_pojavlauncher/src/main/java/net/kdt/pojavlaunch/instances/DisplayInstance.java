package net.kdt.pojavlaunch.instances;

import java.io.File;

public class DisplayInstance {
    protected transient File mInstanceRoot;
    public String name;
    public String versionId;
    public String icon;

    /** Stable directory identity, independent of whichever profile is currently selected. */
    public String getInstanceId() { return mInstanceRoot.getName(); }

    /** The icon file this instance carries, or null when it has no directory of its own. */
    public File getOwnIconLocation() {
        return mInstanceRoot == null ? null : getInstanceIconLocation();
    }

    /**
     * Whether this instance carries artwork of its own, as opposed to a built-in loader mark.
     * A modpack writes the pack's own logo here.
     */
    public boolean hasOwnIcon() {
        File icon = getOwnIconLocation();
        return icon != null && icon.isFile() && icon.canRead();
    }

    /**
     * Identity the resolved icon is cached under. The directory name survives the instance list
     * being read again, so a newly written icon replaces the old one instead of hiding behind it.
     */
    public String getIconCacheKey() {
        return mInstanceRoot != null ? mInstanceRoot.getName() : "detached@" + System.identityHashCode(this);
    }

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
