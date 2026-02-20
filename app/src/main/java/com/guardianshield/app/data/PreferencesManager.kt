package com.guardianshield.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val GHOST_MODE = stringPreferencesKey("ghost_mode")
        val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val PCR_NUMBER = stringPreferencesKey("pcr_number")
        val SOS_ACTIVE = booleanPreferencesKey("sos_active")
        val ACTIVE_SOS_EVENT_ID = stringPreferencesKey("active_sos_event_id")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    val ghostMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GHOST_MODE] ?: "default"
    }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_SETUP_COMPLETE] ?: false
    }

    val pinHash: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PIN_HASH] ?: ""
    }

    val pcrNumber: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PCR_NUMBER] ?: "112" // Default to India emergency number
    }

    val isSosActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SOS_ACTIVE] ?: false
    }

    val activeSosEventId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_SOS_EVENT_ID] ?: ""
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME] ?: ""
    }

    suspend fun setGhostMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[GHOST_MODE] = mode }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_SETUP_COMPLETE] = complete }
    }

    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { prefs -> prefs[PIN_HASH] = hash }
    }

    suspend fun setPcrNumber(number: String) {
        context.dataStore.edit { prefs -> prefs[PCR_NUMBER] = number }
    }

    suspend fun setSosActive(active: Boolean) {
        context.dataStore.edit { prefs -> prefs[SOS_ACTIVE] = active }
    }

    suspend fun setActiveSosEventId(id: String) {
        context.dataStore.edit { prefs -> prefs[ACTIVE_SOS_EVENT_ID] = id }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[USER_NAME] = name }
    }
}
