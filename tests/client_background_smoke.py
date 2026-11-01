"""Guard the shared boot/title artwork and instance-specific version wiring."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/client"
pack = (client / "AerixClientPackGenerator.kt").read_text()
overlay = (client / "AerixClientOverlayView.kt").read_text()
bridge = (client / "AerixClientInputBridge.kt").read_text()
activity = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/GameActivity.java").read_text()
asset = root / "app_pojavlauncher/src/main/assets/aerix/client_dark_background.webp"
data = asset.read_bytes()
assert data[:4] == b"RIFF" and data[8:12] == b"WEBP"
assert len(data) < 500_000
assert 'CLIENT_BACKGROUND_ASSET = "aerix/client_dark_background.webp"' in pack
assert 'assets.open(CLIENT_BACKGROUND_ASSET)' in pack
assert 'assets.open(AerixClientPackGenerator.CLIENT_BACKGROUND_ASSET)' in overlay
assert 'v6_aerix_dark_landscape_2026' in pack
assert 'append(PACK_SCHEMA_REV)' in pack
assert 'if (AerixClientStore.packCustomMainMenu)' in pack
assert 'for (faceIdx in 0..5)' in pack
assert 'panorama_$idx.png' in pack
assert 'instance.isAerixClient && mAerixClientOverlay != null' in activity
assert 'resetSessionState(instance.versionId)' in overlay
assert 'ClientBootLabels.fromProfile(profileId)' in bridge
assert 'ClientBootLabels.fromLoadingLog(line)' in bridge
assert 'ClientBootLabels.loadingMods(sessionMinecraftVersion)' in bridge
assert 'ClientBootLabels.textureAtlases(sessionMinecraftVersion)' in bridge
assert 'ClientBootLabels.header(AerixClientInputBridge.sessionMinecraftVersion)' in overlay
assert 'Loading Fabric 1.16.5' not in bridge
assert 'Stitching 1.16.5' not in bridge
print('Client background and version wiring smoke test passed')
