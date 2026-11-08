/*
 * Aerix Blocky Shell - the character's emote vocabulary and its reactions to the user.
 * The actual joint animation lives in assets/skinview/skinview.js; this is the launcher-side
 * vocabulary, labels, colours and "react to what the user is doing" mapping.
 */
package net.kdt.pojavlaunch.aerix.ui.blocky

import net.kdt.pojavlaunch.R

enum class BlockyEmote(
    val id: String,
    val label: Int,
    val icon: Int,
    val theme: BlockyTheme,
    val durationMs: Int
) {
    WAVE("wave", R.string.aerix_blocky_emote_wave, R.drawable.aerix_blocky_ic_wave,
        BlockyTheme.CYAN, 2600),
    DANCE("dance", R.string.aerix_blocky_emote_dance, R.drawable.aerix_blocky_ic_dance,
        BlockyTheme.PINK, 3600),
    JUMP("jump", R.string.aerix_blocky_emote_jump, R.drawable.aerix_blocky_ic_jump,
        BlockyTheme.LIME, 2600),
    ACTION("action", R.string.aerix_blocky_emote_action, R.drawable.aerix_blocky_ic_action,
        BlockyTheme.ORANGE, 2800),
    CHEER("cheer", R.string.aerix_blocky_emote_cheer, R.drawable.aerix_blocky_ic_guide,
        BlockyTheme.GOLD, 2800),
    IDLE("idle", R.string.aerix_blocky_emote_idle, R.drawable.aerix_blocky_ic_sun,
        BlockyTheme.SLATE, 0);

    companion object {
        fun fromId(id: String): BlockyEmote = values().firstOrNull { it.id == id } ?: IDLE

        /** The character does emotes on its own; idle is only the resting state. */
        fun random(): BlockyEmote = values().filter { it != IDLE }.random()
    }
}

/** Every user action has a matching reaction, so the character is never passive. */
object BlockyReactions {
    fun of(action: String): BlockyEmote = when (action) {
        "play", "launch" -> BlockyEmote.CHEER
        "instance", "install", "menu", "settings", "library", "discover" -> BlockyEmote.ACTION
        "wardrobe", "friends", "invite", "help", "guide", "account", "classic", "socials" -> BlockyEmote.WAVE
        "chest", "reward" -> BlockyEmote.DANCE
        "rotate", "emotes" -> BlockyEmote.JUMP
        else -> BlockyEmote.WAVE
    }
}
