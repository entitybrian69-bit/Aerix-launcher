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
import net.kdt.pojavlaunch.aerix.social.data.DataStoreSocialStore
import net.kdt.pojavlaunch.aerix.social.data.InMemorySocialStore
import net.kdt.pojavlaunch.aerix.social.data.MockSocialRepository
import net.kdt.pojavlaunch.aerix.social.data.SocialRepository
import net.kdt.pojavlaunch.aerix.social.data.SocialStore

object SocialGraph {
    @Volatile
    private var storeInstance: SocialStore? = null

    val repository: SocialRepository by lazy { MockSocialRepository() }

    val store: SocialStore
        get() = storeInstance ?: InMemorySocialStore().also { storeInstance = it }

    fun init(context: Context) {
        if (storeInstance == null) {
            storeInstance = DataStoreSocialStore(context.applicationContext)
        }
    }
}
