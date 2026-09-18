package com.schedule.app.data.network

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.schedule.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AppUpdateManager"
private const val UPDATE_ENDPOINT = "https://mantledb.sh/v2/sch-app-updates/latest"

/**
 * Модель данных обновления приложения, хранящаяся в защищённом облаке.
 */
@Serializable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseDate: String = "",
    val forceUpdate: Boolean = false
)

object AppUpdateManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Отдельный клиент с расширенным таймаутом для скачивания больших файлов APK
    private val downloadClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000L // 10 минут на скачивание
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 60_000L
        }
    }

    /**
     * Проверяет облако на наличие новой версии приложения.
     * @return AppUpdateInfo если версия в облаке > текущей (BuildConfig.VERSION_CODE),
     *         null если установлена актуальная версия.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(UPDATE_ENDPOINT)
            if (response.status == HttpStatusCode.NotFound) {
                return@withContext Result.success(null)
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                if (body.contains("Path not found", ignoreCase = true) ||
                    body.contains("not found", ignoreCase = true)) {
                    return@withContext Result.success(null)
                }
                return@withContext Result.failure(Exception("Ошибка сервера: ${response.status.value}"))
            }

            val body = response.bodyAsText()
            if (body.isBlank() || body.contains("\"error\":")) {
                return@withContext Result.success(null)
            }

            val updateInfo = json.decodeFromString<AppUpdateInfo>(body)
            val currentVersionCode = BuildConfig.VERSION_CODE

            Log.d(TAG, "Current versionCode: $currentVersionCode, Remote versionCode: ${updateInfo.versionCode}")
            if (updateInfo.versionCode > currentVersionCode) {
                Result.success(updateInfo)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check updates: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Получает текущую метаинформацию о версии на сервере без фильтрации по номеру.
     */
    suspend fun fetchLatestRemoteInfo(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(UPDATE_ENDPOINT)
            if (response.status == HttpStatusCode.NotFound) return@withContext Result.success(null)
            val body = response.bodyAsText()
            if (body.isBlank() || body.contains("\"error\":")) return@withContext Result.success(null)
            Result.success(json.decodeFromString<AppUpdateInfo>(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Публикует информацию о новой версии APK в облако.
     */
    suspend fun publishUpdate(info: AppUpdateInfo): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(info)
            val response = httpClient.post(UPDATE_ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "Update published: v${info.versionName} (${info.versionCode})")
                Result.success(true)
            } else {
                Result.failure(Exception("Ошибка публикации: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish update: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Скачивает APK по ссылке с отслеживанием прогресса.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetFileName: String = "school_update.apk",
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = downloadUrl.trim()
            if (cleanUrl.isBlank() || !cleanUrl.startsWith("http", ignoreCase = true)) {
                return@withContext Result.failure(
                    Exception("Ссылка на APK пуста или некорректна. Укажите прямой URL в настройках публикации.")
                )
            }

            val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val apkFile = File(destinationDir, targetFileName)
            if (apkFile.exists()) {
                apkFile.delete()
            }

            downloadClient.prepareGet(cleanUrl).execute { httpResponse ->
                if (httpResponse.status == HttpStatusCode.NotFound) {
                    throw Exception("Файл обновления не найден на сервере (HTTP 404). Проверьте правильность ссылки на APK.")
                }
                if (!httpResponse.status.isSuccess()) {
                    throw Exception("Ошибка скачивания: HTTP ${httpResponse.status.value} (${httpResponse.status.description})")
                }

                val contentLength = httpResponse.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val channel = httpResponse.bodyAsChannel()

                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L

                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val progress = if (contentLength > 0) {
                            (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                        withContext(Dispatchers.Main) {
                            onProgress(progress, downloaded, contentLength)
                        }
                    }
                    output.flush()
                }
            }

            if (!apkFile.exists() || apkFile.length() == 0L) {
                return@withContext Result.failure(Exception("Файл обновления пуст или не был сохранён"))
            }

            Log.d(TAG, "APK downloaded: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download APK failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Запускает установку APK через системный PackageInstaller Android.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent: ${e.message}", e)
            Result.failure(e)
        }
    }
}
