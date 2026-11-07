#!/usr/bin/env python3
"""Game launch diagnostics must not print Minecraft bearer tokens."""
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java'
s = p.read_text()
assert 'launchArgs.toString()' not in s
assert 'Log.i("GameRunner", "Starting Minecraft")' in s
assert 'varArgMap.put("auth_access_token", effectiveToken)' in s
print('Game launch keeps the bearer token out of the argument log')
