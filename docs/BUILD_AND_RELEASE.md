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
| Source | the 1.4.0 tree on this branch (`version.properties` = `1.4.0`) |
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

Current public release: **v1.4.0**, tag target `6f790e7`.

| Asset | Size | SHA-256 |
|---|---|---|
| `Aerix-Launcher-1.4.0.apk` | 121,248,012 | `2da4e4e3ff0e657b4e4c68c2fbfc738c71552a1bfc495a945a7dc66ce973cba3` |
| `Aerix-Launcher-1.4.0-noruntime.apk` | 92,137,418 | `7726574de9a6b001d30455bcdf01a9f764fa2a29ac039ea3b6c85e2bf6f14463` |
| `Aerix-Launcher.apk` | 121,248,012 | same bytes as the 1.4.0 APK |
| `Aerix-Launcher-noruntime.apk` | 92,137,418 | same bytes as the 1.4.0 no-runtime APK |
| `SHA256SUMS` | 372 | `3c7cfe2dbeb47ad293d9cee4fae1dcd5f2c4eef4b3cda66d73f60ddbdd54a7da` |

The unversioned names are copies of the same files. The download buttons on the
website point at `releases/latest/download/<name>`, so a release has to carry
them alongside the versioned names the in-app updater looks for.

Release notes for each version live in `docs/releases/`.

### Releasing

Both publishing workflows take a confirmation input and do nothing without it:

* `.github/workflows/release.yml` — needs `RELEASE` typed in. It builds both
  editions, names them after `version.properties`, writes `SHA256SUMS`, then a
  second job puts them on the Releases page as `v<version>` with
  `docs/releases/v<version>.md` as the notes. It refuses to touch a tag that
  already exists. Where the tooling has no Actions-write token, the same build
  can be started by pushing a change to that file with `[build-release]` in the
  commit message.
* `.github/workflows/nightly.yml` — needs `NIGHTLY` typed in. This one creates a
  prerelease on GitHub.

To release a new version: raise `versionName` in `version.properties`, add
`docs/releases/v<version>.md`, and run the release workflow.

A release happens when the owner says so, and not before. Until then, changes are
verified on the debug APK.
