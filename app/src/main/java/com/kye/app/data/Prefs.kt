package com.kye.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kye_prefs")

data class Settings(
    val theme: String = "system",      // system | light | dark
    val voiceLang: String = "en-IN",   // en-IN | ta-IN
    val askedNotifications: Boolean = false
)

class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val theme = stringPreferencesKey("theme")
    private val voice = stringPreferencesKey("voice_lang")
    private val asked = booleanPreferencesKey("asked_notif")

    val settings: Flow<Settings> = appContext.dataStore.data.map {
        Settings(
            theme = it[theme] ?: "system",
            voiceLang = it[voice] ?: "en-IN",
            askedNotifications = it[asked] ?: false
        )
    }

    suspend fun setTheme(value: String) {
        appContext.dataStore.edit { it[theme] = value }
    }

    suspend fun setVoiceLang(value: String) {
        appContext.dataStore.edit { it[voice] = value }
    }

    suspend fun setAskedNotifications() {
        appContext.dataStore.edit { it[asked] = true }
    }
}
