package com.schedule.app.data.network

import android.util.Log
import com.schedule.app.data.model.Schedule
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
     * Преобразует введённый пользователем код семьи (любой текст или цифры)
     * в безопасный ASCII-идентификатор для URL хранилища.
     * Например: "Алиса 2026" -> "alisa-2026", "1234" -> "family-1234".
     */
    fun normalizeSyncCode(code: String): String {
        val trimmed = code.trim().lowercase()
        if (trimmed.isBlank()) return "alisa-2026"

        val transliterated = transliterate(trimmed)
        val safe = transliterated.replace(Regex("[^a-z0-9_-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        return if (safe.length >= 3) {
            safe
        } else {
            // Если после очистки слишком коротко, добавляем хэш
            "family-" + md5(trimmed).take(8)
        }
    }

    /**
     * Отправляет расписание в защищённое облачное хранилище.
     * Доступно в любое время с любого устройства с данным кодом синхронизации.
     */
    suspend fun uploadSchedule(syncCode: String, schedule: Schedule): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val namespace = "sch-" + normalizeSyncCode(syncCode)
                val url = "$CLOUD_BASE_URL/$namespace/schedule"
                val scheduleJson = json.encodeToString(schedule)

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(scheduleJson)
                }

                if (response.status.isSuccess()) {
                    Log.d(TAG, "Uploaded schedule v${schedule.version} to cloud namespace $namespace")
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
     * Загружает расписание из облака по коду семьи.
     * Возвращает null, если расписание с таким кодом ещё не загружалось в облако.
     */
    suspend fun fetchSchedule(syncCode: String): Result<Schedule?> =
        withContext(Dispatchers.IO) {
            try {
                val namespace = "sch-" + normalizeSyncCode(syncCode)
                val url = "$CLOUD_BASE_URL/$namespace/schedule"

                val response = httpClient.get(url)

                if (response.status == HttpStatusCode.NotFound) {
                    Log.d(TAG, "Schedule not found in cloud namespace $namespace")
                    return@withContext Result.success(null)
                }

                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    // MantleDB возвращает 400 с ошибкой если путь не найден
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

                val schedule = json.decodeFromString<Schedule>(bodyText)
                Log.d(TAG, "Fetched schedule v${schedule.version} from cloud namespace $namespace")
                Result.success(schedule)
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed: ${e.message}", e)
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
