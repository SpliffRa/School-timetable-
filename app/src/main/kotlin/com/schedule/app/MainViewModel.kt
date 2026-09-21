package com.schedule.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.excel.ScheduleExcelManager
import com.schedule.app.data.model.Day
import com.schedule.app.data.model.Lesson
import com.schedule.app.data.model.Schedule
import com.schedule.app.data.network.AppUpdateInfo
import com.schedule.app.data.network.AppUpdateManager
import com.schedule.app.data.network.AppUpdateNotificationHelper
import com.schedule.app.data.network.CloudSync
import com.schedule.app.data.network.SyncService
import com.schedule.app.data.store.AppDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

sealed class UiState {
    data object Loading : UiState()

    data class Success(
        val schedule: Schedule,
        val fromCache: Boolean = false,
        val cacheDate: String? = null
    ) : UiState()

    data class Error(val message: String) : UiState()
}

// ---------------------------------------------------------------------------
// Sync State — состояние кнопки ручной синхронизации
// ---------------------------------------------------------------------------

enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }

// ---------------------------------------------------------------------------
// Update State — состояние проверки и установки обновлений приложения через облако
// ---------------------------------------------------------------------------

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateUiState()
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState()
    data class ReadyToInstall(val info: AppUpdateInfo, val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = AppDataStore(application)
    private val json = Json { ignoreUnknownKeys = true }

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** "SERVER" или "CLIENT" */
    private val _deviceRole = MutableStateFlow("CLIENT")
    val deviceRole: StateFlow<String> = _deviceRole.asStateFlow()

    /** Статус синхронизации для отображения в UI */
    private val _syncStatus = MutableStateFlow("Поиск устройств...")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    /** Состояние кнопки ручной синхронизации */
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /** Текст ошибки синхронизации (null если нет ошибки) */
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /** Набор ключей собранных вещей в рюкзак (изолированно для каждого дня недели) */
    private val _backpackCheckedItems = MutableStateFlow<Set<String>>(emptySet())
    val backpackCheckedItems: StateFlow<Set<String>> = _backpackCheckedItems.asStateFlow()

    fun toggleBackpackItem(dayName: String, lessonNumber: Int, item: String) {
        val key = "${dayName.trim().lowercase()}_${lessonNumber}_${item.trim().lowercase()}"
        val current = _backpackCheckedItems.value.toMutableSet()
        if (current.contains(key)) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _backpackCheckedItems.value = current

        viewModelScope.launch(Dispatchers.IO) {
            dataStore.toggleBackpackItem(key)
        }
    }

    fun clearBackpackItemsForDay(dayName: String) {
        val prefix = "${dayName.trim().lowercase()}_"
        val current = _backpackCheckedItems.value.toMutableSet()
        current.removeAll { it.startsWith(prefix) }
        _backpackCheckedItems.value = current

        viewModelScope.launch(Dispatchers.IO) {
            dataStore.clearBackpackItemsForDay(prefix)
        }
    }

    /** Доступ к настройкам (для экрана Settings) */
    fun deviceRoleFlow() = dataStore.deviceRoleFlow
    fun deviceNameFlow() = dataStore.deviceNameFlow
    fun deviceNameServerFlow() = dataStore.deviceNameServerFlow
    fun deviceNameClientFlow() = dataStore.deviceNameClientFlow
    fun editorPinFlow() = dataStore.editorPinFlow
    fun syncCodeFlow() = dataStore.syncCodeFlow
    fun fontScaleFlow() = dataStore.fontScaleFlow
    fun themeModeFlow() = dataStore.themeModeFlow

    fun saveFontScale(scale: Float) {
        viewModelScope.launch {
            dataStore.saveFontScale(scale)
        }
    }

    fun saveThemeMode(mode: String) {
        viewModelScope.launch {
            dataStore.saveThemeMode(mode)
        }
    }

    fun saveSyncCode(code: String) {
        viewModelScope.launch {
            dataStore.saveSyncCode(code)
        }
    }

    /** Состояние проверки и загрузки обновлений приложения через облако */
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** Баннер доступного обновления (если найдено при тихой проверке) */
    private val _availableUpdateBanner = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdateBanner: StateFlow<AppUpdateInfo?> = _availableUpdateBanner.asStateFlow()

    private var lastNotifiedVersionCode: Int = 0

    /**
     * Открывает диалоговое окно обновления для указанной версии
     */
    fun showUpdateAvailableDialog(info: AppUpdateInfo) {
        _updateState.value = UpdateUiState.UpdateAvailable(info)
    }

    /**
     * Проверяет наличие обновления в облаке.
     * @param isManual true если вызвано по нажатию пользователем (показывает диалог и индикатор)
     */
    fun checkForUpdates(isManual: Boolean = true) {
        viewModelScope.launch {
            if (isManual) {
                _updateState.value = UpdateUiState.Checking
            }
            val result = AppUpdateManager.checkForUpdate()
            if (result.isSuccess) {
                val updateInfo = result.getOrNull()
                if (updateInfo != null) {
                    _availableUpdateBanner.value = updateInfo
                    if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                        // Отправляем системное уведомление в Android (шторку) с кнопкой «Обновить»
                        if (lastNotifiedVersionCode != updateInfo.versionCode) {
                            lastNotifiedVersionCode = updateInfo.versionCode
                            AppUpdateNotificationHelper.showUpdateNotification(getApplication(), updateInfo)
                        }
                    }
                    if (isManual) {
                        _updateState.value = UpdateUiState.UpdateAvailable(updateInfo)
                    }
                } else {
                    _availableUpdateBanner.value = null
                    AppUpdateNotificationHelper.dismissNotification(getApplication())
                    if (isManual) {
                        _updateState.value = UpdateUiState.UpToDate
                    }
                }
            } else {
                if (isManual) {
                    val err = result.exceptionOrNull()?.message ?: "Не удалось проверить обновления"
                    _updateState.value = UpdateUiState.Error(err)
                }
            }
        }
    }

    /**
     * Скачивает APK-файл из облака и сразу запускает установщик пакетов Android.
     */
    fun startDownloadAndInstall(context: Context, info: AppUpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(
                info = info,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = -1L
            )

            val downloadResult = AppUpdateManager.downloadApk(
                context = context,
                downloadUrl = info.downloadUrl,
                targetFileName = "school_update_${info.versionCode}.apk"
            ) { progress, downloaded, total ->
                _updateState.value = UpdateUiState.Downloading(
                    info = info,
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }

            if (downloadResult.isSuccess) {
                val apkFile = downloadResult.getOrThrow()
                AppUpdateNotificationHelper.dismissNotification(getApplication())
                _updateState.value = UpdateUiState.ReadyToInstall(info, apkFile)
                AppUpdateManager.installApk(context, apkFile)
            } else {
                val err = downloadResult.exceptionOrNull()?.message ?: "Ошибка скачивания APK"
                _updateState.value = UpdateUiState.Error(err)
            }
        }
    }

    /**
     * Повторная попытка запуска установки уже скачанного файла APK
     */
    fun installDownloadedApk(context: Context, apkFile: File) {
        AppUpdateManager.installApk(context, apkFile)
    }

    /**
     * Закрывает диалог обновления
     */
    fun dismissUpdateDialog() {
        _updateState.value = UpdateUiState.Idle
    }

    /**
     * Публикует новое обновление приложения в облако (для телефона папы / разработчика).
     */
    fun publishUpdateToCloud(info: AppUpdateInfo, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = AppUpdateManager.publishUpdate(info)
            onDone(res.isSuccess)
            if (res.isSuccess) {
                checkForUpdates(isManual = false)
            }
        }
    }

    /**
     * Временный «режим папы» для текущей сессии.
     * false = режим планшета Алисы,
     * true  = режим папы (разблокирован паролем).
     */
    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    /** Job ручной синхронизации с таймаутом */
    private var manualSyncJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Управление ролями («Телефон папы» и «Планшет Алисы»)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Разблокировать и переключить на «Устройство родителя» по паролю.
     * Если пароль верный → isAdminMode = true, роль SERVER, неограниченный режим.
     */
    suspend fun switchToParentDevice(enteredPassword: String): Boolean {
        if (!checkPin(enteredPassword)) return false
        _isAdminMode.value = true
        _deviceRole.value = "SERVER"
        dataStore.saveRole("SERVER")
        restartSyncService()
        _syncStatus.value = "Режим родителя (сервер) ✓"
        return true
    }

    /** Совместимость */
    suspend fun switchToParentPhone(enteredPassword: String): Boolean = switchToParentDevice(enteredPassword)

    /**
     * Переключить на «Устройство ребёнка» (CLIENT).
     * Пароль не требуется.
     */
    fun switchToChildDevice() {
        _isAdminMode.value = false
        _deviceRole.value = "CLIENT"
        viewModelScope.launch {
            dataStore.saveRole("CLIENT")
            restartSyncService()
            _syncStatus.value = "Поиск устройства родителя..."
        }
    }

    /** Совместимость */
    fun switchToAliceTablet() = switchToChildDevice()

    /** Совместимость со старым вызовом */
    suspend fun switchToAdminMode(enteredPin: String): Boolean = switchToParentDevice(enteredPin)

    /** Совместимость со старым вызовом */
    fun exitAdminMode() = switchToChildDevice()

    // BroadcastReceiver — слушает обновления и ошибки от SyncService
    private val scheduleUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SyncService.BROADCAST_SCHEDULE_UPDATED -> {
                    val scheduleJson = intent.getStringExtra(SyncService.EXTRA_SCHEDULE_JSON) ?: return
                    viewModelScope.launch {
                        try {
                            val schedule = json.decodeFromString<Schedule>(scheduleJson)
                            _uiState.value = UiState.Success(schedule = schedule, fromCache = false)
                            _syncStatus.value = "Синхронизировано ✓"
                            _syncError.value = null

                            // Если шла ручная синхронизация — помечаем успех
                            if (_syncState.value == SyncState.SYNCING) {
                                manualSyncJob?.cancel()
                                _syncState.value = SyncState.SUCCESS
                                launch {
                                    delay(4_000)
                                    _syncState.value = SyncState.IDLE
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                SyncService.BROADCAST_SYNC_ERROR -> {
                    val errorMsg = intent.getStringExtra(SyncService.EXTRA_ERROR_MESSAGE)
                        ?: "Ошибка синхронизации"
                    viewModelScope.launch {
                        _syncStatus.value = "❌ $errorMsg"
                        _syncError.value = errorMsg

                        // Если шла ручная синхронизация — помечаем ошибку
                        if (_syncState.value == SyncState.SYNCING) {
                            manualSyncJob?.cancel()
                            _syncState.value = SyncState.ERROR
                            launch {
                                delay(5_000)
                                _syncState.value = SyncState.IDLE
                                _syncError.value = null
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        // Подписываемся на Broadcast от SyncService (оба экшена)
        val filter = IntentFilter().apply {
            addAction(SyncService.BROADCAST_SCHEDULE_UPDATED)
            addAction(SyncService.BROADCAST_SYNC_ERROR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(scheduleUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(scheduleUpdateReceiver, filter)
        }

        loadInitialState()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(scheduleUpdateReceiver)
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Инициализация
    // ─────────────────────────────────────────────────────────────────────────

    private val ALL_WEEK_DAYS = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    /** Гарантирует наличие всех дней недели в расписании */
    private fun ensureAllDays(schedule: Schedule): Schedule {
        val existingMap = schedule.days.associateBy { it.name.trim().lowercase() }
        // Сохраняем дни в правильном порядке недели, дополняя недостающие
        val allDays = ALL_WEEK_DAYS.map { dayName ->
            existingMap[dayName.lowercase()] ?: Day(name = dayName, lessons = emptyList())
        }
        // Добавляем любые нестандартные дни, если пользователь их создал
        val extraDays = schedule.days.filter { day ->
            ALL_WEEK_DAYS.none { it.equals(day.name.trim(), ignoreCase = true) }
        }
        return schedule.copy(days = allDays + extraDays)
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            // Подписываемся на сохранённые отметки рюкзака
            launch {
                dataStore.backpackCheckedItemsFlow.collect { items ->
                    _backpackCheckedItems.value = items
                }
            }

            // Читаем роль
            val role = dataStore.deviceRoleFlow.first()
            _deviceRole.value = role

            // Загружаем кэш
            val cachedJson = dataStore.cachedScheduleFlow.first()
            if (cachedJson != null) {
                try {
                    val schedule = json.decodeFromString<Schedule>(cachedJson)
                    val fullSchedule = ensureAllDays(schedule)
                    _uiState.value = UiState.Success(
                        schedule = fullSchedule,
                        fromCache = role == "CLIENT",
                        cacheDate = fullSchedule.updated
                    )
                } catch (_: Exception) {
                    val defaultSched = createDefaultSchedule()
                    _uiState.value = UiState.Success(schedule = defaultSched, fromCache = true)
                }
            } else if (role == "SERVER") {
                // Сервер без кэша — создаём пустое расписание со всеми 7 днями
                val empty = createDefaultSchedule()
                saveScheduleAsServer(empty)
            } else {
                // Планшет Алисы без кэша — показываем пустое расписание со всеми 7 днями
                val empty = createDefaultSchedule()
                _uiState.value = UiState.Success(
                    schedule = empty,
                    fromCache = true,
                    cacheDate = null
                )
            }

            // Запускаем синхронизацию
            startSyncService()

            // Если это планшет Алисы, сразу же тихо проверяем облако
            if (role == "CLIENT") {
                checkCloudUpdateSilently()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SyncService
    // ─────────────────────────────────────────────────────────────────────────

    fun restartSyncService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, SyncService::class.java).apply {
            action = SyncService.ACTION_RESTART
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start SyncService: ${e.message}", e)
        }
        val role = _deviceRole.value
        _syncStatus.value = if (role == "SERVER") "Сервер запущен ✓" else "Поиск устройства родителя..."
    }

    fun startSyncService() = restartSyncService()

    fun stopSyncService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, SyncService::class.java).apply {
            action = SyncService.ACTION_STOP
        }
        try {
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to stop SyncService: ${e.message}", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ручная синхронизация
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Запускает ручную синхронизацию.
     *
     * Для SERVER: отправляет расписание в облако и запускает сервер → SUCCESS.
     * Для CLIENT: сначала проверяет облако (работает даже когда телефон папы выключен).
     *   Если найдено новое расписание → мгновенный SUCCESS.
     *   Если расписание актуально → мгновенный SUCCESS.
     *   Если облако недоступно → fallback на локальный Wi-Fi.
     */
    fun manualSync() {
        manualSyncJob?.cancel()
        manualSyncJob = viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            _syncError.value = null
            val syncCode = dataStore.syncCodeFlow.first()

            if (_deviceRole.value == "SERVER") {
                _syncStatus.value = "Отправка в облако..."
                val current = (_uiState.value as? UiState.Success)?.schedule
                if (current != null) {
                    val cloudRes = CloudSync.uploadSchedule(syncCode, current)
                    if (cloudRes.isSuccess) {
                        _syncState.value = SyncState.SUCCESS
                        _syncStatus.value = "Расписание отправлено в облако ✓"
                    } else {
                        _syncState.value = SyncState.SUCCESS
                        _syncStatus.value = "Сервер запущен (локально) ✓"
                    }
                } else {
                    _syncState.value = SyncState.ERROR
                    _syncError.value = "Расписание не загружено"
                    _syncStatus.value = "❌ Расписание не загружено"
                }
                delay(4_000)
                _syncState.value = SyncState.IDLE
                _syncError.value = null
            } else {
                // Клиент: проверяем облако
                _syncStatus.value = "Проверка в облаке..."
                try {
                    val cloudRes = CloudSync.fetchSchedule(syncCode)
                    if (cloudRes.isSuccess) {
                        val remote = cloudRes.getOrNull()
                        val lastVersion = dataStore.lastKnownVersionFlow.first()

                        if (remote != null && remote.version > lastVersion) {
                            val full = ensureAllDays(remote)
                            val scheduleJson = json.encodeToString(full)
                            dataStore.saveCache(scheduleJson)
                            dataStore.saveLastKnownVersion(full.version)
                            _uiState.value = UiState.Success(schedule = full, fromCache = false)
                            _syncState.value = SyncState.SUCCESS
                            _syncStatus.value = "Расписание обновлено из облака ✓"
                            delay(4_000)
                            _syncState.value = SyncState.IDLE
                            return@launch
                        } else if (remote != null) {
                            _syncState.value = SyncState.SUCCESS
                            _syncStatus.value = "Расписание уже актуально ✓"
                            delay(4_000)
                            _syncState.value = SyncState.IDLE
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Cloud fetch in manualSync failed: ${e.message}")
                }

                // Fallback: локальный Wi-Fi поиск
                _syncStatus.value = "Поиск по локальному Wi-Fi..."
                restartSyncService()
                delay(12_000)

                if (_syncState.value == SyncState.SYNCING) {
                    _syncState.value = SyncState.ERROR
                    _syncError.value = "Сервер не найден — проверь интернет или Wi-Fi"
                    _syncStatus.value = "❌ Сервер не найден"
                    delay(5_000)
                    _syncState.value = SyncState.IDLE
                    _syncError.value = null
                }
            }
        }
    }

    /** Job периодической фоновой синхронизации (раз в минуту в активном приложении) */
    private var periodicSyncJob: Job? = null

    /**
     * Вызывается при переходе приложения на передний план (foreground).
     * Сразу же выполняет первую проверку синхронизации, а затем повторяет её
     * с интервалом в 1 минуту (60 секунд), пока приложение открыто.
     */
    fun onAppForegrounded() {
        periodicSyncJob?.cancel()
        periodicSyncJob = viewModelScope.launch {
            // Тихо проверяем наличие обновлений приложения в облаке при открытии
            launch { checkForUpdates(isManual = false) }

            while (isActive) {
                checkSyncUpdateSilently()
                delay(60_000L) // 1 минута
            }
        }
    }

    /**
     * Вызывается при сворачивании приложения в фон.
     * Приостанавливает периодическую синхронизацию для экономии батареи и трафика.
     */
    fun onAppBackgrounded() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
    }

    /**
     * Тихо проверяет наличие обновлений в облаке:
     * Для CLIENT — проверяет облако, скачивает новую версию, если папа обновил расписание.
     * Для SERVER — проверяет актуальность версии в облаке и отправляет расписание при необходимости.
     */
    suspend fun checkSyncUpdateSilently() {
        val role = _deviceRole.value
        val syncCode = dataStore.syncCodeFlow.first()
        if (role == "CLIENT") {
            try {
                val result = CloudSync.fetchSchedule(syncCode)
                if (result.isSuccess) {
                    val remote = result.getOrNull() ?: return
                    val lastVersion = dataStore.lastKnownVersionFlow.first()
                    if (remote.version > lastVersion) {
                        val full = ensureAllDays(remote)
                        val scheduleJson = json.encodeToString(full)
                        dataStore.saveCache(scheduleJson)
                        dataStore.saveLastKnownVersion(full.version)
                        _uiState.value = UiState.Success(schedule = full, fromCache = false)
                        _syncStatus.value = "Синхронизировано из облака ✓"
                    }
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Silent client sync: ${e.message}")
            }
        } else if (role == "SERVER") {
            try {
                val current = (_uiState.value as? UiState.Success)?.schedule
                if (current != null) {
                    val cloudRes = CloudSync.fetchSchedule(syncCode)
                    val remoteVersion = cloudRes.getOrNull()?.version ?: 0L
                    if (current.version > remoteVersion) {
                        CloudSync.uploadSchedule(syncCode, current)
                    }
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Silent server sync: ${e.message}")
            }
        }
    }

    /**
     * Совместимость со старыми вызовами (при смене настроек или старте)
     */
    fun checkCloudUpdateSilently() {
        viewModelScope.launch {
            checkSyncUpdateSilently()
        }
    }

    /**
     * Экспортирует текущее расписание в виде JSON-строки для отправки
     * через Telegram, WhatsApp, Bluetooth или файл.
     */
    fun exportScheduleJson(): String {
        val schedule = (_uiState.value as? UiState.Success)?.schedule ?: createDefaultSchedule()
        return json.encodeToString(schedule)
    }

    /**
     * Импортирует расписание из JSON-строки (из файла или мессенджера).
     */
    fun importScheduleJson(jsonString: String): Boolean {
        return try {
            val parsed = json.decodeFromString<Schedule>(jsonString)
            val full = ensureAllDays(parsed)
            saveSchedule(full)
            true
        } catch (e: Exception) {
            Log.e("MainViewModel", "Import schedule error: ${e.message}")
            false
        }
    }

    /**
     * Экспортирует текущее расписание в файл Excel (.xlsx) и открывает системное меню «Поделиться».
     */
    fun shareExcelSchedule(context: Context) {
        val current = (_uiState.value as? UiState.Success)?.schedule ?: createDefaultSchedule()
        val file = ScheduleExcelManager.exportToExcelFile(context, current)
        ScheduleExcelManager.shareExcelFile(context, file)
    }

    /**
     * Импортирует расписание из выбранного файла Excel (.xlsx или .csv).
     */
    fun importScheduleFromExcel(context: Context, uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val parseResult = withContext(Dispatchers.IO) {
                ScheduleExcelManager.importFromExcelOrCsv(context, uri)
            }
            if (parseResult.isSuccess) {
                val newSchedule = parseResult.getOrThrow()
                saveSchedule(newSchedule)
                val totalLessons = newSchedule.days.sumOf { it.lessons.size }
                onResult(Result.success(totalLessons))
            } else {
                onResult(Result.failure(parseResult.exceptionOrNull() ?: Exception("Ошибка импорта файла")))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Настройки
    // ─────────────────────────────────────────────────────────────────────────

    fun saveSettings(role: String, deviceName: String, pin: String, syncCode: String = "") {
        viewModelScope.launch {
            dataStore.saveRole(role)
            dataStore.saveDeviceName(deviceName, role)  // сохраняем для конкретной роли
            dataStore.saveEditorPin(pin)
            if (syncCode.isNotBlank()) {
                dataStore.saveSyncCode(syncCode)
            }
            _deviceRole.value = role

            // Бесшовно перезапускаем сервис с новой ролью
            restartSyncService()

            // Если роль CLIENT — проверяем облако по новому коду
            if (role == "CLIENT") {
                checkCloudUpdateSilently()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PIN-проверка
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun checkPin(enteredPin: String): Boolean {
        val savedPin = dataStore.editorPinFlow.first().trim().ifBlank { "1234" }
        return enteredPin.trim() == savedPin
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD расписания (только SERVER)
    // ─────────────────────────────────────────────────────────────────────────

    /** Сохраняет обновлённое расписание, проставляет новую версию, уведомляет SyncService */
    fun saveSchedule(schedule: Schedule) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            val updated = LocalDateTime.now().format(formatter)
            val newSchedule = schedule.copy(version = now, updated = updated)
            saveScheduleAsServer(newSchedule)
        }
    }

    private suspend fun saveScheduleAsServer(schedule: Schedule) {
        val scheduleJson = json.encodeToString(schedule)
        dataStore.saveCache(scheduleJson)
        dataStore.saveLastKnownVersion(schedule.version)
        _uiState.value = UiState.Success(schedule = schedule, fromCache = false)

        // 1. Уведомить локальный SyncService
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, SyncService::class.java).apply {
            action = SyncService.ACTION_UPDATE_SCHEDULE
            putExtra(SyncService.EXTRA_SCHEDULE_JSON, scheduleJson)
        }
        try {
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to update server schedule: ${e.message}", e)
        }

        // 2. Отправить в защищённое облако (работает даже когда телефон папы выключен!)
        viewModelScope.launch {
            val syncCode = dataStore.syncCodeFlow.first()
            val cloudRes = CloudSync.uploadSchedule(syncCode, schedule)
            if (cloudRes.isSuccess) {
                _syncStatus.value = "Сохранено и отправлено в облако ✓"
            } else {
                _syncStatus.value = "Сохранено локально ✓"
            }
        }
    }

    /** Добавить урок в указанный день */
    fun addLesson(dayName: String, lesson: Lesson) {
        val current = (uiState.value as? UiState.Success)?.schedule ?: createDefaultSchedule()
        val dayExists = current.days.any { it.name.trim().equals(dayName.trim(), ignoreCase = true) }
        val updatedDays = if (dayExists) {
            current.days.map { day ->
                if (day.name.trim().equals(dayName.trim(), ignoreCase = true)) {
                    day.copy(lessons = (day.lessons + lesson).sortedBy { it.number })
                } else day
            }
        } else {
            current.days + Day(dayName, listOf(lesson))
        }
        saveSchedule(current.copy(days = updatedDays))
    }

    /** Обновить урок (с поддержкой изменения номера урока) */
    fun updateLesson(dayName: String, lesson: Lesson, oldNumber: Int = lesson.number) {
        val current = (uiState.value as? UiState.Success)?.schedule ?: createDefaultSchedule()
        val updated = current.copy(
            days = current.days.map { day ->
                if (day.name.trim().equals(dayName.trim(), ignoreCase = true)) {
                    val filtered = day.lessons.filterNot { it.number == oldNumber }
                    day.copy(lessons = (filtered + lesson).sortedBy { it.number })
                } else day
            }
        )
        saveSchedule(updated)
    }

    /** Удалить урок */
    fun deleteLesson(dayName: String, lessonNumber: Int) {
        val current = (uiState.value as? UiState.Success)?.schedule ?: return
        val updated = current.copy(
            days = current.days.map { day ->
                if (day.name.trim().equals(dayName.trim(), ignoreCase = true)) {
                    day.copy(lessons = day.lessons.filter { it.number != lessonNumber })
                } else day
            }
        )
        saveSchedule(updated)
    }

    /** Добавить новый день */
    fun addDay(dayName: String) {
        val trimmed = dayName.trim()
        if (trimmed.isBlank()) return
        val current = (uiState.value as? UiState.Success)?.schedule ?: createDefaultSchedule()
        if (current.days.any { it.name.trim().equals(trimmed, ignoreCase = true) }) return
        val updated = current.copy(days = current.days + Day(name = trimmed, lessons = emptyList()))
        saveSchedule(updated)
    }

    /** Удалить день */
    fun deleteDay(dayName: String) {
        val current = (uiState.value as? UiState.Success)?.schedule ?: return
        val updated = current.copy(days = current.days.filter { !it.name.trim().equals(dayName.trim(), ignoreCase = true) })
        saveSchedule(updated)
    }

    /** Восстановить все стандартные 7 дней недели, сохраняя имеющиеся уроки */
    fun restoreAllWeekDays() {
        val current = (uiState.value as? UiState.Success)?.schedule ?: createDefaultSchedule()
        val restored = ensureAllDays(current)
        saveSchedule(restored)
    }

    /** Сохранить новый пароль режима папы */
    fun savePassword(newPassword: String) {
        viewModelScope.launch {
            dataStore.saveEditorPin(newPassword)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательное
    // ─────────────────────────────────────────────────────────────────────────

    /** Pull-to-refresh (для CLIENT — ничего не делает кроме обновления статуса) */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncStatus.value = "Обновление..."
            // Синхронизация происходит через SyncService автоматически
            // Здесь просто даём визуальную обратную связь
            delay(1_000)
            _isRefreshing.value = false
            val role = _deviceRole.value
            _syncStatus.value = if (role == "SERVER") "Сервер запущен ✓" else "Ожидание обновлений..."
        }
    }

    fun createDefaultSchedule(): Schedule {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        return Schedule(
            updated = LocalDateTime.now().format(formatter),
            version = System.currentTimeMillis(),
            days = listOf(
                Day("Понедельник", emptyList()),
                Day("Вторник", emptyList()),
                Day("Среда", emptyList()),
                Day("Четверг", emptyList()),
                Day("Пятница", emptyList()),
                Day("Суббота", emptyList()),
                Day("Воскресенье", emptyList())
            )
        )
    }
}
