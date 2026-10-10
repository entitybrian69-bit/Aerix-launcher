package net.kdt.pojavlaunch.instances;

import android.util.Log;

import com.google.gson.JsonSyntaxException;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Instances {
    private static final File sInstancePath = new File(Tools.DIR_GAME_HOME, "instances");
    public static final File SHARED_DATA_DIRECTORY = new File(Tools.DIR_GAME_HOME, "shared_dir");

    public final List<DisplayInstance> list;
    public final int selectedIndex;

    private Instances(List<DisplayInstance> instances, int selectedIndex) {
        this.list = instances;
        this.selectedIndex = selectedIndex;
    }

    private static <T extends DisplayInstance> T read(File instanceRoot, Class<T> tClass) {
        try {
            T instance = JSONUtils.readFromFile(metadataLocation(instanceRoot), tClass);
            if(instance == null) return null;
            instance.mInstanceRoot = instanceRoot;
            return instance;
        }catch (IOException | JsonSyntaxException e) {
            return null;
        }
    }

    protected static File metadataLocation(File instanceDir) {
        return new File(instanceDir, "mojo_instance.json");
    }

    private static File selectedInstanceLocation() {
        String directoryName = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_INSTANCE, "");
        File instanceRoot = new File(sInstancePath, directoryName);
        if(!instanceRoot.exists())
            Log.e("Instances", "New instance dir doesn't exist!");
        if(!metadataLocation(instanceRoot).exists()) return null;
        return instanceRoot;
    }

    private static boolean filterInstanceDirectories(File instanceDir) {
        if(!instanceDir.canRead() || !instanceDir.canWrite()) return false;
        if(!instanceDir.isDirectory()) return false;
        File instanceMetadata = metadataLocation(instanceDir);
        if(!instanceMetadata.isFile()) return false;
        return instanceMetadata.canRead();
    }

    private static <T extends DisplayInstance> List<T> loadInstances(Class<T> tClass, int[] selectionDst) throws IOException {
        synchronized (sInstancePath) {
            FileUtils.ensureDirectory(sInstancePath);
        }
        File[] instanceDirectories = sInstancePath.listFiles(Instances::filterInstanceDirectories);
        if(instanceDirectories == null) throw new IOException("Failed to enumerate instances");
        File selectedInstanceLocation = selectionDst != null ? selectedInstanceLocation() : null;
        ArrayList<T> instances = new ArrayList<>(instanceDirectories.length);

        for(File instanceDir : instanceDirectories) {
            T instance = read(instanceDir, tClass);

            if(instance == null) continue;
            if(instance instanceof Instance) {
                Instance fullInstance = (Instance) instance;
                if(!Tools.isValidString(fullInstance.aerixId)) {
                    fullInstance.aerixId = UUID.randomUUID().toString();
                    fullInstance.maybeWrite();
                }
            }
            instance.sanitize();
            instances.add(instance);

            if(selectionDst != null && instanceDir.equals(selectedInstanceLocation)) {
                selectionDst[0] = instances.size() - 1;
            }
        }
        instances.trimToSize();
        return instances;
    }

    public static Instances loadDisplay() throws IOException {
        int[] selectionIndex = new int[] { -1 };
        List<DisplayInstance> instances = loadInstances(DisplayInstance.class, selectionIndex);
        if(instances.isEmpty()) {
            createFirstTimeInstance();
            return loadDisplay();
        }else if(selectionIndex[0] == -1) {
            setSelectedInstance(instances.get(0));
            selectionIndex[0] = 0;
        }
        return new Instances(Collections.unmodifiableList(instances), selectionIndex[0]);
    }

    public static List<Instance> loadAllInstances() throws IOException {
        return loadInstances(Instance.class, null);
    }

    /** Return a persistent launcher metadata key that survives instance renames. */
    public static String getStableId(Instance instance) {
        if(instance == null) return "";
        if(!Tools.isValidString(instance.aerixId)) {
            instance.aerixId = UUID.randomUUID().toString();
            instance.maybeWrite();
        }
        return instance.aerixId;
    }

    /** Record a game launch attempt for the library's recent-play sort. */
    public static void recordLaunch(Instance instance) {
        String stableId = getStableId(instance);
        if(stableId.isEmpty()) return;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putLong("aerix_instance_last_played_" + stableId, System.currentTimeMillis())
                .apply();
    }

    private static File findNewInstanceRoot(String prefix) {
        File instanceRoot;
        do {
            String proposedDirectoryName = UUID.randomUUID().toString();
            if(prefix != null) {
                proposedDirectoryName = prefix + "-" + proposedDirectoryName;
            }
            instanceRoot = new File(sInstancePath, proposedDirectoryName);
        } while(instanceRoot.exists() && instanceRoot.isDirectory());
        return instanceRoot;
    }

    /**
     * Set the currently selected instance and save it in user preferences
     * @param instance new selected instance
     */
    public static void setSelectedInstance(DisplayInstance instance) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(
                        LauncherPreferences.PREF_KEY_CURRENT_INSTANCE,
                        instance.mInstanceRoot.getName()
                ).apply();
    }

    /**
     * Remove the instance. This also removes its data storage folder.
     * @param instance the Instance to remove
     * @throws IOException in case of errors during directory removal
     */
    public static void removeInstance(Instance instance) throws IOException {
        File instanceDirectory = instance.mInstanceRoot;
        if(instanceDirectory == null) return;
        org.apache.commons.io.FileUtils.deleteDirectory(instanceDirectory);
    }

    /**
     * Clone an installed profile directory without duplicating launcher metadata identity.
     * Shared-data profiles continue to point at the shared game directory.
     */
    public static Instance cloneInstance(Instance source, String newName) throws IOException {
        if (source == null || source.mInstanceRoot == null || !source.mInstanceRoot.isDirectory()) {
            throw new IOException("The selected profile directory is unavailable");
        }
        if (source.installer != null) throw new IOException("Finish the profile installation before cloning it");
        String safeName = newName == null ? "" : newName.trim();
        String directoryPrefix = safeName.isEmpty() ? null : FileUtils.escapeFileName(safeName);
        File target = findNewInstanceRoot(directoryPrefix);
        try {
            org.apache.commons.io.FileUtils.copyDirectory(source.mInstanceRoot, target);
            Instance clone = read(target, Instance.class);
            if (clone == null) throw new IOException("Could not read the cloned profile metadata");
            clone.aerixId = UUID.randomUUID().toString();
            if (!safeName.isEmpty()) clone.name = safeName;
            clone.write();
            return clone;
        } catch (IOException | RuntimeException e) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(target);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Could not clone the profile", e);
        }
    }

    /** Export the profile directory and, for shared-data profiles, the shared game directory. */
    public static void writeBackup(Instance instance, OutputStream output) throws IOException {
        if (instance == null || instance.mInstanceRoot == null || !instance.mInstanceRoot.isDirectory()) {
            throw new IOException("The selected profile directory is unavailable");
        }
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            Set<String> entries = new HashSet<>();
            Set<String> visitedDirectories = new HashSet<>();
            addBackupDirectory(zip, instance.mInstanceRoot, instance.mInstanceRoot,
                    "profile/", entries, visitedDirectories);
            if (instance.sharedData && SHARED_DATA_DIRECTORY.isDirectory()) {
                addBackupDirectory(zip, SHARED_DATA_DIRECTORY, SHARED_DATA_DIRECTORY,
                        "shared-data/", entries, visitedDirectories);
            }
            String lineBreak = String.valueOf((char) 10);
            String safeName = instance.name == null ? "" : instance.name.replace((char) 10, ' ');
            String safeVersion = instance.versionId == null ? "" : instance.versionId.replace((char) 10, ' ');
            String metadata = "Aerix profile backup" + lineBreak
                    + "name=" + safeName + lineBreak
                    + "version=" + safeVersion + lineBreak
                    + "sharedDataIncluded=" + instance.sharedData + lineBreak;
            zip.putNextEntry(new ZipEntry("backup-info.txt"));
            zip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
        }
    }

    private static void addBackupDirectory(ZipOutputStream zip, File directory, File root,
                                           String prefix, Set<String> entries,
                                           Set<String> visitedDirectories) throws IOException {
        String canonicalRoot = root.getCanonicalPath();
        String canonicalDirectory = directory.getCanonicalPath();
        if (!canonicalDirectory.equals(canonicalRoot)
                && !canonicalDirectory.startsWith(canonicalRoot + File.separator)) {
            throw new IOException("A profile file resolves outside its backup directory");
        }
        if (!visitedDirectories.add(prefix + canonicalDirectory)) return;
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Could not read profile directory " + directory.getName());
        for (File child : children) {
            String canonicalChild = child.getCanonicalPath();
            if (!canonicalChild.equals(canonicalRoot)
                    && !canonicalChild.startsWith(canonicalRoot + File.separator)) {
                throw new IOException("A profile file resolves outside its backup directory");
            }
            if (child.isDirectory()) {
                addBackupDirectory(zip, child, root, prefix, entries, visitedDirectories);
                continue;
            }
            if (!child.isFile()) continue;
            String relative = root.toURI().relativize(child.getCanonicalFile().toURI()).getPath();
            String zipName = prefix + relative;
            if (!entries.add(zipName)) continue;
            zip.putNextEntry(new ZipEntry(zipName));
            try (FileInputStream input = new FileInputStream(child)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        }
    }

    /**
     * Create a new instance intended for first-time launcher users.
     */
    private static void createFirstTimeInstance() throws IOException {
        internalCreateInstance((instance)-> {
            instance.sharedData = true;
            instance.versionId = "1.12.2";
        }, null);
    }

    /**
     * Create a new instance based on a default template.
     * @return the new instance
     */
    public static Instance createDefaultInstance() throws IOException {
        return createInstance((instance)-> {
            instance.sharedData = true;
            instance.versionId = Instance.VERSION_LATEST_RELEASE;
        }, null);
    }

    /**
     * Create an instance without attempting to load the instance list first. Only use this
     * method during initialization.
     */
    private static Instance internalCreateInstance(InstanceSetter instanceSetter, String namePrefix) throws IOException{
        File root = findNewInstanceRoot(namePrefix);
        FileUtils.ensureDirectory(root);
        Instance instance = new Instance();
        instance.mInstanceRoot = root;
        instance.aerixId = UUID.randomUUID().toString();
        instanceSetter.setInstanceProperties(instance);
        instance.write();
        return instance;
    }

    /**
     * Create a new instance with defaults set by user
     * @param instanceSetter setter function called to set user parameters
     * @param namePrefix a name prefix (for the user to easily distinguish installed instances)
     * @return the created instance
     * @throws IOException if directory creation/instance writing fails
     */
    public static Instance createInstance(InstanceSetter instanceSetter, String namePrefix) throws IOException {
        return internalCreateInstance(instanceSetter, namePrefix);
    }

    /**
     * Load the currently selected instance. Note that this method must not be used along with any code
     * which uses getImmutableInstanceList()
     * @return currently selected instance
     */
    public static Instance loadSelectedInstance() {
        File selectedInstanceLocation = selectedInstanceLocation();
        Instance instance = read(selectedInstanceLocation, Instance.class);
        if(instance == null) return null;
        instance.sanitize();
        return instance;
    }

    /**
     * Rename the provided instance directory. This will apply the new name only if it's unique.
     * If no name provided - using bare UUID. If a name conflict - newName as prefix + UUID.
     * @param instance Instance
     * @param newName New instance name
     */
    public static void renameInstanceDirectory(Instance instance, String newName) {
        if(newName == null) return;
        if(newName.trim().isEmpty())
            newName = String.valueOf(UUID.randomUUID());
        else
            newName = FileUtils.escapeFileName(newName);
        File targetDirectory = new File(sInstancePath, newName);
        if(targetDirectory.exists())
            targetDirectory = findNewInstanceRoot(newName);
        String oldName = instance.mInstanceRoot.getName();
        if(!instance.mInstanceRoot.renameTo(targetDirectory))
            throw new RuntimeException("Failed to rename instance!");
        instance.mInstanceRoot = targetDirectory;
        if(oldName.equals(LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_INSTANCE, "")))
            setSelectedInstance(instance);
    }
}
