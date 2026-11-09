# Wardrobe + Trinkies continuation progress

Updated: 2026-11-08 (UTC)

## Current gate: Step 0 — build and verification infrastructure

**Status: IN PROGRESS / UNVERIFIED.** Local Android compilation is unavailable; GitHub Actions has been selected as the compiler. Do not begin feature Steps 1–5 until the remote build/test result has been inspected and this gate is either accepted or explicitly blocked.

### Evidence collected

- `java -version`: **BLOCKED**, `java: command not found`.
- `sdkmanager --version`: **BLOCKED**, `sdkmanager: command not found`; `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset.
- `./gradlew --version`: **BLOCKED**, wrapper reports no `JAVA_HOME` and no `java` in `PATH`.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tests.trinkies_integration_smoke tests.skin_preview_camera_smoke`: **VERIFIED**, 8 tests passed.
- `node --check app_pojavlauncher/src/main/assets/skinview/skinview.js`: **VERIFIED**, exit 0.
- `tools/trinkies_assets/validate_catalog.py --require-slice`: **VERIFIED**, 30 items (five in each category), compressed pack 324,514 bytes.
- `git diff --check`: **VERIFIED**, exit 0.
- `tests/trinkies_jar_verify.py`: **VERIFIED** against a temporary fixture ZIP assembled from the source descriptor and generated catalog; it has not yet been run against a Gradle-built JAR.
- JDK install attempt: `sudo -n apt-get install -y openjdk-21-jdk-headless` could not locate the package. `sudo -n apt-get update` failed to fetch Debian mirrors because outbound access is restricted in this workspace. Android command-line tools are not available from the workspace's permitted network hosts.
- Fabric client-gametest API discovery: **VERIFIED EXISTS** in the FabricMC/fabric `1.21.11` tree at `fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/api/client/gametest/v1/FabricClientGameTest.java`. Source tree: <https://github.com/FabricMC/fabric/tree/1.21.11/fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/api/client/gametest/v1>.
- The current `:aerix_client_mod` remains a plain Java-library build with a local compile stub for `ClientModInitializer`; it does not yet use Fabric Loom or the client-gametest API. No in-game test has run.
- GitHub CLI authentication is available. The `aerix-debug.yml` workflow has been extended to run on every push to the working branch, build the mod JAR explicitly with the debug APK, verify packaged catalog contents, and upload both artifacts.
- GitHub Actions runs [42558605730](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42558605730) (`e7f8063`) and [42559113121](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559113121) (`9537fa2`): **VERIFIED** smoke-test step and Gradle APK/JAR task passed; post-build JAR discovery failed because CI searched the wrong directory. GitHub's check annotation confirmed the upload glob `aerix_client_mod/build/libs/*.jar` matched no files. `AssetTaskRegistrar.projectJarDependency` redirects the JAR output to `app_pojavlauncher/build/intermediates/package_jar_deps/components/aerix_client`; the workflow was corrected accordingly.
- GitHub Actions run [42559710909](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559710909), commit `a071522`: **VERIFIED** all smoke tests, Gradle debug APK and mod JAR build, JAR content/catalog validation, debug APK package/signature verification, and both artifact uploads. API metadata reports the uploaded JAR is 338,966 bytes and APK is 90,462,521 bytes. Separate Android CI run [42559710893](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559710893) also passed full/no-runtime debug APK tasks. `gh run download` cannot retrieve the artifact bytes in this sandbox because the artifact redirect host `productionresultssa3.blob.core.windows.net` is outside the allowed egress list. This proves the launcher/JAR build path only; it does not prove any Minecraft-version runtime compatibility or in-game rendering.
- GitHub Actions run [42561704155](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42561704155), commit `b1c365a`: **VERIFIED** Android APK/JAR build and JAR contents; **FAILED** before launching the GameTest client. The extracted runner log says `fabric-loom:1.19.4 requires at least Gradle 9.8.0; this build uses Gradle 8.14.3`. The test workflow now installs Gradle 9.8 for the isolated Loom test project and leaves the app build on its existing 8.14.3 wrapper.
- **Outstanding Step 0:** a separate Loom-based 1.21.11 client GameTest harness is configured to load the exact JAR emitted by the Android asset pipeline, start a local world, check the Trinkies mod is loaded, and capture a baseline third-person screenshot with Xvfb/Mesa llvmpipe. This harness remains **UNVERIFIED** until the next pushed CI run and does not exercise an equipped cosmetic. The current build still produces one reflective JAR, not a separate tested artifact for every Minecraft/Java version in `PLAN.md`. Keep Step 0 **IN PROGRESS / UNVERIFIED** until the harness runs and the matrix is resolved or documented as blocked.

### Next action

Commit and push the Gradle 9.8 correction; inspect the 1.21.11 client test run, screenshot artifact, and any Gradle annotations. Fix launch/test failures before moving the gate. Then implement real version adapters/build checks for the requested matrix—do not claim compatibility from the current reflective JAR build alone. If either gate cannot be completed after distinct attempts, record exact errors and stop before dependent feature steps.

## Feature gate status

| Step | Status | Evidence / next action |
|---|---|---|
| 0. Build and verification | IN PROGRESS / UNVERIFIED | Local toolchain unavailable; remote GitHub Actions build pending. Client-gametest API exists but is not wired. |
| 1. One hat end-to-end | NOT STARTED | Requires a green remote APK/JAR build and the version/mapping decision. |
| 2. Other wearable categories and emotes | NOT STARTED | Depends on Step 1. |
| 3. Pets | NOT STARTED | Depends on the renderer and loadout contract. |
| 4. Remaining 270 assets | NOT STARTED / GATED | Do not generate until Steps 1–3 are verified or explicitly blocked with evidence. |
| 5. Other-player sync | NOT STARTED / LAST | Requires explicit opt-in UX, security review, migrations, and deployment verification. |

No hat-render screenshot, real-device measurement, successful client GameTest run, or verified multi-version compatibility matrix is claimed. Run `42561704155` failed before the client launched; its Gradle-9.8 correction is pending CI.
