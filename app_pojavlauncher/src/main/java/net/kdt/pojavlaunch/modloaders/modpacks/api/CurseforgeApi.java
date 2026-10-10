package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloader.AcquireableTaskMetadata;
import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.downloader.TaskMetadata;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ForgelikeUtils;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.FabriclikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.ForgelikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.LoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.HashUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MC_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // Minecraft CurseForge class IDs for Mods, Resource Packs, Shader Packs, and Worlds.
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_RESOURCE_PACK_CLASS_ID = 12;
    private static final int CURSEFORGE_WORLD_CLASS_ID = 17;
    private static final int CURSEFORGE_SHADER_CLASS_ID = 6552;
    private static final long MAX_CONTENT_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_WORLD_EXPANDED_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_WORLD_FILES = 20000;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        String projectType = searchFilters.resolvedProjectType();
        int classId = classIdForProjectType(projectType);
        if (classId < 0) return null;
        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MC_GAME_ID);
        params.put("classId", classId);
        params.put("searchFilter", searchFilters.name == null ? "" : searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = GsonJsonUtils.getJsonArraySafe(response, "data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = GsonJsonUtils.getJsonObjectSafe(response, "pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            if (allowModDistribution != null && !allowModDistribution.isJsonNull()
                    && !allowModDistribution.getAsBoolean()) continue;
            JsonObject logo = GsonJsonUtils.getJsonObjectSafe(dataElement, "logo");
            String imageUrl = GsonJsonUtils.getStringSafe(logo, "thumbnailUrl");
            String title = GsonJsonUtils.getStringSafe(dataElement, "name");
            String description = GsonJsonUtils.getStringSafe(dataElement, "summary");
            String projectId = GsonJsonUtils.getStringSafe(dataElement, "id");
            if (projectId == null || projectId.isEmpty()) continue;
            boolean isModpack = "modpack".equals(projectType);
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    isModpack,
                    projectType,
                    projectId,
                    title == null ? "CurseForge project" : title,
                    description == null ? "" : description,
                    imageUrl);
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        JsonElement totalCount = paginationInfo == null ? null : paginationInfo.get("totalCount");
        try {
            curseforgeSearchResult.totalResultCount = totalCount == null || totalCount.isJsonNull()
                    ? dataArray.size() : totalCount.getAsInt();
        } catch (RuntimeException ignored) {
            curseforgeSearchResult.totalResultCount = dataArray.size();
        }
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        String requestedVersion = item.requestedMinecraftVersion;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id, requestedVersion);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        int length = allModDetails.size();
        String[] versionNames = new String[length];
        String[] mcVersionNames = new String[length];
        String[] versionUrls = new String[length];
        String[] hashes = new String[length];
        String[] versionIds = new String[length];
        String[] fileNames = new String[length];
        for(int i = 0; i < allModDetails.size(); i++) {
            JsonObject file = allModDetails.get(i);
            versionIds[i] = GsonJsonUtils.getStringSafe(file, "id");
            fileNames[i] = GsonJsonUtils.getStringSafe(file, "fileName");
            versionNames[i] = GsonJsonUtils.getStringSafe(file, "displayName");
            if (versionNames[i] == null || versionNames[i].trim().isEmpty()) {
                versionNames[i] = fileNames[i] == null ? "CurseForge file" : fileNames[i];
            }
            JsonElement downloadUrl = file.get("downloadUrl");
            if (downloadUrl != null && !downloadUrl.isJsonNull()) versionUrls[i] = downloadUrl.getAsString();

            JsonArray gameVersions = file.getAsJsonArray("gameVersions");
            if (gameVersions != null) {
                for(JsonElement jsonElement : gameVersions) {
                    if (jsonElement == null || jsonElement.isJsonNull()) continue;
                    String gameVersion = jsonElement.getAsString();
                    if(!sMcVersionPattern.matcher(gameVersion).matches()) continue;
                    mcVersionNames[i] = gameVersion;
                    break;
                }
            }
            hashes[i] = getSha1FromModData(file);
        }
        return new ModDetail(item, versionNames, mcVersionNames, versionUrls, hashes,
                versionIds, fileNames, null, null, item.projectType);
    }

    @Override
    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
        if (modDetail == null) throw new IOException("Missing CurseForge project details");
        if (modDetail.isModpack || "modpack".equals(modDetail.projectType)) {
            return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installCurseforgeZip);
        }
        try {
            installSingleProject(modDetail, selectedVersion);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        return null;
    }

    private static int classIdForProjectType(String projectType) {
        if ("modpack".equals(projectType)) return CURSEFORGE_MODPACK_CLASS_ID;
        if ("mod".equals(projectType)) return CURSEFORGE_MOD_CLASS_ID;
        if ("resourcepack".equals(projectType)) return CURSEFORGE_RESOURCE_PACK_CLASS_ID;
        if ("shader".equals(projectType)) return CURSEFORGE_SHADER_CLASS_ID;
        if ("world".equals(projectType)) return CURSEFORGE_WORLD_CLASS_ID;
        return -1;
    }

    private void installSingleProject(ModDetail detail, int selectedVersion) throws IOException {
        if (selectedVersion < 0 || detail.versionIds == null || selectedVersion >= detail.versionIds.length) {
            throw new IOException("Choose a valid CurseForge file version");
        }
        final long projectId;
        final long fileId;
        try {
            projectId = Long.parseLong(detail.id);
            fileId = Long.parseLong(detail.versionIds[selectedVersion]);
        } catch (RuntimeException e) {
            throw new IOException("CurseForge returned an invalid project or file ID", e);
        }
        JsonObject metadata = getFile(projectId, fileId);
        checkRequiredFileFields(metadata);
        String projectType = detail.projectType;
        String folderName;
        if ("mod".equals(projectType)) folderName = "mods";
        else if ("resourcepack".equals(projectType)) folderName = "resourcepacks";
        else if ("shader".equals(projectType)) folderName = "shaderpacks";
        else if ("world".equals(projectType)) folderName = "saves";
        else throw new IOException("Unsupported CurseForge content type: " + projectType);

        String fileName = GsonJsonUtils.getStringSafe(metadata, "fileName");
        validateContentFileName(fileName, projectType);
        long size = metadata.get("fileLength").getAsLong();
        if (size <= 0 || size > MAX_CONTENT_FILE_BYTES) {
            throw new IOException("CurseForge file is empty or exceeds the 512 MiB download limit");
        }
        String sha1 = getSha1FromModData(metadata);
        if (sha1 == null || !sha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IOException("CurseForge did not provide a valid SHA-1 checksum; refusing an unverified download");
        }
        URL downloadUrl;
        try {
            downloadUrl = new URL(getDownloadUrl(metadata));
        } catch (Exception e) {
            throw new IOException("CurseForge returned an invalid download URL", e);
        }
        validateContentDownloadUrl(downloadUrl);

        Instance selectedInstance = Instances.loadSelectedInstance();
        if (selectedInstance == null) throw new IOException("Select a Minecraft profile before installing content");
        File gameRoot = selectedInstance.getGameDirectory().getCanonicalFile();
        File category = new File(gameRoot, folderName).getCanonicalFile();
        if (!category.getPath().startsWith(gameRoot.getPath() + File.separator)) {
            throw new IOException("CurseForge install directory escapes the selected profile");
        }
        FileUtils.ensureDirectory(category);

        if ("world".equals(projectType)) {
            File temporaryArchive = new File(Tools.DIR_CACHE, "curseforge-world-" + UUID.randomUUID() + ".zip");
            try {
                downloadVerifiedFile(temporaryArchive, downloadUrl, size, sha1);
                installWorldArchive(temporaryArchive, category, detail.title);
            } finally {
                if (temporaryArchive.exists() && !temporaryArchive.delete()) {
                    Log.w("CurseforgeApi", "Could not remove temporary world archive " + temporaryArchive);
                }
            }
            return;
        }

        File target = chooseUniqueContentTarget(category, fileName);
        downloadVerifiedFile(target, downloadUrl, size, sha1);
    }

    private void downloadVerifiedFile(File target, URL downloadUrl, long size, String sha1) throws IOException {
        ArrayList<TaskMetadata> downloads = new ArrayList<>(1);
        downloads.add(new TaskMetadata(target, downloadUrl, size, sha1, DownloadMirror.DOWNLOAD_CLASS_NONE));
        try {
            new CurseContentDownloader().download(downloads);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            target.delete();
            throw new IOException("CurseForge download was cancelled", e);
        } catch (IOException e) {
            target.delete();
            throw e;
        } catch (RuntimeException e) {
            target.delete();
            throw new IOException("CurseForge download failed", e);
        }
        boolean verified;
        try {
            verified = target.isFile() && target.length() == size && HashUtils.compareSHA1(target, sha1);
        } catch (IOException e) {
            target.delete();
            throw e;
        }
        if (!verified) {
            target.delete();
            throw new IOException("The downloaded CurseForge file failed size or SHA-1 verification");
        }
    }

    private static void validateContentDownloadUrl(URL url) throws IOException {
        String protocol = url.getProtocol();
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        boolean trustedHost = "forgecdn.net".equals(host) || host.endsWith(".forgecdn.net")
                || "curseforge.com".equals(host) || host.endsWith(".curseforge.com");
        if (!"https".equalsIgnoreCase(protocol) || !trustedHost || url.getUserInfo() != null) {
            throw new IOException("Refusing an untrusted or insecure CurseForge download URL");
        }
    }

    private static void validateContentFileName(String fileName, String projectType) throws IOException {
        if (fileName == null || fileName.trim().isEmpty() || fileName.length() > 240
                || fileName.contains("/") || fileName.contains("\\") || fileName.indexOf('\0') >= 0
                || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IOException("CurseForge returned an unsafe filename");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if ("mod".equals(projectType) && !lower.endsWith(".jar")) {
            throw new IOException("This CurseForge mod version is not a Java .jar file");
        }
        if (("resourcepack".equals(projectType) || "shader".equals(projectType) || "world".equals(projectType))
                && !lower.endsWith(".zip")) {
            throw new IOException("This CurseForge project is not a supported .zip archive");
        }
    }

    private static File chooseUniqueContentTarget(File category, String fileName) throws IOException {
        String rootPath = category.getCanonicalPath();
        int extensionIndex = fileName.lastIndexOf('.');
        String stem = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
        for (int suffix = 0; suffix <= 999; suffix++) {
            String candidate = suffix == 0 ? fileName : stem + " (" + suffix + ")" + extension;
            File target = new File(category, candidate).getCanonicalFile();
            if (!target.getPath().startsWith(rootPath + File.separator)) {
                throw new IOException("CurseForge filename escapes its install folder");
            }
            if (!target.exists()) return target;
        }
        throw new IOException("Could not choose a unique destination filename");
    }

    private static void installWorldArchive(File archive, File savesDirectory, String title) throws IOException {
        String baseName = safeWorldFolderName(title);
        File staging = new File(savesDirectory, ".aerix-import-" + UUID.randomUUID()).getCanonicalFile();
        String savesPath = savesDirectory.getCanonicalPath();
        if (!staging.getPath().startsWith(savesPath + File.separator) || !staging.mkdirs()) {
            throw new IOException("Could not prepare a safe temporary world folder");
        }
        boolean installed = false;
        try (ZipFile zipFile = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int count = 0;
            long declaredTotal = 0;
            long actualTotal = 0;
            byte[] buffer = new byte[32 * 1024];
            String stagingPath = staging.getCanonicalPath();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_WORLD_FILES) throw new IOException("World archive contains too many files");
                String entryName = entry.getName();
                validateZipEntryPath(entryName);
                if (entry.isDirectory()) continue;
                if (entry.getSize() > 0) {
                    declaredTotal += entry.getSize();
                    if (declaredTotal > MAX_WORLD_EXPANDED_BYTES) {
                        throw new IOException("World archive expands beyond the 1 GiB safety limit");
                    }
                }
                File outputFile = new File(staging, entryName).getCanonicalFile();
                if (!outputFile.getPath().startsWith(stagingPath + File.separator)) {
                    throw new IOException("World archive contains a path outside its save folder");
                }
                FileUtils.ensureParentDirectory(outputFile);
                try (InputStream input = zipFile.getInputStream(entry);
                     FileOutputStream output = new FileOutputStream(outputFile)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        actualTotal += read;
                        if (actualTotal > MAX_WORLD_EXPANDED_BYTES) {
                            throw new IOException("World archive expands beyond the 1 GiB safety limit");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
            File worldRoot = findWorldRoot(staging, 0);
            if (worldRoot == null) throw new IOException("The downloaded archive does not contain a Minecraft level.dat");
            File target = chooseUniqueDirectory(savesDirectory, baseName);
            if (!worldRoot.renameTo(target)) throw new IOException("Could not move the world into the selected profile");
            installed = true;
        } finally {
            deleteTree(staging);
            if (!installed) Log.w("CurseforgeApi", "Removed incomplete world import from " + staging);
        }
    }

    private static File findWorldRoot(File folder, int depth) {
        if (new File(folder, "level.dat").isFile()) return folder;
        if (depth >= 3) return null;
        File[] children = folder.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findWorldRoot(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void validateZipEntryPath(String entryName) throws IOException {
        if (entryName == null || entryName.isEmpty() || entryName.startsWith("/")
                || entryName.contains("\\") || entryName.indexOf('\0') >= 0 || entryName.contains(":")) {
            throw new IOException("World archive contains an unsafe path");
        }
        String normalized = entryName;
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) throw new IOException("World archive contains an unsafe path");
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("World archive contains an unsafe path");
            }
        }
    }

    private static File chooseUniqueDirectory(File parent, String name) throws IOException {
        String rootPath = parent.getCanonicalPath();
        for (int suffix = 0; suffix <= 999; suffix++) {
            File candidate = new File(parent, suffix == 0 ? name : name + " (" + suffix + ")").getCanonicalFile();
            if (!candidate.getPath().startsWith(rootPath + File.separator)) {
                throw new IOException("World save path escapes the selected profile");
            }
            if (!candidate.exists()) return candidate;
        }
        throw new IOException("Could not choose a unique world-save folder");
    }

    private static String safeWorldFolderName(String title) {
        String value = title == null ? "Imported world" : title.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ");
        while (value.startsWith(".")) value = value.substring(1);
        if (value.isEmpty()) value = "Imported world";
        if (value.length() > 80) value = value.substring(0, 80).trim();
        return value;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) Log.w("CurseforgeApi", "Could not clean up " + file);
    }

    private static final class CurseContentDownloader extends Downloader {
        CurseContentDownloader() { super(ProgressLayout.INSTALL_MODPACK); }
        void download(ArrayList<TaskMetadata> files) throws IOException, InterruptedException {
            runDownloads(files);
        }
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installCurseforgeZip);
    }

    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId,
                                    String requestedGameVersion) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);
        if (requestedGameVersion != null && !requestedGameVersion.trim().isEmpty()) {
            params.put("gameVersion", requestedGameVersion.trim());
        }

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        Log.i("CurseforgeApi", "data...");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;
        Log.i("CurseforgeApi", "filtering...");
        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            JsonElement serverPack = fileInfo.get("isServerPack");
            if(serverPack != null && !serverPack.isJsonNull() && serverPack.getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        Log.i("CurseforgeApi", "pag_end");
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private LoaderInstaller installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            try {
                new CurseDownloader().start(curseManifest, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private LoaderInstaller createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        LoaderInstaller loaderInstaller;
        switch (modLoaderName) {
            case "forge":
                return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, minecraft.version, modLoaderVersion);
            case "fabric":
                return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, minecraft.version, modLoaderVersion);
            case "neoforge":
                return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, minecraft.version, modLoaderVersion);
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
    }

    private String getDownloadUrl(JsonObject fileMetadata) throws IOException {
        if (fileMetadata == null || !fileMetadata.has("modId") || !fileMetadata.has("id")
                || fileMetadata.get("modId").isJsonNull() || fileMetadata.get("id").isJsonNull()) {
            throw new IOException("CurseForge returned incomplete file metadata");
        }
        long projectID = fileMetadata.get("modId").getAsLong();
        long fileID = fileMetadata.get("id").getAsLong();
        JsonObject response = mApiHandler.get("mods/" + projectID + "/files/" + fileID + "/download-url",
                JsonObject.class);
        JsonElement data = response == null ? null : response.get("data");
        if (data != null && !data.isJsonNull()) {
            String officialUrl = data.getAsString();
            if (officialUrl != null && !officialUrl.trim().isEmpty()) return officialUrl;
        }
        String fileName = GsonJsonUtils.getStringSafe(fileMetadata, "fileName");
        if (fileName == null || fileName.isEmpty()) throw new IOException("CurseForge did not provide a download filename");
        String encodedName;
        try {
            encodedName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IOException("Could not encode the CurseForge filename", impossible);
        }
        return String.format(java.util.Locale.ROOT, "https://edge.forgecdn.net/files/%s/%s/%s",
                fileID / 1000, fileID % 1000, encodedName);
    }

    private void checkRequiredFileFields(JsonObject fileMetadata) throws IOException {
        if(fileMetadata == null || fileMetadata.isJsonNull()) throw new IOException("CurseForge file metadata is missing");
        JsonElement projectId = fileMetadata.get("modId");
        JsonElement fileId = fileMetadata.get("id");
        JsonElement length = fileMetadata.get("fileLength");
        if (projectId == null || projectId.isJsonNull() || fileId == null || fileId.isJsonNull()
                || length == null || length.isJsonNull()) {
            throw new IOException("CurseForge file metadata is missing an ID or file length");
        }
        try {
            if (projectId.getAsLong() <= 0 || fileId.getAsLong() <= 0 || length.getAsLong() <= 0) {
                throw new IOException("CurseForge returned invalid file IDs or size");
            }
        } catch (RuntimeException e) {
            throw new IOException("CurseForge returned malformed file IDs or size", e);
        }
    }

    private @Nullable JsonObject getFile(long projectID, long fileID) {
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        return GsonJsonUtils.getJsonObjectSafe(response, "data");
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }

    class CurseDownloader extends Downloader {

        public CurseDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        public void start(CurseManifest curseManifest, File instanceDestination) throws IOException, InterruptedException {
            ArrayList<AcquireableTaskMetadata> taskMetadatas = new ArrayList<>(curseManifest.files.length);
            for(final CurseManifest.CurseFile file : curseManifest.files) {
                taskMetadatas.add(new CurseTaskMetadata(file, instanceDestination));
            }
            runDownloads(taskMetadatas);
        }
    }

    class CurseTaskMetadata extends AcquireableTaskMetadata {
        private final CurseManifest.CurseFile mFile;
        private final File mInstanceDestination;

        public CurseTaskMetadata(CurseManifest.CurseFile mFile, File mInstanceDestination) {
            super(DownloadMirror.DOWNLOAD_CLASS_METADATA);
            this.mFile = mFile;
            this.mInstanceDestination = mInstanceDestination;
        }

        @Override
        public void acquireMetadata() throws IOException {
            JsonObject fileMetadata = getFile(mFile.projectID, mFile.fileID);
            checkRequiredFileFields(fileMetadata);
            String url = getDownloadUrl(fileMetadata);
            this.url = new URL(url);
            this.path = new File(mInstanceDestination, "mods/"+ URLDecoder.decode(FileUtils.getFileName(url),"UTF-8"));
            FileUtils.ensureParentDirectorySilently(this.path);
            this.sha1Hash = getSha1FromModData(fileMetadata);
            this.size = fileMetadata.get("fileLength").getAsLong();
        }
    }
}
