package net.kdt.pojavlaunch.modloaders.modpacks.api;


import android.content.Context;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.LoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public interface ModpackApi {

    /**
     * @param searchFilters Filters
     * @param previousPageResult The result from the previous page
     * @return the list of mod items from specified offset
     */
    SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult);

    /**
     * @param searchFilters Filters
     * @return A list of mod items
     */
    default SearchResult searchMod(SearchFilters searchFilters) {
        return searchMod(searchFilters, null);
    }

    /**
     * Fetch the mod details
     * @param item The moditem that was selected
     * @return Detailed data about a mod(pack)
     */
    ModDetail getModDetails(ModItem item);

    /**
     * Download and install the modpack
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    default void handleModpackInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        // Doing this here since when starting installation, the progress does not start immediately
        // which may lead to two concurrent installations (very bad)
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                LoaderInstaller installer = installModpack(modDetail, selectedVersion);
                if (installer == null && modDetail != null && !modDetail.isModpack) {
                    int successMessage = "world".equals(modDetail.projectType)
                            ? R.string.aerix_discover_world_import_success
                            : R.string.aerix_discover_content_install_success;
                    Tools.runOnUiThread(() -> Toast.makeText(context.getApplicationContext(),
                            successMessage, Toast.LENGTH_LONG).show());
                }
            } catch (IOException e) {
                if (modDetail != null && "world".equals(modDetail.projectType)) {
                    String message = context.getString(R.string.aerix_discover_world_import_error,
                            e.getLocalizedMessage() == null ? e.getClass().getSimpleName() : e.getLocalizedMessage());
                    Tools.showErrorRemote(message, e);
                } else {
                    Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
                }
            }
        });
    }

    LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException;

    /**
     * Install the mod(pack).
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException;
}
