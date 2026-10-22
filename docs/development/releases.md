# Launcher versions and in-app updates

- `version.properties` is the single source of the release version. Increment `versionName` for each published update; Gradle derives the monotonically increasing Android version code (`major * 10000 + minor * 100 + patch`). Minor and patch components must stay below 100.
- About and renderer settings display `BuildConfig.VERSION_NAME`, including the no-runtime suffix where applicable. Do not add a separate hard-coded UI version.
- Build the signed release APKs with Android CI. Publish the full APK under `Aerix-Launcher-<version>.apk` (or `Aerix-Launcher.apk`), and the no-runtime edition under `Aerix-Launcher-<version>-noruntime.apk` (or `Aerix-Launcher-noruntime.apk`). The stable GitHub release tag must be `v<version>` and must match the APK's embedded version. Do not relabel an older APK as a new release.
- The in-app checker reads the official repository's latest stable GitHub release, not CI artifacts, drafts or prereleases. Publishing a release is a separate maintainer operation; pushing a build does not publish it automatically.
- The updater preserves full/no-runtime edition, validates size and GitHub SHA-256 when available, and requires the APK's package, embedded version, version code and signing certificate to match an authentic newer update of the installed application. Keep using the same release signing identity.
- Downloads use the private update cache. Android receives only a read-only URI for the update APK. Android's install permission and confirmation are mandatory; the launcher never silently installs an APK.
- Required source copyright and license notices remain in the repository even though the optional About-page acknowledgements cards have been removed.
