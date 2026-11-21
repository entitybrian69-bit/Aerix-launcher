#!/usr/bin/env python3
"""Wiring checks for resource-pack fixes: priority, stamp location, unwrapping, and activation."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix'

pack_gen = (JAVA / 'client/AerixClientPackGenerator.kt').read_text(encoding='utf-8')
assert 'File(configDir, ".aerix_pack_stamp")' in pack_gen, \
    '.aerix_pack_stamp must live under config/, never inside resourcepacks/'
assert 'legacyStampFile.delete()' in pack_gen, \
    'stale resourcepacks/.aerix_pack_stamp must be cleaned up'
assert 'ResourcePackOptions.mergeClientPack(' in pack_gen, \
    'client pack must be merged below user resource packs via ResourcePackOptions'

client_opts = (JAVA / 'client/ClientInstanceOptions.kt').read_text(encoding='utf-8')
assert 'ResourcePackOptions.parseStrict(' in client_opts
assert 'fun setPackEnabledNow(' in client_opts
assert 'ResourcePackOptions.setPackEnabled(' in client_opts

content_bridge = (JAVA / 'bridge/PojavContentBridge.kt').read_text(encoding='utf-8')
assert 'ResourcePackArchive.unwrapSingleRootIfNeeded(' in content_bridge
assert 'ClientInstanceOptions.setPackEnabledNow(' in content_bridge
assert 'suspend fun setResourcePackEnabled(' in content_bridge

asset_bridge = (JAVA / 'bridge/PojavAssetBridge.kt').read_text(encoding='utf-8')
assert 'ResourcePackArchive.unwrapSingleRootInPlace(' in asset_bridge
assert 'ClientInstanceOptions.setPackEnabledNow(' in asset_bridge

game_dl = (JAVA / 'bridge/PojavGameDownloadsBridge.kt').read_text(encoding='utf-8')
assert 'activateInOptions = false' in game_dl
assert 'ClientInstanceOptions.queuePack(' in game_dl

editor = (JAVA / 'ui/screens/InstanceEditorScreen.kt').read_text(encoding='utf-8')
assert 'PojavContentBridge.setResourcePackEnabled(entry, enabled)' in editor

print('resourcepack_fixes_smoke passed')
