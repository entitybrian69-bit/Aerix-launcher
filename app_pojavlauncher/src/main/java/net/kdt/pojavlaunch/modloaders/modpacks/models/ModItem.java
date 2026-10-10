package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class ModItem extends ModSource {

    public String id;
    public String title;
    public String description;
    public String imageUrl;
    /** Optional Minecraft-version filter supplied by Discover for detail/version resolution. */
    public String requestedMinecraftVersion;

    public ModItem(int apiSource, boolean isModpack, String id, String title, String description, String imageUrl) {
        this(apiSource, isModpack, isModpack ? "modpack" : "mod", id, title, description, imageUrl);
    }

    public ModItem(int apiSource, boolean isModpack, String projectType, String id, String title,
                   String description, String imageUrl) {
        this.apiSource = apiSource;
        this.isModpack = isModpack;
        this.projectType = projectType == null ? (isModpack ? "modpack" : "mod") : projectType;
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }

    public String getIconCacheTag() {
        return apiSource+"_"+id;
    }
}
