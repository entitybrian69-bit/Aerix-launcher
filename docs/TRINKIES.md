# Trinkies cosmetics integration

Trinkies is Aerix Launcher's client-side cosmetics bridge. Gradle is configured to build the Fabric JAR from `:aerix_client_mod`, include it in the Android APK as `components/aerix_client/aerix_client_mod.jar`, and package the generated item source under `assets/trinkies/`. The launcher is configured to package the same source directory as `trinkies/` assets. **These new Gradle resource paths have not been verified by an Android or mod-JAR build in this environment.**

## Install scope and file safety

- Existing Fabric-family and Aerix Client instances are checked locally when Home opens; a supported instance is checked again before launch. Vanilla, Forge, and NeoForge are not modified and Fabric is not silently installed for them.
- The managed artifact path is `<instance>/mods/trinkies-aerix.jar`. `<instance>/mods/trinkies-aerix.manifest.json` records schema, mod ID, version, artifact name, and SHA-256 for the exact managed JAR.
- An existing JAR is only updated when the manifest proves it is the prior managed byte sequence. A duplicate `trinkies` mod ID, legacy `aerix-client-animator.jar`, malformed manifest, or unowned target is left untouched and reported. The installer does not delete legacy JARs and does not fall back to overwriting a target after an atomic rename failure.
- The sidecar is an ownership/hash record, not a malware scan. Review unexpected mod conflicts manually. Per-instance disable UI and version-matched JAR selection remain unfinished.

## Current source-tree behavior

- The launcher preview uses the account's local/cached/service skin where available, supports classic/slim model selection, existing cape paths, and a 2D fallback when WebGL is unavailable.
- Existing account skins, saved skin presets, 15 original animated cape designs, imported capes, and the shared catalog are retained. All 300 catalog items are selectable, one per wardrobe slot (`hat`, `suit`, `back`, `pet`, `emote`, `aura`). Equipping bakes each item's `garment.png` patch over the account skin in UV space before that skin is served to the game, and the same ids are written atomically to `<instance game directory>/trinkies/equipped.json` before a supported instance starts. The bake needs a custom skin to paint on: on a plain account skin there is nothing to overlay. Nothing here has been visually verified in Minecraft; skin/cape visibility to other players depends on the account/skin service and server.
- `trinkies_assets/` contains 300 deterministic original assets: 50 hats, suits, back items, pets, emotes, and auras/trails each. Every item folder holds the Blockbench model and its own texture, a 192×192 `preview.png` of the mannequin actually wearing the item, and the `garment.png` bake patch (back items add a 64×32 `cape_patch.png`, laid out in the cape's own UV space, which the launcher bakes onto both the preview and the cape sheet it serves to the game). Art is authored once, in `tools/trinkies_assets/garments.py`, against the skin layout in `mc_skin_render.py`, so the patch and the card preview cannot drift apart.
- The launcher bake (`TrinkiesGarmentComposite`) is the only cosmetic renderer that runs today. The client mod does not yet consume `equipped.json`, and there is no pet follower, floating 3D prop, particle trail, or emote wheel; those remain catalog previews. JSON Schema, texture/model/garment budget, palette, UV, animation-clip, manifest, and pack-size checks are in `tools/trinkies_assets/validate_catalog.py`.
- The full catalog passes offline structural validation, but remains marked unverified in-game. The generated contact sheets are texture/model previews, not Minecraft screenshots or art approval. The current branch's Android/mod-JAR build and real in-game hat check are still pending.

## Visibility, sync, and privacy limits

The current Fabric mod is client-only. It includes existing local cape/texture bridges and client display overrides; it does not include an `equipped.json` consumer, the generated 3D attachments for wings/pets/auras, a pet follower, emote wheel, or synchronized multiplayer cosmetics protocol. Cosmetics reach the game today because the launcher bakes them into the served skin, not because the mod draws them. Other players are not guaranteed to see custom skins/capes, and unmodified vanilla clients/servers cannot display Trinkies-only attachments or animations.

No Trinkies cosmetic state is sent to Supabase and no cosmetics sharing opt-in/delete UI exists yet. Existing Aerix social features use Supabase separately. Offline account cosmetics remain local; no UUID sharing is added by this integration. See [`wardrobe/SPEC.md`](wardrobe/SPEC.md) before any future sync work.
