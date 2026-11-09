# Wardrobe + Trinkies verification checklist

**Status: APPROVED-MATRIX STARTUP SMOKES PASSED; current catalog/hat feature verification is pending.** The 300-item catalog passes local structural validation, and launcher source now includes catalog-backed hat selection, a skin-overlay path, and an atomic per-supported-launch loadout snapshot. The latest Android/Kotlin changes have not been built or visually exercised in Minecraft. The owner-approved targets are 26.3/Java 25, 1.21.11/Java 21, and 1.20.1/Java 17; 1.16.5/Java 8 is deferred. Earlier run [42563283038](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563283038) passed a 1.21.11 world/player baseline under JDK 25, but not the approved Java 21 runtime or equipped-cosmetic rendering. Follow-up [42585312512](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42585312512) passed the narrow startup/mod-load matrix: llvmpipe GLX preflight; world-free Fabric Client GameTests on 26.3/Java 25 and 1.21.11/Java 21; standard-client boot marker on 1.20.1/Java 17. Those screenshots were not visually inspected and do not prove local-world/player behavior or cosmetic rendering.

## Automated prerequisites

- [x] `:app_pojavlauncher:assembleNoruntimeDebug` succeeds in GitHub Actions; full and no-runtime debug APK builds/uploads passed in [run 42563282869](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563282869).
- [x] `:aerix_client_mod:jar` succeeded in earlier runs; the previous `tests/trinkies_jar_verify.py` checked the 30-item catalog in [run 42563283038](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563283038). The verifier has since been upgraded to require all 300 items and their manifest hashes; the new version is pending CI.
- [x] Offline full-catalog validation passed locally: item/animation schemas, model/texture embedding, UV coverage, rig/cube budgets, hashes, catalog IDs, 50 items/category, and a 3,153,012-byte compressed pack. This does not verify in-game rendering.
- [ ] Unit tests cover equipped-state atomic write/read, invalid catalog IDs, last-good-state preservation, model parsing/baking, no per-frame allocations, animation interpolation/blending, and item renderer culling.
- [x] Baseline client GameTest passed for Minecraft 1.21.11 under Xvfb + Mesa software GL in [run 42563283038](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42563283038); its screenshot artifact is uploaded. The image could not be downloaded for visual review here.
- [x] Approved startup/mod-load matrix passed in [run 42585312512](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42585312512); per-target results are shown below. The runner validates the expected target and Java major before a target can pass.
- [x] CI headless-graphics preflight passed: Xvfb + Mesa llvmpipe GLX was verified before launching Minecraft in run 42585312512.

| Approved tuple | Automated check | Result / scope |
|---|---|---|
| 26.3 / Java 25 | Fabric Client GameTest | **PASS** — packaged Trinkies loaded; startup screenshot was written, checked, and copied to the CI artifact. No world/player created. |
| 1.21.11 / Java 21 | Fabric Client GameTest | **PASS** — packaged Trinkies loaded; startup screenshot was written, checked, and copied to the CI artifact. No world/player created. |
| 1.20.1 / Java 17 | Standard client boot fallback | **PASS** — `CLIENT_STARTED` event, Trinkies-loaded assertion, and target/runtime marker verified. |

Run 42585312512 uploaded `trinkies-fabric-client-matrix-smoke` (1,273,792 bytes), the Trinkies JAR (340,078 bytes), and debug APK (90,463,780 bytes). Artifact metadata is available, but the screenshots were not visually reviewed in this workspace.

## Manual local-player hat check

For each explicitly supported Minecraft/Fabric/Java combination, use a clean test instance with the test Trinkies JAR and Fabric API. Record launcher build, mod SHA-256, Minecraft version, Fabric Loader/API version, Java runtime, OS, GPU/renderer, display resolution, and test date.

1. Import or select a standard classic skin. In Wardrobe, choose one hat, equip it, verify the 3D preview changes immediately, restart Aerix, and confirm the equipped choice and selected account remain correct.
2. Launch the target supported instance. Verify `<instance game directory>/trinkies/equipped.json` contains schema 1 and the selected catalog hat ID, with unsupported categories null. Confirm vanilla and Forge/NeoForge instances are left untouched. A per-instance disable toggle and truthful install-status UI are not implemented yet.
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
