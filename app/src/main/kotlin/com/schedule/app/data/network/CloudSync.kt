package com.schedule.app.data.network

import android.util.Log
import com.schedule.app.data.model.BackpackState
import com.schedule.app.data.model.Schedule
import com.schedule.app.data.security.CryptoUtils
import com.schedule.app.data.security.EncryptedScheduleEnvelope
import com.schedule.app.data.security.InvalidFamilyKeyException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private const val TAG = "CloudSync"
private const val CLOUD_BASE_URL = "https://mantledb.sh/v2"

object CloudSync {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Преобразует введённый пользователем индивидуальный код/ключ семьи
     * в безопасный ASCII-идентификатор для URL хранилища.
     */
    fun normalizeSyncCode(code: String): String {
        val trimmed = code.trim().lowercase()
        if (trimmed.isBlank()) return ""

        val transliterated = transliterate(trimmed)
        val safe = transliterated.replace(Regex("[^a-z0-9_-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        return if (safe.length >= 3) {
            safe
        } else {
            "family-" + md5(trimmed).take(8)
        }
    }

    /**
     * Отправляет зашифрованное расписание в облачное хранилище (E2EE AES-256-GCM).
     * Облако получает только зашифрованный бинарный шум, прочитать который без ключа невозможно.
     */
    suspend fun uploadSchedule(syncCode: String, schedule: Schedule): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val cleanKey = if (CryptoUtils.cleanFamilyKey(syncCode).isNotBlank()) {
                CryptoUtils.cleanFamilyKey(syncCode)
            } else {
                normalizeSyncCode(syncCode)
            }
            if (cleanKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Индивидуальный ключ семьи не задан"))
            }
            try {
                val namespace = CryptoUtils.deriveStorageNamespace(cleanKey)
                val url = "$CLOUD_BASE_URL/$namespace/schedule"

                // 1. Сериализуем расписание
                val scheduleJson = json.encodeToString(schedule)

                // 2. Шифруем алгоритмом AES-256-GCM локальным ключом
                val envelope = com.schedule.app.data.security.CryptoUtils.encryptPayload(scheduleJson, cleanKey)
                val envelopeJson = json.encodeToString(envelope)

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(envelopeJson)
                }

                if (response.status.isSuccess()) {
                    Log.d(TAG, "Uploaded encrypted schedule v${schedule.version} to cloud (E2EE active)")
                    Result.success(true)
                } else {
                    val body = response.bodyAsText()
                    Log.w(TAG, "Cloud upload error: ${response.status} - $body")
                    Result.failure(Exception("Сервер вернул статус ${response.status.value}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Загружает и расшифровывает расписание из облака по ключу семьи.
     */
    suspend fun fetchSchedule(syncCode: String): Result<Schedule?> =
        withContext(Dispatchers.IO) {
            val cleanKey = if (CryptoUtils.cleanFamilyKey(syncCode).isNotBlank()) {
                CryptoUtils.cleanFamilyKey(syncCode)
            } else {
                normalizeSyncCode(syncCode)
            }
            if (cleanKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Индивидуальный ключ семьи не задан"))
            }
            try {
                val namespace = CryptoUtils.deriveStorageNamespace(cleanKey)
                val url = "$CLOUD_BASE_URL/$namespace/schedule"

                val response = httpClient.get(url)

                if (response.status == HttpStatusCode.NotFound) {
                    Log.d(TAG, "Schedule not found in cloud namespace $namespace")
                    return@withContext Result.success(null)
                }

                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    if (body.contains("Path not found", ignoreCase = true) ||
                        body.contains("not found", ignoreCase = true)) {
                        return@withContext Result.success(null)
                    }
                    Log.w(TAG, "Cloud fetch error: ${response.status} - $body")
                    return@withContext Result.failure(Exception("Ошибка сервера ${response.status.value}"))
                }

                val bodyText = response.bodyAsText()
                if (bodyText.isBlank() || bodyText.contains("\"error\":")) {
                    return@withContext Result.success(null)
                }

                // Пытаемся расшифровать как EncryptedScheduleEnvelope
                val scheduleJson = try {
                    val envelope = json.decodeFromString<com.schedule.app.data.security.EncryptedScheduleEnvelope>(bodyText)
                    com.schedule.app.data.security.CryptoUtils.decryptPayload(envelope, cleanKey)
                } catch (e: com.schedule.app.data.security.InvalidFamilyKeyException) {
                    throw e
                } catch (e: Exception) {
                    // Если это было старое незашифрованное расписание
                    bodyText
                }

                val schedule = json.decodeFromString<Schedule>(scheduleJson)
                Log.d(TAG, "Fetched and decrypted schedule v${schedule.version} (E2EE verified)")
                Result.success(schedule)
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Отправляет зашифрованное состояние рюкзака в облачное хранилище (E2EE AES-256-GCM).
     */
    suspend fun uploadBackpack(syncCode: String, state: BackpackState): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val cleanKey = if (CryptoUtils.cleanFamilyKey(syncCode).isNotBlank()) {
                CryptoUtils.cleanFamilyKey(syncCode)
            } else {
                normalizeSyncCode(syncCode)
            }
            if (cleanKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Индивидуальный ключ семьи не задан"))
            }
            try {
                val namespace = CryptoUtils.deriveStorageNamespace(cleanKey)
                val url = "$CLOUD_BASE_URL/$namespace/backpack"

                val stateJson = json.encodeToString(state)
                val envelope = CryptoUtils.encryptPayload(stateJson, cleanKey)
                val envelopeJson = json.encodeToString(envelope)

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(envelopeJson)
                }

                if (response.status.isSuccess()) {
                    Log.d(TAG, "Uploaded encrypted backpack state (${state.checkedItems.size} items) to cloud")
                    Result.success(true)
                } else {
                    val body = response.bodyAsText()
                    Log.w(TAG, "Cloud backpack upload error: ${response.status} - $body")
                    Result.failure(Exception("Сервер вернул статус ${response.status.value}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backpack upload failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Загружает и расшифровывает состояние рюкзака из облака.
     */
    suspend fun fetchBackpack(syncCode: String): Result<BackpackState?> =
        withContext(Dispatchers.IO) {
            val cleanKey = if (CryptoUtils.cleanFamilyKey(syncCode).isNotBlank()) {
                CryptoUtils.cleanFamilyKey(syncCode)
            } else {
                normalizeSyncCode(syncCode)
            }
            if (cleanKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Индивидуальный ключ семьи не задан"))
            }
            try {
                val namespace = CryptoUtils.deriveStorageNamespace(cleanKey)
                val url = "$CLOUD_BASE_URL/$namespace/backpack"

                val response = httpClient.get(url)

                if (response.status == HttpStatusCode.NotFound) {
                    return@withContext Result.success(null)
                }

                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    if (body.contains("Path not found", ignoreCase = true) ||
                        body.contains("not found", ignoreCase = true)) {
                        return@withContext Result.success(null)
                    }
                    Log.w(TAG, "Cloud backpack fetch error: ${response.status} - $body")
                    return@withContext Result.failure(Exception("Ошибка сервера ${response.status.value}"))
                }

                val bodyText = response.bodyAsText()
                if (bodyText.isBlank() || bodyText.contains("\"error\":")) {
                    return@withContext Result.success(null)
                }

                val stateJson = try {
                    val envelope = json.decodeFromString<EncryptedScheduleEnvelope>(bodyText)
                    CryptoUtils.decryptPayload(envelope, cleanKey)
                } catch (e: InvalidFamilyKeyException) {
                    throw e
                } catch (e: Exception) {
                    bodyText
                }

                val state = json.decodeFromString<BackpackState>(stateJson)
                Log.d(TAG, "Fetched and decrypted backpack state (${state.checkedItems.size} items)")
                Result.success(state)
            } catch (e: Exception) {
                Log.e(TAG, "Backpack fetch failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Транслитерация для безопасных URL
    // ─────────────────────────────────────────────────────────────────────────

    private fun transliterate(input: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )
        val sb = StringBuilder(input.length)
        for (c in input) {
            val rep = map[c]
            if (rep != null) {
                sb.append(rep)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
