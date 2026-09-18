package com.schedule.app.data.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.schedule.app.MainActivity
import com.schedule.app.R
import com.schedule.app.data.store.AppDataStore
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val TAG = "SyncService"
private const val CHANNEL_ID = "sync_channel"
private const val NOTIF_FOREGROUND_ID = 1
private const val NOTIF_UPDATE_ID = 2
private const val POLL_INTERVAL_MS = 30_000L

/**
 * ForegroundService, управляющий P2P-синхронизацией.
 *
 * В режиме СЕРВЕРА:
 *   – запускает [ScheduleServer] (HTTP на порту 8080)
 *   – запускает UDP Broadcast loop ([PeerDiscovery.broadcastLoop])
 *     с правильным broadcast-адресом из WifiManager
 *
 * В режиме КЛИЕНТА:
 *   – слушает UDP ([PeerDiscovery.listenForServer]) до нахождения сервера
 *   – затем каждые 30 с опрашивает GET /version, при изменении скачивает /schedule
 *   – показывает Push-уведомление «Расписание обновлено»
 *   – при ошибке рассылает [BROADCAST_SYNC_ERROR]
 */
class SyncService : Service() {

    companion object {
        const val ACTION_START   = "com.schedule.app.SYNC_START"
        const val ACTION_STOP    = "com.schedule.app.SYNC_STOP"
        const val ACTION_RESTART = "com.schedule.app.SYNC_RESTART"
        const val ACTION_UPDATE_SCHEDULE = "com.schedule.app.UPDATE_SCHEDULE"
        const val EXTRA_SCHEDULE_JSON = "schedule_json"

        /** Broadcast-интент, который SyncService отправляет при получении нового расписания */
        const val BROADCAST_SCHEDULE_UPDATED = "com.schedule.app.SCHEDULE_UPDATED"

        /** Broadcast-интент при ошибке синхронизации */
        const val BROADCAST_SYNC_ERROR = "com.schedule.app.SYNC_ERROR"
        const val EXTRA_ERROR_MESSAGE  = "error_message"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Job для UDP-рассылки (SERVER) */
    private var serverJob: Job? = null
    /** Job для прослушивания UDP до обнаружения сервера (CLIENT) — ОТДЕЛЬНО от pollJob! */
    private var listenJob: Job? = null
    /** Job для периодического опроса HTTP-сервера (CLIENT) */
    private var pollJob: Job? = null
    /** Job для периодического опроса облачного хранилища (CLIENT) */
    private var cloudPollJob: Job? = null

    private val scheduleServer = ScheduleServer()
    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var dataStore: AppDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        dataStore = AppDataStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESTART -> {
                promoteToForeground()
                stopSync()
                startSync()
            }
            ACTION_STOP -> {
                stopSync()
                stopSelf()
            }
            ACTION_UPDATE_SCHEDULE -> {
                // ViewModel уведомляет сервис об изменении расписания на этом устройстве
                val scheduleJson = intent.getStringExtra(EXTRA_SCHEDULE_JSON) ?: return START_NOT_STICKY
                updateServerSchedule(scheduleJson)
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_FOREGROUND_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSync()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Запуск / остановка
    // ─────────────────────────────────────────────────────────────────────────

    private fun startSync() {
        serviceScope.launch {
            val role = dataStore.deviceRoleFlow.first()
            if (role == "SERVER") {
                startServer()
            } else {
                startClient()
            }
        }
    }

    private suspend fun startServer() {
        // Загрузить кэшированное расписание и отдать серверу
        val cachedJson = dataStore.cachedScheduleFlow.first()
        if (cachedJson != null) {
            try {
                val schedule = json.decodeFromString<com.schedule.app.data.model.Schedule>(cachedJson)
                scheduleServer.updateSchedule(schedule)
                // Также сразу синхронизируем с облаком
                serviceScope.launch {
                    val syncCode = dataStore.syncCodeFlow.first()
                    CloudSync.uploadSchedule(syncCode, schedule)
                }
            } catch (_: Exception) {}
        }

        scheduleServer.start()
        Log.d(TAG, "HTTP server started on :$SERVER_PORT")

        val deviceName = dataStore.deviceNameFlow.first()
        val broadcastAddr = getWifiBroadcastAddress()
        serverJob = serviceScope.launch {
            PeerDiscovery.broadcastLoop(deviceName, broadcastAddr)
        }
        Log.d(TAG, "UDP broadcast started: $deviceName → $broadcastAddr")
    }

    /**
     * Запускает клиент: сначала слушает UDP (listenJob), затем при нахождении
     * сервера запускает поллинг (pollJob).
     *
     * listenJob и pollJob — РАЗНЫЕ корутины, чтобы cancel одного не затрагивал другой.
     */
    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("ScheduleMulticastLock")?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            multicastLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun startClient() {
        listenJob?.cancel()
        pollJob?.cancel()
        startCloudPolling()
        acquireMulticastLock()
        listenJob = serviceScope.launch {
            try {
                // listenForServer выходит из цикла (break) сразу после нахождения сервера
                PeerDiscovery.listenForServer { ip ->
                    releaseMulticastLock()
                    // Запускаем поллинг в отдельной корутине
                    pollJob = serviceScope.launch {
                        startPolling(ip)
                    }
                }
                // listenForServer вернулся нормально → listenJob завершается
                Log.d(TAG, "listenForServer: done, polling started")
            } catch (e: Exception) {
                Log.w(TAG, "listenJob error: ${e.message}")
            }
        }
    }

    /**
     * Фоновый опрос облачного хранилища каждые 40 секунд.
     * Работает даже когда телефон папы выключен или находится вне дома.
     */
    private fun startCloudPolling() {
        cloudPollJob?.cancel()
        cloudPollJob = serviceScope.launch {
            while (true) {
                try {
                    val syncCode = dataStore.syncCodeFlow.first()
                    val result = CloudSync.fetchSchedule(syncCode)
                    if (result.isSuccess) {
                        val remote = result.getOrNull()
                        val lastVersion = dataStore.lastKnownVersionFlow.first()
                        if (remote != null && remote.version > lastVersion) {
                            Log.d(TAG, "Cloud update found: $lastVersion → ${remote.version}")
                            val scheduleJson = json.encodeToString(remote)
                            dataStore.saveCache(scheduleJson)
                            dataStore.saveLastKnownVersion(remote.version)

                            sendBroadcast(Intent(BROADCAST_SCHEDULE_UPDATED).apply {
                                `package` = applicationContext.packageName
                                putExtra(EXTRA_SCHEDULE_JSON, scheduleJson)
                            })
                            showUpdateNotification()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud poll error: ${e.message}")
                }
                delay(40_000L)
            }
        }
    }

    private suspend fun startPolling(serverIp: String) {
        Log.d(TAG, "Starting polling server at $serverIp")
        var lastVersion: Long = dataStore.lastKnownVersionFlow.first()

        while (true) {
            try {
                val remoteVersion = withContext(Dispatchers.IO) {
                    val resp = httpClient.get("http://$serverIp:$SERVER_PORT/version")
                    val bodyJson = resp.body<String>()
                    Json.parseToJsonElement(bodyJson).jsonObject["version"]?.jsonPrimitive?.long ?: 0L
                }

                if (remoteVersion > lastVersion) {
                    Log.d(TAG, "Schedule updated! $lastVersion → $remoteVersion")
                    // Скачиваем полное расписание
                    val schedule = withContext(Dispatchers.IO) {
                        fetchSchedule(serverIp, SERVER_PORT)
                    }
                    val scheduleJson = json.encodeToString(schedule)
                    dataStore.saveCache(scheduleJson)
                    dataStore.saveLastKnownVersion(remoteVersion)
                    lastVersion = remoteVersion

                    // Уведомляем ViewModel через Broadcast
                    sendBroadcast(Intent(BROADCAST_SCHEDULE_UPDATED).apply {
                        `package` = applicationContext.packageName
                        putExtra(EXTRA_SCHEDULE_JSON, scheduleJson)
                    })

                    // Push-уведомление
                    showUpdateNotification()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Polling error: ${e.message}")
                // При потере соединения по Wi-Fi не прерываемся, пробуем снова
                delay(POLL_INTERVAL_MS)
                continue
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun updateServerSchedule(scheduleJson: String) {
        try {
            val schedule = json.decodeFromString<com.schedule.app.data.model.Schedule>(scheduleJson)
            scheduleServer.updateSchedule(schedule)
            Log.d(TAG, "Server schedule updated, version=${schedule.version}")

            // Отправляем в облако
            serviceScope.launch {
                val syncCode = dataStore.syncCodeFlow.first()
                CloudSync.uploadSchedule(syncCode, schedule)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update server schedule: ${e.message}")
        }
    }

    private fun stopSync() {
        releaseMulticastLock()
        serverJob?.cancel()
        listenJob?.cancel()
        pollJob?.cancel()
        cloudPollJob?.cancel()
        scheduleServer.stop()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WifiManager — правильный broadcast-адрес подсети
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает broadcast-адрес текущей Wi-Fi подсети (например 192.168.1.255).
     * Если получить не удалось — fallback на "255.255.255.255".
     */
    @Suppress("DEPRECATION")
    private fun getWifiBroadcastAddress(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return "255.255.255.255"
            val dhcp = wm.dhcpInfo ?: return "255.255.255.255"
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            String.format(
                "%d.%d.%d.%d",
                broadcast and 0xff,
                (broadcast shr 8) and 0xff,
                (broadcast shr 16) and 0xff,
                (broadcast shr 24) and 0xff
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not get Wi-Fi broadcast address: ${e.message}")
            "255.255.255.255"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Уведомления
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Синхронизация расписания",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Фоновая синхронизация расписания по Wi-Fi"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification() = run {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Расписание")
            .setContentText("Синхронизация активна")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showUpdateNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Расписание обновлено! 📚")
            .setContentText("Папа изменил расписание — посмотри!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_UPDATE_ID, notification)
    }
}
