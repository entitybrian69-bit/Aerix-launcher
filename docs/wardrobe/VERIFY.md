# Wardrobe + Trinkies verification checklist

**Status: PARTIALLY VERIFIED; approved runtime matrix still UNVERIFIED.** This checklist is not evidence that a Trinkies cosmetic renderer or device has passed. The owner approved 26.3/Java 25, 1.21.11/Java 21, and 1.20.1/Java 17; 1.16.5/Java 8 is deferred. Earlier run [42563283038](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563283038) passed a 1.21.11 baseline under JDK 25, creating a local world/player and screenshot; it does not validate the approved Java 21 runtime or render an equipped cosmetic. The latest matrix run [42581205391](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42581205391) passed APK/JAR build and Gradle setup but failed in client tests: 26.3/Java 25 lacked a usable GLX visual and Vulkan `VK_KHR_surface`; 1.21.11/Java 21.0.13 loaded Trinkies but timed out creating the test world. The 1.20.1/Java 17 outcome remains unconfirmed because GitHub log/artifact download returned EOF; no pass is inferred. The current harness revision pins Ubuntu 24.04, installs Mesa/Xvfb dependencies and requires a passing llvmpipe GLX probe, runs world-free API Client GameTests with a startup screenshot for 26.3 and 1.21.11, and verifies exact Java/pass markers and per-target artifact results. 1.20.1 continues to use the documented standard-client boot/mod-load fallback. This revision still requires CI; keep Steps 1–5 gated until there is explicit runtime evidence or a documented unsupported tuple.

## Automated prerequisites

- [x] `:app_pojavlauncher:assembleNoruntimeDebug` succeeds in GitHub Actions; full and no-runtime debug APK builds/uploads passed in [run 42563282869](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563282869).
- [x] `:aerix_client_mod:jar` succeeds; `tests/trinkies_jar_verify.py` verified client-only metadata, client entrypoint, 30-item catalog, and manifest in [run 42563283038](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563283038).
- [x] Offline asset validation passed: item/animation schemas, model/texture embedding, UV coverage, rig/cube budgets, hashes, catalog IDs, and <15 MiB compressed pack; local result is recorded in `PROGRESS.md`. This does not verify in-game rendering.
- [ ] Unit tests cover equipped-state atomic write/read, invalid catalog IDs, last-good-state preservation, model parsing/baking, no per-frame allocations, animation interpolation/blending, and item renderer culling.
- [x] Baseline client GameTest passed for Minecraft 1.21.11 under Xvfb + Mesa software GL in [run 42563283038](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563283038); its screenshot artifact is uploaded. The image could not be downloaded for visual review here.
- [ ] Obtain explicit passing result files for all owner-approved tuples: world-free client startup GameTests plus screenshot on 26.3/Java 25 and 1.21.11/Java 21, and the standard-client boot/mod-load fallback on 1.20.1/Java 17. The runner must record the actual Java version and confirm the expected target; none of these approved tuples has a recorded pass yet.
- [ ] Verify the CI headless-graphics artifact reports Xvfb + Mesa llvmpipe GLX before accepting the 26.3 result.

## Manual local-player hat check

For each explicitly supported Minecraft/Fabric/Java combination, use a clean test instance with the test Trinkies JAR and Fabric API. Record launcher build, mod SHA-256, Minecraft version, Fabric Loader/API version, Java runtime, OS, GPU/renderer, display resolution, and test date.

1. Import or select a standard classic skin. In Wardrobe, choose one hat, equip it, verify the 3D preview changes immediately, restart Aerix, and confirm the equipped choice and selected account remain correct.
2. Launch the target instance. Verify the selected instance has Trinkies enabled; disable it and re-enable it from per-instance controls and confirm the Wardrobe status text updates accurately.
3. In third person, capture the local player standing, sneaking, swimming, riding, and turning. Verify the hat follows the head pose, remains aligned, and is not cropped or detached.
4. Equip a vanilla helmet. Confirm the configured default (hat above helmet) and the hide-hat/hide-helmet setting. Confirm the hat is hidden in first person.
5. Repeat with a slim-arm skin. Check classic/slim, armor, GUI scale, and at least one supported mobile renderer without changing renderer flags or using device-specific workarounds.
6. Capture separate, unaltered screenshots for each condition plus the relevant game log. Note visual defects rather than editing or compositing evidence.
7. Check vanilla and Forge/NeoForge instances: Trinkies must not be installed and their files must not be changed. Check an existing unowned or duplicate-ID mod: the installer must report conflict and preserve it byte-for-byte.
8. Check offline mode: no cosmetics network calls are made. Do not test multiplayer visibility or infer that skin/cape visibility guarantees Trinkies accessory visibility.

## Other-category and pet checks (future gates)

- Suits: overlay/replace mode, armor overlap, classic/slim arms, standing/sneaking/swimming/flying.
- Back/wings: idle, jump flap, elytra flap, pose reset, frustum/distance culling.
- Auras: client particle setting, configured particle cap, brightness contrast, far-distance culling.
- Emotes: partial-tick interpolation, blend in/out, loop boundary, cancel on movement/damage/item use; wheel accessible on keyboard and touch; user layout/import/export preserved.
- Pets: each archetype follow/sit/sleep/jump state, no collision or movement blocking, terrain height, nearest-16 limit and frustum culling.

## Device/performance record (currently not measured)

- Preview rotation frame time/FPS: **not measured**.
- Wardrobe cold-start time: **not measured**.
- In-game frame time with 20 Trinkies players: **not measured**.
- Mali-G57 / Narzo 50, Snapdragon, MediaTek, ARM32 and desktop software-renderer results: **not measured**.
