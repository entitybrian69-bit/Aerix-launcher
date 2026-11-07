#!/usr/bin/env python3
"""Check every offline-account creation entry uses the same username policy."""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix'
auth = (root / 'bridge/PojavAuthBridge.kt').read_text()
bridge = (root / 'bridge/PojavAccountBridge.kt').read_text()
screen = (root / 'ui/screens/AccountsScreen.kt').read_text()
offline = (root / 'ui/screens/OfflineAccountScreen.kt').read_text()
assert 'name.length in 3..16 && offlineNamePattern.matches(name)' in auth
assert 'Regex("^[a-zA-Z0-9_]*$")' in auth
listener = bridge.split('private val localLoginListener', 1)[1].split('/** Standard Minecraft', 1)[0]
assert 'isValidOfflineName(name)' in listener
assert listener.index('isValidOfflineName(name)') < listener.index('Accounts.create')
create = bridge.split('fun createOfflineAccount(username: String): Boolean', 1)[1].split('/**', 1)[0]
assert 'isValidOfflineName(trimmed)' in create and 'return false' in create
assert 'isValidOfflineName(offlineUsername.trim())' in screen
assert 'enabled = validOfflineName' in screen and 'aerix_account_name_rule' in screen
assert 'PojavAuthBridge.isValidOfflineName(name)' in offline
print('Offline username validation is wired through both forms and account creation')
