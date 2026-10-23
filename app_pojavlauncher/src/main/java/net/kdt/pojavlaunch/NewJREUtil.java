package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;

import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.MathUtils;
import net.kdt.pojavlaunch.utils.SignatureCheckUtil;
import net.kdt.pojavlaunch.utils.jre.RuntimeSelectionException;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.kdt.pojavlaunch.R;

public class NewJREUtil {
    private static final String DOWNLOAD_URL = "https://mojolauncher.github.io/jre-download/";

    private static String getRemoteRuntimeVersion(InternalRuntime internalRuntime) throws IOException{
        return DownloadUtils.downloadString(DOWNLOAD_URL+internalRuntime.path+"/version");
    }

    private static boolean checkLastUpdateTime(InternalRuntime internalRuntime) {
        long lastUpdateTime = MultiRTUtils.readLastUpdateTime(internalRuntime.name);
        long currentTime = System.currentTimeMillis() / 1000L;
        return lastUpdateTime != -1 && currentTime - lastUpdateTime < 259200;
    }

    private static void writeLastUpdateTime(InternalRuntime internalRuntime) {
        MultiRTUtils.writeLastUpdateTime(internalRuntime.name, System.currentTimeMillis() / 1000L);
    }

    private static void checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) throws RuntimeSelectionException {
        String remote_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        if(installed_runtime_version != null && checkLastUpdateTime(internalRuntime)) return;
        try {
            remote_runtime_version = getRemoteRuntimeVersion(internalRuntime);
        }catch (IOException exc) {
            Log.i("NewJreUtil", "Failed to get remote runtime version", exc);
            // We failed to get the version of the runtime available on the web server.
            // Let's just hope that we have an internal version installed in that case.
            if(installed_runtime_version == null)
                throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INTERNAL_RUNTIME_MISSING, internalRuntime.majorVersion);
            return;
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!remote_runtime_version.equals(installed_runtime_version)) unpackInternalRuntime(assetManager, internalRuntime, remote_runtime_version);
        writeLastUpdateTime(internalRuntime);
    }

    private static class RuntimeDownloaderVerifier {

        private final Map<String, byte[]> mSignatures;
        private final String mRuntimePath;
        private final byte[] mDownloadBuffer = new byte[8192];
        private final SignatureCheckUtil mSignatureCheckUtil;

        public RuntimeDownloaderVerifier(Map<String, byte[]> signatures, InternalRuntime internalRuntime, SignatureCheckUtil mSignatureCheckUtil) {
            this.mSignatures = signatures;
            this.mRuntimePath = DOWNLOAD_URL + internalRuntime.path + "/";
            this.mSignatureCheckUtil = mSignatureCheckUtil;
        }

        public RuntimeDownloaderVerifier(Map<String, byte[]> signatures, String runtimePath, SignatureCheckUtil mSignatureCheckUtil) {
            this.mSignatures = signatures;
            this.mRuntimePath = runtimePath;
            this.mSignatureCheckUtil = mSignatureCheckUtil;
        }

        public boolean downloadAndVerify(String component, File output, int progressString) throws IOException {
            DownloadUtils.downloadFileMonitored(
                    mRuntimePath + component, output, mDownloadBuffer,
                    new DownloaderProgressWrapper(progressString, ProgressLayout.UNPACK_RUNTIME)
            );
            byte[] signature = mSignatures.get(component);
            try (FileInputStream fileInputStream = new FileInputStream(output)){
                return mSignatureCheckUtil.verify(fileInputStream, signature);
            }
        }
    }

    private static void throwInstallFail(InternalRuntime internalRuntime, Throwable cause) throws RuntimeSelectionException {
        RuntimeSelectionException e = new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, internalRuntime.majorVersion);
        e.initCause(cause);
        throw e;
    }

    private static void throwInstallFail(InternalRuntime internalRuntime) throws RuntimeSelectionException {
        throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, internalRuntime.majorVersion);
    }

    private static void unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String versionSignatures) throws RuntimeSelectionException {
        Map<String, byte[]> signatures = SignatureCheckUtil.decodeSignatureBundle(versionSignatures);
        String platformBinFile = "bin-"+archAsString(Tools.DEVICE_ARCHITECTURE)+".tar.xz";
        if(!signatures.containsKey("universal.tar.xz") || !signatures.containsKey(platformBinFile)) {
            throwInstallFail(internalRuntime);
        }

        File universalCache = null, platformCache = null;
        try {
            SignatureCheckUtil signatureCheckUtil = SignatureCheckUtil.create(assetManager);
            universalCache = File.createTempFile("jre-install-", "-universal", Tools.DIR_CACHE);
            platformCache = File.createTempFile("jre-install-", "-platform", Tools.DIR_CACHE);
            RuntimeDownloaderVerifier runtimeDownloaderVerifier = new RuntimeDownloaderVerifier(signatures, internalRuntime, signatureCheckUtil);
            if (!runtimeDownloaderVerifier.downloadAndVerify("universal.tar.xz", universalCache, R.string.downloading_java_runtime_uni) ||
                    !runtimeDownloaderVerifier.downloadAndVerify(platformBinFile, platformCache, R.string.downloading_java_runtime_platform)) {
                throwInstallFail(internalRuntime);
            }

            try (FileInputStream universal = new FileInputStream(universalCache); FileInputStream platform = new FileInputStream(platformCache)) {
                MultiRTUtils.installRuntimeNamedBinpack(universal, platform, internalRuntime.name, versionSignatures);
                MultiRTUtils.postPrepare(internalRuntime.name);
                MultiRTUtils.forceReread(internalRuntime.name);
            }
        } catch (IOException e) {
            throwInstallFail(internalRuntime, e);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
            // Those files being deleted are on a "i wish" basis
            if(universalCache != null && universalCache.isFile())
                universalCache.delete();
            if(platformCache != null && platformCache.isFile())
                platformCache.delete();
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }


    public static void installNewJreIfNeeded(AssetManager assetManager, JVersionList.Version versionInfo) throws IOException, RuntimeSelectionException {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) throw new IOException("No selected instance for runtime selection");
        int baseMinimum = versionInfo.javaVersion == null ? 8 : versionInfo.javaVersion.majorVersion;
        int gameRequiredVersion = net.kdt.pojavlaunch.utils.jre.ModJavaRequirements.minimumJava(instance.getGameDirectory(), baseMinimum);
        // Vanilla legacy metadata may say Java 8 while a mod's mixins explicitly need Java 17+;
        // ModJavaRequirements has already folded that in, so gameRequiredVersion is the truth.
        String profileRuntime = Tools.getSelectedRuntime(instance);
        Runtime runtime = MultiRTUtils.read(profileRuntime);
        // Partly trust the user with his own selection, if the game can even try to run in this case
        if (runtime.javaVersion >= gameRequiredVersion) {
            // Check whether the selection is an internal runtime
            InternalRuntime internalRuntime = getInternalRuntime(runtime);
            // If it is, check if updates are available from the APK file
            if(internalRuntime != null) {
                checkInternalRuntime(assetManager, internalRuntime);
            }
            return;
        }

        // A legacy game needs Java 8. Returning here without doing anything (the old behaviour)
        // left a fresh install with no runtime and no offer to download one, so detect it and
        // install the real Java 8 build.
        if (gameRequiredVersion <= 8) {
            String installedJava8 = MultiRTUtils.getExactJreName(8);
            if (installedJava8 == null) {
                ExternalRuntime.JRE_8.downloadRuntime(assetManager);
                installedJava8 = ExternalRuntime.JRE_8.name;
            }
            instance.selectedRuntime = installedJava8;
            instance.write();
            return;
        }

        // If the runtime version selected by the user is not appropriate for this version (which means the game won't run at all)
        // automatically pick from either an already installed runtime, or a runtime packed with the launcher
        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        MathUtils.RankedValue<?> nearestInternalRuntime = getNearestInternalRuntime(gameRequiredVersion);

        MathUtils.RankedValue<?> selectedRankedRuntime = MathUtils.objectMin(
                nearestInternalRuntime, nearestInstalledRuntime, (value)->value.rank
        );

        // No possible selections
        if(selectedRankedRuntime == null) {
            throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_SELECTION_FAILED, gameRequiredVersion);
        }

        Object selected = selectedRankedRuntime.value;
        String appropriateRuntime;
        InternalRuntime internalRuntime;

        // Perform checks on the picked runtime
        if(selected instanceof Runtime) {
            Runtime selectedRuntime = (Runtime) selected;
            // If it's an already installed runtime, save its name and check if
            // it's actually an internal one (just in case)
            appropriateRuntime = selectedRuntime.name;
            internalRuntime = getInternalRuntime(selectedRuntime);
        } else if (selected instanceof InternalRuntime) {
            // If it's an internal runtime, set it's name as the appropriate one.
            internalRuntime = (InternalRuntime) selected;
            appropriateRuntime = internalRuntime.name;
        } else {
            throw new RuntimeException("Unexpected type of selected: "+selected.getClass().getName());
        }

        // If it turns out the selected runtime is actually an internal one, attempt automatic installation or update
        if(internalRuntime != null) {
            // Not calling showRuntimeFail on failure here because we did, technically, find the compatible runtime
            checkInternalRuntime(assetManager, internalRuntime);
        }

        instance.selectedRuntime = appropriateRuntime;
        instance.write();
    }

    public enum ExternalRuntime {
        /**
         * Java 8 is published as one zip (jre8-pojav.zip) that holds the same binpack pieces
         * (universal.tar.xz, bin-&lt;arch&gt;.tar.xz, version) as the component folders below.
         * It used to be missing entirely, so the only "Java 8" on offer installed Java 17.
         */
        JRE_8(8, "Internal-8", null,
                "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/jre8-pojav.zip"),
        JRE_17(17, "Internal-17", "components/jre-new", null),
        JRE_21(21, "Internal-21", "components/jre-21", null),
        JRE_25(25, "Internal-25", "components/jre-25", null);

        public final int majorVersion;
        public final String name;
        /** Component folder on the download host; null when the runtime ships as one zip. */
        public final String path;
        /** Full URL of the runtime zip; null for the component-folder runtimes. */
        public final String zipUrl;
        public boolean isDownloading = false;

        ExternalRuntime(int majorVersion, String name, String path, String zipUrl) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
            this.zipUrl = zipUrl;
        }

        /** What the Runtime Manager shows: the Java version, never an internal slot name. */
        public String getLabel() {
            return "Java " + majorVersion;
        }

        public void downloadRuntime(AssetManager assetManager) throws RuntimeSelectionException {
            if (zipUrl != null) downloadZipRuntime(this);
            else tryDownloadExternalRuntime(assetManager, this);
        }
    }

    /**
     * Downloads and installs a runtime published as a single zip of binpack pieces (Java 8).
     * The archive is kept on disk between attempts, so an interrupted download resumes instead of
     * starting over.
     */
    private static void downloadZipRuntime(ExternalRuntime externalRuntime) throws RuntimeSelectionException {
        File archive = new File(Tools.DIR_CACHE, externalRuntime.name + ".zip");
        File universal = null;
        File platform = null;
        try {
            DownloadUtils.downloadFileMonitored(
                    externalRuntime.zipUrl, archive, null,
                    new DownloaderProgressWrapper(R.string.downloading_java_runtime_uni, ProgressLayout.UNPACK_RUNTIME));
            String platformName = "bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz";
            String version = "zip-" + archive.length();
            try (ZipFile zipFile = new ZipFile(archive)) {
                ZipEntry versionEntry = findZipEntry(zipFile, "version");
                if (versionEntry != null) {
                    try (InputStream inputStream = zipFile.getInputStream(versionEntry)) {
                        String stamped = new String(IOUtils.toByteArray(inputStream), StandardCharsets.UTF_8).trim();
                        if (!stamped.isEmpty()) version = stamped;
                    }
                }
                universal = extractZipEntry(zipFile, "universal.tar.xz");
                platform = extractZipEntry(zipFile, platformName);
            }
            try (FileInputStream universalStream = new FileInputStream(universal);
                 FileInputStream platformStream = new FileInputStream(platform)) {
                MultiRTUtils.installRuntimeNamedBinpack(
                        universalStream, platformStream, externalRuntime.name, version);
                MultiRTUtils.postPrepare(externalRuntime.name);
                MultiRTUtils.forceReread(externalRuntime.name);
            }
        } catch (IOException | RuntimeException failure) {
            RuntimeSelectionException error = new RuntimeSelectionException(
                    RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, externalRuntime.majorVersion);
            error.initCause(failure);
            throw error;
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
            if (universal != null && universal.isFile()) universal.delete();
            if (platform != null && platform.isFile()) platform.delete();
        }
    }

    /** Looks an entry up by bare file name; the zip is flat today, but never assume that. */
    private static ZipEntry findZipEntry(ZipFile zipFile, String name) {
        ZipEntry direct = zipFile.getEntry(name);
        if (direct != null) return direct;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            int slash = entryName.lastIndexOf('/');
            if ((slash >= 0 ? entryName.substring(slash + 1) : entryName).equals(name)) return entry;
        }
        return null;
    }

    private static File extractZipEntry(ZipFile zipFile, String name) throws IOException {
        ZipEntry entry = findZipEntry(zipFile, name);
        if (entry == null) throw new IOException("Runtime archive is missing " + name);
        File target = File.createTempFile("jre-install-", "-" + name, Tools.DIR_CACHE);
        try (InputStream inputStream = zipFile.getInputStream(entry);
             OutputStream outputStream = new FileOutputStream(target)) {
            IOUtils.copy(inputStream, outputStream);
        }
        return target;
    }

    private static void tryDownloadExternalRuntime(AssetManager assetManager, ExternalRuntime externalRuntime) throws RuntimeSelectionException {
        try {
            String remoteVersion = DownloadUtils.downloadString(DOWNLOAD_URL + externalRuntime.path + "/version");
            unpackExternalRuntime(assetManager, externalRuntime, remoteVersion);
        } catch (IOException e) {
            throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, externalRuntime.majorVersion);
        }
    }

    private static void unpackExternalRuntime(AssetManager assetManager, ExternalRuntime externalRuntime, String versionSignatures) throws RuntimeSelectionException {
        Map<String, byte[]> signatures = SignatureCheckUtil.decodeSignatureBundle(versionSignatures);
        String platformBinFile = "bin-"+archAsString(Tools.DEVICE_ARCHITECTURE)+".tar.xz";
        if(!signatures.containsKey("universal.tar.xz") || !signatures.containsKey(platformBinFile)) {
            throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, externalRuntime.majorVersion);
        }

        File universalCache = null, platformCache = null;
        try {
            SignatureCheckUtil signatureCheckUtil = SignatureCheckUtil.create(assetManager);
            universalCache = File.createTempFile("jre-install-", "-universal", Tools.DIR_CACHE);
            platformCache = File.createTempFile("jre-install-", "-platform", Tools.DIR_CACHE);

            String runtimePath = DOWNLOAD_URL + externalRuntime.path + "/";
            RuntimeDownloaderVerifier verifier = new RuntimeDownloaderVerifier(signatures, runtimePath, signatureCheckUtil);

            if (!verifier.downloadAndVerify("universal.tar.xz", universalCache, R.string.downloading_java_runtime_uni) ||
                    !verifier.downloadAndVerify(platformBinFile, platformCache, R.string.downloading_java_runtime_platform)) {
                throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, externalRuntime.majorVersion);
            }

            try (FileInputStream universal = new FileInputStream(universalCache); FileInputStream platform = new FileInputStream(platformCache)) {
                MultiRTUtils.installRuntimeNamedBinpack(universal, platform, externalRuntime.name, versionSignatures);
                MultiRTUtils.postPrepare(externalRuntime.name);
                MultiRTUtils.forceReread(externalRuntime.name);
            }
        } catch (IOException e) {
            throw new RuntimeSelectionException(RuntimeSelectionException.RUNTIME_STATE_INSTALLATION_FAILED, externalRuntime.majorVersion);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
            if(universalCache != null && universalCache.isFile())
                universalCache.delete();
            if(platformCache != null && platformCache.isFile())
                platformCache.delete();
        }
    }
}
