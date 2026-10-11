package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.FabricInstallFragment;
import net.kdt.pojavlaunch.fragments.ForgeInstallFragment;
import net.kdt.pojavlaunch.fragments.InstanceEditorFragment;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.ModVersionListFragment;
import net.kdt.pojavlaunch.fragments.NeoforgeInstallFragment;
import net.kdt.pojavlaunch.fragments.OptiFineInstallFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceExperimentalFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.MoJsonExtras;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MoJsonDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import net.kdt.pojavlaunch.R;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    private FragmentContainerView mFragmentView;
    private ImageButton mSettingsButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private static ActivityResultLauncher<String> mRequestPermissionLauncher;

    /* The crystal dock: ten destinations, one tap each */
    private static final int[] DOCK_SLOTS = {
            R.id.dock_launch, R.id.dock_instances, R.id.dock_mods, R.id.dock_controls,
            R.id.dock_settings, R.id.dock_video, R.id.dock_input, R.id.dock_java,
            R.id.dock_misc, R.id.dock_labs
    };
    private static final int[] DOCK_ICONS = {
            R.drawable.ic_px_home, R.drawable.ic_px_file, R.drawable.ic_px_dynamic,
            R.drawable.ic_px_gamepad, R.drawable.ic_px_sliders, R.drawable.ic_px_image_renderer,
            R.drawable.ic_px_gestures, R.drawable.ic_px_java, R.drawable.ic_px_alt_sliders,
            R.drawable.ic_px_experiment
    };
    private static final int[] DOCK_LABELS = {
            R.string.liquid_tab_launch, R.string.liquid_tab_instances, R.string.liquid_tab_mods,
            R.string.liquid_tab_controls, R.string.liquid_tab_settings, R.string.liquid_tab_video,
            R.string.liquid_tab_input, R.string.liquid_tab_java, R.string.liquid_tab_misc,
            R.string.liquid_tab_labs
    };

    private final View.OnClickListener[] mDockActions = new View.OnClickListener[] {
            // LAUNCH
            v -> Tools.backToMainMenu(this),
            // INSTANCE - edit the currently selected instance
            v -> Tools.swapFragment(this, InstanceEditorFragment.class,
                    InstanceEditorFragment.TAG, null),
            // MODS - search mods and modpacks
            v -> Tools.swapFragment(this, SearchModFragment.class, SearchModFragment.TAG, null),
            // CONTROL - the control editor keeps its own activity
            v -> startActivity(new Intent(this, CustomControlsActivity.class)),
            // SETTINGS
            v -> Tools.swapFragment(this, LauncherPreferenceFragment.class,
                    SETTING_FRAGMENT_TAG, null),
            // VIDEO
            v -> Tools.swapFragment(this, LauncherPreferenceVideoFragment.class,
                    "VIDEO_FRAGMENT", null),
            // INPUT
            v -> Tools.swapFragment(this, LauncherPreferenceControlFragment.class,
                    "CONTROL_FRAGMENT", null),
            // JAVA
            v -> Tools.swapFragment(this, LauncherPreferenceJavaFragment.class,
                    "JAVA_FRAGMENT", null),
            // MISC
            v -> Tools.swapFragment(this, LauncherPreferenceMiscellaneousFragment.class,
                    "MISC_FRAGMENT", null),
            // LABS
            v -> Tools.swapFragment(this, LauncherPreferenceExperimentalFragment.class,
                    "EXPERIMENTAL_FRAGMENT", null)
    };

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), f instanceof MainMenuFragment
                    ? R.drawable.ic_px_sliders : R.drawable.ic_px_home));
            highlightDock(f);
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        // The "false" value is used to stop auth method selection
        FragmentManager manager = getSupportFragmentManager();
        if(!value || manager.isStateSaved()) return false;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        // Allow starting the add account only from the main menu, should it be moved to fragment itself ?
        if(!(fragment instanceof MainMenuFragment)) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        if(fragment instanceof MainMenuFragment){
            Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
        } else{
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        Instance selectedInstance = Instances.loadSelectedInstance();

        if(selectedInstance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            return false;
        }

        if(selectedInstance.installer != null) {
            selectedInstance.installer.start();
            return false;
        }

        if (!Tools.isValidString(selectedInstance.versionId)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }

        if(Accounts.getCurrent() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
        String normalizedVersionId = MoJsonExtras.normalizeVersionId(selectedInstance.versionId);
        JVersionList.Version mcVersion = MoJsonExtras.getListedVersion(normalizedVersionId);
        new MoJsonDownloader().start(
                this.getAssets(),
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
        return false;
    };
    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pojav_launcher);
        MoJsonDownloader.prepareSubstitutionMap(getAssets());

        try {
            Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
         }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        IconCacheJanitor.runJanitor();

        getWindow().setBackgroundDrawable(null);
        bindViews();
        bindLiquidDock();
        mRequestPermissionLauncher = this.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) Tools.runOnUiThread(() -> Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show());
                }
        );
        checkNotificationPermission();
        if(LauncherPreferences.PREF_MIGRATION_NOTICE)
            PojavApplication.sExecutorService.submit(this::checkPreviousInstalls);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mSettingsButton.setOnClickListener(mSettingButtonListener);
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions));

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_GAME);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
        mProgressLayout.observe(ProgressLayout.INSTANCE_INSTALL);
        mProgressLayout.observe(ProgressLayout.DATA_MIGRATION);
    }

    /* Binds the crystal dock: icon, label, accent colour and destination. */
    private void bindLiquidDock() {
        int[] accents = {
                R.color.liquid_cyan, R.color.liquid_mint, R.color.liquid_violet,
                R.color.liquid_azure, R.color.liquid_cyan, R.color.liquid_rose,
                R.color.liquid_mint, R.color.liquid_amber, R.color.liquid_cyan,
                R.color.liquid_violet
        };
        for (int i = 0; i < DOCK_SLOTS.length; i++) {
            View slot = findViewById(DOCK_SLOTS[i]);
            if (slot == null) continue;
            ImageView iconView = slot.findViewById(R.id.dock_slot_icon);
            TextView label = slot.findViewById(R.id.dock_slot_label);
            if (iconView != null) {
                iconView.setImageResource(DOCK_ICONS[i]);
                ImageViewCompat.setImageTintList(iconView,
                        android.content.res.ColorStateList.valueOf(
                                ResourcesCompat.getColor(getResources(), accents[i], getTheme())));
            }
            if (label != null) label.setText(DOCK_LABELS[i]);
            slot.setOnClickListener(mDockActions[i]);
        }
    }

    /* Highlights the dock slot that matches the visible fragment. */
    private void highlightDock(@NonNull Fragment fragment) {
        int index = dockIndexFor(fragment);
        for (int i = 0; i < DOCK_SLOTS.length; i++) {
            View slot = findViewById(DOCK_SLOTS[i]);
            if (slot == null) continue;
            View halo = slot.findViewById(R.id.dock_slot_halo);
            TextView label = slot.findViewById(R.id.dock_slot_label);
            boolean active = i == index;
            if (halo != null) halo.setVisibility(active ? View.VISIBLE : View.GONE);
            if (label != null) label.setTextColor(ResourcesCompat.getColor(getResources(),
                    active ? R.color.liquid_ink : R.color.liquid_faint, getTheme()));
            slot.setAlpha(active ? 1f : 0.78f);
        }
    }

    private static int dockIndexFor(@NonNull Fragment fragment) {
        Class<?> fragmentClass = fragment.getClass();
        if (fragmentClass == MainMenuFragment.class) return 0;
        if (fragmentClass == InstanceEditorFragment.class
                || fragmentClass == ProfileTypeSelectFragment.class) return 1;
        if (fragmentClass == SearchModFragment.class
                || fragmentClass == ModVersionListFragment.class
                || fragmentClass == FabricInstallFragment.class
                || fragmentClass == ForgeInstallFragment.class
                || fragmentClass == NeoforgeInstallFragment.class
                || fragmentClass == OptiFineInstallFragment.class) return 2;
        if (fragmentClass == LauncherPreferenceVideoFragment.class) return 5;
        if (fragmentClass == LauncherPreferenceControlFragment.class) return 6;
        if (fragmentClass == LauncherPreferenceJavaFragment.class) return 7;
        if (fragmentClass == LauncherPreferenceMiscellaneousFragment.class) return 8;
        if (fragmentClass == LauncherPreferenceExperimentalFragment.class) return 9;
        if (fragmentClass == LauncherPreferenceFragment.class) return 4;
        return -1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        InstanceInstaller.postInstallCheck(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        super.onBackPressed();
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    public void askForPermission(int minApi, final String permission) {
        if(Build.VERSION.SDK_INT < minApi) return;
        mRequestPermissionLauncher.launch(permission);
    }
    public boolean checkForPermission(int minApi, final String permission) {
        return Build.VERSION.SDK_INT < minApi ||
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_DENIED;
    }
    public boolean checkForPermissionRationale(int minApi, final String permission) {
        return checkForPermission(minApi, permission) || ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
            this.checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }
        showNotificationPermissionReasoning();
    }

    // Call async
    private void checkPreviousInstalls(){
        final String[] packages = {"net.kdt.pojavlaunch", "net.kdt.pojavlaunch.debug", "net.kdt.pojavlaunch.pub"};
        for(String s : packages){
            Intent i = getPackageManager().getLaunchIntentForPackage(s);
            if(i == null) continue;
            Tools.runOnUiThread(() ->
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.migration_progress_warning_title)
                        .setMessage(R.string.migration_notice)
                        .setPositiveButton(android.R.string.ok, (d, button) -> LauncherPreferences.DEFAULT_PREF.edit().putBoolean("migrationNotice", false).apply())
                        .setOnDismissListener(d -> LauncherPreferences.PREF_MIGRATION_NOTICE = false)
                        .show());
            break;
        }
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        askForPermission(33, Manifest.permission.POST_NOTIFICATIONS))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mProgressLayout = findViewById(R.id.progress_layout);
    }
}
