package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.runtime.MutableState;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.aerix.ui.screens.ModpackBrowserHost;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SearchModFragment extends Fragment {

    public static final String TAG = "SearchModFragment";
    private ModpackApi modpackApi;
    private MutableState<Boolean> mTasksRunningState;
    private TaskCountListener mTaskCountListener;

    ActivityResultLauncher<String> mImportLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                Context context = getContext();
                if (context == null) return;
                ContentResolver contentResolver = context.getContentResolver();
                PojavApplication.sExecutorService.execute(() -> performLocalInstall(uri, context, contentResolver));
            });

    public void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
        String fileName = Tools.getFileName(context, uri);
        if (fileName == null) return;
        File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, R.string.multirt_progress_caching);
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(outFile)) {
            if (inputStream == null) return;
            IOUtils.copy(inputStream, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            Tools.showErrorRemote("Error", e);
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            return;
        }
        try {
            modpackApi.installLocalModpack(fileName, outFile, null);
        } catch (IOException e) {
            Tools.showErrorRemote("Error", e);
        } finally {
            outFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mTasksRunningState = ModpackBrowserHost.newTasksRunningState(ProgressKeeper.getTaskCount() > 0);
        mTaskCountListener = taskCount -> {
            runOnUiThread(() -> {
                if (mTasksRunningState != null) {
                    mTasksRunningState.setValue(taskCount > 0);
                }
            });
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        return ModpackBrowserHost.createView(
                this,
                modpackApi,
                mTasksRunningState,
                () -> mImportLauncher.launch("*/*"),
                (current, callback) -> VersionSelectorDialog.open(requireContext(), true, (id, snapshot) -> callback.onPicked(id)),
                () -> {
                    if (isAdded()) {
                        Tools.backToMainMenu(requireActivity());
                    }
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mTaskCountListener != null) {
            ProgressKeeper.removeTaskCountListener(mTaskCountListener);
            mTaskCountListener = null;
        }
        mTasksRunningState = null;
    }
}
