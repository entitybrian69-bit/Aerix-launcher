package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.downloader.TaskMetadata;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ForgelikeUtils;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.Lwjgl3ifyUtils;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.FabriclikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.ForgelikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.LoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.Lwjgl3ifyLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", searchFilters.resolvedProjectType()));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            String projectType = hit.get("project_type").getAsString();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    projectType.equals("modpack"),
                    projectType,
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").getAsString()
            );
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        JsonArray response;
        if (item.requestedMinecraftVersion != null && !item.requestedMinecraftVersion.trim().isEmpty()) {
            HashMap<String, Object> params = new HashMap<>();
            params.put("game_versions", jsonArrayParameter(item.requestedMinecraftVersion.trim()));
            response = mApiHandler.get("project/" + item.id + "/version", params, JsonArray.class);
        } else {
            response = mApiHandler.get("project/" + item.id + "/version", JsonArray.class);
        }
        if (response == null) return null;
        int count = response.size();
        String[] names = new String[count];
        String[] mcNames = new String[count];
        String[] urls = new String[count];
        String[] hashes = new String[count];
        String[] versionIds = new String[count];
        String[] filenames = new String[count];
        String[][] dependencies = new String[count][];
        String[][] loaders = new String[count][];

        for (int i = 0; i < count; ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = jsonString(version, "name", "Version " + (i + 1));
            versionIds[i] = jsonString(version, "id", null);
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            mcNames[i] = gameVersions == null || gameVersions.size() == 0
                    ? "" : gameVersions.get(0).getAsString();
            JsonArray versionLoaders = version.getAsJsonArray("loaders");
            loaders[i] = jsonStringArray(versionLoaders);

            JsonArray files = version.getAsJsonArray("files");
            if (files == null || files.size() == 0) continue;
            JsonObject primaryFile = files.get(0).getAsJsonObject();
            for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
                JsonObject candidate = files.get(fileIndex).getAsJsonObject();
                if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                    primaryFile = candidate;
                    break;
                }
            }
            urls[i] = jsonString(primaryFile, "url", null);
            filenames[i] = jsonString(primaryFile, "filename", null);
            JsonObject hashesMap = primaryFile.getAsJsonObject("hashes");
            hashes[i] = hashesMap == null ? null : jsonString(hashesMap, "sha1", null);

            ArrayList<String> required = new ArrayList<>();
            JsonArray dependencyArray = version.getAsJsonArray("dependencies");
            if (dependencyArray != null) {
                for (int dependencyIndex = 0; dependencyIndex < dependencyArray.size(); dependencyIndex++) {
                    JsonObject dependency = dependencyArray.get(dependencyIndex).getAsJsonObject();
                    if (!"required".equals(jsonString(dependency, "dependency_type", ""))) continue;
                    String exactVersion = jsonString(dependency, "version_id", null);
                    String projectId = jsonString(dependency, "project_id", null);
                    if (exactVersion != null && !exactVersion.isEmpty()) required.add("version:" + exactVersion);
                    else if (projectId != null && !projectId.isEmpty()) required.add("project:" + projectId);
                }
            }
            dependencies[i] = required.toArray(new String[0]);
        }

        return new ModDetail(item, names, mcNames, urls, hashes, versionIds, filenames,
                dependencies, loaders, item.projectType);
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String[] jsonStringArray(JsonArray array) {
        if (array == null) return new String[0];
        String[] values = new String[array.size()];
        for (int i = 0; i < array.size(); i++) values[i] = array.get(i).getAsString();
        return values;
    }

    @Override
    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException {
        if (modDetail == null) throw new IOException("Missing project details");
        if (modDetail.isModpack || "modpack".equals(modDetail.projectType)) {
            return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installMrpack);
        }
        installProjectWithDependencies(modDetail, selectedVersion);
        return null;
    }

    private void installProjectWithDependencies(ModDetail detail, int selectedVersion) throws IOException {
        if (selectedVersion < 0 || selectedVersion >= detail.versionNames.length
                || detail.versionIds == null || selectedVersion >= detail.versionIds.length
                || !isSafeId(detail.id)) {
            throw new IOException("This Modrinth version cannot be installed safely");
        }
        String gameVersion = detail.mcVersionNames[selectedVersion];
        if (gameVersion == null || gameVersion.isEmpty()) {
            throw new IOException("The selected version has no Minecraft version metadata");
        }
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) throw new IOException("Select a Minecraft profile before installing content");

        String versionId = detail.versionIds[selectedVersion];
        if (!isSafeId(versionId)) throw new IOException("Modrinth returned an invalid version identifier");
        String[] loaders = detail.versionLoaders != null && selectedVersion < detail.versionLoaders.length
                ? detail.versionLoaders[selectedVersion] : new String[0];
        LinkedHashMap<String, ResolvedModrinthFile> resolvedFiles = new LinkedHashMap<>();
        HashMap<String, String> projectVersions = new HashMap<>();
        HashSet<String> visitedVersions = new HashSet<>();
        resolveVersionTree(versionId, detail.id, detail.projectType, gameVersion, loaders,
                0, visitedVersions, projectVersions, resolvedFiles);

        File gameRoot = instance.getGameDirectory().getCanonicalFile();
        ArrayList<TaskMetadata> downloads = new ArrayList<>();
        HashMap<String, String> plannedTargets = new HashMap<>();
        long totalBytes = 0;
        for (ResolvedModrinthFile resolved : resolvedFiles.values()) {
            File target = chooseInstallTarget(gameRoot, resolved, plannedTargets);
            String canonicalTarget = target.getCanonicalPath();
            if (target.isFile() && sha1File(target).equalsIgnoreCase(resolved.sha1)) continue;
            if (plannedTargets.containsKey(canonicalTarget)) continue;
            plannedTargets.put(canonicalTarget, resolved.sha1);
            totalBytes += resolved.size;
            if (totalBytes > 2L * 1024L * 1024L * 1024L) {
                throw new IOException("Required content exceeds the 2 GiB safety limit");
            }
            downloads.add(new TaskMetadata(target, new URL(resolved.url), resolved.size,
                    resolved.sha1, DownloadMirror.DOWNLOAD_CLASS_NONE));
        }
        if (!downloads.isEmpty()) {
            try {
                new ModrinthDownloader().downloadInstallFiles(downloads);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Modrinth installation was cancelled", e);
            }
        }
    }

    private void resolveVersionTree(String versionId, String fallbackProjectId, String fallbackType,
                                    String gameVersion, String[] loaders, int depth,
                                    Set<String> visitedVersions, Map<String, String> projectVersions,
                                    LinkedHashMap<String, ResolvedModrinthFile> resolvedFiles) throws IOException {
        if (depth > 16) throw new IOException("Modrinth dependency graph is too deep to resolve safely");
        if (!isSafeId(versionId) || !isSafeId(fallbackProjectId)) {
            throw new IOException("Modrinth returned an invalid dependency identifier");
        }
        if (visitedVersions.contains(versionId)) return;
        if (visitedVersions.size() >= 64) throw new IOException("Modrinth dependency graph exceeds 64 versions");

        JsonObject version = mApiHandler.get("version/" + versionId, JsonObject.class);
        if (version == null) throw new IOException("Could not resolve Modrinth version " + versionId);
        String projectId = jsonString(version, "project_id", fallbackProjectId);
        String canonicalVersionId = jsonString(version, "id", versionId);
        if (!isSafeId(projectId) || !isSafeId(canonicalVersionId)) {
            throw new IOException("Modrinth returned malformed dependency metadata");
        }
        String alreadyResolvedVersion = projectVersions.get(projectId);
        if (alreadyResolvedVersion != null && !alreadyResolvedVersion.equals(canonicalVersionId)) {
            throw new IOException("Dependency conflict: project " + projectId
                    + " requires both " + alreadyResolvedVersion + " and " + canonicalVersionId);
        }
        projectVersions.put(projectId, canonicalVersionId);
        visitedVersions.add(versionId);
        visitedVersions.add(canonicalVersionId);

        String projectType = fallbackType;
        if (!projectId.equals(fallbackProjectId) || projectType == null || projectType.isEmpty()) {
            JsonObject project = mApiHandler.get("project/" + projectId, JsonObject.class);
            if (project == null) throw new IOException("Could not resolve dependency project " + projectId);
            projectType = jsonString(project, "project_type", "mod");
        }
        if (!("mod".equals(projectType) || "resourcepack".equals(projectType) || "shader".equals(projectType))) {
            throw new IOException("Unsupported required dependency type: " + projectType);
        }

        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.size() == 0) throw new IOException("A required Modrinth version has no downloadable file");
        JsonObject file = files.get(0).getAsJsonObject();
        for (int i = 0; i < files.size(); i++) {
            JsonObject candidate = files.get(i).getAsJsonObject();
            if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                file = candidate;
                break;
            }
        }
        String fileName = jsonString(file, "filename", null);
        String downloadUrl = jsonString(file, "url", null);
        JsonObject hashes = file.getAsJsonObject("hashes");
        String sha1 = hashes == null ? null : jsonString(hashes, "sha1", null);
        long fileSize = -1;
        try {
            if (file.has("size") && !file.get("size").isJsonNull()) fileSize = file.get("size").getAsLong();
        } catch (RuntimeException ignored) {
            fileSize = -1;
        }
        validateModrinthFile(fileName, downloadUrl, sha1, fileSize);
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (("mod".equals(projectType) && !lowerFileName.endsWith(".jar"))
                || (("resourcepack".equals(projectType) || "shader".equals(projectType))
                && !lowerFileName.endsWith(".zip"))) {
            throw new IOException("Modrinth returned a file with an unexpected type for " + projectType);
        }
        if (!resolvedFiles.containsKey(canonicalVersionId)) {
            resolvedFiles.put(canonicalVersionId, new ResolvedModrinthFile(
                    projectId, canonicalVersionId, projectType, fileName, downloadUrl, sha1, fileSize));
        }

        JsonArray dependencies = version.getAsJsonArray("dependencies");
        if (dependencies == null) return;
        for (int i = 0; i < dependencies.size(); i++) {
            JsonObject dependency = dependencies.get(i).getAsJsonObject();
            if (!"required".equals(jsonString(dependency, "dependency_type", ""))) continue;
            String exactVersion = jsonString(dependency, "version_id", null);
            String dependencyProject = jsonString(dependency, "project_id", null);
            if (exactVersion == null || exactVersion.isEmpty()) {
                if (!isSafeId(dependencyProject)) {
                    throw new IOException("A required Modrinth dependency has no resolvable version or project ID");
                }
                exactVersion = findCompatibleVersion(dependencyProject, gameVersion, loaders);
            }
            if (!isSafeId(exactVersion)) throw new IOException("Could not select a compatible dependency version");
            if (dependencyProject == null || dependencyProject.isEmpty()) dependencyProject = projectIdForVersion(exactVersion);
            String dependencyType = projectTypeForProject(dependencyProject);
            resolveVersionTree(exactVersion, dependencyProject, dependencyType, gameVersion, loaders,
                    depth + 1, visitedVersions, projectVersions, resolvedFiles);
        }
    }

    private String findCompatibleVersion(String projectId, String gameVersion, String[] loaders) throws IOException {
        if (!isSafeId(projectId)) throw new IOException("Invalid Modrinth dependency project ID");
        HashMap<String, Object> params = new HashMap<>();
        params.put("game_versions", jsonArrayParameter(gameVersion));
        if (loaders != null && loaders.length > 0) params.put("loaders", jsonArrayParameter(loaders));
        JsonArray versions = mApiHandler.get("project/" + projectId + "/version", params, JsonArray.class);
        if (versions == null) throw new IOException("Could not fetch compatible versions for dependency " + projectId);
        for (int i = 0; i < versions.size(); i++) {
            JsonObject candidate = versions.get(i).getAsJsonObject();
            JsonArray gameVersions = candidate.getAsJsonArray("game_versions");
            if (gameVersions == null || !jsonArrayContains(gameVersions, gameVersion)) continue;
            if (loaders != null && loaders.length > 0) {
                JsonArray candidateLoaders = candidate.getAsJsonArray("loaders");
                if (candidateLoaders == null || !hasLoaderIntersection(candidateLoaders, loaders)) continue;
            }
            String id = jsonString(candidate, "id", null);
            if (isSafeId(id)) return id;
        }
        throw new IOException("No compatible version found for required dependency " + projectId);
    }

    private String projectIdForVersion(String versionId) throws IOException {
        JsonObject version = mApiHandler.get("version/" + versionId, JsonObject.class);
        String projectId = jsonString(version, "project_id", null);
        if (!isSafeId(projectId)) throw new IOException("The dependency version has no project ID");
        return projectId;
    }

    private String projectTypeForProject(String projectId) throws IOException {
        JsonObject project = mApiHandler.get("project/" + projectId, JsonObject.class);
        String type = jsonString(project, "project_type", null);
        if (type == null) throw new IOException("Could not determine dependency project type");
        return type;
    }

    private static boolean jsonArrayContains(JsonArray array, String value) {
        for (int i = 0; i < array.size(); i++) if (value.equals(array.get(i).getAsString())) return true;
        return false;
    }

    private static boolean hasLoaderIntersection(JsonArray candidateLoaders, String[] wantedLoaders) {
        for (int i = 0; i < candidateLoaders.size(); i++) {
            String candidate = candidateLoaders.get(i).getAsString();
            for (String wanted : wantedLoaders) if (candidate.equals(wanted)) return true;
        }
        return false;
    }

    private static String jsonArrayParameter(String... values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) json.append(',');
            json.append('"');
            for (int c = 0; c < values[i].length(); c++) {
                char value = values[i].charAt(c);
                if (value == '\\' || value == '"') json.append('\\');
                json.append(value);
            }
            json.append('"');
        }
        return json.append(']').toString();
    }

    private static boolean isSafeId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,80}");
    }

    private static void validateModrinthFile(String fileName, String downloadUrl, String sha1, long size) throws IOException {
        if (fileName == null || fileName.isEmpty() || fileName.length() > 180
                || fileName.contains("/") || fileName.contains("\\")
                || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IOException("Modrinth returned an unsafe file name");
        }
        if (sha1 == null || !sha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IOException("Refusing to install a Modrinth file without a valid SHA-1 checksum");
        }
        if (size <= 0 || size > 512L * 1024L * 1024L) {
            throw new IOException("Modrinth file size is missing or exceeds the 512 MiB limit");
        }
        try {
            URL url = new URL(downloadUrl);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null
                    || !("modrinth.com".equals(host) || host.endsWith(".modrinth.com"))) {
                throw new IOException("Refusing an untrusted Modrinth download URL");
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Modrinth returned an invalid download URL", e);
        }
    }

    private static File chooseInstallTarget(File gameRoot, ResolvedModrinthFile resolved,
                                            Map<String, String> plannedTargets) throws IOException {
        String folderName;
        if ("mod".equals(resolved.projectType)) folderName = "mods";
        else if ("resourcepack".equals(resolved.projectType)) folderName = "resourcepacks";
        else if ("shader".equals(resolved.projectType)) folderName = "shaderpacks";
        else throw new IOException("Unsupported Modrinth install type: " + resolved.projectType);

        File category = new File(gameRoot, folderName).getCanonicalFile();
        String rootPath = gameRoot.getCanonicalPath();
        if (!category.getCanonicalPath().startsWith(rootPath + File.separator)) {
            throw new IOException("Modrinth install directory resolves outside the selected profile");
        }
        FileUtils.ensureDirectory(category);
        String baseName = resolved.fileName;
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String extension = dot > 0 ? baseName.substring(dot) : "";
        for (int suffix = 0; suffix <= 999; suffix++) {
            String name = suffix == 0 ? baseName : stem + " (" + suffix + ")" + extension;
            File target = new File(category, name).getCanonicalFile();
            if (!target.getCanonicalPath().startsWith(category.getCanonicalPath() + File.separator)) {
                throw new IOException("Modrinth install path escapes its content folder");
            }
            if (target.isFile()) {
                try {
                    if (sha1File(target).equalsIgnoreCase(resolved.sha1)) return target;
                } catch (IOException ignored) {
                    // Preserve the existing file and choose a separate collision-safe filename.
                }
                continue;
            }
            if (!target.exists()) {
                String planned = plannedTargets.get(target.getCanonicalPath());
                if (planned == null || planned.equalsIgnoreCase(resolved.sha1)) return target;
            }
        }
        throw new IOException("Could not choose a conflict-free filename for " + resolved.fileName);
    }

    private static String sha1File(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[32 * 1024];
            try (FileInputStream input = new FileInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder(40);
            for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 is not available on this device", e);
        }
    }

    private static final class ResolvedModrinthFile {
        final String projectId;
        final String versionId;
        final String projectType;
        final String fileName;
        final String url;
        final String sha1;
        final long size;

        ResolvedModrinthFile(String projectId, String versionId, String projectType,
                             String fileName, String url, String sha1, long size) {
            this.projectId = projectId;
            this.versionId = versionId;
            this.projectType = projectType;
            this.fileName = fileName;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installMrpack);
    }

    private static LoaderInstaller createInfo(ModrinthIndex modrinthIndex, File installDestination) throws IOException {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.QUILT_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, mcVersion, modLoaderVersion);
        } else if(dependencies.size() == 1) {
            // "Vanilla" pack. Possibly GT:NH, let's try to detect lwjgl3ify
            File lwjgl3ifyJar = Lwjgl3ifyUtils.detectLwjgl3ifyJar(installDestination);
            if(lwjgl3ifyJar != null) return new Lwjgl3ifyLoaderInstaller(lwjgl3ifyJar);
        }

        return null;
    }

    private LoaderInstaller installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            try {
                new ModrinthDownloader().startDownloads(modrinthIndex.files, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex, instanceDestination);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }

    static class ModrinthDownloader extends Downloader {
        public ModrinthDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        protected void startDownloads(ModrinthIndex.ModrinthIndexFile[] indexFiles, File instanceDestination)
                throws IOException, InterruptedException {
            File canonicalRoot = instanceDestination.getCanonicalFile();
            String rootPath = canonicalRoot.getCanonicalPath();
            ArrayList<TaskMetadata> taskMetadatas = new ArrayList<>(indexFiles.length);
            HashSet<String> seenPaths = new HashSet<>();
            for (ModrinthIndex.ModrinthIndexFile file : indexFiles) {
                if (file == null || file.path == null || file.path.isEmpty()
                        || file.path.startsWith("/") || file.path.contains("\\")
                        || file.path.indexOf('\0') >= 0) throw new IOException("Unsafe path in Modrinth pack index");
                if (file.env != null && "unsupported".equals(file.env.client)) continue;
                File targetPath = new File(canonicalRoot, file.path).getCanonicalFile();
                if (!targetPath.getCanonicalPath().startsWith(rootPath + File.separator)) {
                    throw new IOException("Modrinth pack path escapes its instance directory");
                }
                String normalizedPath = targetPath.getCanonicalPath().toLowerCase(Locale.ROOT);
                if (!seenPaths.add(normalizedPath)) throw new IOException("Conflicting duplicate path in Modrinth pack: " + file.path);
                if (file.downloads == null || file.downloads.length == 0 || file.hashes == null
                        || file.hashes.sha1 == null || !file.hashes.sha1.matches("(?i)[0-9a-f]{40}")
                        || file.fileSize <= 0) throw new IOException("Modrinth pack index has incomplete file integrity metadata");
                URL downloadUrl = new URL(file.downloads[0]);
                if (!"https".equalsIgnoreCase(downloadUrl.getProtocol())) {
                    throw new IOException("Refusing an insecure HTTP download in the Modrinth pack");
                }
                FileUtils.ensureParentDirectory(targetPath);
                taskMetadatas.add(new TaskMetadata(targetPath, downloadUrl,
                        file.fileSize, file.hashes.sha1, DownloadMirror.DOWNLOAD_CLASS_NONE));
            }
            runDownloads(taskMetadatas);
        }

        protected void downloadInstallFiles(ArrayList<TaskMetadata> files) throws IOException, InterruptedException {
            runDownloads(files);
        }
    }
}
