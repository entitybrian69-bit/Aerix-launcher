package net.kdt.pojavlaunch.aerix.social

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence

/**
 * Handles explicit notification actions (`Direct Reply` and `Mark as Read`) and Discord Rich
 * Presence post-exit clear alarms without ever auto-starting background services.
 */
class SocialNotificationReceiver : BroadcastReceiver() {
    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val action = intent?.action ?: return

        when (action) {
            SocialNotificationHelper.ACTION_DIRECT_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence(SocialNotificationHelper.KEY_TEXT_REPLY)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                val peerId = intent.getStringExtra(SocialNotificationHelper.EXTRA_PEER_ID)
                val partyId = intent.getStringExtra(SocialNotificationHelper.EXTRA_PARTY_ID)
                val senderName = intent.getStringExtra(SocialNotificationHelper.EXTRA_SENDER_NAME) ?: "Friend"
                val partyTitle = intent.getStringExtra(SocialNotificationHelper.EXTRA_PARTY_TITLE)

                if (replyText.isEmpty()) return

                val pending = goAsync()
                receiverScope.launch {
                    try {
                        SocialBackend.initPassive(app)
                        val sent = SocialBackend.sendDirectReplyFromNotification(
                            peerId = peerId,
                            partyId = partyId,
                            replyText = replyText
                        )
                        SocialNotificationHelper.onDirectReplySent(
                            context = app,
                            peerId = peerId,
                            partyId = partyId,
                            partyTitle = partyTitle,
                            peerDisplayName = senderName,
                            replyText = if (sent) replyText else "$replyText (queued)"
                        )
                        SocialSoundEffects.playMessageSent()
                    } finally {
                        pending.finish()
                    }
                }
            }

            SocialNotificationHelper.ACTION_MARK_READ -> {
                val peerId = intent.getStringExtra(SocialNotificationHelper.EXTRA_PEER_ID)
                val partyId = intent.getStringExtra(SocialNotificationHelper.EXTRA_PARTY_ID)
                SocialNotificationHelper.clearConversationNotification(app, peerId = peerId, partyId = partyId)

                val pending = goAsync()
                receiverScope.launch {
                    try {
                        SocialBackend.initPassive(app)
                        if (!peerId.isNullOrBlank()) {
                            SocialBackend.markRead(peerId)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }

            AerixDiscordPresence.ACTION_DISCORD_GAME_STARTED -> {
                val instanceName = intent.getStringExtra(AerixDiscordPresence.EXTRA_INSTANCE_NAME)
                val mcVersion = intent.getStringExtra(AerixDiscordPresence.EXTRA_MC_VERSION)
                val modpackIconUrl = intent.getStringExtra(AerixDiscordPresence.EXTRA_MODPACK_ICON_URL)
                val startedAt = intent.getLongExtra(AerixDiscordPresence.EXTRA_STARTED_AT, 0L)
                AerixDiscordPresence.handleGameStartedBroadcast(
                    app,
                    instanceName,
                    mcVersion,
                    startedAt,
                    modpackIconUrl
                )
            }

            AerixDiscordPresence.ACTION_DISCORD_SCHEDULE_CLEAR -> {
                val delayMs = intent.getLongExtra(
                    AerixDiscordPresence.EXTRA_DELAY_MS,
                    AerixDiscordPresence.POST_EXIT_TIMEOUT_MS
                )
                AerixDiscordPresence.handleScheduleClearBroadcast(app, delayMs)
            }

            AerixDiscordPresence.ACTION_DISCORD_CLEAR_NOW -> {
                val pending = goAsync()
                AerixDiscordPresence.performScheduledClear(app) {
                    runCatching { pending.finish() }
                }
            }
        }
    }
}
