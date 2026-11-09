# Wardrobe + Trinkies plan

**Status (2026-11-09):** Cosmetics are now data-driven skin bakes for all six wardrobe categories, and the hand-painted hat overlay that preceded them has been deleted: the bake is the only cosmetic paint path in the launcher, and a smoke test fails if launcher Kotlin names a catalog item at all. Each item ships `garment.png`, a 64×64 patch in the player skin's own UV layout, and `TrinkiesGarmentComposite` composites the equipped patches onto the skin the launcher serves to the game; back items additionally ship a 64×32 `cape_patch.png` in the cape's own UV space, which the launcher bakes onto the cape sheet it serves and onto every animation frame it writes into a supported instance (a back item therefore changes the cape without disturbing the elytra wings, which stay the launcher's own). Catalog card previews are now 192×192 renders of a mannequin actually wearing the item, authored from the same layout module, so a card and a bake can no longer disagree about where a face is. Local `validate_catalog.py --require-full` passes with a 6,335,932-byte compressed pack and byte-stable regeneration; the smoke tests cover the cross-language UV table and pin the cape wiring, and the JAR gate now sees a `cape_texture` path on all fifty back rows. Android CI and the Aerix debug gate (APK + Trinkies JAR + every `tests/*.py`) are green on this branch, which is the only compile-and-run check available in this workspace (no `javac`, no Android SDK here); nothing has been verified in real Minecraft: the mod reads `equipped.json` for cadence and confirmation but does not render attachments, and 3D attachment renderers, pet followers, particle trails, and the emote wheel remain unimplemented. Do not treat the full catalog or the local startup matrix as art/render approval.

## Recon findings

### Navigation and screen registration

- `ui/AerixNavigationRail.kt` defines `LauncherSection`, including `TRINKIES_WARDROBE`. The main switch in `ui/AerixLauncherScreen.kt` dispatches that destination to `TrinkiesWardrobeScreen` inside `AnimatedContent`.
- The shell keeps `AerixHomeUtilityRail` outside the section animation so Home shortcuts remain available while navigating. The Wardrobe has its own lobby header and suppresses the primary left rail, while the shell utility rail remains available.
- `TrinkiesWardrobeScreen.kt` is a Compose screen with responsive wide/portrait arrangements. It retains account skin presets and 15 animated cape designs, reads the shared 300-item catalog, and lets users equip any item in all six categories; each category has its own persisted slot on `AerixClientStore`. Equipping stores the id and re-bakes the preview/served skin from that item's `garment.png`. What is *not* drawn anywhere is the item's 3D geometry: floating wings, followers, particles, and prop rigs exist only as catalog previews until the mod renders them.

### Account skin and preview pipeline

- `bridge/PojavAccountBridge.kt` owns account selection and the account-keyed skin wardrobe. `resolveAccountSkinBytes()` prefers a locally imported skin, then local caches, then account-type-specific Mojang/Ely.by or mirror skin URLs; local/offline accounts remain local. Slim/classic is stored per account. Cape loading prefers a local import and only fetches an official Microsoft cape when requested.
- `components/AerixPlayerSkin.kt` creates an Android WebView around the bundled `skinview3d` viewer in `assets/skinview/`. Local PNG bytes are served from memory through the WebView asset handler. The component uses the actual account skin, supports a 2D full-body fallback on WebGL failure, and destroys its WebView on disposal.
- The bundled viewer uses a Three.js-based WebGL canvas and an FXAA post-process; it is not the game's GL4ES/VirGL/Zink renderer. The screenshot fix now fits a conservative player bounding sphere to both viewport axes, caps preview render pixels, and frame-synchronizes Compose camera changes. This reduces work but is not an FPS measurement.

### Existing cape bridge and game hook

- `aerix_client_mod/AerixClientMod.java` is a dependency-light Fabric client entrypoint. It resolves Minecraft members reflectively, schedules updates on the client executor, loads local cape frames, and updates vanilla texture objects. `ModernCapeBridge.java` adapts the newer player-skin asset lookup with reflective access to the client skin/texture APIs. It also watches local `config/aerix_cape_frames` files and WaveyCapes configuration.
- The bridge is not a feature-renderer implementation for hats, suits, wings, pets, auras, or emotes. It does not implement the requested other-player cosmetics protocol. The current reflection surface is version-sensitive and must be validated against each selected Minecraft version. No raw-GL accessory renderer should be added.

### Instance install and data paths

- The root `settings.gradle` includes `:aerix_client_mod`; Gradle targets Java 8 bytecode and `app_pojavlauncher/build.gradle` is configured to package its JAR under `components/aerix_client`. The new shared asset-source wiring is not build-verified because Java/Android SDK are unavailable here.
- `TrinkiesInstaller.kt` scans existing Fabric-family instances when Home opens and repairs the selected compatible instance just before launch. Supported detection includes Fabric, Quilt, Legacy Fabric, and Aerix Client instances; vanilla and Forge/NeoForge are skipped. The game instance's own `mods/` directory is the install target. Existing local cape assets are synced separately.
- The previous deploy routine could overwrite `mods/trinkies.jar` and delete `mods/aerix-client-animator.jar` without proving ownership. The new routine uses `trinkies-aerix.jar`, a visible per-instance `trinkies-aerix.manifest.json` SHA-256 sidecar, duplicate-ID checks, atomic temporary writes, and a conflict result. An unowned or legacy JAR is left untouched and reported; it is never silently deleted. A per-instance disable toggle and version-matched JAR matrix remain unimplemented.

### Supabase and social data

- `social/SocialBackend.kt` uses the existing Supabase project through REST and `SocialRealtime.kt` implements Phoenix-channel presence/realtime over OkHttp. Current features include Aerix social account/profile, friends, chat/parties/presence, and social-media storage. The wardrobe screen calls the existing social attach path for its upload/profile actions.
- There is no Trinkies equipped-state table, Minecraft UUID proof, room-scoped emote channel, or cosmetics sync/delete flow. This patch does not add any new network request or widen existing profile data. Do not infer cosmetics sharing from the launcher social presence system.

### Touch-controls integration

- `customcontrols/ControlLayout.java` loads a `CustomControls` model, creates button/drawer/joystick views, and persists user layouts through the existing layout files. `ControlLayoutImporter.java` converts Pojav, Zalith, and Fold Craft layout JSON and reports skipped/unsupported controls.
- No stable Trinkies action registry or safe merge-by-ID path for a new emote-wheel button was found. A future wheel must be added to the untouched default layout without replacing a user's customized layout, and must survive import/export. This is not implemented in this slice.

### Repo/privacy/licensing constraints

- Android IDs, release/debug signing setup, and version-code logic are kept unchanged in this work.
- README privacy language promises no ads and local-first profiles/cosmetics. Trinkies remains client-only and no cosmetics sync has been introduced. Existing optional local HUD telemetry is gated by a local opt-in marker and writes locally; it is not a Trinkies network feature.
- New slice art is generated by the checked-in deterministic Python script and authored cube templates; no scraped/Mojang/third-party assets are used. The project license remains GPL-3.0.

## OWNER DECISION — approved initial Minecraft/Java matrix; other defaults remain recommendations

1. **Minecraft/Java matrix — OWNER APPROVED:** first support tier is **26.3 / Java 25, 1.21.11 / Java 21, and 1.20.1 / Java 17**. Defer **1.16.5 / Java 8** to a later tier. Approval sets the targets; it does not certify compatibility. CI must test each JAR/runtime tuple before claiming support. The Fabric client GameTest API exists for 26.3 and 1.21.11; it is absent from the Fabric 1.20.1 source branch, so that tuple uses a test-only client-boot/mod-load smoke plus the manual in-game checklist. The earlier 1.21.11 baseline passed with JDK 25, not the approved Java 21 game runtime. Matrix run [42581205391](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42581205391) passed Gradle setup but failed 26.3 on missing GLX/Vulkan backends and 1.21.11/Java 21.0.13 during local-world creation; the 1.20.1/Java 17 result was not inspectable. Follow-up [42585312512](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/42585312512) passed all three exact-target startup/mod-load smoke checks: 26.3/25 and 1.21.11/21 Client GameTests, plus the 1.20.1/17 boot fallback. The runner validated the Java major, mod-load/pass marker, and screenshot/boot evidence for each path. Treat those tuples as verified only for this baseline smoke scope; the full feature/runtime claims still require the manual and feature-specific checks below.
2. **Loaders.** Recommend Fabric first and Quilt only after its Fabric compatibility is tested against the same JAR. Forge/NeoForge are a later stretch goal; never place a Fabric-only entrypoint in those mods folders.
3. **Sync/offline accounts.** Recommend no cosmetics sync by default. A future opt-in may publish only catalog IDs for a verified Minecraft UUID, with RLS, validation, rate limits, TTL/cache/backoff, a kill switch, and an actual delete operation. Microsoft/Ely.by accounts need an ownership proof before writes. Offline accounts remain local-only by default; do not allow unverified UUID sharing unless the owner explicitly accepts and labels the impersonation risk.
4. **Module shape.** The current `:aerix_client_mod` already owns the bundled mod ID and APK integration. Recommend migrating/evolving that single artifact into the proposed `trinkies_mod` boundary only when the new version matrix is buildable; do not package two JARs with the same Fabric ID.

## Risks and hard parts

- Rendering onto local and remote player models through versioned Fabric hooks, without raw GL/shaders and while coexisting with Sodium/Iris/Embeddium, is the largest technical risk. Existing cape texture replacement does not solve this.
- Pets need a local-follow simulation that respects terrain, dimensions, culling, and render-thread allocation budgets. Emotes need a mixin/API hook and movement/cancel rules. Neither is in the current bridge.
- A secure multiplayer protocol needs identity ownership proof, per-UUID RLS, catalog validation, deletion, clear opt-in UX, and a server-session protocol. Reusing social presence is not sufficient.
- App startup install currently has no per-instance disable UI. Hash ownership and conflict protection are now present, but legacy migrations need careful user-facing handling.
- Generated Blockbench-style assets and schemas validate structurally. The generated preview sheet is a local procedural projection, not a Minecraft render. Classic/slim, armor, pose, world-lighting, animation quality, and texture alignment have not been verified in-game.
- Device/browser WebGL performance, WebView context-loss behavior, Android 8, ARM32/x86, Mali-G57, Snapdragon, MediaTek, GL4ES/LTW/Zink/MobileGlues, and 60-fps targets remain unmeasured.

## Phase gates and next steps

- **Phase 1 (asset source):** complete for the current 300-item roster. The deterministic generator, item/catalog schemas, budgets, palette/UV checks, shared launcher/mod assets, docs, 300-row roster, transparent per-item shaded model previews, and full preview sheet are present. Structural checks pass locally; the catalog remains unverified in-game.
- **Phase 2 (runtime):** partial. A launcher-side catalog reader and palette/shape-driven hat skin overlay exist, and the legacy client mod still handles capes. The version-adapted feature renderer/model sampler for catalog models, runtime cache, LOD/culling, safe config, graceful load failures, and mod-side loadout consumer are not implemented.
- **Wardrobe wiring:** partial. Catalog-derived shaded 3D model previews and rarity-colored cards are shown in all categories; the 50 hats are selectable and the launcher snapshots the selected hat to `trinkies/equipped.json` atomically before supported launches. Preview images are static launcher UI renders, not equipped Minecraft models. Existing account skins/capes are retained; hats have not yet passed an in-game visual check.
- **Phase 3 (not complete):** only after OWNER DECISION, add opt-in, verified state sync and scoped realtime emote events; no sync is enabled by this change.
- **Phase 5a:** the remaining 270 are generated. The full catalog is structurally validated, but no catalog-wide Blockbench/game-render review, real-device screenshots, or owner art review exists. Do not describe generated entries as accepted or done cosmetics.
