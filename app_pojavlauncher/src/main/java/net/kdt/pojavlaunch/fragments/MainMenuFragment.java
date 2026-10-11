package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.jre.GameRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainMenuFragment extends Fragment {

    /* The recent instances shown on the home screen, and the views that list them. */
    private final List<DisplayInstance> mRecent = new ArrayList<>();
    private ListView mRecentList;
    private TextView mTasksDetail;
    private TextView mInstanceCount;
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView mInstanceDetail;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mNewsButton = view.findViewById(R.id.news_button);
        View mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        View mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        mInstanceDetail = view.findViewById(R.id.liquid_instance_detail);
        startRingSweep(view.findViewById(R.id.liquid_launch_ring));
        View mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        View mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        if (mNewsButton != null) {
            mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
            mNewsButton.setOnLongClickListener((v)->{
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
        }
        if (mDiscordButton != null) {
            mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        }
        if (mCustomControlButton != null) {
            mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }
        if (mInstallJarButton != null) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        }
        if (mEditProfileButton != null) {
            mEditProfileButton.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }

        if (mPlayButton != null) {
            mPlayButton.setOnClickListener(v -> {
                Instance instance = Instances.loadSelectedInstance();
                if (instance == null) {
                    Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
                    return;
                }
                File gamedir = instance.getGameDirectory();

                if (GameRunner.hasVkMod(gamedir)) {
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.vk_mod_title)
                        .setMessage(R.string.vk_mod_message)
                        .setPositiveButton(R.string.continue_button, (d, w) -> {
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .show();
                } else if (GameRunner.hasReplay(gamedir) && GameRunner.hasFfmpeg(requireContext())) {
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.no_ffmpeg_title)
                        .setMessage(R.string.no_ffmpeg_message)
                        .setPositiveButton(R.string.install_button, (d, w) -> {
                            Tools.openURL(requireActivity(), "https://github.com/MojoLauncher/FFmpegPlugin/releases");
                        })
                        .setNegativeButton(R.string.continue_button, (d, w) -> {
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .show();
                } else {
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                }
            });
        }

        if (mShareLogsButton != null) {
            mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        }

        if (mOpenDirectoryButton != null) {
            mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));
        }

        bindHomeTiles(view);
    }

    /* Wires the panels Mirai's home screen adds on top of the old buttons. */
    private void bindHomeTiles(@NonNull View view) {
        View createInstance = view.findViewById(R.id.home_create_instance);
        if (createInstance != null) {
            createInstance.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                    ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        }
        View manageVersions = view.findViewById(R.id.home_manage_versions);
        if (manageVersions != null) {
            manageVersions.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                    InstanceListFragment.class, InstanceListFragment.TAG, null));
        }
        View tasks = view.findViewById(R.id.home_tasks);
        if (tasks != null) {
            tasks.setOnClickListener(v -> {
                View progress = requireActivity().findViewById(R.id.progress_layout);
                if (progress == null) return;
                progress.setVisibility(progress.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            });
        }
        View tileCreate = view.findViewById(R.id.home_tile_create);
        if (tileCreate != null) {
            tileCreate.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                    ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        }
        View tileExplore = view.findViewById(R.id.home_tile_explore);
        if (tileExplore != null) {
            tileExplore.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                    SearchModFragment.class, SearchModFragment.TAG, null));
        }
        View tileLibrary = view.findViewById(R.id.home_tile_library);
        if (tileLibrary != null) {
            tileLibrary.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                    InstanceListFragment.class, InstanceListFragment.TAG, null));
        }

        mRecentList = view.findViewById(R.id.home_recent_list);
        if (mRecentList != null) {
            mRecentList.setAdapter(mRecentAdapter);
            mRecentList.setOnItemClickListener((parent, row, position, id) -> {
                DisplayInstance instance = mRecent.get(position);
                PojavApplication.sExecutorService.execute(() -> {
                    Instances.setSelectedInstance(instance);
                    Tools.runOnUiThread(() -> {
                        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, true);
                        refreshInstanceDetail();
                        refreshRecent();
                    });
                });
            });
        }
        mTasksDetail = view.findViewById(R.id.home_tasks_detail);
        mInstanceCount = view.findViewById(R.id.home_tile_library_sub);

        refreshRecent();
        ProgressKeeper.addTaskCountListener(mTaskCountListener);
    }

    private void refreshRecent() {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Instances loaded = Instances.loadDisplay();
                List<DisplayInstance> recent = loaded.list.size() > 4
                        ? new ArrayList<>(loaded.list.subList(0, 4))
                        : new ArrayList<>(loaded.list);
                Tools.runOnUiThread(() -> {
                    mRecent.clear();
                    mRecent.addAll(recent);
                    mRecentAdapter.notifyDataSetChanged();
                    if (mInstanceCount != null) {
                        mInstanceCount.setText(getResources().getQuantityString(
                                R.plurals.mirai_home_instance_count, loaded.list.size(), loaded.list.size()));
                    }
                });
            } catch (IOException e) {
                // A broken instance file should not take the home screen down.
            }
        });
    }

    private final BaseAdapter mRecentAdapter = new BaseAdapter() {
        @Override public int getCount() { return mRecent.size(); }
        @Override public Object getItem(int position) { return mRecent.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView ? (TextView) convertView
                    : new TextView(parent.getContext());
            DisplayInstance instance = mRecent.get(position);
            row.setText(instance.name != null ? instance.name : instance.versionId);
            row.setTextColor(getResources().getColor(R.color.mirai_text_primary, requireContext().getTheme()));
            row.setTextSize(14);
            row.setPadding(0, 12, 0, 12);
            row.setSingleLine(true);
            return row;
        }
    };

    private final TaskCountListener mTaskCountListener = taskCount -> {
        if (mTasksDetail == null) return !isAdded();
        Tools.runOnUiThread(() -> mTasksDetail.setText(taskCount > 0
                ? getString(R.string.mirai_home_tasks_running, taskCount)
                : getString(R.string.mirai_home_tasks_idle)));
        return false;
    };

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        refreshInstanceDetail();
    }

    /** Slowly turns the sweep ring that sits behind the launch crystal. */
    private void startRingSweep(View ring) {
        if (ring == null) return;
        RotateAnimation sweep = new RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        sweep.setDuration(9000);
        sweep.setRepeatCount(Animation.INFINITE);
        sweep.setInterpolator(new LinearInterpolator());
        ring.startAnimation(sweep);
    }

    /** Fills the hero card with the version that is currently selected. */
    private void refreshInstanceDetail() {
        if (mInstanceDetail == null) return;
        // Read the instance off the main thread, it touches the disk.
        PojavApplication.sExecutorService.execute(() -> {
            Instance instance = Instances.loadSelectedInstance();
            String detail = (instance == null || !Tools.isValidString(instance.versionId))
                    ? getString(R.string.liquid_instance_default)
                    : instance.versionId;
            Tools.runOnUiThread(() -> {
                if (mInstanceDetail != null) mInstanceDetail.setText(detail);
            });
        });
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
