package com.schedule.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.Day
import com.schedule.app.data.model.Lesson
import com.schedule.app.data.model.Schedule
import com.schedule.app.data.network.CloudSync
import com.schedule.app.data.network.SyncService
import com.schedule.app.data.store.AppDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
     * Разблокировать и переключить на «Телефон папы» по паролю.
     * Если пароль верный → isAdminMode = true, роль SERVER, неограниченный режим.
     */
    suspend fun switchToParentPhone(enteredPassword: String): Boolean {
        if (!checkPin(enteredPassword)) return false
        _isAdminMode.value = true
        _deviceRole.value = "SERVER"
        dataStore.saveRole("SERVER")
        restartSyncService()
        _syncStatus.value = "Режим папы (сервер) ✓"
        return true
    }

    /**
     * Переключить на «Планшет Алисы» (CLIENT).
     * Пароль не требуется.
     */
    fun switchToAliceTablet() {
        _isAdminMode.value = false
        _deviceRole.value = "CLIENT"
        viewModelScope.launch {
            dataStore.saveRole("CLIENT")
            restartSyncService()
            _syncStatus.value = "Поиск телефона папы..."
        }
    }

    /** Совместимость со старым вызовом */
    suspend fun switchToAdminMode(enteredPin: String): Boolean = switchToParentPhone(enteredPin)

    /** Совместимость со старым вызовом */
    fun exitAdminMode() = switchToAliceTablet()

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
        _syncStatus.value = if (role == "SERVER") "Сервер запущен ✓" else "Поиск телефона папы..."
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
