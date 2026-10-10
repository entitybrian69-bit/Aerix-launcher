package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.InstanceLibraryFragment;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.fragments.ServerManagerFragment;
import net.kdt.pojavlaunch.fragments.SkinManagerFragment;
import net.kdt.pojavlaunch.fragments.WallpaperGalleryFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.MoJsonExtras;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MoJsonDownloader;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.utils.WallpaperUtils;

import net.kdt.pojavlaunch.R;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    private FragmentContainerView mFragmentView;
    private ImageButton mSettingsButton;
    private ImageButton mHomeButton;
    private ImageButton mCreateButton;
    private ImageButton mLibraryButton;
    private ImageButton mDiscoverButton;
    private ImageButton mWallpapersButton;
    private ImageButton mSkinsButton;
    private ImageButton mServersButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private static ActivityResultLauncher<String> mRequestPermissionLauncher;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            if (f.getParentFragment() != null || mSettingsButton == null) return;
            mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_px_sliders));
            mHomeButton.setActivated(f instanceof MainMenuFragment);
            mCreateButton.setActivated(f instanceof ProfileTypeSelectFragment);
            mLibraryButton.setActivated(f instanceof InstanceLibraryFragment);
            mDiscoverButton.setActivated(f instanceof SearchModFragment);
            mWallpapersButton.setActivated(f instanceof WallpaperGalleryFragment);
            mSkinsButton.setActivated(f instanceof SkinManagerFragment);
            mServersButton.setActivated(f instanceof ServerManagerFragment);
            boolean settingsSelected = f.getClass().getName().startsWith("net.kdt.pojavlaunch.prefs.screens.");
            mSettingsButton.setActivated(settingsSelected);
            setNavigationLabelState(R.id.home_nav_label, f instanceof MainMenuFragment, AerixThemeManager.SECTION_HOME);
            setNavigationLabelState(R.id.create_nav_label, f instanceof ProfileTypeSelectFragment, AerixThemeManager.SECTION_HOME);
            setNavigationLabelState(R.id.library_nav_label, f instanceof InstanceLibraryFragment, AerixThemeManager.SECTION_LIBRARY);
            setNavigationLabelState(R.id.discover_nav_label, f instanceof SearchModFragment, AerixThemeManager.SECTION_DISCOVER);
            setNavigationLabelState(R.id.wallpapers_nav_label, f instanceof WallpaperGalleryFragment, AerixThemeManager.SECTION_APPEARANCE);
            setNavigationLabelState(R.id.skins_nav_label, f instanceof SkinManagerFragment, AerixThemeManager.SECTION_SKINS);
            setNavigationLabelState(R.id.servers_nav_label, f instanceof ServerManagerFragment, AerixThemeManager.SECTION_SERVERS);
            setNavigationLabelState(R.id.settings_nav_label, settingsSelected, AerixThemeManager.SECTION_SETTINGS);
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
        // The account picker is available from the persistent navigation rail on every launcher page.
        if(fragment == null) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Settings remains a dedicated navigation destination; Home has its own rail button. */
    private final View.OnClickListener mSettingButtonListener = v -> {
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        if(fragment != null && fragment.getClass().getName().startsWith("net.kdt.pojavlaunch.prefs.screens.")) return;
        navigateTo(LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG);
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
        Instances.recordLaunch(selectedInstance);
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
        loadSavedWallpaper();
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
        mHomeButton.setOnClickListener(v -> navigateTo(MainMenuFragment.class, MainMenuFragment.TAG));
        mCreateButton.setOnClickListener(v -> navigateTo(ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG));
        mLibraryButton.setOnClickListener(v -> navigateTo(InstanceLibraryFragment.class, InstanceLibraryFragment.TAG));
        mDiscoverButton.setOnClickListener(v -> navigateTo(SearchModFragment.class, SearchModFragment.TAG));
        mWallpapersButton.setOnClickListener(v -> navigateTo(WallpaperGalleryFragment.class, WallpaperGalleryFragment.TAG));
        mSkinsButton.setOnClickListener(v -> navigateTo(SkinManagerFragment.class, SkinManagerFragment.TAG));
        mServersButton.setOnClickListener(v -> navigateTo(ServerManagerFragment.class, ServerManagerFragment.TAG));
        bindNavigationLabelClick(R.id.home_nav_label, mHomeButton);
        bindNavigationLabelClick(R.id.create_nav_label, mCreateButton);
        bindNavigationLabelClick(R.id.library_nav_label, mLibraryButton);
        bindNavigationLabelClick(R.id.discover_nav_label, mDiscoverButton);
        bindNavigationLabelClick(R.id.wallpapers_nav_label, mWallpapersButton);
        bindNavigationLabelClick(R.id.skins_nav_label, mSkinsButton);
        bindNavigationLabelClick(R.id.servers_nav_label, mServersButton);
        bindNavigationLabelClick(R.id.settings_nav_label, mSettingsButton);
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

    private void loadSavedWallpaper() {
        String savedWallpaper = LauncherPreferences.DEFAULT_PREF.getString(WallpaperUtils.PREFERENCE_KEY, null);
        String wallpaperId = WallpaperUtils.selectedId(this);
        int maxWidth = Math.max(960, getResources().getDisplayMetrics().widthPixels);
        int maxHeight = Math.max(540, getResources().getDisplayMetrics().heightPixels);
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (Tools.isValidString(savedWallpaper)) {
                    bitmap = WallpaperUtils.decode(getApplicationContext(), Uri.parse(savedWallpaper), maxWidth, maxHeight);
                } else {
                    bitmap = WallpaperUtils.decodeBundled(getApplicationContext(), wallpaperId, maxWidth, maxHeight);
                }
            } catch (Exception customFailure) {
                if (Tools.isValidString(savedWallpaper)) {
                    LauncherPreferences.DEFAULT_PREF.edit().remove(WallpaperUtils.PREFERENCE_KEY).apply();
                    try {
                        bitmap = WallpaperUtils.decodeBundled(getApplicationContext(), wallpaperId, maxWidth, maxHeight);
                    } catch (Exception bundledFailure) {
                        android.util.Log.e("AerixWallpaper", "Failed to load wallpaper", bundledFailure);
                    }
                }
            }
            if (bitmap == null) return;
            AerixThemeManager.setWallpaperAccentIfMissing(WallpaperUtils.sampleAccentColor(bitmap));
            Bitmap selectedBitmap = bitmap;
            Tools.runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    selectedBitmap.recycle();
                    return;
                }
                ImageView backdrop = findViewById(R.id.launcher_wallpaper_backdrop);
                if (backdrop == null) return;
                backdrop.setImageBitmap(selectedBitmap);
                backdrop.setVisibility(View.VISIBLE);
            });
        });
    }

    private void navigateTo(Class<? extends Fragment> fragmentClass, String tag) {
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;
        manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.container_fragment, fragmentClass, null, tag)
                .commit();
    }

    private void bindNavigationLabelClick(int labelId, ImageButton target) {
        TextView label = findViewById(labelId);
        if (label != null && target != null) label.setOnClickListener(v -> target.performClick());
    }

    private void setNavigationLabelState(int labelId, boolean selected, String section) {
        TextView label = findViewById(labelId);
        if (label == null) return;
        label.setTextColor(selected
                ? AerixThemeManager.accentColor(this, section)
                : Color.rgb(230, 243, 252));
        label.setTypeface(android.graphics.Typeface.DEFAULT,
                selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mHomeButton = findViewById(R.id.home_nav_button);
        mCreateButton = findViewById(R.id.create_nav_button);
        mLibraryButton = findViewById(R.id.library_nav_button);
        mDiscoverButton = findViewById(R.id.discover_nav_button);
        mWallpapersButton = findViewById(R.id.wallpapers_nav_button);
        mSkinsButton = findViewById(R.id.skins_nav_button);
        mServersButton = findViewById(R.id.servers_nav_button);
        mProgressLayout = findViewById(R.id.progress_layout);
        AerixThemeManager.tintNavigationButton(mHomeButton, this, AerixThemeManager.SECTION_HOME);
        AerixThemeManager.tintNavigationButton(mCreateButton, this, AerixThemeManager.SECTION_HOME);
        AerixThemeManager.tintNavigationButton(mLibraryButton, this, AerixThemeManager.SECTION_LIBRARY);
        AerixThemeManager.tintNavigationButton(mDiscoverButton, this, AerixThemeManager.SECTION_DISCOVER);
        AerixThemeManager.tintNavigationButton(mWallpapersButton, this, AerixThemeManager.SECTION_APPEARANCE);
        AerixThemeManager.tintNavigationButton(mSkinsButton, this, AerixThemeManager.SECTION_SKINS);
        AerixThemeManager.tintNavigationButton(mServersButton, this, AerixThemeManager.SECTION_SERVERS);
        AerixThemeManager.tintNavigationButton(mSettingsButton, this, AerixThemeManager.SECTION_SETTINGS);
    }
}
