# Aerix — Liquid Crystal

A complete re-skin of the launcher shell: ten tab screens drawn in a **liquid
crystal** language — deep abyss backdrop, aurora blooms under frosted glass,
chromatic refraction rims, and one floating crystal dock for navigation.

Nothing from the old shell survives:

| old shell | Liquid Crystal |
|---|---|
| vertical stack of `LauncherMenuButton`s | quick-action crystal tiles on the launch tab |
| `MineButton` play bar pinned to the bottom | orbital launch crystal (tap / hold) |
| version spinner + edit pencil | hero instance card + full editor sheet |
| right side `ListView` drawer | floating dock, 10 tabs, always reachable |
| `PreferenceFragmentCompat` lists | glass rows, tiles, sliders and segmented pickers |

## The ten tabs

| # | tab | what lives there |
|---|---|---|
| 01 | **LAUNCH** | instance hero, launch crystal, quick actions (custom controls, execute .jar, share logs, game directory, wiki, community), active tasks, accounts (Microsoft / Ely.by / offline) |
| 02 | **INSTANCE** | search, release / snapshot / old-alpha / old-beta filters, instance list (clone / edit / remove), instance editor (icon, name, version, control scheme, shared data, JVM args, runtime, renderer, save, delete) |
| 03 | **MODS** | new-instance types (vanilla, OptiFine, Fabric, Quilt, Legacy Fabric, Forge, NeoForge, modpack, BTA), loader installer (game version, loader version, stable only, install), mod search + filters, import local modpack |
| 04 | **CONTROL** | control studio stage (joystick, buttons, hotbar, virtual mouse, snap grid), editor toolbar (add button / drawer / joystick / sub-button, load, save, default), button inspector (mapping, size, background, stroke, toggleable, pass-thru, swipeable, forward lock, absolute tracking, corner radius, stroke width, opacity) |
| 05 | **SETTINGS** | the five category cards, force English, notification permission, about, licenses, wiki, community |
| 06 | **VIDEO** | all 10 renderers, renderer settings, resolution scaler, ignore notch, sustained performance, alternate surface, force VSync, ANGLE, system ANGLE, Zink system driver |
| 07 | **INPUT** | gestures, long-press delay, button scaling / transparency / caps, virtual mouse scale + speed + start-on, gyroscope (enable, smoothing, invert X/Y, sensitivity, sample rate), controller (remap, wipe, deadzone), keyboard auto-panning |
| 08 | **JAVA** | runtime list (8 / 11 / 17 / 21) + install, memory allocation, JVM launch arguments, sandbox .jar execution, runtime info |
| 09 | **MISC** | verify game files, fast startup check, verify manifest, download source, microphone access, data migration, clear metadata cache, memory warning, game directory, share logs, execute .jar, storage |
| 10 | **LABS** | dump shaders, big-core affinity, freedreno sysmem, OpenSL in OpenAL Soft, UBWC workaround, live log view, share logs, verbose JNI logging, reset all flags |

Every preference key of `pref_main.xml`, `pref_video.xml`, `pref_control.xml`,
`pref_java.xml`, `pref_misc.xml` and `pref_experimental.xml` is present, as is
every action of `MainMenuFragment`, `InstanceEditorFragment`,
`ProfileTypeSelectFragment`, the mod-loader install fragments, `SearchModFragment`
and `CustomControlsActivity`.

## Files

```
design/liquid-crystal/
  kit.py               design system (glass material, controls, typography)
  screens.py           the ten screen layouts
  build.py             renders the mockups
  verify.py            pixel statistics per screen
  verify2.py           probes for the bespoke widgets
  verify3.py           layout validation (text on glass, nothing colliding)
  tools/
    build_icons.py     builds the Lucide sprite used by the prototype
    contact_sheet.py   stitches the mockups into one overview image
  prototype/           interactive HTML/CSS prototype (real backdrop-filter)
```

## Regenerating the mockups

The renderer needs Inter and the Lucide icon font. Both are built once from
packages that need no GitHub access beyond npm:

```sh
python3 -m venv ~/.venv && ~/.venv/bin/pip install Pillow numpy fonttools brotli
mkdir -p ~/.fonts_build
cd /tmp && npm pack @fontsource-variable/inter && tar xzf fontsource-variable-inter-*.tgz
#      extract files/inter-latin-wght-normal.woff2, instantiate it at
#      wght 400/500/600/700/800 with fontTools, save as TTF into ~/.fonts_build
cd /tmp && npm pack lucide-static && tar xzf lucide-static-*.tgz
cp lucide/package/font/lucide.ttf lucide/package/font/codepoints.json ~/.fonts_build

~/.venv/bin/python design/liquid-crystal/build.py /home/user/mockups
~/.venv/bin/python design/liquid-crystal/tools/contact_sheet.py
```

`AERIX_FONT_DIR` overrides the font location; without the fonts the kit falls
back to DejaVu Sans (icons will be missing).

## Running the interactive prototype

```sh
python3 -m http.server 8080 --bind 0.0.0.0 \
  --directory design/liquid-crystal/prototype
```

The prototype uses real CSS `backdrop-filter` glass, the animated aurora, live
toggles and the same ten tabs, which makes it the quickest way to click through
the whole flow before any of it is written in XML.
