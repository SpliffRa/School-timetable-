package com.schedule.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Одиночный экземпляр DataStore для контекста приложения
private val Context.dataStore by preferencesDataStore(name = "schedule_prefs")

private val KEY_CACHE              = stringPreferencesKey("cached_schedule")
private val KEY_ROLE               = stringPreferencesKey("device_role")          // "SERVER" | "CLIENT"
private val KEY_DEVICE_NAME_SERVER = stringPreferencesKey("device_name_server")   // имя для SERVER-роли
private val KEY_DEVICE_NAME_CLIENT = stringPreferencesKey("device_name_client")   // имя для CLIENT-роли
private val KEY_PIN                = stringPreferencesKey("editor_pin")
private val KEY_LAST_VERSION       = longPreferencesKey("last_known_version")
private val KEY_SYNC_CODE          = stringPreferencesKey("sync_code")

class AppDataStore(private val context: Context) {

    /** Последний успешно загруженный JSON расписания (или null) */
    val cachedScheduleFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CACHE]
    }

    /** Роль устройства: "SERVER" (папа) или "CLIENT" (дочка). По умолчанию CLIENT. */
    val deviceRoleFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ROLE] ?: "CLIENT"
    }

    /**
     * Имя устройства для текущей роли.
     * Возвращает SERVER-имя когда роль=SERVER, CLIENT-имя когда роль=CLIENT.
     * Дефолты: "Телефон папы" / "Планшет дочки".
     */
    val deviceNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val role = prefs[KEY_ROLE] ?: "CLIENT"
        if (role == "SERVER") {
            prefs[KEY_DEVICE_NAME_SERVER] ?: "Телефон папы"
        } else {
            prefs[KEY_DEVICE_NAME_CLIENT] ?: "Планшет Алисы"
        }
    }

    /** Имя устройства в режиме SERVER (папа) */
    val deviceNameServerFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_NAME_SERVER] ?: "Телефон папы"
    }

    /** Имя устройства в режиме CLIENT (Алиса) */
    val deviceNameClientFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_NAME_CLIENT] ?: "Планшет Алисы"
    }

    /** Пароль / PIN-код режима папы (по умолчанию "1234") */
    val editorPinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN]?.takeIf { it.isNotBlank() } ?: "1234"
    }

    /** Версия последнего полученного расписания — для сравнения при синхронизации */
    val lastKnownVersionFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_VERSION] ?: 0L
    }

    /** Код семьи для облачной синхронизации (по умолчанию "Алиса-2026") */
    val syncCodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_CODE]?.takeIf { it.isNotBlank() } ?: "Алиса-2026"
    }

    suspend fun saveCache(json: String) {
        context.dataStore.edit { prefs -> prefs[KEY_CACHE] = json }
    }

    suspend fun saveRole(role: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ROLE] = role }
    }

    /**
     * Сохраняет имя устройства для конкретной роли.
     * @param name  Имя устройства
     * @param role  "SERVER" или "CLIENT" (по умолчанию берётся из хранилища)
     */
    suspend fun saveDeviceName(name: String, role: String) {
        context.dataStore.edit { prefs ->
            if (role == "SERVER") {
                prefs[KEY_DEVICE_NAME_SERVER] = name
            } else {
                prefs[KEY_DEVICE_NAME_CLIENT] = name
            }
        }
    }

    suspend fun saveEditorPin(pin: String) {
        context.dataStore.edit { prefs -> prefs[KEY_PIN] = pin }
    }

    suspend fun saveLastKnownVersion(version: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_VERSION] = version }
    }

    suspend fun saveSyncCode(code: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SYNC_CODE] = code.trim() }
    }
}
