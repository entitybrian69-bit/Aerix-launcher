# Debug builds and public releases

Testing happens on the debug APK. The public APK is only built when the owner
asks for a release and confirms it.

## The debug APK

| | |
|---|---|
| File | `apk/Aerix-Launcher-debug.apk` |
| Checksum | `apk/Aerix-Launcher-debug.apk.sha256` |
| Build info | `apk/DEBUG_BUILD.txt` (commit, workflow run, build time) |
| CI artifact | Actions run of **Aerix debug (testing)**, artifact `aerix-debug-apk` |
| Source | the `v1.3.6` release commit (`ba3e4c2`) restored into this branch |
| Variant | `noruntimeDebug` |
| applicationId | `net.kdt.pojavlaunch.debug` |
| Label | Aerix Launcher (Debug) |
| Signing | `app_pojavlauncher/debug.keystore` (alias `androiddebugkey`) |

Because the applicationId ends in `.debug`, this APK installs next to the public
launcher on the same phone. Both can be open at once, and updating the debug
build never touches the released app or its data.

The `noruntime` flavor does not embed a Java runtime, so the APK stays under the
100 MB file limit that GitHub applies to files stored in the repository. The
launcher downloads the runtime it needs on first launch.

### When it is rebuilt

`.github/workflows/aerix-debug.yml` runs on every push to the working branch that
touches the app sources, Gradle files, submodules or the workflow itself. It also
has a manual trigger. The job builds the APK, checks that the package name really
is `net.kdt.pojavlaunch.debug`, uploads it as an artifact, and commits it to
`apk/` so the newest test build always sits at a fixed path.

Commits made by that job only replace the files in `apk/`; they carry no other
changes.

### Building one locally

```bash
./gradlew :app_pojavlauncher:assembleNoruntimeDebug
# output: app_pojavlauncher/build/outputs/apk/noruntime/debug/app_pojavlauncher-noruntime-debug.apk
```

A local build needs JDK 21, the Android SDK with NDK `29.0.14206865`, and
initialised submodules (`git submodule update --init --recursive`).

## Public releases

Public APKs are **not** stored in this repository. `Aerix-Launcher.apk` is
around 118 MB, which is above GitHub's 100 MB per-file limit, so release builds
live as assets on the GitHub Releases page and nowhere else.

Current public release: **v1.3.6**, tag target `ba3e4c2`.

The sources in this branch are that release tree. The `v3_openjdk` tip this
branch was originally cut from was missing 45 Aerix source files (for example
`DiscoverScreen.kt` was a 44-line stub instead of 1,915 lines), so anything built
from it was not the code users run. The restore commit brings the release tree
back, and the debug APK is now built from it.

| Asset | Size | SHA-256 |
|---|---|---|
| `Aerix-Launcher-1.3.6.apk` | 118,540,082 | `e3afc03e19d35b7dc32385af04e596866799485dd64bf833731790f8f400f069` |
| `Aerix-Launcher-1.3.6-noruntime.apk` | 89,453,304 | `9126d57917b272c38dc8924d5c64fdde7a87c0433002f8d7fc854cf0d99ee6e5` |

Those assets are left untouched. Nothing in this repository rebuilds them or
re-uploads them.

### Releasing

Both publishing workflows take a confirmation input and do nothing without it:

* `.github/workflows/release.yml` — needs `RELEASE` typed in.
* `.github/workflows/nightly.yml` — needs `NIGHTLY` typed in. This one creates a
  prerelease on GitHub.

A release happens when the owner says so, and not before. Until then, changes are
verified on the debug APK.
