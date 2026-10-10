# Aerix Launcher parity checklist

Status reflects the current source/worktree, not a promise of physical-device behavior. **Done** means the source path exists and is documented; **In Progress** means a partial implementation exists or build/device verification remains; **Blocked** means the item cannot be finished without missing assets or external evidence. An untested device matrix remains **Not Tested** even when source status is Done.

| Requested item | Status | Evidence / remaining work |
|---|---|---|
| Preserve the Minecraft Java launch, version metadata, runtime, native-library, and classpath foundations | Done | Existing Pojav/Mojo launch path remains; renderer/runtime changes are additions around it. |
| Aerix identity, Android `versionName` 1.0.0, and valid version code | Done | Build configuration retains the existing package/storage strategy. Check each future release variant. |
| Apply the uploaded Aerix logo | Blocked | No uploaded logo is present in this checkout/searchable workspace; only the upstream `pojavlauncher.png` was found. Do not redraw or substitute it. |
| Match the approved liquid-glass mockups across screens | In Progress | Landscape wallpaper-backed shell, glass rail/panels, cyan active states, settings tabs, and Home/Create/Library/Discover/Skins/Servers/Appearance styling are implemented. Current source passes CI and static checks; no rendered-device screenshots are available for visual comparison, so parity is not claimed. |
| Exact six reference screenshots | Blocked | Screenshot files are absent from the workspace. Keep `<!-- MISSING: screenshot-1..6.png -->` until supplied. |
| Landscape-first navigation and Home/Create/Library/Discover/Skins/Servers/Settings routes | In Progress | Rail routes, account entry, and hidden authentication spinner remain; `skins_nav_button` is present in both portrait and landscape layout resources. Route behavior, orientation, touch, and window-inset handling remain device-unverified. |
| Fullscreen/immersive launcher window | In Progress | `LauncherActivity` opts into the existing immersive system-bar mode (transient bars can still be revealed by gesture; multi-window is exempt). System-bar, cutout, and gesture behavior has not been device-tested. |
| Home design with hero, account management, selected profile, RAM summary, quick actions, and Play | In Progress | Home spacing is tightened, the crowded quick-action cluster is a Quick Tools menu, account management is routed to a separate manager, and the selected account skin face/profile/RAM summaries remain. XML/static checks pass; no rendered-device test. |
| Create vanilla/modded/modpack instances with upstream installation flows | In Progress | Upstream install routes are retained; a landscape-specific two-column glass-card layout replaces the tall compressed list. XML/static checks pass; loader interaction and rendering are untested. |
| Instance library: select, edit, delete, launch, favorites/groups/pins/sort/recent launches | In Progress | Profile storage stays authoritative and metadata is persisted in preferences; cards now wrap their content rather than using a fixed 380dp height. Reflow/scroll behavior remains device-unverified. |
| Instance clone and backup/export | Done | Clone and ZIP backup actions exist; confirm shared-data semantics and verify error/recovery cases. |
| Per-instance JVM-argument merge/replace modes | In Progress | Instance model stores `jvmArgs`/`argsMode`; complete editor wiring and launch verification remain. |
| Per-instance RAM override | In Progress | Global RAM allocation is shown on Home; profile-specific override is not wired through memory warnings and Java heap arguments. |
| Discover categories, filters, provider selection, details, pagination, and installs | In Progress | Modrinth and optional CurseForge sources are selectable; filters cover modpacks, mods, resource packs, shaders, and world saves. Current source builds in CI; single-project installs and safe world archive extraction have source-level guards but still need live-API and device tests. |
| Modrinth installation, dependency resolution, conflicts, rollback, and path safety | In Progress | Required dependency resolution, checksum/path checks, and direct content installs exist in source; conflict/recovery cases and full installer regression coverage need explicit tests. |
| CurseForge integration without embedded credentials | In Progress | Public builds no longer inject a shared key. Users can enter/remove a personal key in private app settings or Discover; the key is not logged or shown in summaries. CurseForge API responses and downloads are not live-tested. |
| Account login, account management, and account/skin face display | In Progress | The new account manager lists cached skin faces, selects/removes saved accounts, and routes Add account through the existing Microsoft/Ely.by/local flows; the hidden spinner still handles upstream login callbacks. Build passes CI; authentication and deletion flows still need interactive UI/device testing. |
| Skin username lookup and preview | In Progress | Source queries Mojang public profile/session endpoints over HTTPS with username validation, bounded responses, no bearer token, and a texture-host allowlist. CI build passes; live-network behavior remains unverified. |
| Skin upload to the signed-in Microsoft account | In Progress | User-selected 64×64 PNG uploads only after confirmation; remote search results are preview-only. Minecraft Services behavior is not live-tested. |
| Renderer settings and capability-driven GL4ES/LTW/ANGLE/Vulkan policy | In Progress | Smart Pick is restricted to GL4ES/LTW capability/game/library rules; ANGLE is manual opt-in and Vulkan/Zink manual-select only. Device behavior remains Not Tested. |
| Renderer policy avoids device-brand/model workarounds | Done | Policy uses ABI, game requirements, GLES/GPU capability, and installed renderer artifacts. |
| EGL/OpenGL fallback behavior | Done | Source has EGL entry-point checks and GL4ES fallback; physical renderer behavior remains Not Tested. |
| Controls, gamepad, touch, editor, and preserved launch controls | Done | Existing paths remain. Orientation, input, inset, and device-specific behavior remains Not Tested. |
| 25 bundled wallpaper choices | In Progress | Catalog and image files `01`–`25` pass the local and current CI verifiers. `01`–`20` are generated scenes; `21`–`25` are color-tuned variants derived from those assets. Gallery selection and rendering remain runtime/device-unverified. |
| Wallpaper changes full-screen image and wallpaper-derived launcher colors | In Progress | Bundled/custom image decoding, persistence, sampled accent, theme modes, and full-screen backdrop integration pass the current CI build; selection, persistence, and visual accent changes remain runtime/device-unverified. |
| Fifteen presets, custom hex, Material You, and per-section accents | In Progress | Theme manager and controls exist in source; Android API 31+ Material You reads system wallpaper colors. UI behavior and persistence remain unverified. |
| Multiplayer server list manager | Done | Add/edit/copy/delete reads and writes selected profile `servers.dat`; this is a local list editor, not a direct-connect client. |
| Server order/import/export/status/favorites/direct connect | In Progress | These additional actions are not implemented. Direct connection is not claimed. |
| Secure updater | In Progress | Release metadata check opens the GitHub release page; no in-app APK download/hash/signature/install/rollback flow is claimed. |
| README and product assets | In Progress | `README.aerix.md` now documents the current feature status and retains exact missing-asset markers. Upstream `README.md` is unchanged. Team portrait `assets/team/entitybrian.jpg` is absent. |
| Compatibility matrix | Done | `docs/COMPATIBILITY_MATRIX.md` enumerates API 23–36, four ABIs, renderer routes, Java-runtime × Minecraft 26.x × ABI rows, and form factors; unknown cells are Not Tested. |
| CI build/tests/APK artifacts for current code | Done | Run [38026169765](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38026169765) passed for commit `b279c09`: launcher unit tests, wallpaper/layout verification, full/no-runtime Debug APK builds, and four-ABI native-library verification. [Full Debug artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38026169765/artifacts/11659459181); [no-runtime Debug artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38026169765/artifacts/11659064502). No release AAB is produced. Local build remains unavailable here (no Java or Android SDK); CI does not establish device behavior. |
| Release AAB | Not produced | CI produces Debug APKs, not a release AAB. |
| Physical-device/emulator coverage | Blocked | No emulator or physical device is available in this environment. Keep all applicable matrix cells Not Tested. |
| Optional Mesa artifact lookup | Done | **Non-blocking — Not Found. Do not retry.** |

## Asset handoff markers

- Logo: `<!-- MISSING: aerix-logo.svg -->`
- Reference screenshots: `<!-- MISSING: screenshot-1..6.png -->`
- Team image: `<!-- MISSING: assets/team/entitybrian.jpg -->`

These are explicit blockers, not permission to fabricate substitutes. Keep upstream `README.md` unchanged until the intended assets can be reviewed together.
