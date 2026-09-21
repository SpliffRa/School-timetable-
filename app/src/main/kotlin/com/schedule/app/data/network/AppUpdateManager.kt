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
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AppUpdateManager"

// Настройки репозитория GitHub для раздачи обновлений
private const val GITHUB_OWNER = "SpliffRa"
private const val GITHUB_REPO = "School-timetable-"

// Основной эндпоинт метаданных на GitHub (CDN Fastly, без лимитов скорости и API)
private const val PRIMARY_UPDATE_ENDPOINT = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"
// Резервный CDN jsDelivr (Cloudflare, обходит ограничения провайдеров и кэши)
private const val JSDELIVR_UPDATE_ENDPOINT = "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/version.json"
private const val FALLBACK_MASTER_ENDPOINT = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/master/version.json"

/**
 * Модель данных обновления приложения, хранящаяся на GitHub.
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

    // Отдельный клиент с расширенным таймаутом и следованием редиректам (для GitHub Releases / AWS S3)
    private val downloadClient = HttpClient(OkHttp) {
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000L // 10 минут на скачивание
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 60_000L
        }
    }

    /**
     * Запрашивает текст из списка эндпоинтов по очереди.
     */
    private suspend fun fetchRemoteJsonText(): String? {
        val endpoints = listOf(PRIMARY_UPDATE_ENDPOINT, JSDELIVR_UPDATE_ENDPOINT, FALLBACK_MASTER_ENDPOINT)
        for (endpoint in endpoints) {
            try {
                val response = httpClient.get(endpoint)
                if (response.status.isSuccess()) {
                    val body = response.bodyAsText().trim()
                    if (body.isNotBlank() && !body.contains("\"error\":") && body.contains("\"versionCode\":")) {
                        Log.d(TAG, "Update metadata fetched successfully from: $endpoint")
                        return body
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch update metadata from $endpoint: ${e.message}")
            }
        }
        return null
    }

    /**
     * Проверяет наличие новой версии приложения на GitHub.
     * @return AppUpdateInfo если версия на сервере > текущей (BuildConfig.VERSION_CODE),
     *         null если установлена актуальная версия.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val body = fetchRemoteJsonText()
                ?: return@withContext Result.success(null)

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
            val body = fetchRemoteJsonText()
                ?: return@withContext Result.success(null)
            Result.success(json.decodeFromString<AppUpdateInfo>(body))
        } catch (e: Exception) {
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
            // На Android 8.0+ (API 26+) проверяем разрешение на установку неизвестных приложений
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    try {
                        val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot open unknown sources settings: ${e.message}")
                    }
                }
            }

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
