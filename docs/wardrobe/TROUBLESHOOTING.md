# Wardrobe and Trinkies troubleshooting

## 3D preview is blank, clipped, or falls back to a flat skin

- The launcher preview uses Android System WebView/WebGL. If WebGL initialization or context creation fails, `AerixSkinPreview3D` shows a crisp 2D full-body skin image where available. This is a preview fallback, not a Minecraft renderer failure.
- The viewer now computes a conservative fit distance from both preview dimensions and limits the canvas pixel ratio/pixel budget. Rotation or pose can still be unverified on a specific WebView/GPU; no 60-fps guarantee is made.
- Resize the preview or reopen Wardrobe after an orientation change. The viewer resizes through a `ResizeObserver`; no device-specific GPU workaround is used.
- For the in-game renderer, model alignment and GL renderer compatibility have not been validated. Collect Minecraft log output and the renderer/device/version before reporting a crash; do not infer that WebView preview support proves GL4ES/LTW/Zink compatibility.

## Trinkies install reports a conflict

- Each instance's `mods/` folder contains the managed artifact `trinkies-aerix.jar` and a readable `trinkies-aerix.manifest.json` with the artifact SHA-256.
- If another JAR provides Fabric mod ID `trinkies`, or a legacy `aerix-client-animator.jar` exists, Aerix leaves it untouched and shows a warning. Review the files and decide manually which one to keep. The launcher will not delete or overwrite an unowned JAR.
- If `trinkies-aerix.jar` exists but its SHA does not match the sidecar, treat it as user-modified/unowned. Back up and inspect it before making any manual change. A malformed or unrelated manifest is not replaced.
- A manifest SHA proves byte identity to the file Aerix installed; it does not prove that a third-party mod has been audited. Do not edit the JAR in place.
- The supported local installer targets Fabric-family instances (including Quilt/Legacy Fabric by current detection) and Aerix Client. It does not install Fabric into vanilla or modify Forge/NeoForge.

## Cosmetics or synchronization seem missing

- Only existing local skin/cape/headwear paths are currently equipped by the app. The generated 30-item JSON/PNG/model catalog is not yet connected to the Wardrobe equip flow or game renderer.
- Other-player custom skins/capes depend on the account/skin service and server configuration. Pets, back attachments, aura particles, and emote network events are not shipped as game features.
- No Trinkies cosmetics state sync is active. Existing Aerix social presence is not a substitute for a verified Minecraft UUID protocol.

## Asset validation fails

1. Use Python 3.11+ and `pip install -r tools/trinkies_assets/requirements.txt`.
2. Run `python tools/trinkies_assets/validate_catalog.py --require-slice` and fix the first reported item/path.
3. For generator-owned slice files, rerun `python tools/trinkies_assets/generate_slice.py`, then validate again. The generator rewrites those 30 assets and preview sheets deterministically; it is not safe to use as a general manual-asset importer.
4. Check `trinkies_assets/trinkies/manifest.json` after any asset change. Do not update the hash manifest to conceal a failed model/UV/schema check.
