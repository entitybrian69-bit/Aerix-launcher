"""Check the Rich Presence image points to the shipped logo in both link modes."""
import hashlib
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
base = root / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/discord'
src = (base / 'AerixDiscordPresence.kt').read_text()
url = re.search(r'const val AERIX_LOGO_URL\s*=\s*"([^"]+)"', src).group(1)
assert url == ('https://raw.githubusercontent.com/entitybrian69-bit/Aerix-launcher/'
               '4a3b1c37a8d4838eaa5cb8933610db7fb6efdec5/assets/aerix-logo.png')
logo = (root / 'assets/aerix-logo.png').read_bytes()
assert hashlib.sha256(logo).hexdigest() == '19b955b6c9842c425d8778ca577b2befe991d2517fe929bfb0184c2d6197a03d'
assert logo == (root / 'app_pojavlauncher/src/main/res/drawable-nodpi/aerix_logo.png').read_bytes()
activity = src.split('private fun sendActivity()', 1)[1].split('private fun send(frame:', 1)[0]
assets, links = activity.split('val assets = JSONObject()', 1)[1].split('if (clickable) {', 1)
assert 'assets.put("large_image", AERIX_LOGO_URL)' in assets
assert 'assets.put("small_image", AERIX_LOGO_URL)' in assets
assert 'clickable && inGameMode && isValidHttpAssetUrl(it)' in assets
assert 'activity.put("assets", assets)' in links
preview = (base / 'AerixDiscordPresenceCard.kt').read_text().split('fun DiscordRichPresenceLivePreview', 1)[1]
assert 'painterResource(R.drawable.aerix_logo)' in preview
print('Discord presence logo wiring passed')
