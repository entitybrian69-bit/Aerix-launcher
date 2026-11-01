# Importing controls from other launchers

Settings → Controls → Import accepts control JSON files, plus Pojav's `layout.json` bitmap ZIP containers. Choose a file, name the imported copy, then select it as the global or per-instance layout. Importing does not edit the selected original file.

| Format family | Compatibility |
| --- | --- |
| Pojav/legacy Pojav, Mojo, Aerix, and forks using the same native JSON schema | Existing version 1–9 migrations; omitted legacy drawer/joystick arrays are normalized. |
| Zalith Launcher 2 `editorVersion` / `layers` schema | Keyboard/mouse/launcher buttons, toggle/swipe/passthrough, basic style/ARGB colors, percentage edge positions, joystick directions and lock keys. |
| Fold Craft Launcher `viewGroups` schema | Existing button/joystick conversion, including LWJGL2-to-Android key translation and custom joystick direction bindings. |

Imported joysticks can bind all eight directions independently. Existing native joysticks with no custom maps retain WASD/Ctrl and the legacy dead zone. Custom joystick events must be keyboard events: launcher actions on a joystick direction are reported as unsupported, not dispatched as keyboard codes.

## Limits

This is **not universal or lossless compatibility with every launcher/version**. Foreign layers are flattened; text widgets, text/chat macros, layer-switching actions, source-specific images, exact joystick styling, source-only gestures and some visual effects cannot be reproduced by Aerix's control engine. Import shows a confirmation explaining the limits and counts skipped widgets, inert buttons, and unsupported/excess events. Each button holds at most four key bindings.

Unknown formats and unusable foreign layouts are rejected rather than silently imported. Do not rename arbitrary ZIP files and expect them to become controls; containers require Pojav's `layout.json` structure. For a format not covered here, obtain an example exported layout and its schema before adding another adapter.

## Schema references and tests

Zalith Launcher 2's `LayerController` model was inspected for `ControlLayout`, `ControlLayer`, `NormalData`, `JoystickData`, `ButtonSize`, `ButtonPosition`, `ClickEvent`, and the color serializer:
https://github.com/ZalithLauncher/ZalithLauncher2/tree/main/LayerController/src/main/java/com/movtery/layer_controller

Robolectric regression tests exercise conversion with Android key codes and display metrics, including joystick-only layouts, toggle buttons, legacy migration, adaptive positioning, old/new ARGB encoding, unsupported events, and custom FCL joystick maps. Actual touch behavior and visual parity still require device testing with the user's exported layouts.
