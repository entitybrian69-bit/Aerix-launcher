#!/usr/bin/env python3
"""Wiring checks for the public-complaint batch.

Every assertion here is about a connection that used to be missing - a preference nothing read, a
request file nothing polled, a jar nothing installed - rather than about wording.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
MOD = ROOT / 'aerix_client_mod/src/main/java/aerix/client/mod'
RES = ROOT / 'app_pojavlauncher/src/main/res'


def read(path):
    return path.read_text(encoding='utf-8')


# 1. The black strip beside the game: the game window must go edge-to-edge like the launcher's own.
preferences = read(JAVA / 'prefs/LauncherPreferences.java')
assert 'public static boolean PREF_IGNORE_NOTCH = true;' in preferences, \
    'the cutout default must be edge-to-edge, not a reserved black strip'
assert 'DEFAULT_PREF.getBoolean("ignoreNotch", true)' in preferences, \
    'players who never touched the setting must not inherit the old black-strip default'
game = read(JAVA / 'game/GameActivity.java')
assert 'protected boolean shouldIgnoreNotch()' in game, 'the game window has to say it ignores the cutout'
notch = game.split('protected boolean shouldIgnoreNotch()')[1].split('    }')[0]
assert 'return true;' in notch, 'a reserved cutout is what produced the black band next to the game'

# 2. Mouse speed must not touch aim while the game holds the pointer.
touch = read(JAVA / 'game/TouchEventProcessor.java')
assert 'float mouseSpeed = Platform.isGrabbing() ? 1f : LauncherPreferences.PREF_MOUSESPEED;' in touch, \
    'the launcher multiplier must stop applying once the game owns the mouse'
assert 'x * LauncherPreferences.PREF_MOUSESPEED' not in touch

# 3. Cursor size has to follow the slider, and be reachable in game.
assert 'applyAerixPointerPreferences();' in game
applier = game.split('public void applyAerixPointerPreferences()')[1].split('    }')[0]
assert 'setCursorScale(LauncherPreferences.PREF_MOUSESCALE)' in applier
assert 'public void onCursorScaleChanged()' in game, 'quick settings must push the size at the cursor view'
dialog = read(JAVA / 'prefs/QuickSettingSideDialog.java')
assert 'R.id.editCursorSize_seekbar' in dialog and 'R.id.editZoomStrength_seekbar' in dialog, \
    'cursor size and zoom strength belong in the in-game quick settings'
assert 'PREF_MOUSESCALE = progress / 100f;' in dialog
assert 'PREF_ZOOM_FOV_SCALE = progress / 100f;' in dialog
layout = read(RES / 'layout/dialog_quick_setting.xml')
for view in ('editCursorSize_seekbar', 'editZoomStrength_seekbar', 'editCursorSize_textView',
             'editZoomStrength_textView'):
    assert 'android:id="@+id/%s"' % view in layout, 'missing view in quick settings layout: ' + view
chain = layout.split('@id/editMouseSpeed_seekbar')[1].split('checkboxGesture')[1]
assert '@id/editZoomStrength_seekbar' in chain, 'the gesture row must hang below the new bars, not over them'
strings = read(RES / 'values/strings.xml')
assert 'quick_setting_cursor_size' in strings and 'quick_setting_zoom_strength' in strings
values = read(RES / 'values/values.xml')
assert 'zoom_strength_seekbar_min' in values and 'zoom_strength_seekbar_max' in values

# 4. The FPS counter now has an off switch outside the legacy screens.
settings_bridge = read(JAVA / 'aerix/bridge/PojavSettingsBridge.kt')
assert 'PojavSetting.Toggle("show_fps"' in settings_bridge, 'the FPS display must be togglable'

# 5. The client's in-game half must reach the instance, or nothing below it can work.
installer = read(JAVA / 'aerix/client/AerixClientInstaller.kt')
bridge = read(JAVA / 'aerix/client/AerixClientModBridge.kt')
assert 'AerixClientModBridge.installIfNeeded(context, gameDir)' in installer, \
    'the bridge mod has to be copied into the instance before the game starts'
assert 'assets.open("$ASSET_DIR/$ASSET_JAR")' in bridge
assert 'INSTALLED_NAME = "aerix_client.jar"' in bridge
assert 'aerix_status.properties' in bridge, 'the game side has to be able to report back'
gradle = read(ROOT / 'app_pojavlauncher/build.gradle')
assert 'registrar.projectJarDependency project(":aerix_client_mod"), "components/aerix_client_mod"' in gradle, \
    'the mod jar must be packaged as an asset, exactly like the forge_installer component'

# 6. Live options: the launcher writes requests, the game applies them and restores the player's own.
live = read(JAVA / 'aerix/client/ClientLiveOptionsBridge.kt')
for key in ('aerix_live.properties', 'aerix_server_boost.properties', 'aerix_live_recovery.properties'):
    assert key in live, 'missing bridge file: ' + key
assert 'fun zoomTargetFov()' in live and 'PREF_ZOOM_FOV_SCALE' in live
assert 'fun restoreAtLaunch(gameDir: File)' in live and 'ClientOptionLines.merge' in live
assert 'fun setZoomHeld' in live
assert 'activeGameDir' in live
options = read(MOD / 'ClientLiveOptions.java')
assert 'static final String LIVE_FILE = "config/aerix_live.properties"' in options
assert 'static final String BOOST_FILE = "config/aerix_server_boost.properties"' in options
assert 'static final String RECOVERY_FILE = "config/aerix_live_recovery.properties"' in options
assert 'private static final String[] BOOST_KEYS' in options
assert 'multiplayerState' in options, 'the boost must know a server from a local world'
assert 'if (value < minimum || value > maximum) return false;' in options, \
    'an out-of-range number must be refused rather than written to a stranger option'
assert 'private Member fovHandle(Object options)' in options, 'a discovered FOV member must be reused to restore'

generator = read(JAVA / 'aerix/client/AerixClientPackGenerator.kt')
assert 'ClientLiveOptionsBridge.syncAll(instanceGameDir)' in generator, \
    'one owner for the live-props file, so the launch path and the live path agree'
assert 'gamma=${if (AerixClientStore.fullbrightEnabled) "100.0" else "1.0"}' not in generator, \
    'the gamma value the in-game option actually takes is 0..1'
input_bridge = read(JAVA / 'aerix/client/AerixClientInputBridge.kt')
zoom = input_bridge.split('fun setZoomActive(holding: Boolean)')[1].split('\n    }')[0]
assert 'ClientLiveOptionsBridge.setZoomHeld(gameDir, holding)' in zoom, \
    'the ZOOM pill has to reach the game even when no zoom mod is installed'
assert 'KEYCODE_C' in zoom, 'instances without the bridge mod keep the key press'
overlay = read(JAVA / 'aerix/client/AerixClientOverlayView.kt')
assert 'ClientLiveOptionsBridge.activeGameDir = instance.gameDirectory' in overlay
fullbright = overlay.split('AerixClientStore.fullbrightEnabled = !AerixClientStore.fullbrightEnabled')[1][:160]
assert 'ClientLiveOptionsBridge.syncAll()' in fullbright, 'Fullbright must apply without a restart'
assert 'Server Boost' in overlay
assert 'ClientLiveOptionsBridge.BoostPreset.values()' in overlay, 'the boost needs a strength choice, not just a switch'
assert 'describeGameStatus()' in overlay, 'the menu must show what the game side reports'
assert 'Game side:' in overlay, 'the Capes page must say why a cape is or is not showing'
assert 'Re-send cape frames to the game' in overlay
store = read(JAVA / 'aerix/client/AerixClientStore.kt')
assert 'var serverBoostEnabled by mutableStateOf(false)' in store
assert 'var serverBoostPreset by mutableStateOf("MEDIUM")' in store
assert 'putBoolean("serverBoostEnabled", serverBoostEnabled)' in store
assert 'putString("serverBoostPreset", serverBoostPreset)' in store

# 7. Cape failures must surface instead of being swallowed by runCatching.
cosmetics = read(JAVA / 'aerix/client/AerixClientCosmetics.kt')
assert 'var lastCapeSyncStatus' in cosmetics
sync = cosmetics.split('fun syncCapeAssetsToInstance')[1].split('fun syncCapeAssetsToAllClientInstances')[0]
assert 'onFailure' in sync and 'lastCapeSyncStatus = "Could not write cape frames' in sync
cape = read(MOD / 'ModernCapeBridge.java')
assert 'String status()' in cape, 'a single renamed accessor used to hide behind silence'
assert 'attempts++' in cape and 'retryAfter = now + Math.min(30_000L, 1_500L * attempts)' in cape, \
    'resolve failures must be retried, never disabled forever'
assert 'resolvePositionalSkin(skin)' in cape, 'older skin classes still need the body, cape, elytra order'
assert 'getRecordComponents()' in cape

# 8. A recording has to be openable by other apps, not only by this launcher.
output = read(JAVA / 'recording/RecordingOutput.kt')
assert 'RecordingLibrary.publish(context, destination)' in output
assert 'val mediaUri: Uri? = null' in read(JAVA / 'recording/RecordingOutput.kt')
library = read(JAVA / 'recording/RecordingLibrary.kt')
assert 'MediaStore.Video.Media.RELATIVE_PATH' in library and 'Movies/' in library
assert 'IS_PENDING' in library
assert 'FLAG_GRANT_READ_URI_PERMISSION' in library
assert 'Tools.openPath(context, file, share)' in library, 'the document provider remains the fallback'
controller = read(JAVA / 'recording/RecorderController.kt')
assert 'RecordingLibrary.view(activity, file, action == 1)' in controller
service = read(JAVA / 'recording/ScreenRecordingService.kt')
assert 'Saved to Movies/Aerix' in service

# 9. The bridge must not be blocked on the launch path, and a modpack's mods are not ours to edit.
launch = installer.split('fun syncLocalAssetsAtGameStart')[1].split('suspend fun scheduleThirdPartyModRepair'
                     if 'suspend fun scheduleThirdPartyModRepair' in installer else 'private fun scheduleThirdPartyModRepair')[0]
assert 'ensureWaveyCapes(modsDir' not in launch, 'a Modrinth call on the launch path is what froze the boot screen'
assert 'ensurePodiumAndSodium(modsDir' not in launch
assert 'if (instance.modpackSource == null) scheduleThirdPartyModRepair' in launch, \
    'a modpack owns its mod set; adding our optimization mods is how conflicts start'
repair = installer.split('private fun scheduleThirdPartyModRepair')[1].split('\n    }')[0]
assert 'worker.isDaemon = true' in repair and 'worker.start()' in repair

# 10. The game side of the mod must run on every runtime, not only the older one.
mod = read(MOD / 'AerixClientMod.java')
tick = mod.split('private void onRenderThreadTick')[1].split('\n    }')[0]
assert tick.index('liveOptions.tick(mc, runDir, world)') < tick.index('if (assetSkin)'), \
    'live options used to be skipped entirely by the newer runtime early return'
assert 'writeStatus(runDir, world, modernCapeBridge.status())' in tick
assert 'writeStatus(runDir, world, legacyCapeStatus())' in tick
assert 'syncLiveOptionsIfNeeded' not in mod, 'the old gamma-only path is replaced by the bridge'
assert 'components/aerix_client_mod' in gradle

# 11. A modpack belongs to the client it was built for, in the search and in the install sheet.
discover = read(ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/DiscoverScreen.kt')
assert 'val result = PojavAssetBridge.searchFollowing(' in discover, \
    'the strict filter used to return an empty page with no explanation at all'
assert 'PojavAssetBridge.instanceTarget()' in discover, 'the search must start from the instance in use'
assert 'followsInstance = false' in discover, 'a touched pill has to stop the follow, not fight it'
assert 'Nothing is tagged' in discover, 'a relaxed page must say that it is relaxed'
install_match = read(ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/AssetInstallMatch.kt')
assert 'internal fun loaderMatters(type: AssetType): Boolean = type.supportsLoaderFilter' in install_match, \
    'a Fabric pack in a Forge instance is exactly what the loader check exists to catch'
asset_bridge = read(ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/bridge/PojavAssetBridge.kt')
assert 'class AssetSearch(' in asset_bridge and 'relaxedVersion: Boolean = false' in asset_bridge
relief = asset_bridge.split('suspend fun searchFollowing')[1].split('private suspend fun')[0]
assert ': return@withContext AssetSearch(null)' in relief, \
    'a failed request must stay failed: only an empty page may be relaxed'
assert 'if (strict.projects.isNotEmpty() || (mcVersion.isNullOrBlank() && loader.isNullOrBlank()))' in relief

# 12. Our mods only move when the player says so, and a modpack keeps the ones it shipped with.
assert 'suspend fun updateOwnMods(context: Context, instance: Instance): Result<String>' in installer
update = installer.split('suspend fun updateOwnMods')[1].split('private fun jarNamesFor')[0]
assert 'instance.modpackSource != null' in update and 'curated.filter { it.slug == "fabric-api" }' in update, \
    'a pack instance may only gain Fabric API, and never lose what it already has'
assert 'force = !fromPack' in update, 'a pack must not have its version-matched jars replaced'
assert 'AerixClientModBridge.installIfNeeded(context, gameDir)' in update
assert 'unavailable for $mcVer' in update, 'a Modrinth that did not answer must not read as success'
assert 'force: Boolean = false' in installer and 'if (!force && fabricId != null' in installer, \
    'only the explicit update may replace a jar already in the folder'
assert 'AerixClientInstaller.updateOwnMods(activity, installed)' in read(
    ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/AerixClientScreen.kt'), \
    'the button is the only caller; nothing pushes mods on its own'

# 13. What the launcher is doing is visible to friends, and switchable off.
activity = read(JAVA / 'aerix/social/SocialActivity.kt')
assert 'SocialBackend.reportLauncherActivity(' in activity
assert 'Reading $title \u00b7 $kind' in activity and 'Building $clean' in activity
backend = read(JAVA / 'aerix/social/SocialBackend.kt')
assert 'fun reportLauncherActivity(line: String?)' in backend
assert 'launcherActivityLine' in backend and 'shareLauncherActivity = p.getBoolean("share_activity", true)' in backend
chain = backend.split('private suspend fun syncRichPresenceToServer')[1].split('val wireStatus')[0]
assert chain.index('activeGameStatusLine') < chain.index('launcherActivityLine'), \
    'the game session is the true sentence while a world is open'
assert backend.index('if (!shareLauncherActivity) return') < backend.index('fun toggleBackgroundNotifications'), \
    'Activity Off has to stop the line before it is remembered, not after'
assert 'toggleShareLauncherActivity(!SocialBackend.shareLauncherActivity)' in read(
    ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/SocialUi.kt')
assert 'SocialActivity.reading(project)' in discover and 'SocialActivity.building(' in read(
    ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/CreateInstanceScreen.kt')

# 14. The chat glitch: no double bubble, no yank to the bottom, no menu on the wrong row.
chat = read(JAVA / 'aerix/ui/screens/SocialChatViews.kt')
assert 'internal fun socialMessageKey(m: SocialMessage): String' in chat, \
    'the dedup and the list key must agree on what one message is'
assert chat.count('socialMessageKey(') >= 3, 'the row state needs the same key, or it follows the slot'
gate = chat.split('val pinnedToEnd = remember')[1].split('SocialPanel')[0]
assert 'lastVisible.index >= info.totalItemsCount - 2' in gate
assert 'if (!initialScrollDone || inGame || lastIsMine || pinnedToEnd.value)' in gate, \
    'a receipt or a typing dot must not move the list out from under the player'
assert 'import androidx.compose.runtime.derivedStateOf' in chat
for snippet in ('if (alreadySent(enriched, meId, queued)) return@forEachIndexed',
                'if (alreadySent(enriched, me, q)) return@forEachIndexed',
                'private fun alreadySent(rows: List<SocialMessage>, me: String, queued: SocialOutboxItem)'):
    assert snippet in backend, 'a queued send shown beside its delivered copy is the double bubble: ' + snippet

print('public-complaint wiring passed')
