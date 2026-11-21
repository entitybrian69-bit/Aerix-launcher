# Aerix Launcher for Windows

The desktop port of Aerix Launcher: the Minecraft: Java Edition launcher, its glass UI and its
instance model, rebuilt for Windows instead of Android. It shares the design source of truth with
the Android app (`app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/**`) but none of its
Android-only machinery: no GL4ES, no angle, no touch controls, no Android JVM — just the real
desktop LWJGL, a real `java.exe` child process and a Swing/Java2D window.

Analysis behind the port: [`../DESKTOP_MIGRATION_PLAN.md`](../DESKTOP_MIGRATION_PLAN.md).
Feature matrix: [`../FEATURE_CHECKLIST.md`](../FEATURE_CHECKLIST.md).

---

## Build

There is no Gradle. The build is one shell script that needs a JDK 17+ and the Kotlin compiler and
downloads nothing else.

```bash
export JAVA_HOME=/path/to/jdk        # 17 or newer
export KOTLINC=/path/to/kotlinc      # Kotlin 2.x
cd windows/aerix-desktop

./build.sh                # -> build/aerix-launcher.jar (~13 MB, stdlib only)
./build.sh --test         # unit tests
./build.sh --selftest     # headless data + UI self test (exit code 1 on failure)
./build.sh --screenshot   # render all 13 pages to build/screenshots
./build.sh --dist         # portable app image in dist/AerixLauncher/
./build.sh --installer    # + .msi/.exe through jpackage (needs WiX on Windows)
```

`build.sh` also finds a toolchain by itself if `JAVA_HOME`/`KOTLINC` are unset, looking in
`toolchain/jdk` and `toolchain/kc` (the layout the dev container uses) and on `PATH`.

## Run

```bash
java -jar build/aerix-launcher.jar
```

| Option | Effect |
|---|---|
| `--help`, `-h` | usage |
| `--version` | print the version and exit |
| `--selftest` | run the headless self test, exit non-zero on failure |
| `--screenshot` | render every page to `build/screenshots` (no display required) |
| `--list-pages` | print page ids, titles and icon names |
| `--data <dir>` | use `<dir>` instead of `%APPDATA%\Aerix Launcher` |
| `--game-dir <dir>` | put instances and game files in `<dir>` |
| `--safe-mode` | start without the wallpaper or animations |
| `[file]` | open a `.mrpack`, `.zip`, mod, resource pack or skin and import it |

## What is implemented

* **Instances** — create, duplicate, favourite, trash/restore, per-instance memory/Java/args, a
  shared install root so Minecraft versions are downloaded once and linked into each instance.
* **Versions** — Mojang manifest, rule evaluation for Windows, library + asset downloads with
  SHA-1 verification and ranged resume, natives extraction, argument building, logging.
* **Loaders** — Fabric, Quilt, Forge, NeoForge, OptiFine and Better than Adventure.
  Fabric/Quilt install from their profile JSON; Forge/NeoForge/OptiFine run the official installer;
  BTA fetches its client jar and writes its own profile. All of them end as a normal
  `versions/<id>/<id>.json`, so nothing downstream needs to know which one produced it.
* **Accounts** — Microsoft sign-in (browser code flow and device code flow), offline profiles,
  Minecraft skin import and a draggable 3-D preview. Tokens are sealed with DPAPI on Windows and
  AES-GCM elsewhere; nothing writes a token in plain text (the self test asserts this).
* **Content** — Modrinth and CurseForge search, `.mrpack` / CurseForge pack import, mod, resource
  pack, shader and world installation into the right folder.
* **Servers** — the 240-server LabyMod catalogue the Android build ships, with banners, search and
  join-with-instance; plus servers you add yourself.
* **Every page from the Android app** — Home, Create, Library, Discover, Modpacks, Servers,
  Releases, Settings (six tabs), Accounts, Contribution, About, Console, Files.
* **Desktop behaviour** — drag and drop import, tray icon with hide-to-tray, "close with the game"
  handling, keyboard shortcuts (`Ctrl+1…9` pages, `F5` refresh, `Alt+Left` back, `Ctrl+L` files,
  `Ctrl+F` search), DPI scaling, persisted window bounds, a single background update check a day.

## Self test and screenshots

`--selftest` is the guard rail, in fourteen checks. The first ones cover the data layer: settings
round-trip, sealed token storage (and that no token is ever written in plain text), instance CRUD
with trash and restore, bundled assets, version comparison, the OptiFine and BTA metadata parsers,
Java discovery, and the data folders.

The rest cover the parts that only fail at runtime, on a player's machine:

| check | what it protects |
| --- | --- |
| launch argument file | writes an `@argfile`, then starts a real JVM from a directory whose name contains a space and runs this very jar through it. `java` only expands argument files in the options position, and its quoting rules are not a shell's; getting either wrong means every launch starts with no memory and no class path. |
| version metadata | resolves fixtures for the three library shapes that appear from 1.5 to 1.21 (raw `url`+`name`, classic `downloads`+`natives` map, modern `natives-<os>-<arch>` entries), a loader profile with `inheritsFrom`, an OS rule that must be excluded, and the class path that pre-1.13 metadata never declares. |
| resumed downloads | an embedded socket server honours, ignores and shifts `Range`; every combination must produce the exact bytes and count no more than the file's size. |
| text clipping | walks all thirteen rendered pages and fails if any component is narrower than its contents, or if a page wants more width than the window. |
| every page renders | paints all thirteen pages offscreen and writes them to `build/screenshots`. |

It exits non-zero if anything fails, so CI can gate on it.

`--screenshot` uses the same renderer and writes one PNG per page. Both run with
`-Djava.awt.headless=true`; the pages are laid out and painted without ever creating a window,
which is how the design was reviewed while this was written.

## Layout

```
src/main/kotlin/net/kdt/aerix/desktop/
├── Main.kt                  entry point, CLI modes, tray, shutdown
├── app/                     AppEnv (paths), Settings, Scheduler, Log, Updates
├── core/json/               JSON tree, parser, writer (no external dependencies)
├── core/net/                HttpClient wrapper, batched downloader (retry, resume, SHA)
├── core/game/               Mojang manifest, installer, Java runtimes, launch
├── core/loaders/            Fabric/Quilt/Forge/NeoForge/OptiFine/BTA
├── core/auth/               Microsoft OAuth chain, secure token store
├── core/content/            Modrinth, CurseForge, .mrpack import
├── core/instance/           instance model and store
├── ui/theme/                colours, fonts, glass painting, wallpapers
├── ui/components/           panels, buttons, fields, pills, empty states, skin renderer
├── ui/icons/                Android vector drawable → Java2D path renderer
├── ui/shell/                window, navigation rail, self test, screenshot harness
└── ui/screens/              the thirteen pages
```

## Licence

GPL-3.0, like the PojavLauncher fork it comes from. Bundled server artwork belongs to the servers;
see `src/main/resources/aerix/brand/server_artwork_NOTICE.txt`.
