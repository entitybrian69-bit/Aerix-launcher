#!/usr/bin/env python3
"""Regression checks for Discord Rich Presence post-exit grace period."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

presence = (SRC / "aerix/discord/AerixDiscordPresence.kt").read_text()
receiver = (SRC / "aerix/social/SocialNotificationReceiver.kt").read_text()
card = (SRC / "aerix/discord/AerixDiscordPresenceCard.kt").read_text()


def block_of(source: str, signature: str) -> str:
    start = source.index(signature)
    open_brace = source.index("{", start)
    depth = 0
    for idx in range(open_brace, len(source)):
        ch = source[idx]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace:idx + 1]
    raise AssertionError(f"Unbalanced braces for {signature!r}")


# 1. Grace period constants are configured for a few minutes (5 minutes).
assert "internal const val LAUNCHER_EXIT_GRACE_MS = 5 * 60_000L" in presence
assert "internal const val GAME_EXIT_GRACE_MS = 5 * 60_000L" in presence

# 2. Leaving the launcher keeps owner="launcher" and schedules the grace clear instead of wiping immediately.
on_stopped = block_of(presence, "fun onLauncherStopped(context: Context?)")
assert 'owner = "launcher"' in on_stopped
assert "scheduleGraceClear(app, expectedOwner = \"launcher\", delayMs = LAUNCHER_EXIT_GRACE_MS)" in on_stopped
assert "AlarmManager" not in "\n".join(l for l in on_stopped.splitlines() if not l.strip().startswith("//"))
assert "ELAPSED_REALTIME_WAKEUP" not in presence

# 3. Late Compose section updates do not mutate the card while backgrounded, and returning resets exitedAtElapsedMs.
in_launcher = block_of(presence, "fun inLauncher(section: String?)")
assert "disk.exitedAtElapsedMs > 0L" in in_launcher
on_started = block_of(presence, "fun onLauncherStarted(context: Context?)")
assert "invalidatePendingClear()" in on_started
assert "exitedAtElapsedMs = 0L" in on_started

# 4. Game backgrounding keeps presence for the grace window while explicit exit clears immediately.
on_game_stopped = block_of(presence, "fun onGameStopped(context: Context?)")
assert "it.isFinishing || it.isDestroyed" in on_game_stopped
on_game_stopped_internal = block_of(presence, "private fun onGameStoppedInternal(context: Context?, immediate: Boolean)")
assert "putExtra(EXTRA_DELAY_MS, GAME_EXIT_GRACE_MS)" in on_game_stopped_internal
assert "putExtra(EXTRA_DELAY_MS, 0L)" in on_game_stopped_internal
on_game_destroyed = block_of(presence, "fun onGameDestroyed(context: Context?)")
assert "onGameStoppedInternal(context, immediate = true)" in on_game_destroyed
on_process_exiting = block_of(presence, "fun onProcessExiting(context: Context? = null)")
assert "onGameStoppedInternal(app, immediate = true)" in on_process_exiting
on_swiped = block_of(presence, "fun onAppSwipedFromRecents(")
assert "cancelGraceClear()" in on_swiped

# 5. SocialNotificationReceiver must not hold goAsync() across a multi-minute delay.
sched_branch = block_of(receiver, "AerixDiscordPresence.ACTION_DISCORD_SCHEDULE_CLEAR ->")
assert "EXTRA_DELAY_MS" in sched_branch
assert "if (delayMs > 0L)" in sched_branch
assert sched_branch.index("if (delayMs > 0L)") < sched_branch.index("val pending = goAsync()")

# 6. Dead Binder cleanup on service disconnect / reattach.
on_disconnected = block_of(presence, "override fun onServiceDisconnected(name: ComponentName?)")
assert "service = null" in on_disconnected
assert "isBinderAlive == true" in presence
assert "staying visible for a few minutes" in card

print("discord_presence_grace_period_smoke: all checks passed")
