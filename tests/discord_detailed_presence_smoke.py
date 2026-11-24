#!/usr/bin/env python3
"""Regression checks for super-detailed Discord Rich Presence (server logo, modpack, activity)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

presence = (SRC / "aerix/discord/AerixDiscordPresence.kt").read_text()
monitor = (SRC / "aerix/discord/GameDiscordPresenceMonitor.kt").read_text()
receiver = (SRC / "aerix/social/SocialNotificationReceiver.kt").read_text()
game = (SRC / "game/GameActivity.java").read_text()
manifest = (ROOT / "app_pojavlauncher/src/main/AndroidManifest.xml").read_text()
card = (SRC / "aerix/discord/AerixDiscordPresenceCard.kt").read_text()

# 1. Server logo, modpack logo, and activity formatting exist in AerixDiscordPresence.
assert "ACTION_DISCORD_GAME_UPDATE" in presence
assert "formatInGamePresence(" in presence
assert "validServerIcon" in presence and "validPackIcon" in presence
assert "https://mcsrvstat.us/server/" in presence
assert "onGameDestroyed(" in presence

# 2. Live monitor extracts modpack, loader, mod count, servers.dat names, server favicon & latest.log activity.
assert "https://api.mcsrvstat.us/icon/" in monitor
assert "readServerListNames(" in monitor
assert "isLocalOrLanAddress(" in monitor
assert "LogActivityTracker" in monitor
assert "RecordingState.isActive()" in monitor

# 3. Broadcast wiring between :game and :launcher.
assert "ACTION_DISCORD_GAME_UPDATE" in receiver
assert "handleGameBroadcast(" in receiver
assert "ACTION_DISCORD_GAME_UPDATE" in manifest
assert "AerixDiscordPresence.onGameStarted(\n                    this,\n                    instance," in game
assert "AerixDiscordPresence.onGameDestroyed(this)" in game
assert "Server & modpack art" in card

print("Detailed Discord Rich Presence smoke checks passed")
