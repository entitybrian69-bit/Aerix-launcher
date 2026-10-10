package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
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

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.PrismGlass;
import net.kdt.pojavlaunch.utils.jre.GameRunner;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private ObjectAnimator mOrbMotion;
    private ImageView mAccountIcon;
    private TextView mAccountSummary;
    private TextView mRamSummary;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mHeroCreateButton = view.findViewById(R.id.hero_create_button);
        Button mHeroLibraryButton = view.findViewById(R.id.hero_library_button);
        Button mHeroModsButton = view.findViewById(R.id.hero_mods_button);
        Button mAccountManageButton = view.findViewById(R.id.account_manage_button);
        Button mQuickToolsButton = view.findViewById(R.id.quick_tools_button);
        mAccountIcon = view.findViewById(R.id.home_account_icon);
        mAccountSummary = view.findViewById(R.id.home_account_summary);
        mRamSummary = view.findViewById(R.id.home_ram_label);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        if (mVersionSpinner != null && getResources().getConfiguration().orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            mVersionSpinner.setTextColor(Color.rgb(20, 42, 64));
        }
        styleGlassButton(mHeroCreateButton);
        styleGlassButton(mHeroLibraryButton);
        styleGlassButton(mHeroModsButton);
        styleGlassButton(mAccountManageButton);
        styleGlassButton(mQuickToolsButton);
        if (mPlayButton != null && getResources().getConfiguration().orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            mPlayButton.setBackgroundResource(R.drawable.prism_orb);
            mPlayButton.setBackgroundTintList(null);
            mPlayButton.setTextColor(Color.rgb(23, 55, 86));
            mOrbMotion = ObjectAnimator.ofFloat(mPlayButton, View.TRANSLATION_Y,
                    -16f * getResources().getDisplayMetrics().density,
                    -23f * getResources().getDisplayMetrics().density);
            mOrbMotion.setDuration(2200);
            mOrbMotion.setRepeatCount(ValueAnimator.INFINITE);
            mOrbMotion.setRepeatMode(ValueAnimator.REVERSE);
            if (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) {
                mOrbMotion.start();
            }
        } else if (mPlayButton != null) {
            AerixThemeManager.tintPrimaryButton(mPlayButton, requireContext(), AerixThemeManager.SECTION_HOME);
        }
        PrismGlass.apply(view.findViewById(R.id.instance_panel));
        updateHomeStatus();

        setClickIfPresent(mEditProfileButton, v -> {
            if (mVersionSpinner != null) mVersionSpinner.openProfileEditor(requireActivity());
        });
        setClickIfPresent(mHeroCreateButton, v -> Tools.swapFragment(requireActivity(),
                ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        setClickIfPresent(mHeroLibraryButton, v -> Tools.swapFragment(requireActivity(),
                InstanceLibraryFragment.class, InstanceLibraryFragment.TAG, null));
        setClickIfPresent(mHeroModsButton, v -> Tools.swapFragment(requireActivity(),
                SearchModFragment.class, SearchModFragment.TAG, null));
        setClickIfPresent(mAccountManageButton, v -> Tools.swapFragment(requireActivity(),
                AccountManagerFragment.class, AccountManagerFragment.TAG, null));
        setClickIfPresent(mQuickToolsButton, v -> showQuickToolsDialog());

        setClickIfPresent(mPlayButton, v -> {
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

    @Override
    public void onStop() {
        if (mOrbMotion != null) mOrbMotion.pause();
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mOrbMotion != null && mOrbMotion.isPaused()) mOrbMotion.resume();
    }

    @Override
    public void onDestroyView() {
        if (mOrbMotion != null) {
            mOrbMotion.cancel();
            mOrbMotion = null;
        }
        super.onDestroyView();
    }

    private void setClickIfPresent(@Nullable View view, @NonNull View.OnClickListener listener) {
        if (view != null) view.setOnClickListener(listener);
    }

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
        updateHomeStatus();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void updateHomeStatus() {
        Account account = Accounts.getCurrent();
        if (mAccountSummary != null) {
            mAccountSummary.setText(account == null || !Tools.isValidString(account.username)
                    ? getString(R.string.aerix_home_sign_in_hint) : account.username);
        }
        if (mAccountIcon != null) {
            android.graphics.Bitmap skinFace = account == null ? null : account.getSkinFace();
            if (skinFace == null) mAccountIcon.setImageResource(R.drawable.ic_aerix_account);
            else mAccountIcon.setImageBitmap(skinFace);
        }
        if (mRamSummary != null) {
            mRamSummary.setText(getString(R.string.aerix_home_ram,
                    LauncherPreferences.PREF_RAM_ALLOCATION));
        }
    }

    private void showQuickToolsDialog() {
        String[] actions = {
                getString(R.string.mcl_tab_wiki),
                getString(R.string.mcl_button_social_media),
                getString(R.string.mcl_option_customcontrol),
                getString(R.string.main_install_jar_file),
                getString(R.string.main_share_logs),
                getString(R.string.mcl_button_open_directory),
                getString(R.string.aerix_quick_tool_controls)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_quick_tools_title)
                .setItems(actions, (dialog, index) -> {
                    switch (index) {
                        case 0:
                            Tools.openURL(requireActivity(), Tools.URL_HOME);
                            break;
                        case 1:
                            Tools.openURL(requireActivity(), getString(R.string.social_media_invite));
                            break;
                        case 2:
                            startActivity(new Intent(requireContext(), CustomControlsActivity.class));
                            break;
                        case 3:
                            runInstallerWithConfirmation();
                            break;
                        case 4:
                            shareLog(requireContext());
                            break;
                        case 5:
                            openGameDirectory(requireContext());
                            break;
                        case 6:
                            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                                    GamepadMapperFragment.TAG, null);
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void styleGlassButton(Button button) {
        if (button == null) return;
        button.setBackgroundResource(R.drawable.aerix_nav_button);
        button.setBackgroundTintList(null);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
