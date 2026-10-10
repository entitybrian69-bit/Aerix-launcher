package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    public String[] versionIds;
    public String[] versionFilenames;
    /** Required dependency references for each version, prefixed with version: or project:. */
    public String[][] versionDependencies;
    public String[][] versionLoaders;

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, versionUrls, hashes, null, null, null, null, null);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls,
                     String[] hashes, String[] versionIds, String[] versionFilenames,
                     String[][] versionDependencies, String[][] versionLoaders, String projectType) {
        super(item.apiSource, item.isModpack, projectType == null ? item.projectType : projectType,
                item.id, item.title, item.description, item.imageUrl);
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.versionIds = versionIds;
        this.versionFilenames = versionFilenames;
        this.versionDependencies = versionDependencies;
        this.versionLoaders = versionLoaders;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (versionNames[i] != null && mcVersionNames[i] != null && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}
