package net.kdt.pojavlaunch.prefs.screens;

import android.Manifest;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.DataMigrator;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<Uri> mMigrateLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), (uri) -> {
                if(uri != null) {
                    new AlertDialog.Builder(getLauncherActivity())
                            .setTitle(R.string.migration_progress_warning_title)
                            .setMessage(R.string.migration_progress_warning_summary)
                            .setPositiveButton(android.R.string.ok, (d, w) -> new DataMigrator(getLauncherActivity(), uri).migrateData())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            }
    );

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_misc);
        Preference importPreference = requirePreference("runDataMigration");
        importPreference.setOnPreferenceClickListener(preference -> {
            if(ProgressKeeper.getTaskCount() > 0) {
                Toast.makeText(getContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
                return true;
            }
            mMigrateLauncher.launch(null);
            return true;
        });
        setupCacheClearPreference();
        setupCurseforgeApiKeyPreference();
        setupMicrophoneRequestPreference();
        updateVisibility();
    }

    private void updateVisibility(){
        requirePreference("microphoneAccessRequest").setVisible(!getLauncherActivity().checkForPermissionRationale(33, Manifest.permission.RECORD_AUDIO));
        requirePreference("clearMetadataCache").setVisible(new File(Tools.DIR_CACHE, "string_cache").exists());
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setupCurseforgeApiKeyPreference() {
        Preference preference = requirePreference(LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY);
        updateCurseforgePreferenceSummary(preference);
        preference.setOnPreferenceClickListener(clicked -> {
            showCurseforgeKeyDialog(preference);
            return true;
        });
    }

    private void showCurseforgeKeyDialog(Preference preference) {
        String currentKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY, "");
        EditText keyInput = new EditText(requireContext());
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint(R.string.aerix_discover_curseforge_key_hint);
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(6), dp(24), dp(4));
        content.addView(keyInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.aerix_discover_curseforge_key_title)
                .setMessage(R.string.aerix_discover_curseforge_key_required)
                .setView(content)
                .setPositiveButton(R.string.aerix_discover_curseforge_key_save, (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (key.isEmpty() || key.length() > 512) {
                        Toast.makeText(requireContext(), R.string.aerix_discover_curseforge_key_empty,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString(LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY, key)
                            .apply();
                    updateCurseforgePreferenceSummary(preference);
                    Toast.makeText(requireContext(), R.string.aerix_discover_curseforge_key_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (currentKey != null && !currentKey.trim().isEmpty()) {
            builder.setNeutralButton(R.string.aerix_discover_curseforge_key_remove, (dialog, which) -> {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .remove(LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY)
                        .apply();
                updateCurseforgePreferenceSummary(preference);
            });
        }
        builder.show();
    }

    private void updateCurseforgePreferenceSummary(Preference preference) {
        String key = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURSEFORGE_API_KEY, "");
        preference.setSummary(key == null || key.trim().isEmpty()
                ? R.string.aerix_discover_curseforge_key_summary
                : R.string.aerix_discover_curseforge_key_connected);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setupMicrophoneRequestPreference() {
        Preference mRequestMicrophonePermissionPreference = requirePreference("microphoneAccessRequest");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestMicrophonePermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(23, Manifest.permission.RECORD_AUDIO);
                return true;
            });
        } else {
            mRequestMicrophonePermissionPreference.setVisible(false);
        }
    }
    private void setupCacheClearPreference() {
        Preference clearPreference = requirePreference("clearMetadataCache");
        clearPreference.setOnPreferenceClickListener(preference -> {
            if(ProgressKeeper.getTaskCount() > 0) {
                Toast.makeText(getContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
                return true;
            }
            PojavApplication.sExecutorService.submit(() -> {
                try {
                    FileUtils.deleteDirectory(new File(Tools.DIR_CACHE, "string_cache"));
                } catch (IOException e) {
                    Tools.showErrorRemote(getLauncherActivity(), R.string.preference_metadata_clear_fail, e);
                    return;
                }
                Tools.runOnUiThread(() -> {
                    Toast.makeText(getLauncherActivity(), R.string.preference_metadata_clear_complete, Toast.LENGTH_LONG).show();
                    updateVisibility();
                });
            });
            return true;
        });
    }
}
