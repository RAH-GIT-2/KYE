package com.kye.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kye.app.domain.BackupFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kye_prefs")

data class Settings(
    val theme: String = "system",      // system | light | dark
    val voiceLang: String = "en-IN",   // en-IN | ta-IN
    val askedNotifications: Boolean = false,
    val voiceEnabled: Boolean = true,
    /** Fingerprint / face / screen PIN when opening KYE. */
    val appLock: Boolean = false,
    val backup: BackupSettings = BackupSettings()
)

data class BackupSettings(
    val frequency: BackupFrequency = BackupFrequency.OFF,
    /** Folder picked by the user (a SAF tree URI); automatic backups are saved there. */
    val folderUri: String? = null,
    /** Time of the last successful backup, automatic or manual. */
    val lastAt: Long? = null,
    val lastWasAuto: Boolean = false,
    /** Why the last automatic backup failed; cleared by the next success. */
    val error: String? = null,
    /** Save last month's PDF report into the backup folder on the 1st. */
    val monthlyReport: Boolean = true
)

class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val theme = stringPreferencesKey("theme")
    private val voice = stringPreferencesKey("voice_lang")
    private val asked = booleanPreferencesKey("asked_notif")
    private val seed = intPreferencesKey("seed_version")
    private val voiceOn = booleanPreferencesKey("voice_enabled")
    private val lockOn = booleanPreferencesKey("app_lock")
    private val backupFreq = stringPreferencesKey("backup_frequency")
    private val backupFolder = stringPreferencesKey("backup_folder")
    private val backupLastAt = longPreferencesKey("backup_last_at")
    private val backupLastAuto = booleanPreferencesKey("backup_last_auto")
    private val backupError = stringPreferencesKey("backup_error")
    private val reportAuto = booleanPreferencesKey("report_auto")
    private val reportLast = stringPreferencesKey("report_last_month")

    val settings: Flow<Settings> = appContext.dataStore.data.map {
        Settings(
            theme = it[theme] ?: "system",
            voiceLang = it[voice] ?: "en-IN",
            askedNotifications = it[asked] ?: false,
            voiceEnabled = it[voiceOn] ?: true,
            appLock = it[lockOn] ?: false,
            backup = BackupSettings(
                frequency = BackupFrequency.entries.firstOrNull { f -> f.name == it[backupFreq] } ?: BackupFrequency.OFF,
                folderUri = it[backupFolder],
                lastAt = it[backupLastAt],
                lastWasAuto = it[backupLastAuto] ?: false,
                error = it[backupError],
                monthlyReport = it[reportAuto] ?: true
            )
        )
    }

    suspend fun backupSettings(): BackupSettings = settings.first().backup

    suspend fun setTheme(value: String) {
        appContext.dataStore.edit { it[theme] = value }
    }

    suspend fun setVoiceLang(value: String) {
        appContext.dataStore.edit { it[voice] = value }
    }

    suspend fun setVoiceEnabled(value: Boolean) {
        appContext.dataStore.edit { it[voiceOn] = value }
    }

    suspend fun setAppLock(value: Boolean) {
        appContext.dataStore.edit { it[lockOn] = value }
    }

    suspend fun setAskedNotifications() {
        appContext.dataStore.edit { it[asked] = true }
    }

    suspend fun setBackupFrequency(value: BackupFrequency) {
        appContext.dataStore.edit { it[backupFreq] = value.name }
    }

    suspend fun setBackupFolder(uri: String) {
        appContext.dataStore.edit {
            it[backupFolder] = uri
            it.remove(backupError)
        }
    }

    suspend fun recordBackup(at: Long, auto: Boolean) {
        appContext.dataStore.edit {
            it[backupLastAt] = at
            it[backupLastAuto] = auto
            if (auto) it.remove(backupError)
        }
    }

    suspend fun recordBackupError(message: String) {
        appContext.dataStore.edit { it[backupError] = message }
    }

    suspend fun setMonthlyReport(value: Boolean) {
        appContext.dataStore.edit { it[reportAuto] = value }
    }

    /** The last month ("2026-10") whose report was saved automatically. */
    suspend fun lastReportMonth(): String? = appContext.dataStore.data.first()[reportLast]

    suspend fun setLastReportMonth(month: String) {
        appContext.dataStore.edit { it[reportLast] = month }
    }

    /** Which built-in category set this phone has received. 0 = never recorded. */
    suspend fun seedVersion(): Int = appContext.dataStore.data.first()[seed] ?: 0

    suspend fun setSeedVersion(value: Int) {
        appContext.dataStore.edit { it[seed] = value }
    }
}
