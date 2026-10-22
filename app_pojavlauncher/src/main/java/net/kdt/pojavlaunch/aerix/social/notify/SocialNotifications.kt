/*
 * Aerix Launcher Social layer.
 *
 * Real Android notifications for incoming Social messages, respecting the per-category switches
 * the player saved and the message-preview preference.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.NotificationSettings

object SocialNotifications {

    const val CHANNEL_ID = "aerix_social"
    private const val CHANNEL_NAME = "Aerix Social"
    private var nextId = 4200

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Friend requests, messages and party invitations"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posts a message alert unless the player turned that category off. */
    fun notifyMessage(
        context: Context,
        settings: NotificationSettings,
        author: String,
        text: String
    ) {
        if (!settings.privateMessages || !settings.androidNotifications) return
        if (!canPost(context)) return
        ensureChannel(context)
        val body = if (settings.messagePreview && text.isNotBlank()) text else "New message"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.aerix_social_ic_chat)
            .setContentTitle(author)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextId++, notification) }
    }
}
