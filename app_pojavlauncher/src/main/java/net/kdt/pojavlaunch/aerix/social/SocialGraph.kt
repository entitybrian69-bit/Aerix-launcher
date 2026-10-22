/*
 * Aerix Launcher Social layer.
 *
 * Process-wide wiring: one DataStore-backed store and one repository shared by every
 * ViewModel, so the launcher shell and the in-game overlay observe the same state.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social

import android.content.Context
import net.kdt.pojavlaunch.aerix.social.data.DataStoreSocialContentStore
import net.kdt.pojavlaunch.aerix.social.data.DataStoreSocialStore
import net.kdt.pojavlaunch.aerix.social.data.InMemorySocialStore
import net.kdt.pojavlaunch.aerix.social.data.LocalSocialRepository
import net.kdt.pojavlaunch.aerix.social.data.SocialRepository
import net.kdt.pojavlaunch.aerix.social.data.SocialStore
import net.kdt.pojavlaunch.aerix.social.notify.SocialNotifications

object SocialGraph {
    @Volatile
    private var storeInstance: SocialStore? = null

    @Volatile
    private var appContext: Context? = null

    /**
     * The on-device repository. With a context it persists everything it is given; without one
     * (unit tests, previews) it falls back to memory only.
     */
    val repository: SocialRepository by lazy {
        val context = appContext
        LocalSocialRepository(
            contentStore = context?.let { DataStoreSocialContentStore(it) },
            onIncoming = { author, text ->
                context?.let { ctx ->
                    SocialNotifications.notifyMessage(
                        ctx,
                        settings = store.snapshot().notifications,
                        author = author,
                        text = text
                    )
                }
            }
        )
    }

    val store: SocialStore
        get() = storeInstance ?: InMemorySocialStore().also { storeInstance = it }

    fun init(context: Context) {
        val application = context.applicationContext
        appContext = application
        if (storeInstance == null) {
            storeInstance = DataStoreSocialStore(application)
        }
        SocialNotifications.ensureChannel(application)
    }
}
