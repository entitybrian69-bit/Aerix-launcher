# Millida-style shell

> **"Make the ui like the millida launcher one"** — this is the current visual direction for
> Aerix HOME. It replaces the bright Blocky Shell as the default look while keeping its
> underlying engine (living 3D character, autonomous/reactive emotes, square-wave sound,
> stepped motion, real launcher actions).

## Reference

Visual references are the official Millida screenshots, saved under `.design_ref/millida/`
(untracked reference assets, not shipped):

| Source | What it establishes |
| --- | --- |
| `millida/launcher-mobile` `screenshots/lobby.png` | Android lobby: near-black chrome, account chip + two square buttons, green grass character stage, blue "СЕГОДНЯ ИГРАЕМ" instance card, big green "ИГРАТЬ" button, small "Сборка" button, "Мои сборки" tiles, five-tab bottom bar |
| `millida/launcher-mobile` `screenshots/builds.png` | Coloured promo cards with sunburst rays, `+` build tile, orange build tiles, "Куда пойти играть" server cards, "Все >" section links |
| `millida/launcher-mobile` `screenshots/character.png` | Dark full-height character stage, `Стоит / Идёт / Машет` pose chips, tab chips with the active one green |
| `millida/launcher-mobile` `screenshots/catalog.png` | Green active tab, dark notched search/filter chips, white bold headers, green `+ В сборку` buttons |
| `millida/launcher-mobile` `screenshots/more.png` | 2×2 coloured destination cards (blue / orange / green / pink) plus a 2-column dark entry grid |
| `millida/launcher` `screenshots/lobby.png` | Desktop 2.0: green sunburst scene, left promo column, centred 3D character, right Discord card, bottom blue instance + green Play dock |

Official page: <https://millida.net/en/launcher> (2.0 lobby screenshots `lobby-purple-en.webp`
and `lobby-orange-en.webp`; alt text: "Millida Launcher 2.0 lobby: a character with wings and a
halo, a custom instance and the 'Play' button").

Aerix is an **Android** launcher, so the mobile lobby is the primary reference and the desktop
lobby informs the wide/landscape layout. No Millida code, assets, services, or branding are
copied into Aerix — only the layout language described below.

## Visual language

1. **Near-black chrome.** `#141416` background, `#101012` top/bottom bars, `#1E1E21` panels,
   `#29292D` chips. No wallpaper, no glass, no blur, no gradients on chrome.
2. **Notched pixel panels.** Every panel is a rectangle with all four corners cut away by one
   square step, a near-black `#08080A` outline and a hard `#070708` shadow offset down-right.
   Corners are square; nothing is rounded. See `notchedPath` in `MillidaComponents.kt`.
3. **One bright accent.** Lime `#5CD65C` for primary Play, the active bottom tab, active chips,
   and the `+` tile. Dark text on green.
4. **Blue "START HERE" card.** `#1D4EA8` with a chest icon, yellow eyebrow, white instance name
   and version. Tapping it opens a real instance picker.
5. **Coloured destination cards.** Each destination gets its own flat colour (blue, orange,
   pink, purple, teal, red, yellow), white pixel labels, and a sunburst ray field. This keeps the
   earlier "all options will have different different colors and themes" requirement.
6. **Grass character stage.** Flat dark-green scene with radial rays, square sparkles and a
   stepped grass ledge at the bottom. The real, rotatable `AerixSkinPreview3D` stands on it,
   wearing the equipped skin and client cape.
7. **Five-tab bottom bar.** Play / Catalog / Character / Shop / More, each with a coloured pixel
   icon. The active tab is a raised green notched block.
8. **Pixel type.** `minecraft_ten.ttf`, bold white headings, small grey captions.
9. **Hard motion.** Six-step `MillidaMotion.StepEasing`, zero-duration section cuts, press snaps
   the shadow down, and square-wave `BlockySound` on every tap.

## Layout

**Portrait (compact):** top account bar → 300dp character stage → blue START HERE card →
big green PLAY + small BUILD button → MY BUILDS (`+` tile + up to two real instances, with
"ALL >") → three coloured promo cards → WHERE TO PLAY server/community cards → bottom bar.

**Landscape (wide):** left promo column, centre character stage over a two-part dock
(blue instance + green Play), right MY BUILDS column, bottom bar. Mirrors the desktop 2.0 lobby.

**More sheet:** Millida's "Ещё" screen as a notched overlay — account card, four coloured
destination cards, a 2-column dark entry grid, help/sound/classic/close actions.

## Real functionality (no mock destinations)

| UI | Real action |
| --- | --- |
| PLAY | `PojavLaunchBridge.play(activity)` or Discover while installing |
| START HERE | `PojavInstanceBridge.select(instance)` / Library |
| NEW BUILD + build tiles | Library / real instance selection |
| Catalog tab / promo cards | `LauncherSection.DISCOVER`, `CONTENT`, `ACCOUNTS`, `SOCIALS`, `SERVERS` |
| Character tab | Account detail + skin wardrobe |
| Companion mod card | `LauncherSection.AERIX_CLIENT` |
| Friends / Find a party / Invite | Socials + `SocialBackend.inviteCode()` clipboard |
| Daily chest | `AerixThemeManager.wallpapers` unlock |
| Pose chips (STAND / WAVE / DANCE) | the same `BlockyEmote` engine driving `skinview.js` |
| Classic dashboard | turns off both shell preferences |

The 3D character still emotes on its own every eight seconds and reacts to install progress,
wardrobe changes and account switches. `playEmote('idle')` in `skinview.js` now returns straight
to the living idle pose so the STAND chip works.

## Appearance hierarchy

`AerixPreferences.millidaUi` (default `true`) → Millida shell.
Turn it off → `AerixPreferences.blockyUi` (default `true`) → the previous Blocky Shell
(`docs/blocky-shell.md`, `tests/blocky_shell_smoke.py`).
Turn both off → the classic glass dashboard and navigation rail.

`AerixBackground(skipWallpaper = ...)` is forced on for the Millida shell so the near-black
chrome stays flat. The first-run guide still temporarily restores the classic dashboard so its
anchors resolve.

## Files

- `aerix/ui/millida/MillidaTheme.kt` — palette, font, stepped easing, coloured tones
- `aerix/ui/millida/MillidaComponents.kt` — notched panels, buttons, chips, tabs, cards, scene
- `aerix/ui/screens/MillidaShellScreen.kt` — HOME shell, layouts, sheets, emote engine
- `tools/generate_millida_art.py` — 19 coloured 16×16 pixel icons → `drawable-nodpi`
- `preview/millida.html`, `preview/millida-preview.png` — live WebGL/chiptune preview
- `tools/serve_millida_preview.py` — repo-root server for the preview
- `tests/millida_shell_smoke.py` — machine-checkable half of this document
- `.github/workflows/aerix-debug.yml` — runs the smoke test

## Verification

```
PYTHONPATH=/tmp/pillowlib python3 tools/generate_millida_art.py
python3 tests/blocky_shell_smoke.py
python3 tests/millida_shell_smoke.py
python3 -m py_compile tools/render_millida_preview.py tools/serve_millida_preview.py
node --check app_pojavlauncher/src/main/assets/skinview/skinview.js
```
