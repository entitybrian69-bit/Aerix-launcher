package net.kdt.pojavlaunch.aerix.resourcepack;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Unwraps resource-pack ZIPs that were archived with a single wrapping folder (e.g.
 * {@code MyPack/pack.mcmeta}) so Minecraft sees {@code pack.mcmeta} at the archive root.
 */
public final class ResourcePackArchive {
    private static final long ENTRY_LIMIT = 64L * 1024 * 1024;
    private static final long TOTAL_LIMIT = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20000;

    private ResourcePackArchive() { }

    public static boolean unwrapSingleRootInPlace(File zipFile) throws IOException {
        if (zipFile == null || !zipFile.isFile()) return false;
        File parent = zipFile.getCanonicalFile().getParentFile();
        File temp = File.createTempFile(".aerix-rp-unwrap-", ".tmp", parent);
        boolean unwrapped = false;
        try {
            unwrapped = unwrapSingleRootIfNeeded(zipFile, temp);
            if (!unwrapped) return false;
            if (!temp.renameTo(zipFile)) {
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = new FileOutputStream(zipFile)) {
                    copyBounded(in, out, TOTAL_LIMIT);
                }
            }
            return true;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    /**
     * Copies {@code sourceZip} to {@code targetZip}, stripping a single wrapping folder if and only
     * if {@code pack.mcmeta} is absent from root and present directly inside that single wrapper
     * folder. Returns {@code true} when unwrapped, {@code false} when copied verbatim.
     */
    public static boolean unwrapSingleRootIfNeeded(File sourceZip, File targetZip) throws IOException {
        String wrapperPrefix = findSingleRootWrapper(sourceZip);
        if (wrapperPrefix == null) {
            try (InputStream in = new FileInputStream(sourceZip);
                 OutputStream out = new FileOutputStream(targetZip)) {
                copyBounded(in, out, TOTAL_LIMIT);
            }
            return false;
        }

        try (ZipFile zip = new ZipFile(sourceZip);
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetZip)))) {
            Set<String> emitted = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String normalized = normalizeEntryName(entry.getName());
                if (normalized == null || shouldIgnoreMetadata(normalized)) continue;
                if (!normalized.startsWith(wrapperPrefix)) continue;
                String stripped = normalized.substring(wrapperPrefix.length());
                if (stripped.isEmpty()) continue;
                if (entry.isDirectory() && !stripped.endsWith("/")) stripped += "/";
                if (!emitted.add(stripped)) continue;
                ZipEntry copy = new ZipEntry(stripped);
                if (entry.getTime() >= 0) copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                if (!entry.isDirectory()) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        copyBounded(in, out, ENTRY_LIMIT);
                    }
                }
                out.closeEntry();
            }
        }
        return true;
    }

    private static String findSingleRootWrapper(File sourceZip) {
        try (ZipFile zip = new ZipFile(sourceZip)) {
            int count = 0;
            long total = 0;
            boolean hasRootMcmeta = false;
            String singleRoot = null;
            boolean hasWrappedMcmeta = false;

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES || entry.getSize() < 0 || entry.getSize() > ENTRY_LIMIT) {
                    return null;
                }
                total += entry.getSize();
                if (total > TOTAL_LIMIT) return null;

                String name = normalizeEntryName(entry.getName());
                if (name == null) return null;
                if (name.isEmpty() || shouldIgnoreMetadata(name)) continue;

                if (name.equals("pack.mcmeta") && !entry.isDirectory()) {
                    hasRootMcmeta = true;
                    break;
                }

                int slash = name.indexOf('/');
                if (slash <= 0) {
                    // Any non-directory file at root means it is not wrapped in a single folder.
                    if (!entry.isDirectory()) return null;
                    continue;
                }
                String root = name.substring(0, slash);
                if (singleRoot == null) {
                    singleRoot = root;
                } else if (!singleRoot.equals(root)) {
                    return null;
                }
                if (!entry.isDirectory() && name.equals(root + "/pack.mcmeta")) {
                    hasWrappedMcmeta = true;
                }
            }
            if (hasRootMcmeta || singleRoot == null || !hasWrappedMcmeta) return null;
            return singleRoot + "/";
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String normalizeEntryName(String raw) {
        if (raw == null || raw.isEmpty() || raw.startsWith("/") || raw.contains("\\")) return null;
        String[] parts = raw.split("/");
        if (Arrays.asList(parts).contains("..")) return null;
        return raw;
    }

    private static boolean shouldIgnoreMetadata(String name) {
        return name.startsWith("__MACOSX/") || name.equals("__MACOSX") || name.endsWith("/.DS_Store") || name.equals(".DS_Store");
    }

    private static void copyBounded(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > max) throw new IOException("Resource pack exceeds safe size limit");
            out.write(buffer, 0, n);
        }
    }
}
