# Wardrobe + Trinkies verification checklist

**Status: UNVERIFIED.** This is a test plan, not evidence that any Trinkies renderer or device has passed. The Fabric client gametest API exists for 1.21.11. A separate Loom-based CI smoke harness is configured to load the packaged Trinkies JAR, create a local player, and capture a baseline third-person screenshot under Xvfb/Mesa llvmpipe; no Minecraft client has launched yet. Run [42561704155](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42561704155) stopped at Loom configuration because the nested build used Gradle 8.14.3; run [42562329477](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42562329477) stopped because setup-gradle rejected the shorthand version `9.8`; run [42562789568](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42562789568) installed Gradle 9.8.0 but Loom required JDK 25 and the test used JDK 21. The workflow now sets up Temurin JDK 25 for this isolated test, pending CI verification. The harness deliberately does not claim cosmetic rendering. If it cannot launch a client, use the manual checklist below and attach unedited screenshots/logs.

## Automated prerequisites

- [ ] `:app_pojavlauncher:assembleNoruntimeDebug` succeeds in GitHub Actions.
- [ ] `:aerix_client_mod:jar` succeeds; verify the JAR is client-only and includes the shared asset manifest/catalog.
- [ ] Asset schemas, UV/model/texture/animation/hash/size checks pass.
- [ ] Unit tests cover equipped-state atomic write/read, invalid catalog IDs, last-good-state preservation, model parsing/baking, no per-frame allocations, animation interpolation/blending, and item renderer culling.
- [ ] Client gametest launches against each explicitly supported Minecraft/Fabric version under Xvfb + Mesa software GL; save test report and screenshot artifacts.

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
