/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The shape of a social profile, the rank vocabulary and the country list the profile form uses.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.social

import java.util.Locale

/**
 * Official Aerix Launcher staff/founder role tags bound strictly to verified account emails.
 *
 * - `entitybrian69@gmail.com` -> `(OWNER)` in crimson red beside any name they choose.
 * - `kushaladhikari982648@gmail.com` -> `(CO-OWNER)` in sky blue beside any name they choose.
 */
enum class SocialRole(
    val key: String,
    val tag: String,
    val email: String,
    val colorArgb: Long
) {
    OWNER(
        key = "OWNER",
        tag = "(OWNER)",
        email = "entitybrian69@gmail.com",
        colorArgb = 0xFFEF233CL // Crimson red
    ),
    CO_OWNER(
        key = "CO-OWNER",
        tag = "(CO-OWNER)",
        email = "kushaladhikari982648@gmail.com",
        colorArgb = 0xFF38BDF8L // Sky blue
    );

    companion object {
        private val tagCleanupRegex = Regex("""(?i)\s*\((?:CO[-_\s]?OWNER|OWNER)\)\s*""")

        fun fromEmail(email: String?): SocialRole? {
            val clean = email?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
            return values().firstOrNull { it.email == clean }
        }

        fun fromBadgeKey(raw: String?): SocialRole? {
            val clean = raw?.trim()?.uppercase(Locale.US)?.removeSurrounding("(", ")") ?: return null
            return when (clean) {
                "OWNER" -> OWNER
                "CO-OWNER", "CO_OWNER", "CO OWNER" -> CO_OWNER
                else -> null
            }
        }

        /** Removes any `(OWNER)` or `(CO-OWNER)` tokens from a display string. */
        fun stripRoleTags(rawName: String?): String? {
            if (rawName == null) return null
            val cleaned = rawName.replace(tagCleanupRegex, " ").trim().replace(Regex("""\s{2,}"""), " ")
            return cleaned.takeIf { it.isNotEmpty() }
        }

        /**
         * Extracts `(cleanBaseName, SocialRole?)` from a profile row, wire string, or verified email.
         * Checking `CO-OWNER` before `OWNER` ensures clean disambiguation.
         */
        fun extract(
            rawName: String?,
            rawBadge: String? = null,
            knownEmail: String? = null
        ): Pair<String?, SocialRole?> {
            val detected = fromEmail(knownEmail)
                ?: fromBadgeKey(rawBadge)
                ?: rawName?.let { text ->
                    val upper = text.uppercase(Locale.US)
                    when {
                        upper.contains("(CO-OWNER)") || upper.contains("(CO_OWNER)") -> CO_OWNER
                        upper.contains("(OWNER)") -> OWNER
                        else -> null
                    }
                }
            return stripRoleTags(rawName) to detected
        }

        /**
         * Produces the database wire `name` / `display_name` value:
         * - Non-staff accounts always have any manually typed `(OWNER)` / `(CO-OWNER)` stripped.
         * - Verified `OWNER` / `CO_OWNER` accounts automatically carry their tag beside any chosen name.
         */
        fun encodeTaggedName(
            baseName: String?,
            role: SocialRole?,
            fallbackBase: String = "Player"
        ): String? {
            val cleanBase = stripRoleTags(baseName)?.take(40)
            if (role == null) return cleanBase
            val effectiveBase = cleanBase
                ?: stripRoleTags(fallbackBase)?.take(40)
                ?: "Player"
            return "$effectiveBase ${role.tag}"
        }
    }
}

/** Player availability mode selectable from the Change Status sheet/panel. */
enum class SocialPresenceMode(
    val key: String,
    val label: String,
    val description: String,
    val dotArgb: Long
) {
    ONLINE("online", "Online", "Visible to friends and ready to play", 0xFF73E5B0L),
    IDLE("idle", "Idle", "Away or taking a short break", 0xFFFFC777L),
    DND("dnd", "Do Not Disturb", "Busy — mutes join nudges", 0xFFFF7189L),
    INVISIBLE("invisible", "Appear Offline", "Browse and play while appearing offline", 0xFF7D909DL);

    companion object {
        fun fromKey(raw: String?): SocialPresenceMode? {
            val clean = raw?.trim()?.lowercase(Locale.US) ?: return null
            return when (clean) {
                "online" -> ONLINE
                "idle", "away" -> IDLE
                "dnd", "do_not_disturb", "busy" -> DND
                "invisible", "offline", "appear_offline" -> INVISIBLE
                else -> null
            }
        }
    }
}

data class SocialStatusPreset(
    val label: String,
    val statusText: String,
    val mode: SocialPresenceMode = SocialPresenceMode.ONLINE,
    val lookingToPlay: Boolean? = null
)

/**
 * Encodes and parses availability mode + custom status text across `status_text` / `status_line`
 * so status changes work immediately for all players even before optional SQL migrations run.
 */
object SocialPresenceCodec {
    data class ParsedPresence(
        val mode: SocialPresenceMode,
        val customText: String?
    )

    val presets: List<SocialStatusPreset> = listOf(
        SocialStatusPreset("Down to play", "Down to play", SocialPresenceMode.ONLINE, lookingToPlay = true),
        SocialStatusPreset("Survival grind", "Survival grind", SocialPresenceMode.ONLINE),
        SocialStatusPreset("PvP / Minigames", "PvP / Minigames", SocialPresenceMode.ONLINE),
        SocialStatusPreset("Modding instances", "Modding instances", SocialPresenceMode.ONLINE),
        SocialStatusPreset("AFK / BRB", "AFK / BRB", SocialPresenceMode.IDLE),
        SocialStatusPreset("Studying / Busy", "Studying / Busy", SocialPresenceMode.DND)
    )

    private val dndPrefix = Regex("""^(?i)\[(?:dnd|do not disturb)\]\s*""")
    private val idlePrefix = Regex("""^(?i)\[(?:idle|away)\]\s*""")
    private val invisiblePrefix = Regex("""^(?i)\[(?:offline|invisible)\]\s*""")

    fun stripModePrefix(raw: String?): String? {
        if (raw == null) return null
        val stripped = raw
            .replaceFirst(dndPrefix, "")
            .replaceFirst(idlePrefix, "")
            .replaceFirst(invisiblePrefix, "")
            .trim()
        return stripped.takeIf { it.isNotEmpty() }
    }

    fun encodeForWire(mode: SocialPresenceMode, customText: String?): String? {
        val clean = stripModePrefix(customText)?.take(52)
        return when (mode) {
            SocialPresenceMode.INVISIBLE -> null
            SocialPresenceMode.DND -> if (clean == null) "[DND]" else "[DND] $clean".take(60)
            SocialPresenceMode.IDLE -> if (clean == null) "[Idle]" else "[Idle] $clean".take(60)
            SocialPresenceMode.ONLINE -> stripModePrefix(customText)?.take(60)
        }
    }

    fun parse(rawStatus: String?, explicitMode: String? = null): ParsedPresence {
        val trimmed = rawStatus?.trim().orEmpty()
        val fromPrefix = when {
            dndPrefix.containsMatchIn(trimmed) -> SocialPresenceMode.DND
            idlePrefix.containsMatchIn(trimmed) -> SocialPresenceMode.IDLE
            invisiblePrefix.containsMatchIn(trimmed) -> SocialPresenceMode.INVISIBLE
            else -> null
        }
        val mode = SocialPresenceMode.fromKey(explicitMode)
            ?: fromPrefix
            ?: SocialPresenceMode.ONLINE
        return ParsedPresence(mode = mode, customText = stripModePrefix(trimmed))
    }
}

/** Everything the social pages show about one person. */
data class SocialProfile(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val mcUsername: String? = null,
    val country: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val statusText: String? = null,
    val lookingToPlay: Boolean = false,
    val onboarded: Boolean = false,
    val usernameChangedAtMs: Long = 0L,
    val lastSeenMs: Long = 0L,
    val followers: Int = 0,
    val following: Int = 0,
    val friends: Int = 0,
    val messages: Int = 0,
    val ranks: List<String> = emptyList(),
    val isMe: Boolean = false,
    val iFollow: Boolean = false,
    val role: SocialRole? = null,
    val presenceMode: SocialPresenceMode = SocialPresenceMode.ONLINE
) {
    val online: Boolean
        get() = presenceMode != SocialPresenceMode.INVISIBLE &&
            lastSeenMs > 0 &&
            System.currentTimeMillis() - lastSeenMs < 2 * 60 * 1000L

    /** Back in a minute or two, then quiet: shown as a hollow dot rather than green. */
    val away: Boolean
        get() = presenceMode != SocialPresenceMode.INVISIBLE &&
            lastSeenMs > 0 &&
            System.currentTimeMillis() - lastSeenMs in (2 * 60 * 1000L)..(5 * 60 * 1000L)

    val displayName: String
        get() = SocialRole.stripRoleTags(name)?.takeIf { it.isNotBlank() }
            ?: username
            ?: mcUsername
            ?: "Player"

    val handle: String
        get() = if (username.isNullOrBlank()) "" else "@$username"

    /** Days left before the username can change again; 0 means it can change now. */
    fun daysUntilUsernameChange(): Long {
        if (usernameChangedAtMs <= 0L) return 0L
        val unlockAt = usernameChangedAtMs + 30L * 24L * 60L * 60L * 1000L
        val left = unlockAt - System.currentTimeMillis()
        return if (left <= 0L) 0L else (left / (24L * 60L * 60L * 1000L)) + 1L
    }
}

/**
 * The rank vocabulary. The database decides which of these someone has (see `profile_card`), the
 * app only supplies the wording and the colour.
 */
object SocialRanks {
    data class Rank(val key: String, val label: String, val tint: Long)

    private val known = listOf(
        Rank("founder", "Founder", 0xFFFFC777),
        Rank("early", "Early Adopter", 0xFF91E9FF),
        Rank("veteran", "Veteran", 0xFFB7A9FF),
        Rank("complete", "Profile Complete", 0xFF73E5B0),
        Rank("chatterbox", "Chatterbox", 0xFF8DEFE0),
        Rank("butterfly", "Social Butterfly", 0xFFF3A9D9),
        Rank("trendsetter", "Trendsetter", 0xFF7A71F5),
        Rank("ready", "Looking to play", 0xFF37DCEC),
        Rank("partyanimal", "Party Animal", 0xFFFF8FA3),
        Rank("helped", "Helped Out", 0xFFC1B5FF)
    )

    fun of(key: String): Rank =
        known.firstOrNull { it.key == key }
            ?: Rank(key, key.replaceFirstChar { it.uppercase() }, 0xFFA8B9C5)

    fun all(): List<Rank> = known
}

/** A short, phone-friendly country list for the profile form. Free text is allowed too. */
object SocialCountries {
    val all: List<String> = listOf(
        "Afghanistan", "Albania", "Algeria", "Andorra", "Angola", "Argentina", "Armenia", "Australia",
        "Austria", "Azerbaijan", "Bahamas", "Bahrain", "Bangladesh", "Barbados", "Belarus", "Belgium",
        "Belize", "Benin", "Bhutan", "Bolivia", "Bosnia and Herzegovina", "Botswana", "Brazil",
        "Brunei", "Bulgaria", "Burkina Faso", "Burundi", "Cambodia", "Cameroon", "Canada",
        "Cape Verde", "Central African Republic", "Chad", "Chile", "China", "Colombia", "Comoros",
        "Congo", "Costa Rica", "Croatia", "Cuba", "Cyprus", "Czechia", "Denmark", "Djibouti",
        "Dominica", "Dominican Republic", "Ecuador", "Egypt", "El Salvador", "Equatorial Guinea",
        "Eritrea", "Estonia", "Eswatini", "Ethiopia", "Fiji", "Finland", "France", "Gabon", "Gambia",
        "Georgia", "Germany", "Ghana", "Greece", "Grenada", "Guatemala", "Guinea", "Guyana", "Haiti",
        "Honduras", "Hong Kong", "Hungary", "Iceland", "India", "Indonesia", "Iran", "Iraq",
        "Ireland", "Israel", "Italy", "Ivory Coast", "Jamaica", "Japan", "Jordan", "Kazakhstan",
        "Kenya", "Kosovo", "Kuwait", "Kyrgyzstan", "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia",
        "Libya", "Liechtenstein", "Lithuania", "Luxembourg", "Macau", "Madagascar", "Malawi",
        "Malaysia", "Maldives", "Mali", "Malta", "Mauritania", "Mauritius", "Mexico", "Moldova",
        "Monaco", "Mongolia", "Montenegro", "Morocco", "Mozambique", "Myanmar", "Namibia", "Nepal",
        "Netherlands", "New Zealand", "Nicaragua", "Niger", "Nigeria", "North Macedonia", "Norway",
        "Oman", "Pakistan", "Palestine", "Panama", "Papua New Guinea", "Paraguay", "Peru",
        "Philippines", "Poland", "Portugal", "Puerto Rico", "Qatar", "Romania", "Russia", "Rwanda",
        "Saudi Arabia", "Senegal", "Serbia", "Seychelles", "Sierra Leone", "Singapore", "Slovakia",
        "Slovenia", "Somalia", "South Africa", "South Korea", "Spain", "Sri Lanka", "Sudan",
        "Suriname", "Sweden", "Switzerland", "Syria", "Taiwan", "Tajikistan", "Tanzania", "Thailand",
        "Togo", "Trinidad and Tobago", "Tunisia", "Turkey", "Turkmenistan", "Uganda", "Ukraine",
        "United Arab Emirates", "United Kingdom", "United States", "Uruguay", "Uzbekistan",
        "Vanuatu", "Venezuela", "Vietnam", "Yemen", "Zambia", "Zimbabwe"
    )

    fun search(query: String, limit: Int = 6): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { it.contains(q, ignoreCase = true) }
            .sortedBy { if (it.startsWith(q, ignoreCase = true)) 0 else 1 }
            .take(limit)
    }
}
