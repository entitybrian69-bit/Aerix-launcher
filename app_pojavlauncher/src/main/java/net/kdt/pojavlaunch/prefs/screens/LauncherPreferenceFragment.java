package net.kdt.pojavlaunch.prefs.screens;


import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.AerixSettingsTabBar;
import net.kdt.pojavlaunch.utils.AerixThemeManager;
import net.kdt.pojavlaunch.utils.UpdateChecker;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View preferences = super.onCreateView(inflater, container, savedInstanceState);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setBackgroundColor(Color.TRANSPARENT);
        root.addView(AerixSettingsTabBar.create(requireContext(), requireActivity(), getClass()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentParams.topMargin = dp(6);
        root.addView(preferences, contentParams);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(Color.TRANSPARENT);
        super.onViewCreated(view, savedInstanceState);
        RecyclerView list = view.findViewById(android.R.id.list);
        if (list != null) {
            list.setBackgroundColor(Color.TRANSPARENT);
            list.setClipToPadding(false);
            list.setPadding(dp(10), dp(10), dp(10), dp(10));
        }
        applySettingsAccent(getPreferenceScreen(), ColorStateList.valueOf(Color.BLACK));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applySettingsAccent(PreferenceGroup group, ColorStateList tint) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            Drawable icon = preference.getIcon();
            if (icon != null) {
                icon = icon.mutate();
                icon.setTintList(tint);
                preference.setIcon(icon);
            }
            if (preference instanceof PreferenceGroup) {
                applySettingsAccent((PreferenceGroup) preference, tint);
            }
        }
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
        setupUpdateCheckPreference();
    }

    protected String themeSection() {
        return AerixThemeManager.SECTION_SETTINGS;
    }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    private void setupUpdateCheckPreference() {
        requirePreference("check_for_updates").setOnPreferenceClickListener(preference -> {
            if (!isAdded()) return true;
            AlertDialog checking = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.update_checking_title)
                    .setMessage(R.string.update_checking_message)
                    .setNegativeButton(R.string.update_dismiss, (dialog, which) -> dialog.dismiss())
                    .create();
            checking.show();
            UpdateChecker.check((release, error) -> {
                if (!isAdded() || !checking.isShowing()) return;
                checking.dismiss();
                AlertDialog.Builder result = new AlertDialog.Builder(requireContext())
                        .setTitle(error == null ? R.string.update_result_title : R.string.update_error_title);
                if (error != null || release == null) {
                    result.setMessage(R.string.update_error_message)
                            .setPositiveButton(android.R.string.ok, null);
                } else {
                    result.setMessage(release.isNewerThan(BuildConfig.VERSION_NAME)
                                    ? getString(R.string.update_available_message, release.version, BuildConfig.VERSION_NAME)
                                    : getString(R.string.update_up_to_date_message, release.version))
                            .setPositiveButton(R.string.update_open_release, (dialog, which) ->
                                    Tools.openURL(requireActivity(), release.pageUrl));
                }
                result.setNegativeButton(R.string.update_dismiss, null).show();
            });
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
