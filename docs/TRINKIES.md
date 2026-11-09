# Trinkies cosmetics integration

Trinkies is Aerix Launcher's client-side cosmetics bridge. Gradle is configured to build the Fabric JAR from `:aerix_client_mod`, include it in the Android APK as `components/aerix_client/aerix_client_mod.jar`, and package the generated item source under `assets/trinkies/`. The launcher is configured to package the same source directory as `trinkies/` assets. **These new Gradle resource paths have not been verified by an Android or mod-JAR build in this environment.**

## Install scope and file safety

- Existing Fabric-family and Aerix Client instances are checked locally when Home opens; a supported instance is checked again before launch. Vanilla, Forge, and NeoForge are not modified and Fabric is not silently installed for them.
- The managed artifact path is `<instance>/mods/trinkies-aerix.jar`. `<instance>/mods/trinkies-aerix.manifest.json` records schema, mod ID, version, artifact name, and SHA-256 for the exact managed JAR.
- An existing JAR is only updated when the manifest proves it is the prior managed byte sequence. A duplicate `trinkies` mod ID, legacy `aerix-client-animator.jar`, malformed manifest, or unowned target is left untouched and reported. The installer does not delete legacy JARs and does not fall back to overwriting a target after an atomic rename failure.
- The sidecar is an ownership/hash record, not a malware scan. Review unexpected mod conflicts manually. Per-instance disable UI and version-matched JAR selection remain unfinished.

## Current source-tree behavior

- The launcher preview uses the account's local/cached/service skin where available, supports classic/slim model selection, existing cape paths, and a 2D fallback when WebGL is unavailable.
- Existing account skins, saved skin presets, 15 original animated cape designs, imported capes, and the shared catalog are retained. All 50 catalog hats are selectable and use a palette/shape-driven pixel overlay on the outer skin layer. The same selected hat ID is written atomically to `<instance game directory>/trinkies/equipped.json` before a supported instance starts. Hat rendering in Minecraft has not been visually verified; skin/cape visibility to other players depends on the account/skin service and server.
- `trinkies_assets/` contains 300 deterministic original assets: 50 hats, suits, back items, pets, emotes, and auras/trails each. The launcher loads their catalog data and item textures. Non-hat model assets are catalog/previews only; the client does not yet have their feature renderers, an equipped-state consumer, a pet follower, or an emote wheel. JSON Schema, texture/model budget, palette, UV, animation-clip, manifest, and pack-size checks are in `tools/trinkies_assets/validate_catalog.py`.
- The full catalog passes offline structural validation, but remains marked unverified in-game. The generated contact sheets are texture/model previews, not Minecraft screenshots or art approval. The current branch's Android/mod-JAR build and real in-game hat check are still pending.

## Visibility, sync, and privacy limits

The current Fabric mod is client-only. It includes existing local cape/texture bridges and client display overrides; it does not include the requested feature-renderer implementation for the generated hats/suits/wings/pets/auras, a pet follower, emote wheel, or synchronized multiplayer cosmetics protocol. Other players are not guaranteed to see custom skins/capes, and unmodified vanilla clients/servers cannot display Trinkies-only attachments or animations.

No Trinkies cosmetic state is sent to Supabase and no cosmetics sharing opt-in/delete UI exists yet. Existing Aerix social features use Supabase separately. Offline account cosmetics remain local; no UUID sharing is added by this integration. See [`wardrobe/SPEC.md`](wardrobe/SPEC.md) before any future sync work.
