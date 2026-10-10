package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    /** Modrinth project type: modpack, mod, resourcepack, or shader. */
    @Nullable public String projectType = "modpack";
    public String name;

    public String resolvedProjectType() {
        if (projectType != null && !projectType.isEmpty()) return projectType;
        return isModpack ? "modpack" : "mod";
    }

    public void setProjectType(String type) {
        if (type == null || !(type.equals("modpack") || type.equals("mod")
                || type.equals("resourcepack") || type.equals("shader"))) {
            throw new IllegalArgumentException("Unsupported Discover project type");
        }
        projectType = type;
        isModpack = "modpack".equals(type);
    }
    @Nullable public String mcVersion;

}
