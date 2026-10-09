# Aerix implementation parity checklist

The six reference screenshots and exact logo were attached in the conversation, but their image files are not present in this checkout or the workspace upload directory. A landscape-first native shell is now in place, but screenshot-by-screenshot comparison and exact logo integration remain blocked until those original files are accessible. The Aerix README text was supplied inline; README replacement is still pending an asset audit so the result does not ship with broken image references or unsupported claims.

## Confirmed target-project foundations to preserve

- [x] Android launcher and Minecraft Java launch pipeline, including version metadata, classpath/native extraction, and Java runtime selection.
- [x] Existing instance/profile selection, editing, installer, login, and migration flows.
- [x] Existing Microsoft/offline/auth-server login paths present in the target source.
- [x] Existing renderer implementations and plugin hooks present in the target source.
- [x] Existing custom touch controls, gamepad mapping, file/provider flows, logs/crash reporting, and settings screens present in the target source.
- [x] Existing Modrinth and CurseForge mod/modpack code paths are retained, but the distributed Aerix build uses the unkeyed Modrinth provider only. No CurseForge API key is embedded or injected into CI artifacts; CurseForge API access stays disabled until a secure proxy/credential flow exists.

These are source-inventory findings, not claims that each feature was exercised in this session.

## Aerix delivery checklist

### Blocked on supplied material

- [ ] Compare all six screenshots against native launcher screens.
- [ ] Apply the exact supplied Aerix logo to launcher icon, splash, navigation, and About locations. Do not substitute the Pojav icon or a redraw. The current shell uses a text-only “AERIX” label until the actual logo file is available.
- [ ] Copy/verify the README-referenced artwork and screenshots. `assets/team/entitybrian.jpg` was not among the supplied attachment filenames and must be checked once the assets are accessible.
- [ ] Inspect reference commit `a0254b9` and reconcile reference features. The requested reference branch is not present in the origin/API branch list available to this session.

### Implemented in this worktree

- [x] Declare landscape orientation for the launcher, storage gate, launcher settings, and error screens while leaving external browser/OAuth activities and the game host's existing orientation behavior alone.
- [x] Add a dark, responsive Aerix navigation rail and dashboard shell with Home, Create, Library, Discover, Wallpapers, Settings, and persistent account access. It uses an opaque/gradient fallback rather than depending on blur/translucency.
- [x] Route Create to the existing vanilla/loader/modpack creation flows and Discover to the existing Modrinth-backed modpack browser.
- [x] Add a responsive instance library with asynchronous enumeration, select-and-launch, edit, delete confirmation, and a real empty state. Existing instance storage and launch code remain authoritative.
- [x] Add a custom wallpaper picker with a bounded image decoder, persisted document URI, preview, reset action, and gradient fallback. The curated wallpaper catalog/screenshots are not present in this checkout.
- [x] Add a manual GitHub Releases check in Settings. It uses the public API without credentials and opens the release page; it does not download or silently install APKs.
- [x] Add Smart Pick as the new-install renderer default using the existing version-to-renderer compatibility rule; it selects GL4ES for compatible older game versions and LTW only when GLES 3 and the LTW library are available. It never auto-selects Zink/Vulkan.
- [x] Validate the EGL API needed by SDL after loading a renderer and retry the GL4ES route if the chosen backend is unusable.
- [x] Keep Vulkan package-feature detection explicitly documented as a prerequisite rather than a promise of backend-specific Vulkan features.
- [x] Add an ABI/runtime availability policy for the Java 8/17/21/25 channels and unit tests for renderer selection, Vulkan declarations, and runtime/ABI filtering.
- [x] Add a CI APK ABI check for the four declared ABIs.
- [x] Set the Android version name/build labels to Aerix Launcher 1.0.0 and choose a version-code sequence above this checkout's previous local version code. The existing application ID and Pojav storage folder are retained to avoid silently abandoning existing data.
- [x] Publish the source-level Android/GPU/ABI/runtime envelope and clearly separate it from physical-device verification.

### Remaining feature and verification work

- [ ] Apply and compare the exact supplied logo and six screenshots; complete screenshot-level spacing, artwork, and page-parity review.
- [ ] Import the exact supplied README and referenced image files. Reconcile claims against code, correct the Android 8.0+ statement to the actual manifest minimum API 23 / Android 6.0, and verify all URLs and asset paths.
- [ ] Complete the curated wallpaper library; the current picker only supports user-selected images because the supplied wallpaper assets are not in the workspace.
- [ ] Complete per-instance favorites/groups/sorting/pin behavior and verify all instance actions and migration paths.
- [ ] Expand Discover to the requested project categories, filters, details, compatibility, dependencies, verified downloads, destinations, cancellation/retry, caching, and attribution; exercise both providers through a secure configuration.
- [ ] Verify permitted skin-search endpoints/terms and implement account-safe preview/download/equip flows; do not present a website link as an API.
- [ ] Implement secure in-app update download/installation if required; current release checker intentionally only opens the GitHub release page.
- [ ] Add dedicated server management/connection flows; existing logs, game-directory, controls, and file/provider tools are retained, but a new server manager has not been implemented.
- [ ] Verify client/native-library ABI availability and backend requirements release-by-release for Minecraft 26.x on Android. Mojang metadata has been checked for 26.1.2, 26.2, and 26.3 and each declares Java 25; that metadata does not establish Android renderer or ARM32 support.
- [ ] Complete the Narzo 50/Mali-G57 regression, Adreno/Mali Vulkan and GLES-only, ARM32, x86/x86_64, memory-tier, API-level, and orientation/inset device matrix on physical devices/emulators.
- [x] CI run [37955759081](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/37955759081) passed unit tests, built full and no-runtime Debug APKs, and ran the four-ABI native-library verifier. Artifacts: [full Debug ZIP](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/37955759081/artifacts/11627633705) and [no-runtime Debug ZIP](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/37955759081/artifacts/11627608769) (commit `98cbad6`). The optional Mesa artifact lookup was not found; device renderer testing remains outstanding.
- [ ] Verify the contents of the APK artifacts and each bundled runtime on-device; CI ABI/library presence is not a physical-device launch test.

A checked source box only records that a code change or source inspection exists. It does not replace CI, emulator, or physical-device verification.
