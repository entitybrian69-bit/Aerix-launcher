package net.kdt.pojavlaunch;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences;
import net.kdt.pojavlaunch.aerix.bridge.AerixWindowInsets;
import net.kdt.pojavlaunch.aerix.ui.AerixMainFragment;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.aerix.ui.AerixNav;
import net.kdt.pojavlaunch.aerix.ui.LauncherSection;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.MoJsonExtras;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MoJsonDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import net.kdt.pojavlaunch.R;

import java.io.IOException;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    private FragmentContainerView mFragmentView;
    private View mHomeButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private static ActivityResultLauncher<String> mRequestPermissionLauncher;

    /* Aerix: the floating home button is shown only while a Pojav screen sits above the Aerix shell */
    private final FragmentManager.OnBackStackChangedListener mBackStackListener = this::updateHomeButton;

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
        // Allow starting the add account only from the Aerix shell or the classic main menu
        if(fragment instanceof AerixMainFragment) {
            // The Aerix shell shows its own Add Account page (Microsoft / Ely.by / Offline)
            AerixNav.INSTANCE.navigate(LauncherSection.ADD_ACCOUNT);
            return false;
        }
        if(!(fragment instanceof MainMenuFragment)) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Listener for the floating home button: settings now live in the Aerix shell */
    private final View.OnClickListener mHomeButtonListener = v -> {
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;
        Tools.backToMainMenu(this);
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
        // The Aerix shell draws its wallpaper under the whole display and keeps the rail and the
        // pages inside the safe area itself (AerixWindowInsets), so the window may use the cutout.
        // Following the "Ignore notch" video preference here reserved a strip of the display for
        // the camera and painted it black next to the UI.
        return true;
    }

    @Override
    public boolean setFullscreen() {
        // Aerix "launcherFullScreen"; BaseActivity passes it to Tools.setInsetsMode
        return AerixPreferences.INSTANCE.getFullScreen();
    }

    /**
     * The browser comes back here with the tokens of a Socials sign-in in the URI fragment.
     *
     * Handled before {@link #onCreate} does its work so the session is already restored when the
     * Socials page is first drawn.
     */
    private void handleSocialSignIn(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        String data = intent.getData().toString();
        // aerix-social://chat/dm/<peerId> or aerix-social://chat/party/<partyId> from notification tap
        if (data.startsWith("aerix-social://chat/dm/")) {
            String peerId = android.net.Uri.decode(data.substring("aerix-social://chat/dm/".length()));
            net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(getApplicationContext());
            net.kdt.pojavlaunch.aerix.social.SocialBackend.requestOpenConversation(peerId, null);
            net.kdt.pojavlaunch.aerix.ui.AerixNav.INSTANCE
                    .navigate(net.kdt.pojavlaunch.aerix.ui.LauncherSection.SOCIALS);
            return;
        }
        if (data.startsWith("aerix-social://chat/party/")) {
            String partyId = android.net.Uri.decode(data.substring("aerix-social://chat/party/".length()));
            net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(getApplicationContext());
            net.kdt.pojavlaunch.aerix.social.SocialBackend.requestOpenConversation(null, partyId);
            net.kdt.pojavlaunch.aerix.ui.AerixNav.INSTANCE
                    .navigate(net.kdt.pojavlaunch.aerix.ui.LauncherSection.SOCIALS);
            return;
        }
        // aerix-social://friend/CODE opens that player's card inside the social space.
        if (data.startsWith("aerix-social://friend/")) {
            String code = data.substring("aerix-social://friend/".length());
            net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(getApplicationContext());
            net.kdt.pojavlaunch.aerix.social.SocialBackend.acceptInvite(code);
            net.kdt.pojavlaunch.aerix.ui.AerixNav.INSTANCE
                    .navigate(net.kdt.pojavlaunch.aerix.ui.LauncherSection.SOCIALS);
            return;
        }
        if (!data.startsWith(net.kdt.pojavlaunch.aerix.social.SocialBackend.OAUTH_REDIRECT)) return;
        net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(getApplicationContext());
        boolean restored = net.kdt.pojavlaunch.aerix.social.SocialBackend.completeBrowserSession(data);
        Toast.makeText(this, restored ? "Signed in to Socials" : "Sign-in did not complete",
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSocialSignIn(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must run before BaseActivity.onCreate, which reads setFullscreen()
        AerixPreferences.INSTANCE.init(getApplicationContext());
        super.onCreate(savedInstanceState);
        net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(getApplicationContext());
        net.kdt.pojavlaunch.aerix.social.SocialMessagingService.markLauncherSessionOpened(getApplicationContext());
        handleSocialSignIn(getIntent());
        // The decor receives the insets before the content view consumes them for its padding.
        // Publishing them lets the Compose shell keep the rail and the pages on the safe area
        // while its wallpaper stays full bleed. The camera cutout always has to be respected; the
        // system bars only when the launcher is not fullscreen, since Tools.setInsetsMode pads the
        // content for those itself and this would count them twice.
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (view, insets) -> {
            int mask = WindowInsetsCompat.Type.displayCutout();
            if (!AerixPreferences.INSTANCE.getFullScreen()) {
                mask |= WindowInsetsCompat.Type.systemBars();
            }
            Insets safe = insets.getInsets(mask);
            AerixWindowInsets.INSTANCE.update(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
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
        mRequestPermissionLauncher = this.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) Tools.runOnUiThread(() -> Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show());
                }
        );
        // First-time notification ON/OFF choice is handled by Aerix FirstRunNotificationPopupHost.
        if(LauncherPreferences.PREF_MIGRATION_NOTICE)
            PojavApplication.sExecutorService.submit(this::checkPreviousInstalls);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mHomeButton.setOnClickListener(mHomeButtonListener);
        getSupportFragmentManager().addOnBackStackChangedListener(mBackStackListener);
        updateHomeButton();
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions));

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_GAME);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.EXTRACT_COMPONENTS);
        mProgressLayout.observe(ProgressLayout.EXTRACT_SINGLE_FILES);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
        mProgressLayout.observe(ProgressLayout.INSTANCE_INSTALL);
        mProgressLayout.observe(ProgressLayout.DATA_MIGRATION);

        // An account switch made from inside the game comes back here: the game process is gone and
        // the player expects to be back in the game as the account they picked, not on a dashboard
        // they did not ask for.
        relaunchGameIfRequested();
    }

    /**
     * Starts the game again when this activity was brought back by a request to do so.
     *
     * An account switch made from inside the game arrives this way. It is applied *here* on
     * purpose: the choice is saved as a preference, and the game process that asked for the switch
     * is killed a moment later - selecting the account there would race with that write, while
     * this process owns the preference the launch reads.
     *
     * The extras are consumed immediately, so a configuration change or a later return to this
     * activity never launches the game a second time.
     */
    private void relaunchGameIfRequested() {
        Intent intent = getIntent();
        if (intent == null) return;
        boolean relaunch = intent.getBooleanExtra(Tools.EXTRA_RELAUNCH_GAME, false);
        String accountKey = intent.getStringExtra(Tools.EXTRA_SWITCH_ACCOUNT);
        String instanceKey = intent.getStringExtra(Tools.EXTRA_SWITCH_INSTANCE);
        String launchServer = intent.getStringExtra(Tools.EXTRA_LAUNCH_SERVER);
        if (!relaunch && accountKey == null && instanceKey == null && launchServer == null) return;
        intent.removeExtra(Tools.EXTRA_RELAUNCH_GAME);
        intent.removeExtra(Tools.EXTRA_SWITCH_ACCOUNT);
        intent.removeExtra(Tools.EXTRA_SWITCH_INSTANCE);
        intent.removeExtra(Tools.EXTRA_LAUNCH_SERVER);
        if (launchServer != null && !launchServer.isEmpty()) {
            net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge.setPendingServerAddress(launchServer);
        }
        // After the first layout: the launch pipeline reads preferences the shell sets up, and the
        // progress overlay has to exist before it is asked to show itself.
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        if (accountKey == null && instanceKey == null) {
            root.post(() -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            if (instanceKey != null && !instanceKey.isEmpty()) {
                try {
                    net.kdt.pojavlaunch.instances.Instance targetInst =
                            net.kdt.pojavlaunch.instances.Instances.loadInstance(instanceKey);
                    if (targetInst != null) {
                        net.kdt.pojavlaunch.instances.Instances.setSelectedInstance(targetInst);
                    }
                } catch (Throwable error) {
                    Log.w("LauncherActivity", "Could not select requested social instance", error);
                }
            }
            boolean switched = false;
            if (accountKey != null) {
                try {
                    for (Account candidate : Accounts.load().accounts) {
                        if (candidate.mSaveLocation != null &&
                                candidate.mSaveLocation.getName().equals(accountKey)) {
                            Accounts.setCurrent(candidate);
                            switched = true;
                            break;
                        }
                    }
                } catch (IOException | RuntimeException error) {
                    Log.w("LauncherActivity", "Could not select the account to switch to", error);
                }
                Logger.appendToLog(switched
                        ? "Account switch: signed in as the account the game asked for"
                        : "Account switch: the chosen account could not be found; keeping the current one");
            }
            Tools.runOnUiThread(() -> root.post(() -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence.onLauncherStarted(this);
    }

    @Override
    protected void onStop() {
        net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence.onLauncherStopped(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        // The game activity is a separate process and may have changed Client settings.
        net.kdt.pojavlaunch.aerix.client.AerixClientStore.INSTANCE.reload(getApplicationContext());
        InstanceInstaller.postInstallCheck(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
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

        getSupportFragmentManager().removeOnBackStackChangedListener(mBackStackListener);
    }

    private void updateHomeButton() {
        mHomeButton.setVisibility(getSupportFragmentManager().getBackStackEntryCount() > 0 ? View.VISIBLE : View.GONE);
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
            if (s.equals(getPackageName())) continue;
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

    public void markNotificationPromptHandled() {
        handleNoNotificationPermission();
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
        mHomeButton = findViewById(R.id.aerix_home_button);
        mProgressLayout = findViewById(R.id.progress_layout);
    }
}
