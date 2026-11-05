#!/usr/bin/env python3
"""Structural checks for the game-account and appearance integration."""
from pathlib import Path
repo = Path(__file__).resolve().parents[1]
root = repo / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix'
backend = (root / 'social/SocialBackend.kt').read_text()
ui = (root / 'ui/screens/SocialProfileScreen.kt').read_text()
server = (root / 'bridge/AerixSkinServer.kt').read_text()
schema = (repo / 'supabase/aerix-game-accounts.sql').read_text()
assert 'suspend fun publishGameTexture(bytes: ByteArray, expectedOwner: String' in backend
assert 'expectedUserId = expectedOwner' in backend and 'ownerHeaders(expectedOwner)' in backend
assert 'rpc/aerix_publish_game_texture' in backend and 'rpc/aerix_claim_game_name' in backend
assert 'followRedirects(false).followSslRedirects(false)' in backend
assert 'storage/v1/object/public/social-media/$owner/' in backend
assert 'validGameTexturePng(bytes, kind)' in backend and '2 * 1024 * 1024' in backend
assert 'game-skin-slim-' in backend and 'game-skin-wide-' in backend
assert 'public Aerix storage' in ui and 'cloudAction?.let' in ui
assert 'PojavAccountBridge.equipSkinFromUri' in ui and 'PojavAccountBridge.equipCapeFromUri' in ui
assert 'SocialBackend.myUserId() != owner' in ui and 'PojavAccountBridge.current?.key != target.key' in ui
assert 'PojavAccountBridge.setSlimModel' in ui and 'previous.copyTo(backup, overwrite = false)' in ui
assert 'servers configured for Aerix authentication' in ui and 'Claim permanently' in ui
assert 'sharing boolean not null default false' in schema
assert 'revoke all on public.aerix_game_accounts from public, anon, authenticated' in schema
assert 'lower(game_name)' in schema and 'a.owner_id = auth.uid()' in schema
assert 'passThrough || !uuid.matches' in server and 'PojavAccountBridge.offlineUuid(name)' in server
assert 'instanceFollowRedirects = false' in server and 'readBounded' in server
print('Aerix game account and opt-in appearance guards are wired')
