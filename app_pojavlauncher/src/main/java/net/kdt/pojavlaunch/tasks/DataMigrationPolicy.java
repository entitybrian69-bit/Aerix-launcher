package net.kdt.pojavlaunch.tasks;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Pure policy shared by the launcher-data importer and its JVM smoke tests. */
final class DataMigrationPolicy {
    static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    static final long RESERVED_FREE_BYTES = 2L * 1024L * BYTES_PER_MEGABYTE;

    private static final Set<String> TRUSTED_AUTHORITIES;

    static {
        Set<String> authorities = new HashSet<>();
        // Previous Aerix/Pojav package IDs, including the debug and public/nightly variants.
        authorities.add("net.kdt.pojavlaunch.scoped.gamefolder");
        authorities.add("net.kdt.pojavlaunch.scoped.gamefolder.debug");
        authorities.add("net.kdt.pojavlaunch.pub.scoped.gamefolder");
        // Current Aerix package IDs. These also permit imports between installed Aerix variants.
        authorities.add("net.al.aerixlauncher.scoped.gamefolder");
        authorities.add("net.al.aerixlauncher.scoped.gamefolder.debug");
        authorities.add("net.al.aerixlauncher.pub.scoped.gamefolder");
        TRUSTED_AUTHORITIES = Collections.unmodifiableSet(authorities);
    }

    private DataMigrationPolicy() { }

    static boolean isTrustedSourceAuthority(String authority) {
        return authority != null && TRUSTED_AUTHORITIES.contains(authority);
    }

    /** Required free space includes both the incoming files and a 2 GiB post-import reserve. */
    static long requiredFreeBytes(long bytesToCopy) {
        if (bytesToCopy < 0) throw new IllegalArgumentException("bytesToCopy must be non-negative");
        if (Long.MAX_VALUE - RESERVED_FREE_BYTES < bytesToCopy) return Long.MAX_VALUE;
        return bytesToCopy + RESERVED_FREE_BYTES;
    }

    static long missingMegabytes(long availableBytes, long bytesToCopy) {
        long required = requiredFreeBytes(bytesToCopy);
        long available = Math.max(0L, availableBytes);
        if (available >= required) return 0L;
        long missing = required - available;
        return missing / BYTES_PER_MEGABYTE + (missing % BYTES_PER_MEGABYTE == 0 ? 0 : 1);
    }

    static long saturatedAdd(long left, long right) {
        if (left < 0 || right < 0) throw new IllegalArgumentException("sizes must be non-negative");
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}
