# Aerix Launcher → Windows Desktop: Technical Migration Plan

Status: **Phases 0–8 implemented** in `windows/aerix-desktop/`. This document is the analysis the
port was built on, kept up to date as the work landed. The live feature matrix is
[`FEATURE_CHECKLIST.md`](FEATURE_CHECKLIST.md).

---

## 1. What the repository actually is

`Aerix-launcherPc` is a fork of **PojavLauncher (v3_openjdk)** with a large Aerix UI layer on top.

| Area | Location | Language | Windows relevance |
|---|---|---|---|
| Launcher shell (rail, screens, theme) | `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/**` | Kotlin (Jetpack Compose) | **Design source of truth** — colours, spacing, radii, motion, screen inventory |
| Instances | `.../pojavlaunch/instances/**` | Java | Logic reusable (metadata format), Android UI dropped |
| Version/models | `JVersionList.java`, `value/**`, `tasks/**` | Java | JSON model reimplemented for Windows rules (`MoJsonRule` hard-codes `linux` + Android arch) |
| Downloader | `downloader/**`, `utils/DownloadUtils.java` | Java | Behaviour worth copying (retry, range resume, SHA-1 verify); Android progress plumbing dropped |
| Game launch | `utils/jre/GameRunner.java`, `game/GameActivity.java` | Java | Argument/var map logic reimplemented; Android JVM + LWJGL-Android + renderers dropped |
| Accounts | `authenticator/**` | Java | Microsoft OAuth chain kept in behaviour (live.com code flow + XBL/XSTS/MC services) |
| Content/Discover | `aerix/bridge/PojavAssetBridge.kt` | Kotlin | Modrinth v2 + CurseForge v1 REST shapes reused in a fresh client |
| Rendering stack | `render/**`, bundled `*.aar`, `gl4es`, `angle`, `mojoexec`, `sdl` | C/Java | **Android-only. Not ported** (this is the whole point of a desktop port: Minecraft uses the real desktop LWJGL) |
| Custom controls, gyro, gamepad, Vulkan drivers | `customcontrols/**`, `game/platform/**` | Java/C++ | Android-only; replaced by keyboard/mouse/controller settings where meaningful |
| Socials (Supabase) | `aerix/social/**`, `supabase/` | Kotlin/SQL | Not ported in this pass (server-side product); Community page links out |
| Aerix Client mod | `aerix_client_mod/**` | Fabric mod | Not buildable in this environment (needs Minecraft classes); the Clients page installs published performance mods instead |

### 1.1 Android dependencies that had to be replaced

| Android API | Windows equivalent used here |
|---|---|
| `Activity` / `Fragment` / `FragmentManager` | One `JFrame` plus an in-process router (`Navigator`) with a back stack |
| Compose `@Composable` UI | Java2D/Swing retained-mode components painted by hand (`AerixPanel`, `AerixButton`, …) |
| `SharedPreferences` | JSON settings file in `%APPDATA%\Aerix Launcher\` (atomic write, schema-versioned) |
| `AssetManager` | Plain files on disk in the install directory (`resources/`) |
| `Bitmap` / `BitmapFactory` | `BufferedImage` + ImageIO (PNG/JPG), plus a small WIC-free `.webp` note (assets pre-converted to PNG at build time) |
| Java `java` binary (MultiRT `.tar.xz` runtimes built for Android) | Windows JREs found on disk/JAVA_HOME/registry-free scan + Adoptium/Mojang runtime auto-install |
| Android JVM + `JvmLauncher` | A real `java.exe` child process with a generated argument file |
| `WindowInsets`, notch handling, fullscreen | Normal resizable window, persisted bounds, maximized state, DPI scaling |
| `Toast`, `NotificationManager` | Tray notifications + in-app toasts |
| `WebView` (Microsoft login) | System browser plus paste-code flow (see §6) |
| `ZipUtils` over `AssetManager` streams | `java.util.zip` over files (`ZipFile`, `ZipInputStream`) with zip-slip protection |

**Why Java2D/Swing and not Compose Multiplatform or JavaFX:** the build environment for this port
only has the JDK and the Kotlin compiler — Maven Central / Gradle distribution / JetBrains Space
are unreachable, so Compose (needs the Compose compiler plugin + `compose-desktop` artifacts) and
JavaFX (needs `javafx-*` artifacts) cannot be compiled or run here. Java2D is part of `java.desktop`
in every JDK, gives exact control over the glass/rounded/gradient look of the Android theme, and
runs correctly under a real Windows desktop. The theme values are ported 1:1 from
`aerix/theme/AerixSurface.kt`, so the visual identity is preserved rather than approximated.

---

## 2. Target architecture

```
windows/aerix-desktop/
├── build.gradle.kts            # canonical build (Gradle, JDK 21 toolchain)
├── settings.gradle.kts
├── build.ps1 / build.sh        # toolchain detection, jar, portable dist, jpackage .exe
├── src/main/kotlin/net/kdt/aerix/desktop/
│   ├── Main.kt                 # entry point, single instance, window bootstrap
│   ├── app/                    # AppEnv, paths, settings store, window state, logs
│   ├── core/                   # platform services
│   │   ├── json/               # Json tree + parser/writer (no gson on the classpath)
│   │   ├── net/                # HttpClient wrapper, downloader (retry, resume, SHA-1)
│   │   ├── game/               # manifest → libraries/assets/client, natives, launch args
│   │   ├── loader/             # Fabric, Quilt, Forge, NeoForge, OptiFine installers
│   │   ├── auth/               # Microsoft (live.com code flow + XBL/XSTS), offline, DPAPI store
│   │   ├── content/            # Modrinth v2, CurseForge v1, .mrpack importer
│   │   ├── instance/           # instance model + store (same folder layout as Android)
│   │   └── world/              # level.dat reader, skin model + 3D preview renderer
│   └── ui/
│       ├── theme/              # colours/spacing/radii/fonts/motion from AerixSurface.kt
│       ├── components/         # glass panels, buttons, pills, sliders, switches, dialogs
│       ├── shell/              # AerixFrame, navigation rail, account chip, routed content
│       └── screens/            # Home, Library, CreateInstance, Discover, Settings, …
└── src/test/kotlin/            # unit + integration tests (network-gated where needed)
```

**Runtime model**

* One JVM process for the launcher UI; all I/O on a shared worker pool (`Scheduler`).
* Minecraft runs as a separate `java.exe` process with `-cp <computed classpath> <mainClass>`.
  Its stdout/stderr are streamed into the Console screen and to `logs/games/<instance>-<time>.log`.
* Downloads run through `Downloader`: 3 HTTP workers, SHA-1/SHA-512 verification, ranged resume,
  5 retries with backoff, zip-slip-safe extraction, atomic renames.

**Data layout** (Windows)

```
%APPDATA%\Aerix Launcher\        settings.json, accounts/, skins/, logs/
%USERPROFILE%\Aerix\             game home (versions/, libraries/, assets/, instances/)
  instances\<name>\              instance.json, game dir copy/shared, icon.png
```

The instance metadata file keeps the Android field names (`instanceName`, `versionId`,
`jvmArgs`, `argsMode`, `sharedData`, `modpackSource`, …) so an Aerix Android export can be
imported with `File → Import instance`. Android-only fields (`renderer`, `controlLayout`,
`selectedRuntime`) are ignored/rebound, never deleted from the source folder.

---

## 3. Windows-specific behaviour implemented instead of Android behaviour

| Concern | Android | Windows port |
|---|---|---|
| Renderer choice | LTW / GL4ES / VirGL / Zink icons, GPU detection | Removed; the desktop LWJGL uses the real OpenGL driver. Settings keep only *actual* Windows-relevant knobs (Java args, memory, fullscreen start, window size) |
| Memory | Global + per-instance MB slider, device RAM cap | Same sliders, capped by the physical RAM Windows reports |
| Java runtime | MultiRT `.tar.xz` built for Android ABIs | JRE 8/17/21 auto-download (Adoptium API, SHA-256 verified) or any local JDK/JRE, per-instance and global |
| Controls | On-screen touch controls + control maps | Keyboard/mouse; Minecraft's own options handle it; gamepad is native to MC on Windows |
| Fullscreen / notch | Immersive mode, notch inset | F11 fullscreen launcher overlay, remembered window bounds, DPI-aware, min 1120×720 |
| File picking | SAF pickers | `JFileChooser` (native Windows dialogs) |
| Login | Embedded WebView | System browser + code paste, or device code, plus offline profiles |
| Updates | APK download/install | Launcher updater checks GitHub releases of this repository, opens the release page; no silent self-replace |
| Tray | Foreground service notification | `SystemTray` with Show/Quit, notification on game crash |

---

## 4. Minecraft installation pipeline (rewritten, not translated)

1. `version_manifest_v2.json` → release/snapshot/old-beta lists (cached 30 min).
2. Per-version JSON → merge `inheritsFrom` chain (loader versions sit on top of vanilla).
3. Libraries: rule evaluation **for `os.name = windows`, `os.arch = x86_64`**, natives
   classifiers (`natives-windows`, `natives-windows-x86_64`), Maven coordinate → path, LWJGL
   `-Dorg.lwjgl.librarypath` / `-Djava.library.path` natives dir.
4. Assets: `assetIndex` → hashed object store (`assets/objects/xx/hash`) + `indexes/<id>.json`,
   legacy `virtual/`/`resources/` mapping for pre-1.7 versions.
5. Logging config (client-1.12.xml etc.) from `logging.client`.
6. Fabric/Quilt: loader profile from meta API (`fabricmc.net`/`quiltmc.org`), libraries appended.
7. Forge/NeoForge: installer JAR executed with `--installClient` into a **temporary probe
   directory**, then the produced version JSON/libraries are merged into the instance (never into
   the user's `.minecraft` unless that is the chosen game dir).
8. Java requirement per version: `javaVersion.majorVersion` (8 for ≤1.16.5, 17 for 1.17–1.20.4,
   21 for 1.20.5+/modern). Auto-download the correct JRE.
9. Verification screen: re-hash libraries/assets/client jar, list and repair bad files.
10. Launch: build `arguments.jvm` + `arguments.game` with var substitution
    (`auth_player_name`, `auth_uuid`, `auth_access_token`, `assets_root`, `game_directory`,
    `natives_directory`, `classpath`, `library_directory`, `classpath_separator=";"`, …),
    write `%TEMP%\aerix-launch-<id>.txt` **argument file** (hidden token, cleaned up), run
    `java.exe @argfile`. Offline accounts get Mojang's real offline UUID and a
    token shaped like `0`-replacement the game accepts for 1.20.5+.

---

## 5. Content / Discover

* Modrinth v2: `/search` (facets identical to `PojavAssetBridge.searchModrinth`, including the
  `project_type`, `versions`, `categories` facets and sort indexes), `/project/{id}`,
  `/project/{id}/version`, `/tag/game_version`, `/tag/category`.
* CurseForge v1: `/v1/mods/search`, `/v1/mods/{id}`, `/v1/mods/{id}/files`,
  `/v1/categories`; API key from settings (Aerix `curseForgeApiKey` equivalent).
* Install: mods/resource packs/shader packs go to the instance folder with rollback on failure;
  `.mrpack` and CurseForge `.zip` modpacks are read (`modrinth.index.json` /
  `manifest.json`), loader installed first, then files, then `overrides/` copied.
* Favorites persisted; version-compatibility filter defaults to the selected instance.

---

## 6. Accounts

* **Microsoft (browser code flow)**: opens `login.live.com/oauth20_authorize.srf` with the public
  client id the Android app already uses (`00000000402b5328`, scope
  `service::user.auth.xboxlive.com::MBI_SSL`). The user signs in, then pastes the final URL; the
  code is exchanged at `oauth20_token.srf`, then XBL → XSTS → `login_with_xbox` →
  `/minecraft/profile`, exactly like `MicrosoftBackgroundLogin`. Refresh tokens are stored
  DPAPI-encrypted; access tokens are never logged (the logger redacts `access_token` fields).
* **Microsoft (device code)**: `oauth20_connect.srf` device-code request, polled at
  `oauth20_token.srf`; same chain afterwards. Shows code + opens browser.
* **Offline**: name → UUIDv3 (`OfflinePlayer:`), token `"0"` semantics replaced with the
  Aerix-compatible offline token used by `PojavAccountBridge.effectiveAccessToken`.
* **Skins/capes**: 3D preview rendered locally (skinview3d-equivalent, pure Java2D), skin PNG
  import/export, Mojang skin upload for the selected Microsoft account, local wardrobe presets.

---

## 7. Security

* No plaintext secrets: refresh tokens via DPAPI (values encrypted with the current user's key);
  settings that can contain keys (`curseForgeApiKey`) are DPAPI-wrapped too.
* Path traversal blocked in every archive extractor (`normalize()` + prefix check) and every
  user-supplied name is sanitised against Windows reserved names (`CON`, `PRN`, `AUX`, …).
* All downloads verified by hash when the source provides one; JRE tarballs by SHA-256; Modrinth
  files by SHA-1/SHA-512.
* Launch arguments are passed through an argument file, never through a shell string: no command
  injection surface, and tokens do not appear in the process list.
* Deleting an instance moves it to `instances/.trash` (never an immediate recursive delete) and
  never touches a shared game directory.

---

## 8. Test plan and what was executed in this environment

Unit/integration tests (JUnit 5 when Gradle is used; `selftest` CLI otherwise):

* JSON parser/writer round-trips, escaping, number formats.
* Rule evaluation for Windows x64 (allow/disallow/arch/os precedence).
* Library → Maven path mapping, natives classifier selection.
* Variable substitution, classpath separator `;`, quick-play argument gating.
* Offline UUID determinism, DPAPI wrapper round-trip (Windows only), redaction of tokens in logs.
* Zip extraction traversal rejection; instance name sanitiser.
* Modrinth facet builder matches the Android string exactly.
* `level.dat` parsing on generated NBT fixtures.
* Downloader: resume, hash mismatch retry, concurrent limit (against a local HttpServer).

End-to-end in this sandbox: launcher UI renders (headless paint harness), settings persist,
instances create/duplicate/delete, `.mrpack` import from a local archive, skin preview renders.
**Not executable here:** real Minecraft launch (no GPU/display, no Mojang network) and Windows
installer execution. Those are covered by `scripts/verify_windows.ps1` on a Windows machine and
by the `windows-desktop` GitHub Actions workflow.
