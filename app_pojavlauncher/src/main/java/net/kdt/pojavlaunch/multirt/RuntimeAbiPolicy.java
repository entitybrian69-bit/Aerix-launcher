package net.kdt.pojavlaunch.multirt;

/** Keeps the downloadable runtime list aligned with the published multi-architecture packages. */
public final class RuntimeAbiPolicy {
    private RuntimeAbiPolicy() {
    }

    public static boolean isRuntimeAvailable(String architecture, int javaMajor) {
        if (!"arm".equals(architecture)
                && !"arm64".equals(architecture)
                && !"x86".equals(architecture)
                && !"x86_64".equals(architecture)) {
            return false;
        }

        if (javaMajor == 8 || javaMajor == 17) return true;
        if (javaMajor == 21 || javaMajor == 25) return !"x86".equals(architecture);
        return false;
    }
}
