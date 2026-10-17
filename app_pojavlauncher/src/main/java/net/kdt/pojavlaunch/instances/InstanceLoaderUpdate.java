package net.kdt.pojavlaunch.instances;

import java.io.IOException;

/** Change loader metadata only. Never changes the name, storage directory or launch settings. */
public final class InstanceLoaderUpdate {
    public static final String TARGET_ARGUMENT = "aerix_loader_target";
    private InstanceLoaderUpdate() { }
    public static void version(Instance target, String version) throws IOException {
        if (version == null || version.isEmpty()) throw new IOException("Missing installed version");
        String oldVersion = target.versionId;
        InstanceInstaller oldInstaller = target.installer;
        target.versionId = version;
        target.installer = null;
        try { target.write(); }
        catch (IOException e) { target.versionId = oldVersion; target.installer = oldInstaller; throw e; }
    }
    public static void installer(Instance target, InstanceInstaller installer) throws IOException {
        if (installer == null) throw new IOException("Missing loader installer");
        InstanceInstaller old = target.installer;
        installer.targetInstanceId = target.getInstanceId();
        target.installer = installer;
        try { target.write(); }
        catch (IOException e) { target.installer = old; throw e; }
    }
}
