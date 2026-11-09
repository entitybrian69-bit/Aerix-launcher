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
- `tests/trinkies_jar_verify.py`: **VERIFIED** against a temporary fixture ZIP and against the Gradle-built Trinkies JAR in GitHub Actions runs [42559710909](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559710909), [42561704155](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42561704155), and [42562329477](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42562329477).
- JDK install attempt: `sudo -n apt-get install -y openjdk-21-jdk-headless` could not locate the package. `sudo -n apt-get update` failed to fetch Debian mirrors because outbound access is restricted in this workspace. Android command-line tools are not available from the workspace's permitted network hosts.
- Fabric client-gametest API discovery: **VERIFIED EXISTS** in the FabricMC/fabric `1.21.11` tree at `fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/api/client/gametest/v1/FabricClientGameTest.java`. Source tree: <https://github.com/FabricMC/fabric/tree/1.21.11/fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/api/client/gametest/v1>.
- The production `:aerix_client_mod` remains a plain Java-library build with a local compile stub for `ClientModInitializer`; the separate `tools/trinkies_client_gametest` Loom project loads its packaged output for a baseline client smoke test. No in-game test has run yet.
- GitHub CLI authentication is available. The `aerix-debug.yml` workflow has been extended to run on every push to the working branch, build the mod JAR explicitly with the debug APK, verify packaged catalog contents, and upload both artifacts.
- GitHub Actions runs [42558605730](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42558605730) (`e7f8063`) and [42559113121](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559113121) (`9537fa2`): **VERIFIED** smoke-test step and Gradle APK/JAR task passed; post-build JAR discovery failed because CI searched the wrong directory. GitHub's check annotation confirmed the upload glob `aerix_client_mod/build/libs/*.jar` matched no files. `AssetTaskRegistrar.projectJarDependency` redirects the JAR output to `app_pojavlauncher/build/intermediates/package_jar_deps/components/aerix_client`; the workflow was corrected accordingly.
- GitHub Actions run [42559710909](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559710909), commit `a071522`: **VERIFIED** all smoke tests, Gradle debug APK and mod JAR build, JAR content/catalog validation, debug APK package/signature verification, and both artifact uploads. API metadata reports the uploaded JAR is 338,966 bytes and APK is 90,462,521 bytes. Separate Android CI run [42559710893](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42559710893) also passed full/no-runtime debug APK tasks. `gh run download` cannot retrieve the artifact bytes in this sandbox because the artifact redirect host `productionresultssa3.blob.core.windows.net` is outside the allowed egress list. This proves the launcher/JAR build path only; it does not prove any Minecraft-version runtime compatibility or in-game rendering.
- GitHub Actions run [42561704155](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42561704155), commit `b1c365a`: **VERIFIED** Android APK/JAR build and JAR contents; **FAILED** before launching the GameTest client because Loom 1.19.4 requires Gradle 9.8.0 while the nested build used 8.14.3.
- GitHub Actions run [42562329477](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42562329477), commit `ac9ec53`: **VERIFIED** debug APK/JAR Gradle tasks and built-JAR catalog check; **FAILED before Minecraft launch** because `gradle/actions/setup-gradle@v4` rejected the shorthand `gradle-version: "9.8"` with `Gradle version 9.8 does not exist`. The Gradle repository has a `v9.8.0` release tag; the workflow now requests exact `9.8.0` for the isolated test project. No screenshot was produced or uploaded.
- **Outstanding Step 0:** the separate Loom-based 1.21.11 client GameTest harness is configured to load the exact JAR emitted by the Android asset pipeline, start a local world, check the Trinkies mod is loaded, and capture a baseline third-person screenshot with Xvfb/Mesa llvmpipe. It remains **UNVERIFIED** until this exact-version correction is tested and the client actually launches. The harness does not exercise an equipped cosmetic. The current build still produces one reflective JAR, not a separate tested artifact for every Minecraft/Java version in `PLAN.md`. Keep Step 0 **IN PROGRESS / UNVERIFIED** until the harness runs and the matrix is resolved or documented as blocked.

### Next action

Commit and push the exact Gradle 9.8.0 version correction; inspect the next 1.21.11 client test run, screenshot artifact, and any Gradle annotations. Fix launch/test failures before moving the gate. Then implement real version adapters/build checks for the requested matrix—do not claim compatibility from the current reflective JAR build alone. If either gate cannot be completed after distinct attempts, record exact errors and stop before dependent feature steps.

## Feature gate status

| Step | Status | Evidence / next action |
|---|---|---|
| 0. Build and verification | IN PROGRESS / UNVERIFIED | Remote APK/JAR Gradle tasks and built-JAR catalog checks pass in runs 42561704155 and 42562329477. GameTest attempts have both stopped before client launch (first Gradle too old; second setup action rejected shorthand 9.8); exact 9.8.0 is pending CI. Version matrix remains unverified. |
| 1. One hat end-to-end | NOT STARTED | Requires a green remote APK/JAR build and the version/mapping decision. |
| 2. Other wearable categories and emotes | NOT STARTED | Depends on Step 1. |
| 3. Pets | NOT STARTED | Depends on the renderer and loadout contract. |
| 4. Remaining 270 assets | NOT STARTED / GATED | Do not generate until Steps 1–3 are verified or explicitly blocked with evidence. |
| 5. Other-player sync | NOT STARTED / LAST | Requires explicit opt-in UX, security review, migrations, and deployment verification. |

No hat-render screenshot, real-device measurement, successful client GameTest run, or verified multi-version compatibility matrix is claimed. Runs `42561704155` and `42562329477` both failed before the client launched; exact Gradle 9.8.0 setup is pending CI.
