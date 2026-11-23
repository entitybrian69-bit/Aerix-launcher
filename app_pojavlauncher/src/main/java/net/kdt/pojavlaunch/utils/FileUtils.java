package net.kdt.pojavlaunch.utils;

import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    /**
     * Check if a file denoted by a String path exists.
     * @param filePath the path to check
     * @return whether it exists (same as File.exists()
     */
    public static boolean exists(String filePath){
        return new File(filePath).exists();
    }

    /**
     * Get the file name from a path/URL string.
     * @param pathOrUrl the path or the URL of the file
     * @return the file's name
     */
    public static String getFileName(String pathOrUrl) {
        int lastSlashIndex = pathOrUrl.lastIndexOf('/');
        if(lastSlashIndex == -1) return pathOrUrl;
        return pathOrUrl.substring(lastSlashIndex);
    }

    /**
     * Remove the extension (all text after the last dot) from a path/URL string.
     * @param pathOrUrl the path or the URL of the file
     * @return the input with the extension removed
     */
    public static String removeExtension(String pathOrUrl) {
        int lastDotIndex = pathOrUrl.lastIndexOf('.');
        if(lastDotIndex == -1) return pathOrUrl;
        return pathOrUrl.substring(0, lastDotIndex);
    }

    /**
     * Create missing ancestors one directory at a time. Some Android FUSE/MediaProvider
     * implementations fail recursive File.mkdirs() even when stepwise mkdir() succeeds.
     */
    private static void createParentsAndSelf(File targetFile) {
        List<File> missing = new ArrayList<>();
        File current = targetFile;
        while (current != null && !current.exists()) {
            missing.add(current);
            current = current.getParentFile();
        }
        for (int i = missing.size() - 1; i >= 0; i--) {
            File dir = missing.get(i);
            if (!dir.isDirectory()) {
                dir.mkdir();
            }
        }
    }

    /**
     * Ensure that a directory exists, is a directory and is writable.
     * @param targetFile the directory to check
     * @return if the check has succeeded
     */
    public static boolean ensureDirectorySilently(File targetFile) {
        if (targetFile == null) return false;
        if(targetFile.isFile()) return false;
        if(targetFile.exists()) return targetFile.isDirectory() && targetFile.canWrite();
        if(targetFile.mkdirs() || targetFile.isDirectory()) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.createDirectories(targetFile.toPath());
                if (targetFile.isDirectory()) return true;
            } catch (IOException ignored) {}
        }
        createParentsAndSelf(targetFile);
        return targetFile.isDirectory() && targetFile.canWrite();
    }

    /**
     * Ensure that the parent directory of a file exists and is writable
     * @param targetFile the File whose parent should be checked
     * @return if the check as succeeded
     */
    public static boolean ensureParentDirectorySilently(File targetFile) {
        if (targetFile == null) return false;
        File parentFile = targetFile.getParentFile();
        if(parentFile == null) return false;
        return ensureDirectorySilently(parentFile);
    }

    /**
     * Same as ensureDirectorySilently(), but throws an IOException telling why the check failed.
     * @param targetFile the directory to check
     * @throws IOException when the checks fail
     */
    public static void ensureDirectory(File targetFile) throws IOException {
        if (targetFile == null) throw new IOException("Target directory is null");
        if(targetFile.isFile()) throw new IOException("Target directory is a file: " + targetFile.getAbsolutePath());
        if(targetFile.exists()) {
            if(!targetFile.isDirectory()) {
                throw new IOException("Target exists and is not a directory: " + targetFile.getAbsolutePath());
            }
            if(!targetFile.canWrite()) {
                throw new IOException("Target directory is not writable: " + targetFile.getAbsolutePath());
            }
            return;
        }
        if(targetFile.mkdirs() || targetFile.isDirectory()) return;
        IOException nioFailure = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.createDirectories(targetFile.toPath());
                if (targetFile.isDirectory()) return;
            } catch (IOException e) {
                nioFailure = e;
            }
        }
        createParentsAndSelf(targetFile);
        if (targetFile.isDirectory()) return;
        String detail = "Unable to create target directory: " + targetFile.getAbsolutePath();
        if (nioFailure != null && nioFailure.getMessage() != null && !nioFailure.getMessage().isEmpty()) {
            detail += " (" + nioFailure.getMessage() + ")";
        }
        throw new IOException(detail, nioFailure);
    }

    /**
     * Same as ensureParentDirectorySilently(), but throws an IOException telling why the check failed.
     * @param targetFile the File whose parent should be checked
     * @throws IOException when the checks fail
     */
    public static void ensureParentDirectory(File targetFile) throws IOException{
        if (targetFile == null) throw new IOException("Target file is null");
        File parentFile = targetFile.getParentFile();
        if(parentFile == null) throw new IOException("targetFile does not have a parent");
        ensureDirectory(parentFile);
    }

    public static String escapeFileName(String name){
        return name.trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_");
    }
}
