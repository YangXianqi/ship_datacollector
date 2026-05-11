package com.shipyard.collector.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shipyard.collector.model.LoginSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_store")

class SessionStore(private val context: Context) {
    private object Keys {
        val userId = stringPreferencesKey("user_id")
        val phoneNumber = stringPreferencesKey("phone_number")
        val displayName = stringPreferencesKey("display_name")
        val authToken = stringPreferencesKey("auth_token")
        val canUpload = booleanPreferencesKey("can_upload")
        val canDeleteCache = booleanPreferencesKey("can_delete_cache")
        val offlineExpiry = longPreferencesKey("offline_expiry")
    }

    val session: Flow<LoginSession?> = context.sessionDataStore.data.map { prefs ->
        val userId = prefs[Keys.userId] ?: return@map null
        val phoneNumber = prefs[Keys.phoneNumber] ?: return@map null
        val displayName = prefs[Keys.displayName] ?: return@map null
        val authToken = prefs[Keys.authToken] ?: return@map null
        val offlineExpiry = prefs[Keys.offlineExpiry] ?: return@map null

        LoginSession(
            userId = userId,
            phoneNumber = phoneNumber,
            displayName = displayName,
            authToken = authToken,
            canUpload = prefs[Keys.canUpload] ?: false,
            canDeleteCache = prefs[Keys.canDeleteCache] ?: false,
            offlineExpiryEpochMillis = offlineExpiry
        )
    }

    suspend fun saveSession(session: LoginSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.userId] = session.userId
            prefs[Keys.phoneNumber] = session.phoneNumber
            prefs[Keys.displayName] = session.displayName
            prefs[Keys.authToken] = session.authToken
            prefs[Keys.canUpload] = session.canUpload
            prefs[Keys.canDeleteCache] = session.canDeleteCache
            prefs[Keys.offlineExpiry] = session.offlineExpiryEpochMillis
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
