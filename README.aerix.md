# Aerix Launcher

> **Brand assets still pending:** keep the upstream `README.md` untouched. This Aerix product README uses the exact placeholders below until the approved identity package is available. Do not replace these supplied assets with redraws or generated substitutes.
>
> Required files: `aerix-logo.svg`, `screenshot-1.png` through `screenshot-6.png`, and `assets/team/entitybrian.jpg`.

<!-- MISSING: aerix-logo.svg -->

**Aerix Launcher** is an Android launcher for Minecraft: Java Edition, based on the preserved Pojav/Mojo launch and runtime foundations. Its launcher interface is landscape-first; this does not certify successful Minecraft launches on every phone, tablet, foldable, Chromebook, Android release, graphics driver, or game version.

<!-- MISSING: screenshot-1..6.png -->

## Overview

Aerix keeps the existing Minecraft Java launch pipeline and account, profile, renderer, runtime, controls, file, and log foundations while adding a liquid-glass launcher interface: a slim navigation rail, translucent pale-edged panels, cyan/teal active states, and wallpaper-backed screens. Launcher screens opt into Android immersive fullscreen; system bars can be temporarily revealed by gesture, while Android multi-window uses its normal bars. The instance library adds favorites, groups, pinning, sorting, profile editing, and launch actions. A local server-list manager edits the selected profile's standard `servers.dat` file. Discover provides Modrinth browsing and optional CurseForge browsing through a personal API key entered by the user and stored in the app's private preferences. The category filters cover modpacks, mods, resource packs, shaders, and world saves; CurseForge single-file installs include bounded download and checksum checks, and world archives are extracted with path and size limits. These new provider and installation paths still require build/API/runtime verification.

The wallpaper gallery provides **25 selectable landscape presets** (20 generated scenes and five color-tuned variants), plus custom image selection. Choosing a wallpaper updates the full-screen launcher background and samples a saturated color to recolor launcher accents. Preset, custom hex, per-section, and Android 12+ Material You color modes are also available. Skin Studio includes bounded public Mojang username lookup/preview and a separate, confirmed upload flow for the signed-in Microsoft account; public search results cannot be uploaded directly. The earlier CI run linked below passed unit tests, wallpaper verification, full/no-runtime Debug APK builds, and the four-ABI native-library check for its earlier commit only. The current feedback changes are awaiting a new CI build; runtime behavior and visual parity remain unverified on a device, and screenshots/logo parity require review against the supplied assets.

The launcher does not claim universal Minecraft compatibility. Game-version metadata, runtime availability, native libraries, renderer requirements, ABI, Android API, memory, and device behavior must be considered together. The [compatibility matrix](docs/COMPATIBILITY_MATRIX.md) identifies evidence and untested combinations.

## Android and ABI support

- **Manifest minimum:** Android API 23 (Android 6.0).
- **Manifest target:** Android API 36.
- **Declared native ABIs:** `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- **CI evidence:** the Debug APK ABI verifier checks native-library entries. APK ABI presence is **not** a device launch test.
- **Runtimes:** Java 8 is included in the `full` build; Java 17, 21, and 25 are available through signed on-demand runtime channels where supported by the launcher's ABI policy.
- **ARM32 warning:** a Java 25 runtime archive for ARM32 is not evidence that Minecraft 26.x or its client/native dependencies support ARM32.
- **Devices:** physical-device and emulator coverage must be reported separately; no combination should be described as tested without a recorded run.

See [docs/COMPATIBILITY_MATRIX.md](docs/COMPATIBILITY_MATRIX.md) for the API, ABI, runtime × Minecraft-version × ABI, renderer, and form-factor tables.

## Renderer choices

GL4ES and LTW are OpenGL-based paths with different game-version, GLES, and library requirements. ANGLE is an opt-in GLES provider where a supported system implementation or external plugin is available. Vulkan/Zink routes are **manual-select only**; Smart Pick does not automatically select Vulkan. Android Vulkan package features are prerequisites, not proof of a specific driver's required extensions, features, or presentation support.

## Downloads and builds

The latest completed pre-feedback CI run is [GitHub Actions run 38021283279](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38021283279), for commit `e0b470e`. Launcher unit tests, the wallpaper verifier, full/no-runtime Debug APK builds, and four-ABI native-library verification passed there; this run predates the current uncommitted feedback changes and does not validate them. Its [full Debug APK artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38021283279/artifacts/11657803269) and [no-runtime Debug APK artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38021283279/artifacts/11658182971) each include an APK and checksum file. A fresh run is required for the pending changes. APK ABI packaging is not an Android device or Minecraft launch test. CI does not produce a release AAB for this fork/branch.

To build locally, use the checked-in Gradle wrapper and follow the repository's build setup. The Android SDK, NDK, JDK, required submodules, native dependencies, and optional renderer artifacts must be installed/configured as described by the source project. A successful build is not a device-compatibility test.

## Accounts, security, and third-party services

Aerix must not embed shared CurseForge API secrets, OAuth credentials, or user tokens in source or distributed APKs. CurseForge access is optional and requires a user-supplied API key held in private app preferences; the key is not displayed in summaries or logged. Authentication tokens stay in the launcher's account flow and must never be written to logs or copied into documentation. Public API requests should use transport security, bounded responses/downloads, and verified checksums where available. The stored API key uses Android's ordinary app-private preferences and is not represented as hardware-backed or encrypted secret storage.

## Known limitations

- The logo and six supplied screenshots are not staged in this checkout; the approved liquid-glass visual match cannot be certified from generated wallpapers or mockups alone. The existing upstream app icon has not been replaced by an unlocated Aerix logo.
- Modrinth browsing, installer coverage, dependency/conflict handling, skin upload against Minecraft Services, and the updater's final download/install flow need additional verification or implementation as listed in the parity checklist.
- The server manager edits local `servers.dat`; it does not connect to servers.
- The wallpaper catalog and theme code compile, and the bundled catalog passes CI validation. Actual wallpaper selection/rendering, custom-image persistence, sampled accent updates, and custom theme behavior still need runtime/device verification.
- CI/emulator results do not replace physical-device testing. Renderer, ABI, Android API, memory-tier, orientation/inset, and foldable-posture combinations may remain untested.

## Screenshots

The six reference images belong here after the exact supplied files are staged. Keep their order aligned with the UI walkthrough and do not substitute placeholders in a release README.

## Credits and team

<!-- MISSING: assets/team/entitybrian.jpg -->

- Aerix maintainers and contributors: [project repository](https://github.com/entitybrian69-bit/Aerix-launcher).
- Minecraft, PojavLauncher/MojoLauncher foundations, runtime packages, renderer projects, and other third-party libraries remain the property of their respective authors. Preserve their licenses and notices when redistributing.

## License

The repository's existing license and upstream notices remain authoritative. Confirm repository-specific licensing and attribution before publishing this README as the final product page.
