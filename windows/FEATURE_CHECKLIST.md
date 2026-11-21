# Windows desktop: feature checklist

What the Android app has, and where it stands in `aerix-desktop`. Legend: **done** — implemented
and reachable in the UI; **partial** — implemented with a documented gap; **n/a** — Android-only,
deliberately not ported.

The page inventory, colours, spacing and icon set all come from
`app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/**`, so the desktop build is the same
product rather than a lookalike.

## Navigation

| Android screen | Desktop | State |
|---|---|---|
| Aerix home (hero, profile card, instances) | `ui/screens/HomeScreen.kt` | done |
| Create instance | `ui/screens/CreateInstanceScreen.kt` | done |
| Instance management / library | `ui/screens/LibraryScreen.kt`, `InstanceMenu.kt` | done |
| Discover (Modrinth / CurseForge) | `ui/screens/DiscoverScreen.kt` | done |
| Modpacks (import/export .mrpack) | `ui/screens/ModpacksScreen.kt` | done |
| Servers (LabyMod catalogue) | `ui/screens/ServersScreen.kt` | done |
| Other releases | `ui/screens/ReleasesScreen.kt` | done |
| Settings (behaviour, Java, appearance, wallpapers, launcher, about) | `ui/screens/SettingsScreen.kt` | done |
| Accounts + skin wardrobe | `ui/screens/AccountsScreen.kt` | done |
| Contribution (team) | `ui/screens/ContributionScreen.kt` | done |
| About | `ui/screens/AboutScreen.kt` | done |
| Console / logs | `ui/screens/ConsoleScreen.kt` | done |
| File manager | `ui/screens/FilesScreen.kt` | done |
| Community / socials (Supabase) | — | n/a this pass; About links out |
| Renderer picker (LTW/GL4ES/Zink/VirGL) | — | n/a (real desktop OpenGL) |
| Custom controls, gyro, gamepad maps | — | n/a (keyboard/mouse) |
| Gamepad / touch layout editor | — | n/a |

## Instances

| Feature | State |
|---|---|
| Create with name, version, loader, loader build, icon | done |
| Duplicate, rename, favourite, change icon | done |
| Per-instance memory, Java path, JVM args, game args | done |
| Shared install root (download Minecraft once, link per instance) | done |
| Trash + restore + empty trash (never deletes outright) | done |
| `instance.json` keeps the Android field names so exports import | done |
| Import an instance folder / modpack from disk | done (`DropImport`, Modpacks) |

## Minecraft installation

| Step | State |
|---|---|
| Version manifest (cached, ETag-aware) | done |
| Rule evaluation for `windows`, arch, features | done |
| Client jar, libraries, natives, assets with SHA-1 verify + resume | done |
| Argument template assembly (`${...}` substitution, logging config) | done |
| Java runtime discovery (JAVA_HOME, PATH, launcher's own JVM, Program Files, scoop, chocolatey) | done |
| Java runtime auto-download (Adoptium) | done |
| Launch as a child `java.exe` with output in Console | done |
| Stop game / stop all, "close launcher with the game" | done |

## Loaders

| Loader | Listing | Install |
|---|---|---|
| Vanilla | Mojang manifest | done |
| Fabric | meta.fabricmc.net | done (profile JSON) |
| Quilt | meta.quiltmc.org | done (profile JSON) |
| Forge | maven-metadata.xml + promotions | done (official installer) |
| NeoForge | maven-metadata.xml | done (official installer) |
| OptiFine | optifine.net/downloads scrape, adloadx mirror resolved | done (official installer) |
| Better than Adventure | release + nightly manifests | done (client jar + generated profile) |

OptiFine previews are listed as unstable and hidden by the "only stable builds" switch. BTA always
bases itself on `b1.7.3`, so the version column follows the loader instead of the picked release.

## Accounts

| Feature | State |
|---|---|
| Microsoft browser code flow | done |
| Microsoft device code flow | done |
| Offline profiles | done |
| Account switching, removal | done |
| Token refresh | done |
| Sealed token storage (DPAPI on Windows, AES-GCM elsewhere) | done; self test asserts no plaintext token is written |
| Skin import (64×64 and 64×32), classic/slim model | done |
| 3-D character preview, drag to rotate | done |
| Ely.by accounts | partial — the model normalises them, sign-in is Microsoft/offline only |

## Content

| Feature | State |
|---|---|
| Modrinth search (mods, modpacks, resource packs, shaders, worlds, datapacks) | done |
| CurseForge search | done with an API key; the UI says so when the key is missing |
| Install into the right folder of the right instance | done |
| `.mrpack` import (overrides, dependencies) | done |
| CurseForge pack import (manifest + project ids) | done |
| Export an instance as `.mrpack` | done |
| Modrinth required dependencies | done — resolved to their own matching version and installed, up to 12 per install |

## Desktop-only additions

| Feature | State |
|---|---|
| Drag and drop import (modpack, mod, resource pack, shader, skin, world) with content sniffing | done |
| Tray icon, hide-to-tray, stop-game menu | done |
| Keyboard shortcuts (`Ctrl+1…9`, `F5`, `Alt+Left`, `Ctrl+L`, `Ctrl+F`, `Esc`) | done |
| DPI scaling and persisted window bounds | done |
| `--selftest` (10 checks + offscreen render of every page) | done |
| `--screenshot` (13 PNGs, no display needed) | done |
| Background update check (GitHub releases, once a day) | done |
| `--safe-mode` (no wallpaper, no animation) | done |
| Modpack/mod import from the command line (`aerix-launcher file.mrpack`) | done |

## Not ported on purpose

* Everything under `render/`, `customcontrols/`, `game/platform/`: Android's GL stack, touch
  controls and driver shims. Windows gets the real OpenGL driver through LWJGL.
* `aerix/social/**` and the Supabase schema: a server-side product, not a launcher feature.
* `aerix_client_mod/`: it needs Minecraft classes to build; the Clients flow installs published
  performance mods instead.
