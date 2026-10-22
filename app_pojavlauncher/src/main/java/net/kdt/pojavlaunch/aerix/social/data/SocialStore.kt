/*
 * Aerix Launcher Social layer.
 *
 * Persistence contract. The launcher uses DataStore; unit tests use the in-memory
 * implementation at the bottom of this file, so ViewModel behaviour can be verified
 * without Android.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

interface SocialStore {
    val data: Flow<SocialPersisted>

    /** Applies [transform] to the persisted snapshot and writes the result. */
    suspend fun update(transform: (SocialPersisted) -> SocialPersisted)

    suspend fun snapshot(): SocialPersisted = data.first()
}

/** Defaults kept in memory so a ViewModel can run before DataStore is initialised. */
class InMemorySocialStore(initial: SocialPersisted = SocialPersisted()) : SocialStore {
    private val state = MutableStateFlow(initial)
    override val data: Flow<SocialPersisted> = state

    override suspend fun update(transform: (SocialPersisted) -> SocialPersisted) {
        state.update(transform)
    }
}
