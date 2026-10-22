/*
 * Aerix Launcher Social layer.
 *
 * Storage for Aerix accounts. One small preferences file holds the account rows as JSON; it is
 * written with DataStore, so a crash mid-write cannot leave a half-written file behind.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.kdt.pojavlaunch.aerix.social.auth.AerixAccount
import net.kdt.pojavlaunch.aerix.social.auth.AerixCredentials
import org.json.JSONArray
import org.json.JSONObject

/** The Aerix account book: who exists on this device, and who asked to be deleted. */
interface AerixAccountsStore {
    val accounts: Flow<List<AerixAccount>>

    suspend fun snapshot(): List<AerixAccount>

    /** Matches username *or* email, ignoring case, including accounts marked deleted. */
    suspend fun find(identifier: String): AerixAccount?

    /** False when the username or email is already taken. */
    suspend fun insert(account: AerixAccount): Boolean

    suspend fun update(account: AerixAccount)

    suspend fun softDelete(id: String, at: Long = System.currentTimeMillis())

    suspend fun restore(id: String)
}

private val Context.aerixAccountsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "aerix_accounts")

class DataStoreAerixAccountsStore(private val store: DataStore<Preferences>) : AerixAccountsStore {

    constructor(context: Context) : this(context.applicationContext.aerixAccountsDataStore)

    override val accounts: Flow<List<AerixAccount>> =
        store.data.map { read(it) }

    override suspend fun snapshot(): List<AerixAccount> = accounts.first()

    override suspend fun find(identifier: String): AerixAccount? {
        val wanted = AerixCredentials.normalise(identifier)
        if (wanted.isEmpty()) return null
        return snapshot().firstOrNull {
            AerixCredentials.normalise(it.username) == wanted ||
                AerixCredentials.normalise(it.email) == wanted
        }
    }

    override suspend fun insert(account: AerixAccount): Boolean {
        var added = false
        store.edit { prefs ->
            val current = read(prefs)
            val taken = current.any {
                AerixCredentials.normalise(it.username) == AerixCredentials.normalise(account.username) ||
                    AerixCredentials.normalise(it.email) == AerixCredentials.normalise(account.email)
            }
            if (!taken) {
                prefs[Keys.ACCOUNTS] = encode(current + account)
                added = true
            }
        }
        return added
    }

    override suspend fun update(account: AerixAccount) {
        store.edit { prefs ->
            val next = read(prefs).map { if (it.id == account.id) account else it }
            prefs[Keys.ACCOUNTS] = encode(next)
        }
    }

    override suspend fun softDelete(id: String, at: Long) {
        store.edit { prefs ->
            val next = read(prefs).map {
                if (it.id == id) it.copy(deletedAt = at) else it
            }
            prefs[Keys.ACCOUNTS] = encode(next)
        }
    }

    override suspend fun restore(id: String) {
        store.edit { prefs ->
            val next = read(prefs).map { if (it.id == id) it.copy(deletedAt = 0L) else it }
            prefs[Keys.ACCOUNTS] = encode(next)
        }
    }

    private fun read(prefs: Preferences): List<AerixAccount> =
        decode(prefs[Keys.ACCOUNTS].orEmpty())

    private object Keys {
        val ACCOUNTS = stringPreferencesKey("aerix_accounts_json")
    }

    private fun encode(accounts: List<AerixAccount>): String {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("id", account.id)
                    put("username", account.username)
                    put("email", account.email)
                    put("salt", account.salt)
                    put("hash", account.hash)
                    put("algorithm", account.algorithm)
                    put("iterations", account.iterations)
                    put("createdAt", account.createdAt)
                    put("deletedAt", account.deletedAt)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<AerixAccount> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                AerixAccount(
                    id = row.optString("id"),
                    username = row.optString("username"),
                    email = row.optString("email"),
                    salt = row.optString("salt"),
                    hash = row.optString("hash"),
                    algorithm = row.optString("algorithm").ifBlank {
                        AerixCredentials.preferredAlgorithm()
                    },
                    iterations = row.optInt("iterations", AerixCredentials.preferredIterations()),
                    createdAt = row.optLong("createdAt"),
                    deletedAt = row.optLong("deletedAt")
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** Used by unit tests and previews, so nothing needs a Context to exercise the flow. */
class InMemoryAerixAccountsStore(
    initial: List<AerixAccount> = emptyList()
) : AerixAccountsStore {

    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)

    override val accounts: Flow<List<AerixAccount>> = state

    override suspend fun snapshot(): List<AerixAccount> = state.value

    override suspend fun find(identifier: String): AerixAccount? {
        val wanted = AerixCredentials.normalise(identifier)
        return state.value.firstOrNull {
            AerixCredentials.normalise(it.username) == wanted ||
                AerixCredentials.normalise(it.email) == wanted
        }
    }

    override suspend fun insert(account: AerixAccount): Boolean {
        val taken = state.value.any {
            AerixCredentials.normalise(it.username) == AerixCredentials.normalise(account.username) ||
                AerixCredentials.normalise(it.email) == AerixCredentials.normalise(account.email)
        }
        if (taken) return false
        state.value = state.value + account
        return true
    }

    override suspend fun update(account: AerixAccount) {
        state.value = state.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun softDelete(id: String, at: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }

    override suspend fun restore(id: String) {
        state.value = state.value.map { if (it.id == id) it.copy(deletedAt = 0L) else it }
    }
}
