# Aerix Launcher

> **Brand assets still pending:** keep the upstream `README.md` untouched. This Aerix product README uses the exact placeholders below until the approved identity package is available. Do not replace these supplied assets with redraws or generated substitutes.
>
> Required files: `aerix-logo.svg`, `screenshot-1.png` through `screenshot-6.png`, and `assets/team/entitybrian.jpg`.

<!-- MISSING: aerix-logo.svg -->

**Aerix Launcher** is an Android launcher for Minecraft: Java Edition, based on the preserved Pojav/Mojo launch and runtime foundations. Its launcher interface is landscape-first; this does not certify successful Minecraft launches on every phone, tablet, foldable, Chromebook, Android release, graphics driver, or game version.

<!-- MISSING: screenshot-1..6.png -->

## Overview

Aerix keeps the existing Minecraft Java launch pipeline and account, profile, renderer, runtime, controls, file, and log foundations while adding a liquid-glass launcher interface: a slim navigation rail, translucent pale-edged panels, cyan/teal active states, and wallpaper-backed screens. The instance library adds favorites, groups, pinning, sorting, profile editing, and launch actions. A local server-list manager edits the selected profile's standard `servers.dat` file. Discover currently uses public Modrinth API browsing; CurseForge API access remains disabled in public builds until a secure credential/proxy arrangement exists.

The wallpaper gallery provides **25 selectable landscape presets** (20 generated scenes and five color-tuned variants), plus custom image selection. Choosing a wallpaper updates the full-screen launcher background and samples a saturated color to recolor launcher accents. Preset, custom hex, per-section, and Android 12+ Material You color modes are also available. Skin Studio includes bounded public Mojang username lookup/preview and a separate, confirmed upload flow for the signed-in Microsoft account; public search results cannot be uploaded directly. These UI changes compile in GitHub Actions and the current code-validation run passes unit tests, wallpaper verification, full/no-runtime Debug APK builds, and the four-ABI native-library check. Runtime behavior and visual parity remain unverified on a device; screenshots and logo parity are not certified until the supplied assets and rendered screens are reviewed.

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

The latest code-validation run is [GitHub Actions run 38018016829](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38018016829), for code commit `2e7b3ac`. Launcher unit tests, the wallpaper verifier, full/no-runtime Debug APK builds, and four-ABI native-library verification passed. Download the [full Debug APK artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38018016829/artifacts/11657700995) or the [no-runtime Debug APK artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38018016829/artifacts/11657246349); each includes its APK and checksum file. APK ABI packaging is not an Android device or Minecraft launch test. CI does not produce a release AAB for this fork/branch.

To build locally, use the checked-in Gradle wrapper and follow the repository's build setup. The Android SDK, NDK, JDK, required submodules, native dependencies, and optional renderer artifacts must be installed/configured as described by the source project. A successful build is not a device-compatibility test.

## Accounts, security, and third-party services

Aerix must not embed shared CurseForge API secrets, OAuth credentials, or user tokens in source or distributed APKs. Authentication tokens stay in the launcher's account flow and must never be written to logs or copied into documentation. Public API requests should use transport security, bounded responses/downloads, and verified checksums where available.

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
