package net.kdt.pojavlaunch;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences;
import net.kdt.pojavlaunch.aerix.ui.screens.ImportControlHost;
import net.kdt.pojavlaunch.aerix.ui.screens.ImportControlUiState;
import net.kdt.pojavlaunch.customcontrols.ControlLayoutImporter;
import net.kdt.pojavlaunch.customcontrols.LayoutBitmaps;
import net.kdt.pojavlaunch.customcontrols.LayoutConverter;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An activity dedicated to importing control files.
 */
@SuppressWarnings("IOStreamConstructor")
public class ImportControlActivity extends BaseActivity {

    private Uri mUriData;
    private boolean mHasIntentChanged = true;
    private volatile boolean mIsFileVerified = false;
    private String mCompatibilityWarning;
    private ImportControlUiState mUiState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AerixPreferences.INSTANCE.init(getApplicationContext());
        super.onCreate(savedInstanceState);
        if (Tools.checkStorageInteractive(this)) {
            Tools.initStorageConstants(getApplicationContext());
        } else {
            return;
        }

        mUiState = new ImportControlUiState();
        setContentView(ImportControlHost.createView(
                this,
                mUiState,
                this::updateValidationState,
                this::finishAndRemoveTask,
                () -> startImport(null)
        ));
    }

    @Override
    public boolean setFullscreen() {
        return AerixPreferences.INSTANCE.getFullScreen();
    }

    @Override
    protected boolean shouldIgnoreNotch() {
        return true;
    }

    /**
     * Override the previous loaded intent
     * @param intent the intent used to replace the old one.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) setIntent(intent);
        mHasIntentChanged = true;
    }

    /**
     * Update all over again if the intent changed.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (!Tools.checkStorageInteractive(this)) {
            return;
        }
        if (!mHasIntentChanged || mUiState == null) return;
        mIsFileVerified = false;
        mCompatibilityWarning = null;
        getUriData();
        if (mUriData == null) {
            finishAndRemoveTask();
            return;
        }
        String initialName = trimFileName(Tools.getFileName(this, mUriData));
        mUiState.setFileName(initialName);
        mUiState.setVerified(false);
        mUiState.setStatusBadge("Verifying JSON layout…");
        updateValidationState(initialName);
        mHasIntentChanged = false;

        new Thread(() -> {
            importControlFile();

            String verifiedBadge = verifyAndDescribe();
            if (verifiedBadge != null) {
                mIsFileVerified = true;
                runOnUiThread(() -> {
                    if (mUiState != null) {
                        mUiState.setVerified(true);
                        mUiState.setStatusBadge(verifiedBadge);
                        updateValidationState(mUiState.getFileName());
                    }
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(
                            ImportControlActivity.this,
                            getText(R.string.import_control_invalid_file),
                            Toast.LENGTH_SHORT).show();
                    finishAndRemoveTask();
                });
            }
        }).start();
    }

    private void updateValidationState(String rawName) {
        if (mUiState == null) return;
        String clean = trimFileName(rawName == null ? "" : rawName);
        if (clean.isEmpty()) {
            mUiState.setValidationError(getString(R.string.import_control_invalid_name));
        } else if (FileUtils.exists(Tools.CTRLMAP_PATH + "/" + clean + ".json")) {
            mUiState.setValidationError(getString(R.string.import_control_invalid_name));
        } else {
            mUiState.setValidationError(null);
        }
    }

    /**
     * Start the import.
     * @param view optional caller view
     */
    public void startImport(@Nullable View view) {
        String currentName = mUiState != null ? mUiState.getFileName() : "";
        String fileName = trimFileName(currentName);
        if (!isFileNameValid(fileName)) {
            Toast.makeText(this, getText(R.string.import_control_invalid_name), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mIsFileVerified) {
            Toast.makeText(this, getText(R.string.import_control_verifying_file), Toast.LENGTH_LONG).show();
            return;
        }

        if (mCompatibilityWarning != null) {
            new AlertDialog.Builder(this).setTitle(R.string.import_control_compatibility_title)
                    .setMessage(mCompatibilityWarning)
                    .setPositiveButton(R.string.import_control_import_button, (dialog, which) -> finishImport(fileName))
                    .setNegativeButton(android.R.string.cancel, null).show();
        } else {
            finishImport(fileName);
        }
    }

    /**
     * Copy the staged control file into the controlmap folder under the provided name.
     */
    private void finishImport(String fileName) {
        if (!isFileNameValid(fileName)) {
            Toast.makeText(this, R.string.import_control_invalid_name, Toast.LENGTH_LONG).show();
            return;
        }
        if (!new File(Tools.CTRLMAP_PATH, "TMP_IMPORT_FILE.json").renameTo(new File(Tools.CTRLMAP_PATH, fileName + ".json"))) {
            Toast.makeText(this, R.string.import_control_invalid_file, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.import_control_done, Toast.LENGTH_SHORT).show();
        finishAndRemoveTask();
    }

    private void importControlFile() {
        File staged = new File(Tools.CTRLMAP_PATH, "TMP_IMPORT_FILE.json");
        staged.delete();
        try (InputStream is = getContentResolver().openInputStream(mUriData);
             OutputStream os = new FileOutputStream(staged)) {
            if (is == null) throw new IOException("Cannot read the selected control file");
            IOUtils.copy(is, os);
        } catch (IOException e) {
            staged.delete();
            Log.w("ImportControlActivity", "Cannot stage control import", e);
        }
    }

    private static boolean isFileNameValid(String fileName) {
        fileName = trimFileName(fileName);
        if (fileName.isEmpty()) return false;
        return !FileUtils.exists(Tools.CTRLMAP_PATH + "/" + fileName + ".json");
    }

    private static String trimFileName(String fileName) {
        if (fileName == null) return "";
        return fileName
                .replace(".json", "")
                .replaceAll("%..", "/")
                .replace("/", "")
                .replace("\\", "")
                .trim();
    }

    private void getUriData() {
        mUriData = getIntent().getData();
        if (mUriData != null) return;
        try {
            mUriData = getIntent().getClipData().getItemAt(0).getUri();
        } catch (Exception ignored) {}
    }

    @Nullable
    private String verifyAndDescribe() {
        try {
            LayoutBitmaps.ControlsContainer layout = LayoutBitmaps.load(new File(Tools.CTRLMAP_PATH, "TMP_IMPORT_FILE.json"));
            JSONObject layoutJobj = new JSONObject(layout.mControlsJson);
            ControlLayoutImporter.Format format = ControlLayoutImporter.detectFormat(layoutJobj);
            if (format == ControlLayoutImporter.Format.UNKNOWN) return null;
            if (Tools.currentDisplayMetrics == null) Tools.currentDisplayMetrics = getResources().getDisplayMetrics();
            Point size = new Point(Math.max(Tools.currentDisplayMetrics.widthPixels, Tools.currentDisplayMetrics.heightPixels),
                    Math.min(Tools.currentDisplayMetrics.widthPixels, Tools.currentDisplayMetrics.heightPixels));
            if (format == ControlLayoutImporter.Format.POJAV) {
                if (LayoutConverter.loadFromString(this, size, layout.mControlsJson) == null) return null;
                return "Aerix / Pojav JSON · Verified";
            }
            ControlLayoutImporter.Result result = ControlLayoutImporter.convert(this, size, layoutJobj, format);
            if (result == null) return null;
            String sourceLabel = format == ControlLayoutImporter.Format.ZALITH ? "Zalith Launcher 2" : "Fold Craft Launcher";
            mCompatibilityWarning = getString(R.string.import_control_compatibility_warning,
                    sourceLabel, result.skippedEntries, result.inertButtons, result.unsupportedEvents);
            return sourceLabel + " · Ready to convert";
        } catch (Exception e) {
            Log.w("ImportControlActivity", "Failed to validate layout", e);
            return null;
        }
    }
}
