package net.kdt.pojavlaunch;

/**
 * Runtimes the download host serves as Pojav binpacks (universal.tar.xz + bin-&lt;arch&gt;.tar.xz).
 *
 * Java 8 is not listed here on purpose: that host has no Java 8 binpack, so pointing an
 * "Internal-8" entry at it is what once installed Java 17 under the name Internal-8. Java 8 is
 * served as a different artifact and lives in {@link NewJREUtil.ExternalRuntime#JRE_8}.
 */
public enum InternalRuntime {
    JRE_17(17, "Internal-17", "components/jre-new"),
    JRE_21(21, "Internal-21", "components/jre-21"),
    JRE_25(25, "Internal-25", "components/jre-25");

    public final int majorVersion;
    public final String name;
    public final String path;

    InternalRuntime(int majorVersion, String name, String path) {
        this.majorVersion = majorVersion;
        this.name = name;
        this.path = path;
    }
}
