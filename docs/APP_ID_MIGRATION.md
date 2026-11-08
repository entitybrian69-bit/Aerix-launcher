# Moving from the previous Aerix package ID

Aerix now installs as `net.al.aerixlauncher` (`.debug` for testing and `.pub` for nightly builds). Earlier builds used `net.kdt.pojavlaunch` and its variants. Android treats those as different apps, so the new APK installs separately; it does not update or remove the old app.

## Import on Android 10 and newer

1. Install and open the new Aerix app **without uninstalling the old one**.
2. When the old install is detected, choose **Choose old install**. If you dismiss the prompt, use **Settings → Import data** later.
3. In Android's folder picker, select the old Aerix/PojavLauncher document-provider root. Do not select the new installation. Confirm the import.
4. Wait for the summary. Existing files in the new installation are kept; conflicting items are skipped rather than overwritten. The source installation is never modified. The importer checks that the copied data and a 2 GB free-space reserve fit before copying.
5. Check your instances and control maps, sign in again, and download runtimes as needed. Keep the old app until the new installation has launched successfully.

The import is limited to supported files exposed by the old launcher's document provider, including game files, instances, and control maps. Android-private app data is isolated by package ID and is not copied: saved sign-in tokens, preferences, caches, and installed runtimes remain with the old app. For account safety, sign in again in the new app rather than trying to copy authentication tokens.

If the import is interrupted, the source remains intact. You can retry from Settings; already-present destination files are kept.

## Android 9 and older

These versions use the shared `/storage/emulated/0/games/PojavLauncher` game-data directory, so the old and new apps already see the same instances and game files. No file import is needed. Private app data is still separate, so sign in again and reinstall runtimes as needed.

## Publishing

The new package ID requires a separate Google Play app entry. The old Play listing cannot be renamed to the new package ID. Keep download and release notes explicit that this is a separate installation, not an in-place update.
