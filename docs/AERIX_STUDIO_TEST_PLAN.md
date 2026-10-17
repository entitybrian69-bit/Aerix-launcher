# Aerix Studio validation

## Automated coverage
- Cape API: authenticated upload/delete, public download, UUID scoping, invalid UUID,
  corrupt PNG, wrong dimensions and oversize rejection (`services/capes/tests`).
- World display metadata: valid/empty NBT, invalid root, negative/truncated arrays,
  depth limit and list-field spoofing (`WorldMetadataTest`).
- Existing category-query and platform surface tests continue in Android CI.
- Android CI builds the remapped Fabric companion, packages it as an asset, builds
  both APK variants, then runs the launcher unit tests.

Local checks are not substitutes for Android/Minecraft testing. No device test has
been performed for this feature set yet. Do not describe a passing synthetic probe
as Minecraft FPS, shader support, or full compatibility.

## Device acceptance checklist (not yet executed)
1. On narrow portrait and short landscape screens, open Library → instance → Loader.
   The 264 dp sidebar must give way to a full-width inline loader workspace with a back action.
   Check all seven loaders. Buttons must not overlap; small screens show one full-width
   version/build list at a time. OptiFine and BTA stay in the same loader area.
2. Open Studio in both orientations; check every tool and the on-screen keyboard.
   Content scrolls vertically and actions wrap, without fixed-height dense tables.
3. Import a disposable world; verify name/icon/date/size. Create six backups and
   confirm only five remain. Export a ZIP and restore it in another temporary instance.
   Verify symlinks, active session.lock and concurrent launch prevent unsafe backups.
   Backup failure must remove the partial ZIP, not the world.
4. Launch a world and server using a profile tied to a different instance. Confirm
   the intended game directory, controls, renderer and enabled resource packs are
   actually used. Test legacy `--server/--port` and modern manifest-advertised QuickPlay.
   Requirements notes are not an automatic mod/resource-pack installer.
5. Edit controls: move/resize/add/delete/change properties, undo/redo, mirror twice,
   grid snapping and drawer orientations. Use selected-button menu/gameplay presets.
   Save/share/reimport with images. Imported images are not snapshotted by history.
6. Stop Minecraft, run each available GLES-wrapper probe. Verify clear-pixel readback
   and timing display. Force a driver crash/timeout; launcher must survive. Mesa/Vulkan
   comparisons and a Minecraft-scene benchmark are not provided by this EGL probe.
7. Install companion into Fabric 1.20.1 and launch. Reject unsupported loader/version.
   Verify missing companion, stale heartbeat, connected world/server, expired account,
   wrong provider, duplicate request and timeout all explain the failure safely.
   Disconnect/save, switch among offline/Microsoft/same-provider Ely accounts, rejoin,
   and check displayed UUID, server auth and signed chat. Check provider changes and
   Realms are explicitly routed to restart. Never switch an active server connection.
8. Deploy cape service behind HTTPS with proxy body/rate/connection limits and a quota.
   Manually verify UUID ownership and provision a scoped upload key. Use two Fabric
   clients with the same service: upload a cape and check both world and server views,
   including cache refresh and disabling Cape. A vanilla third client must not be
   promised visibility. No service is hosted by this repository or CI.

## Important boundaries
The feature set is experimental. The cape API source is deployable but is not a live
hosted service. The companion is tied to Fabric/Minecraft 1.20.1, not a universal
account switcher. Minecraft stays open, but users must save/disconnect before a switch.
Renderer results are a limited off-screen EGL/pixel/clear probe, not game performance.
