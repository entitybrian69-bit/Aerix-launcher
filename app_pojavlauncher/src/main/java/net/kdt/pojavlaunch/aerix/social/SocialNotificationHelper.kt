package net.kdt.pojavlaunch.aerix.social

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.R
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Builds rich Messenger-style system notifications (`MessagingStyle` + circular sender avatar +
 * inline `RemoteInput` Direct Reply + `Mark as Read` + deep-link tap into conversation) so players
 * see WHO messaged them and can reply directly from the notification shade even when the app is closed.
 */
object SocialNotificationHelper {
    const val CHANNEL_MESSAGES = "aerix_social_messages"
    const val CHANNEL_BG_SYNC = "aerix_social_bg_sync"

    const val NOTIFICATION_ID_BG_SERVICE = 74010
    private const val NOTIFICATION_BASE_CONVERSATION = 75000

    const val ACTION_DIRECT_REPLY = "net.kdt.pojavlaunch.aerix.ACTION_SOCIAL_DIRECT_REPLY"
    const val ACTION_MARK_READ = "net.kdt.pojavlaunch.aerix.ACTION_SOCIAL_MARK_READ"
    const val ACTION_RESTART_SYNC = "net.kdt.pojavlaunch.aerix.ACTION_SOCIAL_RESTART_SYNC"

    const val EXTRA_PEER_ID = "extra_social_peer_id"
    const val EXTRA_PARTY_ID = "extra_social_party_id"
    const val EXTRA_SENDER_NAME = "extra_social_sender_name"
    const val EXTRA_PARTY_TITLE = "extra_social_party_title"
    const val KEY_TEXT_REPLY = "aerix_social_direct_reply_text"

    private data class ThreadEntry(
        val senderId: String?,
        val senderLabel: String,
        val text: String,
        val timestampMs: Long,
        val isSelf: Boolean = false
    )

    private val conversationHistory = ConcurrentHashMap<String, ArrayDeque<ThreadEntry>>()
    private val avatarBitmapCache = object : LruCache<String, Bitmap>(24) {}

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Socials Direct & Party Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up alerts and quick inline replies when friends message you"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(msgChannel)

        val bgChannel = NotificationChannel(
            CHANNEL_BG_SYNC,
            "Socials Background Listener",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps Aerix Socials connected for instant message alerts while closed"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(bgChannel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nm.areNotificationsEnabled()
        } else {
            true
        }
    }

    /** Silent minimal ongoing notification required by Android foreground service rules. */
    fun buildBackgroundSyncNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentPi = PendingIntent.getActivity(context, 74011, openIntent, openFlags)

        return NotificationCompat.Builder(context, CHANNEL_BG_SYNC)
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle("Aerix Socials connected")
            .setContentText("Listening for friend & party messages")
            .setContentIntent(contentPi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Posts or updates a threaded Messenger-style notification with Direct Inline Reply (`RemoteInput`)
     * and `Mark as Read`. Safe to call on a background IO thread (fetches circular avatar if available).
     */
    fun postIncomingMessageNotification(
        context: Context,
        peerId: String?,
        partyId: String?,
        partyTitle: String?,
        senderId: String,
        senderDisplayName: String,
        senderRole: SocialRole?,
        senderAvatarUrl: String?,
        messageBody: String,
        timestampMs: Long
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val convKey = if (!partyId.isNullOrBlank()) "party:$partyId" else "dm:${peerId ?: senderId}"
        val taggedSender = if (senderRole != null && !senderDisplayName.contains(senderRole.tag, ignoreCase = true)) {
            "${SocialRole.stripRoleTags(senderDisplayName) ?: senderDisplayName} ${senderRole.tag}"
        } else {
            senderDisplayName
        }

        val cleanPreview = messageBody.trim().ifEmpty { "Sent an attachment" }
        val deque = conversationHistory.getOrPut(convKey) { ArrayDeque() }
        synchronized(deque) {
            // Avoid appending exact duplicate line
            val last = deque.lastOrNull()
            if (last == null || last.text != cleanPreview || abs(last.timestampMs - timestampMs) > 2_000L) {
                if (deque.size >= 6) deque.removeFirst()
                deque.addLast(
                    ThreadEntry(
                        senderId = senderId,
                        senderLabel = taggedSender,
                        text = cleanPreview,
                        timestampMs = timestampMs,
                        isSelf = false
                    )
                )
            }
        }

        val avatarBmp = loadOrGenerateCircularAvatar(
            avatarUrl = senderAvatarUrl,
            displayName = SocialRole.stripRoleTags(senderDisplayName) ?: senderDisplayName,
            ringColorArgb = senderRole?.colorArgb?.toInt() ?: 0xFF73E5B0.toInt()
        )

        renderConversationNotification(
            context = context,
            convKey = convKey,
            peerId = peerId ?: senderId,
            partyId = partyId,
            partyTitle = partyTitle,
            primarySenderLabel = taggedSender,
            avatarBmp = avatarBmp,
            silentUpdate = false
        )
    }

    /**
     * Called right after `RemoteInput` Direct Reply succeeds so Android's inline reply spinner
     * resolves cleanly and displays `"You: <reply>"` in the notification thread.
     */
    fun onDirectReplySent(
        context: Context,
        peerId: String?,
        partyId: String?,
        partyTitle: String?,
        peerDisplayName: String,
        replyText: String
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val convKey = if (!partyId.isNullOrBlank()) "party:$partyId" else "dm:${peerId.orEmpty()}"
        val deque = conversationHistory.getOrPut(convKey) { ArrayDeque() }
        synchronized(deque) {
            if (deque.size >= 6) deque.removeFirst()
            deque.addLast(
                ThreadEntry(
                    senderId = SocialBackend.myUserId(),
                    senderLabel = "You",
                    text = replyText,
                    timestampMs = System.currentTimeMillis(),
                    isSelf = true
                )
            )
        }
        val avatarBmp = loadOrGenerateCircularAvatar(
            avatarUrl = null,
            displayName = SocialRole.stripRoleTags(peerDisplayName) ?: peerDisplayName,
            ringColorArgb = 0xFF73E5B0.toInt()
        )
        renderConversationNotification(
            context = context,
            convKey = convKey,
            peerId = peerId,
            partyId = partyId,
            partyTitle = partyTitle,
            primarySenderLabel = peerDisplayName,
            avatarBmp = avatarBmp,
            silentUpdate = true
        )
    }

    fun clearConversationNotification(context: Context, peerId: String? = null, partyId: String? = null) {
        val convKey = when {
            !partyId.isNullOrBlank() -> "party:$partyId"
            !peerId.isNullOrBlank() -> "dm:$peerId"
            else -> return
        }
        conversationHistory.remove(convKey)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(notificationIdFor(convKey))
    }

    private fun renderConversationNotification(
        context: Context,
        convKey: String,
        peerId: String?,
        partyId: String?,
        partyTitle: String?,
        primarySenderLabel: String,
        avatarBmp: Bitmap,
        silentUpdate: Boolean
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = notificationIdFor(convKey)

        val mePerson = Person.Builder()
            .setName("You")
            .setKey(SocialBackend.myUserId() ?: "self")
            .build()

        val peerIcon = IconCompat.createWithBitmap(avatarBmp)
        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
        if (!partyId.isNullOrBlank()) {
            messagingStyle.setConversationTitle(partyTitle?.takeIf { it.isNotBlank() } ?: "Party Chat")
            messagingStyle.setGroupConversation(true)
        }

        val entries = conversationHistory[convKey]?.let { synchronized(it) { it.toList() } }.orEmpty()
        for (entry in entries) {
            val person = if (entry.isSelf) {
                null // null Person in MessagingStyle represents the local user ("You")
            } else {
                Person.Builder()
                    .setName(entry.senderLabel)
                    .setKey(entry.senderId ?: entry.senderLabel)
                    .setIcon(peerIcon)
                    .build()
            }
            messagingStyle.addMessage(entry.text, entry.timestampMs, person)
        }

        // 1. Tap notification -> open LauncherActivity straight into this DM or Party chat
        val deepLinkUri = if (!partyId.isNullOrBlank()) {
            Uri.parse("aerix-social://chat/party/${Uri.encode(partyId)}")
        } else {
            Uri.parse("aerix-social://chat/dm/${Uri.encode(peerId.orEmpty())}")
        }
        val openChatIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 1,
            openChatIntent,
            immutableFlags
        )

        // 2. Direct Inline Reply action (`RemoteInput` REQUIRES `FLAG_MUTABLE` on Android 12+)
        val replyLabelTarget = if (!partyTitle.isNullOrBlank()) partyTitle else (SocialRole.stripRoleTags(primarySenderLabel) ?: primarySenderLabel)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply to $replyLabelTarget…")
            .build()

        val replyIntent = Intent(context, SocialNotificationReceiver::class.java).apply {
            action = ACTION_DIRECT_REPLY
            putExtra(EXTRA_PEER_ID, peerId)
            putExtra(EXTRA_PARTY_ID, partyId)
            putExtra(EXTRA_SENDER_NAME, primarySenderLabel)
            putExtra(EXTRA_PARTY_TITLE, partyTitle)
        }
        val mutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            replyIntent,
            mutableFlags
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.notif_icon,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // 3. Mark as Read action
        val markReadIntent = Intent(context, SocialNotificationReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_PEER_ID, peerId)
            putExtra(EXTRA_PARTY_ID, partyId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 3,
            markReadIntent,
            immutableFlags
        )
        val markReadAction = NotificationCompat.Action.Builder(
            0,
            "Mark as Read",
            markReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val latestText = entries.lastOrNull()?.text ?: ""
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.notif_icon)
            .setLargeIcon(avatarBmp)
            .setContentTitle(if (!partyTitle.isNullOrBlank()) "$partyTitle · $primarySenderLabel" else primarySenderLabel)
            .setContentText(latestText)
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setGroup("aerix_social_conversations")
            .setColor(0xFF39C68D.toInt())
            .addAction(replyAction)
            .addAction(markReadAction)

        if (silentUpdate) {
            builder.setOnlyAlertOnce(true)
            builder.setSilent(true)
        }

        nm.notify(notifId, builder.build())
    }

    private fun notificationIdFor(convKey: String): Int =
        NOTIFICATION_BASE_CONVERSATION + (abs(convKey.hashCode()) % 9000) * 4

    private fun loadOrGenerateCircularAvatar(
        avatarUrl: String?,
        displayName: String,
        ringColorArgb: Int
    ): Bitmap {
        val cacheKey = "${avatarUrl.orEmpty()}|$displayName|$ringColorArgb"
        synchronized(avatarBitmapCache) {
            avatarBitmapCache.get(cacheKey)?.let { return it }
        }

        val sizePx = 128
        var downloaded: Bitmap? = null
        if (!avatarUrl.isNullOrBlank() && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
            try {
                val req = Request.Builder().url(avatarUrl).get().build()
                SocialRealtime.restClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            downloaded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val center = sizePx / 2f
        val radius = center - 3f

        if (downloaded != null) {
            val src = downloaded!!
            val cropDim = min(src.width, src.height).coerceAtLeast(1)
            val square = Bitmap.createBitmap(
                src,
                (src.width - cropDim) / 2,
                (src.height - cropDim) / 2,
                cropDim,
                cropDim
            )
            val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(center, center, radius - 2f, paint)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(22, 31, 38)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, radius - 2f, bgPaint)

            val initial = displayName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "A"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(initial, center, textY, textPaint)
        }

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColorArgb
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }
        canvas.drawOval(RectF(3f, 3f, sizePx - 3f, sizePx - 3f), ringPaint)

        synchronized(avatarBitmapCache) {
            avatarBitmapCache.put(cacheKey, out)
        }
        return out
    }
}
