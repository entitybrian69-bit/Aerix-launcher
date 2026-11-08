package net.kdt.pojavlaunch.tasks;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Copies supported files from another Aerix/Pojav launcher installation without deleting its data. */
public class DataMigrator {
    private static final String TAG = "DataMigration";
    private static final int MAX_TREE_DEPTH = 256;
    private static final String[] TREE_PROJECTION = {
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_SIZE
    };

    private final Uri uri;
    private final Activity activity;
    private double progress;
    private int copiedFiles;
    private int skippedItems;
    private long copiedBytes;

    /** @param uri tree URI for the old launcher's exported game-data folder */
    public DataMigrator(Activity activity, Uri uri) {
        this.activity = activity;
        this.uri = uri;
    }

    private void updateProgress(double step, String entry) {
        progress = Math.min(100.0, progress + step);
        ProgressLayout.setProgress(ProgressLayout.DATA_MIGRATION, (int) progress,
                activity.getString(R.string.migration_progress_copying, entry));
    }

    private Uri getRootChildrenUri(Uri treeUri) throws IOException {
        String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        if (rootDocumentId == null || rootDocumentId.isEmpty()) {
            throw new IOException("The selected launcher folder has no document root");
        }
        return DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId);
    }

    private boolean isSameStorageRootAsCurrent(Uri treeUri) {
        try {
            String sourceRoot = DocumentsContract.getTreeDocumentId(treeUri);
            if (sourceRoot == null || !new File(sourceRoot).isAbsolute()) return false;
            return new File(sourceRoot).getCanonicalPath()
                    .equals(new File(Tools.DIR_GAME_HOME).getCanonicalPath());
        } catch (IOException | RuntimeException error) {
            Log.d(TAG, "Could not compare selected and current data roots", error);
            return false;
        }
    }

    private void showToast(int messageId) {
        Tools.runOnUiThread(() -> Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show());
    }

    private void completeMigrationNotice() {
        if (LauncherPreferences.DEFAULT_PREF != null) {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putBoolean("migrationNotice", false)
                    .remove("migrationNoticeSnoozedUntil")
                    .apply();
        }
        LauncherPreferences.PREF_MIGRATION_NOTICE = false;
    }

    private void allowMigrationPromptRetry() {
        if (LauncherPreferences.DEFAULT_PREF != null) {
            LauncherPreferences.DEFAULT_PREF.edit().remove("migrationNoticeSnoozedUntil").apply();
        }
    }

    /** Validate the selected DocumentsProvider before doing any work. */
    public void migrateData() {
        if (uri == null) {
            allowMigrationPromptRetry(); // The user can cancel the SAF picker without starting an import.
            return;
        }
        String authority = uri.getAuthority();
        if (!DataMigrationPolicy.isTrustedSourceAuthority(authority)) {
            allowMigrationPromptRetry();
            Toast.makeText(activity, R.string.migration_progress_foreign, Toast.LENGTH_LONG).show();
            return;
        }
        if (authority.equals(activity.getString(R.string.storageProviderAuthorities))) {
            allowMigrationPromptRetry();
            Toast.makeText(activity, R.string.migration_progress_self, Toast.LENGTH_LONG).show();
            return;
        }
        if (isSameStorageRootAsCurrent(uri)) {
            Toast.makeText(activity, R.string.migration_already_shared, Toast.LENGTH_LONG).show();
            completeMigrationNotice();
            return;
        }
        sExecutorService.submit(this::executeMigrate);
    }

    private void executeMigrate() {
        File destinationRoot = new File(Tools.DIR_GAME_HOME);
        boolean progressStarted = false;
        try {
            if (!destinationRoot.isDirectory() && !destinationRoot.mkdirs() && !destinationRoot.isDirectory()) {
                throw new IOException("Unable to create destination folder " + destinationRoot);
            }

            ContentResolver resolver = activity.getContentResolver();
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri source = getRootChildrenUri(uri);

            ProgressLayout.setProgress(ProgressLayout.DATA_MIGRATION, 0,
                    activity.getString(R.string.migration_progress_scanning));
            progressStarted = true;
            long bytesToCopy = calculateBytesToCopy(source, destinationRoot, 0);
            if (bytesToCopy > 0) {
                long availableBytes = new StatFs(destinationRoot.getAbsolutePath()).getAvailableBytes();
                long missingMegabytes = DataMigrationPolicy.missingMegabytes(availableBytes, bytesToCopy);
                if (missingMegabytes > 0) {
                    allowMigrationPromptRetry();
                    Tools.dialogOnUiThread(activity, activity.getString(R.string.migration_progress_warning_title),
                            activity.getString(R.string.migration_progress_space, missingMegabytes));
                    return;
                }
            }

            progress = 0;
            copiedFiles = 0;
            skippedItems = 0;
            copiedBytes = 0;
            ProgressLayout.setProgress(ProgressLayout.DATA_MIGRATION, 0);
            copyFileTree(source, destinationRoot, 100.0, 0);

            if (copiedFiles == 0 && skippedItems == 0) {
                allowMigrationPromptRetry();
                showToast(R.string.migration_progress_nothing_to_import);
            } else {
                completeMigrationNotice();
                Tools.runOnUiThread(() -> Toast.makeText(activity,
                        activity.getString(R.string.migration_progress_result, copiedFiles, skippedItems),
                        Toast.LENGTH_LONG).show());
            }
            Log.i(TAG, "Imported " + copiedFiles + " files (" + copiedBytes + " bytes); kept " + skippedItems + " existing items");
        } catch (Exception error) {
            allowMigrationPromptRetry();
            Log.e(TAG, "Failed to import launcher data", error);
            showToast(R.string.migration_progress_failed);
            Tools.showErrorRemote(error);
        } finally {
            progress = 0;
            if (progressStarted) ProgressLayout.clearProgress(ProgressLayout.DATA_MIGRATION);
        }
    }

    /** Estimate only entries that will actually be added; existing files are never overwritten. */
    private long calculateBytesToCopy(Uri source, File destination, int depth) throws IOException {
        if (depth > MAX_TREE_DEPTH) throw new IOException("Selected data tree is unexpectedly deep");
        ContentResolver resolver = activity.getContentResolver();
        try (Cursor cursor = resolver.query(source, TREE_PROJECTION, null, null, null)) {
            if (cursor == null) throw new IOException("Unable to read the selected launcher folder");
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            long totalBytes = 0;

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                String type = cursor.getString(typeColumn);
                String documentId = cursor.getString(idColumn);
                Uri child = DocumentsContract.buildChildDocumentsUriUsingTree(source, documentId);
                File destinationEntry = resolveChild(destination, name);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(type)) {
                    if (shouldSkipDirectory(destination, destinationEntry)) continue;
                    if (destinationEntry.exists() && !destinationEntry.isDirectory()) continue;
                    totalBytes = DataMigrationPolicy.saturatedAdd(totalBytes,
                            calculateBytesToCopy(child, destinationEntry, depth + 1));
                } else if (!destinationEntry.exists()) {
                    if (cursor.isNull(sizeColumn)) {
                        throw new IOException("Unable to determine the size of " + name);
                    }
                    long size = cursor.getLong(sizeColumn);
                    if (size < 0) throw new IOException("Unable to determine the size of " + name);
                    totalBytes = DataMigrationPolicy.saturatedAdd(totalBytes, size);
                }
            }
            return totalBytes;
        }
    }

    private void copyFileTree(Uri source, File destination, double progressPortion, int depth) throws IOException {
        if (depth > MAX_TREE_DEPTH) throw new IOException("Selected data tree is unexpectedly deep");
        ContentResolver resolver = activity.getContentResolver();
        try (Cursor cursor = resolver.query(source, TREE_PROJECTION, null, null, null)) {
            if (cursor == null) throw new IOException("Unable to read the selected launcher folder");
            int count = cursor.getCount();
            if (count == 0) {
                updateProgress(progressPortion, destination.getName());
                return;
            }
            double step = progressPortion / count;
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                String type = cursor.getString(typeColumn);
                String documentId = cursor.getString(idColumn);
                Uri child = DocumentsContract.buildChildDocumentsUriUsingTree(source, documentId);
                File destinationEntry = resolveChild(destination, name);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(type)) {
                    if (shouldSkipDirectory(destination, destinationEntry) ||
                            (destinationEntry.exists() && !destinationEntry.isDirectory())) {
                        skippedItems++;
                        updateProgress(step, name);
                        continue;
                    }
                    if (!destinationEntry.exists() && !destinationEntry.mkdirs() && !destinationEntry.isDirectory()) {
                        throw new IOException("Unable to create folder " + destinationEntry);
                    }
                    copyFileTree(child, destinationEntry, step, depth + 1);
                } else {
                    if (destinationEntry.exists()) {
                        skippedItems++;
                    } else {
                        long copied = copyFileAtomically(resolver, child, destinationEntry);
                        if (copied < 0) {
                            skippedItems++;
                        } else {
                            copiedFiles++;
                            copiedBytes = DataMigrationPolicy.saturatedAdd(copiedBytes, copied);
                        }
                    }
                    updateProgress(step, name);
                }
            }
        }
    }

    private boolean shouldSkipDirectory(File parent, File child) throws IOException {
        // Keep a user's current instance intact when an imported instance has the same name.
        File instancesRoot = new File(Tools.DIR_GAME_HOME, "instances");
        return parent.getCanonicalFile().equals(instancesRoot.getCanonicalFile()) && child.exists();
    }

    private File resolveChild(File parent, String name) throws IOException {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name) ||
                name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IOException("The selected launcher returned an invalid file name");
        }
        String rootPath = new File(Tools.DIR_GAME_HOME).getCanonicalPath();
        String parentPath = parent.getCanonicalPath();
        String rootPrefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!parentPath.equals(rootPath) && !parentPath.startsWith(rootPrefix)) {
            throw new IOException("Refusing to write through a folder outside the launcher directory");
        }
        String childPath = new File(parent, name).getCanonicalPath();
        String parentPrefix = parentPath.endsWith(File.separator) ? parentPath : parentPath + File.separator;
        if (!childPath.startsWith(parentPrefix)) throw new IOException("Refusing to write outside the launcher folder");
        return new File(parent, name);
    }

    /** Returns copied bytes, or -1 if a destination conflict appeared while copying. */
    private long copyFileAtomically(ContentResolver resolver, Uri source, File destination) throws IOException {
        if (destination.exists()) return -1;
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())) {
            throw new IOException("Unable to create parent folder for " + destination);
        }

        File temporary = File.createTempFile(".aerix-migration-", ".part", parent);
        long bytesCopied = 0;
        try {
            InputStream input = resolver.openInputStream(source);
            if (input == null) throw new IOException("Unable to open imported file " + destination.getName());
            try (InputStream in = input; FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytesCopied += read;
                }
                out.getFD().sync();
            }

            if (!publishWithoutReplacing(temporary, destination)) return -1;
            return bytesCopied;
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Could not remove temporary import file " + temporary);
            }
        }
    }

    /** Publish the completed temporary file without ever replacing a destination conflict. */
    private boolean publishWithoutReplacing(File temporary, File destination) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return publishWithFilesMove(temporary, destination);
        }
        try {
            // link(2) atomically fails with EEXIST rather than replacing an existing destination.
            Os.link(temporary.getAbsolutePath(), destination.getAbsolutePath());
            return true;
        } catch (ErrnoException error) {
            if (destination.exists()) return false;
            throw new IOException("Unable to publish imported file without replacing an existing item: "
                    + destination.getName(), error);
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private boolean publishWithFilesMove(File temporary, File destination) throws IOException {
        try {
            // No REPLACE_EXISTING option: a file created during the copy remains untouched.
            Files.move(temporary.toPath(), destination.toPath());
            return true;
        } catch (IOException error) {
            if (destination.exists()) return false;
            throw error;
        }
    }
}
