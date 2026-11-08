package net.kdt.pojavlaunch.aerix.vulkan;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.OpenableColumns;

import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports and resolves app-private AdrenoTools-compatible Vulkan driver packages. */
public final class VulkanDriverManager {
    public static final String SYSTEM_DRIVER = "system";
    public static final String TURNIP_DRIVER = "turnip";

    private static final String GLOBAL_DRIVER_KEY = "aerix_global_vulkan_driver";
    private static final String INSTANCE_DRIVER_PREFIX = "aerix_inst_vulkan_";
    private static final String SYSTEM_DRIVER_PREF = "zinkPreferSystemDriver";
    private static final String CUSTOM_PREFIX = "custom-";
    private static final String VULKAN_DRIVER_LIBRARY = "libvulkan_freedreno.so";
    private static final long MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_SINGLE_LIBRARY_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 4096;

    private VulkanDriverManager() {}

    public static final class DriverOption {
        public final String id;
        public final String name;

        private DriverOption(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Installed custom drivers, sorted by their display name. */
    public static List<DriverOption> installedDrivers(Context context) {
        File[] directories = driverRoot(context).listFiles();
        if (directories == null || directories.length == 0) return Collections.emptyList();

        List<DriverOption> result = new ArrayList<>();
        for (File directory : directories) {
            if (!directory.isDirectory() || !directory.getName().startsWith(CUSTOM_PREFIX)) continue;
            File library = new File(directory, VULKAN_DRIVER_LIBRARY);
            if (!library.isFile()) continue;
            String name = readName(new File(directory, "driver_name"));
            if (name == null || name.trim().isEmpty()) name = directory.getName();
            result.add(new DriverOption(directory.getName(), name));
        }
        Collections.sort(result, Comparator.comparing(option -> option.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    /** Current global driver choice. The legacy Pojav system-driver toggle remains authoritative. */
    public static String globalDriverId(Context context) {
        SharedPreferences preferences = preferences(context);
        String selected = preferences.getString(GLOBAL_DRIVER_KEY, null);
        if (preferences.getBoolean(SYSTEM_DRIVER_PREF, false)) return SYSTEM_DRIVER;
        if (isCustomId(selected)) {
            return installedDirectory(context, selected) != null ? selected : TURNIP_DRIVER;
        }
        // Old Aerix builds stored "default driver", "turnip", or "freedreno" here. The latter
        // was never a Vulkan ICD; both it and Turnip used the built-in Turnip loader at runtime.
        return TURNIP_DRIVER;
    }

    /** Converts saved UI values from older builds into current selectable IDs. */
    public static String normalizeInstanceSelection(Context context, String selection) {
        if (selection == null || selection.isEmpty()) return "";
        if (isSystemId(selection)) return SYSTEM_DRIVER;
        if (isTurnipId(selection)) return TURNIP_DRIVER;
        if (isCustomId(selection) && installedDirectory(context, selection) != null) return selection;
        return "";
    }

    /** Resolves an instance override, or follows the current global selection when it is empty. */
    public static String selectedDriverId(Context context, String instanceKey) {
        SharedPreferences preferences = preferences(context);
        if (instanceKey != null && !instanceKey.isEmpty()) {
            String override = normalizeInstanceSelection(context,
                    preferences.getString(INSTANCE_DRIVER_PREFIX + instanceKey, ""));
            if (!override.isEmpty()) return override;
        }
        return globalDriverId(context);
    }

    /** Directory searched first by MojoExec for a selected custom ICD, or null for built-in/system. */
    public static String libraryDirectory(Context context, String driverId) {
        File directory = installedDirectory(context, driverId);
        return directory == null ? null : directory.getAbsolutePath();
    }

    public static boolean isSystemDriver(String driverId) {
        return isSystemId(driverId);
    }

    /**
     * Copy a compatible driver APK/ZIP into private storage. The archive must include an Adreno or
     * Turnip ICD for the current process ABI; matching-ABI shared libraries are retained so the
     * ICD's dependencies can be resolved by Android's linker namespace.
     */
    public static DriverOption importDriver(Context context, Uri source) throws IOException {
        if (source == null) throw new IOException("No driver package was selected");
        String abi = processAbi();
        if (abi == null) throw new IOException("This device's app architecture is not supported");

        ContentResolver resolver = context.getContentResolver();
        String sourceName = queryDisplayName(resolver, source);
        String displayName = displayName(sourceName);
        File root = driverRoot(context);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Could not create Vulkan driver storage");

        String id = CUSTOM_PREFIX + UUID.randomUUID().toString();
        File staging = new File(root, ".import-" + id);
        if (!staging.mkdirs()) throw new IOException("Could not prepare Vulkan driver import");

        boolean imported = false;
        try {
            InputStream raw = resolver.openInputStream(source);
            if (raw == null) throw new IOException("Could not read the selected file");
            extractLibraries(raw, staging, abi);

            File driverLibrary = new File(staging, VULKAN_DRIVER_LIBRARY);
            if (!driverLibrary.isFile()) {
                File packagedIcd = findPackagedIcd(staging);
                if (packagedIcd == null) {
                    throw new IOException("No compatible Adreno/Turnip Vulkan ICD was found for " + abi);
                }
                copyFile(packagedIcd, driverLibrary);
            }
            validateNativeLibraries(staging, abi);
            writeName(new File(staging, "driver_name"), displayName);

            File destination = new File(root, id);
            if (!staging.renameTo(destination)) throw new IOException("Could not finish installing this Vulkan driver");
            imported = true;
            return new DriverOption(id, displayName);
        } finally {
            if (!imported) deleteRecursively(staging);
        }
    }

    private static void extractLibraries(InputStream source, File destination, String abi) throws IOException {
        Set<String> abiAliases = abiAliases(abi);
        Set<String> extractedNames = new HashSet<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream archive = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = archive.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) throw new IOException("Driver package contains too many files");

                String path = entry.getName().replace('\\', '/');
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                AbiPath abiPath = abiPath(path, abiAliases);
                boolean nativeLibrary = !entry.isDirectory() && fileName.toLowerCase(Locale.ROOT).endsWith(".so");
                boolean compatibleAbi = !abiPath.hasAbi || abiPath.matches;
                compatibleAbi &= !abiPath.lib64 || is64BitAbi(abi);
                compatibleAbi &= !abiPath.lib32 || !is64BitAbi(abi);
                boolean extract = nativeLibrary && compatibleAbi && !fileName.isEmpty() &&
                        !fileName.equals(".") && !fileName.equals("..") && extractedNames.add(fileName);
                BufferedOutputStream output = extract
                        ? new BufferedOutputStream(new FileOutputStream(new File(destination, fileName)))
                        : null;
                long libraryBytes = 0;
                try {
                    int read;
                    while ((read = archive.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Driver package is too large to import safely");
                        }
                        if (output != null) {
                            libraryBytes += read;
                            if (libraryBytes > MAX_SINGLE_LIBRARY_BYTES) {
                                throw new IOException("Driver library is too large to import safely");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                } finally {
                    if (output != null) output.close();
                }
                archive.closeEntry();
            }
        }
    }

    private static File findPackagedIcd(File directory) {
        String[] candidates = {
                "libvulkan_adreno.so",
                "vulkan.adreno.so",
                "vulkan_adreno.so",
                "vulkan.freedreno.so",
                "libvulkan_turnip.so",
                "libvulkan_mesa.so"
        };
        for (String candidate : candidates) {
            File file = new File(directory, candidate);
            if (file.isFile()) return file;
        }
        return null;
    }

    private static void copyFile(File source, File destination) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static void validateNativeLibraries(File directory, String abi) throws IOException {
        File[] libraries = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".so"));
        if (libraries == null || libraries.length == 0) throw new IOException("No native libraries were found in the driver package");
        for (File library : libraries) {
            if (!isElfForAbi(library, abi)) {
                throw new IOException("The driver package contains a library for a different architecture: " + library.getName());
            }
        }
    }

    private static boolean isElfForAbi(File file, String abi) throws IOException {
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) return false;
                offset += read;
            }
        }
        if (header[0] != 0x7f || header[1] != 'E' || header[2] != 'L' || header[3] != 'F' || header[5] != 1) {
            return false;
        }
        int expectedClass;
        int expectedMachine;
        switch (abi) {
            case "arm64-v8a": expectedClass = 2; expectedMachine = 183; break;
            case "armeabi-v7a": expectedClass = 1; expectedMachine = 40; break;
            case "x86_64": expectedClass = 2; expectedMachine = 62; break;
            case "x86": expectedClass = 1; expectedMachine = 3; break;
            default: return false;
        }
        int machine = ByteBuffer.wrap(header, 18, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
        return (header[4] & 0xff) == expectedClass && machine == expectedMachine;
    }

    private static AbiPath abiPath(String path, Set<String> aliases) {
        boolean hasAbi = false;
        boolean matches = false;
        boolean lib64 = false;
        boolean lib32 = false;
        String[] parts = path.toLowerCase(Locale.ROOT).split("/");
        for (String part : parts) {
            if (isKnownAbi(part)) {
                hasAbi = true;
                if (aliases.contains(part)) matches = true;
            }
            if (part.equals("lib64")) lib64 = true;
            if (part.equals("lib32")) lib32 = true;
        }
        return new AbiPath(hasAbi, matches, lib64, lib32);
    }

    private static final class AbiPath {
        final boolean hasAbi;
        final boolean matches;
        final boolean lib64;
        final boolean lib32;
        AbiPath(boolean hasAbi, boolean matches, boolean lib64, boolean lib32) {
            this.hasAbi = hasAbi;
            this.matches = matches;
            this.lib64 = lib64;
            this.lib32 = lib32;
        }
    }

    private static Set<String> abiAliases(String abi) {
        Set<String> result = new HashSet<>();
        switch (abi) {
            case "arm64-v8a": Collections.addAll(result, "arm64-v8a", "arm64", "aarch64"); break;
            case "armeabi-v7a": Collections.addAll(result, "armeabi-v7a", "armeabi", "arm"); break;
            case "x86_64": Collections.addAll(result, "x86_64", "amd64"); break;
            case "x86": Collections.addAll(result, "x86", "i686", "i386"); break;
            default: break;
        }
        return result;
    }

    private static boolean isKnownAbi(String abi) {
        return abi.equals("arm64-v8a") || abi.equals("arm64") || abi.equals("aarch64") ||
                abi.equals("armeabi-v7a") || abi.equals("armeabi") || abi.equals("arm") ||
                abi.equals("x86_64") || abi.equals("amd64") || abi.equals("x86") ||
                abi.equals("i686") || abi.equals("i386");
    }

    private static String processAbi() {
        String[] candidates = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            candidates = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        }
        if (candidates == null || candidates.length == 0) candidates = Build.SUPPORTED_ABIS;
        if (candidates != null) {
            for (String candidate : candidates) {
                String canonical = canonicalAbi(candidate);
                if (canonical != null) return canonical;
            }
        }
        return canonicalAbi(System.getProperty("os.arch", ""));
    }

    private static String canonicalAbi(String abi) {
        if (abi == null) return null;
        String value = abi.toLowerCase(Locale.ROOT);
        if (value.contains("arm64") || value.equals("aarch64")) return "arm64-v8a";
        if (value.equals("armeabi-v7a") || value.equals("armeabi") || value.equals("arm") || value.startsWith("armv7")) return "armeabi-v7a";
        if (value.contains("x86_64") || value.equals("amd64")) return "x86_64";
        if (value.equals("x86") || value.equals("i686") || value.equals("i386")) return "x86";
        return null;
    }

    private static boolean is64BitAbi(String abi) {
        return abi.equals("arm64-v8a") || abi.equals("x86_64");
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (RuntimeException ignored) {
            // Some document providers do not implement DISPLAY_NAME; fall back to the URI.
        }
        return uri.getLastPathSegment();
    }

    private static String displayName(String sourceName) {
        String name = sourceName == null ? "Imported Turnip driver" : sourceName.trim();
        name = name.replaceAll("(?i)\\.(zip|apk)$", "").replaceAll("(?i)[._-]adpkg$", "");
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isEmpty()) name = "Imported Turnip driver";
        if (name.length() > 80) name = name.substring(0, 80).trim();
        return name;
    }

    private static void writeName(File file, String name) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static String readName(File file) {
        if (!file.isFile()) return null;
        byte[] buffer = new byte[256];
        try (FileInputStream input = new FileInputStream(file)) {
            int count = input.read(buffer);
            return count <= 0 ? null : new String(buffer, 0, count, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static File driverRoot(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "vulkan_drivers");
    }

    private static File installedDirectory(Context context, String driverId) {
        if (!isCustomId(driverId)) return null;
        File directory = new File(driverRoot(context), driverId);
        File library = new File(directory, VULKAN_DRIVER_LIBRARY);
        return directory.isDirectory() && library.isFile() ? directory : null;
    }

    private static SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static boolean isCustomId(String id) {
        return id != null && id.matches("custom-[0-9a-fA-F-]{36}");
    }

    private static boolean isSystemId(String id) {
        return SYSTEM_DRIVER.equals(id) || "default driver".equalsIgnoreCase(id);
    }

    private static boolean isTurnipId(String id) {
        return TURNIP_DRIVER.equalsIgnoreCase(id) || "freedreno".equalsIgnoreCase(id);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        // Best effort: an interrupted import should not hide the original validation error.
        file.delete();
    }
}
