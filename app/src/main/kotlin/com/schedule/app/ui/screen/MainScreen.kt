package com.schedule.app.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.app.MainViewModel
import com.schedule.app.SyncState
import com.schedule.app.UiState
import com.schedule.app.data.model.Lesson
import com.schedule.app.data.model.Schedule
import com.schedule.app.ui.theme.Accent
import com.schedule.app.ui.theme.AccentDark
import com.schedule.app.ui.theme.Divider
import com.schedule.app.ui.theme.DividerDark
import com.schedule.app.ui.theme.InnerBoxDarkBg
import com.schedule.app.ui.theme.InnerBoxDarkBorder
import com.schedule.app.ui.theme.InnerBoxLightBg
import com.schedule.app.ui.theme.InnerBoxLightBorder
import com.schedule.app.ui.theme.LocalIsDarkTheme
import com.schedule.app.ui.theme.NeutralPill
import com.schedule.app.ui.theme.NeutralPillBright
import com.schedule.app.ui.theme.NeutralPillDarkBg
import com.schedule.app.ui.theme.NeutralPillLight
import com.schedule.app.ui.theme.PendingAmber
import com.schedule.app.ui.theme.PendingAmberBright
import com.schedule.app.ui.theme.PendingAmberDarkBg
import com.schedule.app.ui.theme.PendingAmberLight
import com.schedule.app.ui.theme.SegmentContainer
import com.schedule.app.ui.theme.SegmentContainerDark
import com.schedule.app.ui.theme.SuccessMint
import com.schedule.app.ui.theme.SuccessMintBright
import com.schedule.app.ui.theme.SuccessMintDarkBg
import com.schedule.app.ui.theme.SuccessMintLight
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Константы дней недели
// ─────────────────────────────────────────────────────────────────────────────

private val WEEK_DAYS = listOf(
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
)

private val WEEK_DAYS_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun todayIndex(): Int = LocalDate.now().dayOfWeek.value - 1
private fun tomorrowIndex(): Int = LocalDate.now().dayOfWeek.value % 7

// Шаг в истории навигации по расписанию
private data class NavStep(val tab: Int, val dayIdx: Int)

// ─────────────────────────────────────────────────────────────────────────────
// MainScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToEditor: () -> Unit
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceRole  by viewModel.deviceRole.collectAsStateWithLifecycle()
    val syncStatus  by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncState   by viewModel.syncState.collectAsStateWithLifecycle()
    val syncError   by viewModel.syncError.collectAsStateWithLifecycle()
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val fontScale   by viewModel.fontScaleFlow().collectAsStateWithLifecycle(initialValue = 1.0f)

    val isDark = LocalIsDarkTheme.current
    val isParentMode = isAdminMode || deviceRole == "SERVER"
    val context = LocalContext.current

    val tabs = listOf("День", "Неделя", "Месяц")
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDayIdx by remember { mutableStateOf(tomorrowIndex().coerceIn(0, 6)) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    var showFontSizeDialog by remember { mutableStateOf(false) }

    // История переходов (вкладка + день)
    val navHistory = remember { mutableStateListOf(NavStep(tab = 0, dayIdx = selectedDayIdx)) }

    // Список раскрытых уроков (номера)
    val expandedLessons = remember(selectedDayIdx, selectedTab) { mutableStateListOf<Int>() }

    var lastBackPressTime by remember { mutableStateOf(0L) }

    fun navigateTo(tab: Int, dayIdx: Int) {
        if (selectedTab != tab || selectedDayIdx != dayIdx) {
            selectedTab = tab
            selectedDayIdx = dayIdx
            navHistory.add(NavStep(tab, dayIdx))
        }
    }

    // ── Пошаговая обработка кнопки «Назад» ──────────────────────────────────
    BackHandler {
        when {
            // 1. Если открыт диалог шрифта — закрываем его
            showFontSizeDialog -> {
                showFontSizeDialog = false
            }
            // 2. Если открыт чек-лист какого-либо урока — сворачиваем его
            expandedLessons.isNotEmpty() -> {
                expandedLessons.removeAt(expandedLessons.lastIndex)
            }
            // 3. Если есть история переходов (например, перешли с Дня на Неделю или выбрали другой день) — делаем шаг назад
            navHistory.size > 1 -> {
                navHistory.removeAt(navHistory.lastIndex)
                val prev = navHistory.last()
                selectedTab = prev.tab
                selectedDayIdx = prev.dayIdx
            }
            // 4. Если мы на неделе или месяце без истории — возвращаемся на День
            selectedTab != 0 -> {
                selectedTab = 0
            }
            // 5. Если мы на начальном экране — защита от случайного закрытия (двойное нажатие)
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Нажмите назад ещё раз для выхода", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showFontSizeDialog) {
        FontSizeSelectorDialog(
            currentScale = fontScale,
            onSelectScale = { newScale ->
                viewModel.saveFontScale(newScale)
                showFontSizeDialog = false
            },
            onDismiss = { showFontSizeDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Расписание",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        // Облачная синхронизация
                        CloudSyncPill(
                            syncState = syncState,
                            syncStatus = syncStatus,
                            hasError = syncError != null,
                            onSyncClick = { viewModel.manualSync() }
                        )

                        Spacer(Modifier.width(8.dp))

                        // Быстрая кнопка размера текста «Аа»
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { showFontSizeDialog = true },
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF242834) else Color(0xFFECEFF5),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFE0E5EE))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Аа",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF161922)
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Кнопка настроек с высокой контрастностью шестеренки
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateToSettings),
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF242834) else Color(0xFFECEFF5),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFE0E5EE))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Настройки",
                                    tint = if (isDark) Color.White else Color(0xFF161922),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                // Ошибка синхронизации
                AnimatedVisibility(visible = syncError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = syncError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Apple Segmented Control
                AppleSegmentedControl(
                    items = tabs,
                    selectedIndex = selectedTab,
                    onItemSelected = { newTab ->
                        navigateTo(tab = newTab, dayIdx = selectedDayIdx)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        },
        floatingActionButton = {
            if (isParentMode) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToEditor,
                    icon = {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            "Редактировать",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = if (isDark) AccentDark else Accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = if (isDark) AccentDark else Accent)
                }

                is UiState.Error -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center
                ) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> when (selectedTab) {
                    0 -> DayView(
                        schedule = state.schedule,
                        selectedDayIdx = selectedDayIdx,
                        expandedLessons = expandedLessons,
                        onPrev = { if (selectedDayIdx > 0) navigateTo(0, selectedDayIdx - 1) },
                        onNext = { if (selectedDayIdx < WEEK_DAYS.size - 1) navigateTo(0, selectedDayIdx + 1) }
                    )
                    1 -> WeekView(
                        schedule = state.schedule,
                        todayIdx = todayIndex(),
                        onDayClick = { idx ->
                            navigateTo(tab = 0, dayIdx = idx)
                        }
                    )
                    2 -> MonthView(
                        schedule = state.schedule,
                        currentMonth = currentMonth,
                        onPrevMonth = { currentMonth = currentMonth.minusMonths(1) },
                        onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                        onDayClick = { weekDayIdx ->
                            val safeIdx = weekDayIdx.coerceIn(0, WEEK_DAYS.size - 1)
                            navigateTo(tab = 0, dayIdx = safeIdx)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог выбора масштаба текста
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FontSizeSelectorDialog(
    currentScale: Float,
    onSelectScale: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val options = listOf(
        1.00f to "Обычный (100%)",
        1.15f to "Средний (115%)",
        1.30f to "Крупный (130%)",
        1.45f to "Очень крупный (145%)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FormatSize,
                    contentDescription = null,
                    tint = if (isDark) AccentDark else Accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Размер шрифта",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Выберите комфортный размер текста для уроков и списков:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(4.dp))

                options.forEach { (scale, label) ->
                    val isSelected = kotlin.math.abs(currentScale - scale) < 0.05f
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectScale(scale) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            if (isDark) AccentDark.copy(alpha = 0.2f) else Accent.copy(alpha = 0.12f)
                        } else {
                            if (isDark) Color(0xFF242834) else Color(0xFFF3F5FA)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) (if (isDark) AccentDark else Accent)
                            else (if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF))
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    if (isDark) AccentDark else Accent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (isDark) AccentDark else Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Apple-Style Капсульный переключатель (высокая контрастность)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppleSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    val containerColor = if (isDark) SegmentContainerDark else SegmentContainer
    val activeTabColor = if (isDark) Color(0xFF323846) else Color.White
    val activeBorder   = if (isDark) BorderStroke(1.dp, Color(0xFF454E60)) else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2E3342) else Color(0xFFDEE3ED))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onItemSelected(index)
                            }
                        },
                    shape = RoundedCornerShape(11.dp),
                    color = if (isSelected) activeTabColor else Color.Transparent,
                    shadowElevation = if (isSelected && !isDark) 2.dp else 0.dp,
                    border = if (isSelected) activeBorder else null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) {
                                if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Капсула статуса синхронизации
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloudSyncPill(
    syncState: SyncState,
    syncStatus: String,
    hasError: Boolean,
    onSyncClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    val containerColor = when {
        hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        syncState == SyncState.SYNCING -> (if (isDark) AccentDark else Accent).copy(alpha = 0.20f)
        syncState == SyncState.SUCCESS -> (if (isDark) SuccessMintDarkBg else SuccessMintLight)
        else -> if (isDark) Color(0xFF242834) else Color(0xFFECEFF5)
    }

    val contentColor = when {
        hasError -> MaterialTheme.colorScheme.error
        syncState == SyncState.SYNCING -> if (isDark) AccentDark else Accent
        syncState == SyncState.SUCCESS -> if (isDark) SuccessMintBright else SuccessMint
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSyncClick()
            },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFE0E5EE))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (syncState) {
                SyncState.SYNCING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Обновление…",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                SyncState.ERROR -> {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Ошибка",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                SyncState.SUCCESS -> {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(contentColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Актуально ☁️",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                SyncState.IDLE -> {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Облако",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Заголовок дня
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayHeader(
    dayName: String,
    dateText: String,
    tag: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    prevEnabled: Boolean,
    nextEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (tag != null) {
                Text(
                    text = tag.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) AccentDark else Accent,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(1.dp))
            }
            Text(
                text = dayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (dateText.isNotBlank()) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Круглые кнопки навигации со стрелками
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = prevEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPrev()
                    },
                shape = CircleShape,
                color = if (prevEnabled) {
                    if (isDark) Color(0xFF242834) else Color.White
                } else {
                    if (isDark) Color(0xFF191B22) else Color(0xFFF3F5FA)
                },
                shadowElevation = if (prevEnabled && !isDark) 1.5.dp else 0.dp,
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Предыдущий день",
                        modifier = Modifier.size(20.dp),
                        tint = if (prevEnabled) {
                            if (isDark) Color.White else Color(0xFF161922)
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = nextEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    },
                shape = CircleShape,
                color = if (nextEnabled) {
                    if (isDark) Color(0xFF242834) else Color.White
                } else {
                    if (isDark) Color(0xFF191B22) else Color(0xFFF3F5FA)
                },
                shadowElevation = if (nextEnabled && !isDark) 1.5.dp else 0.dp,
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Следующий день",
                        modifier = Modifier.size(20.dp),
                        tint = if (nextEnabled) {
                            if (isDark) Color.White else Color(0xFF161922)
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Баннер прогресса рюкзака (высокий контраст)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackpackSummaryCard(
    checkedCount: Int,
    totalCount: Int
) {
    if (totalCount == 0) return

    val isDark = LocalIsDarkTheme.current
    val progress = checkedCount.toFloat() / totalCount
    val isComplete = checkedCount == totalCount
    val percent = (progress * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "backpackProgress"
    )

    val cardBg = when {
        isComplete -> if (isDark) SuccessMintDarkBg else SuccessMintLight
        else -> MaterialTheme.colorScheme.surface
    }

    val cardBorder = when {
        isComplete -> BorderStroke(1.5.dp, if (isDark) SuccessMintBright else SuccessMint)
        else -> BorderStroke(1.dp, if (isDark) Color(0xFF2E3342) else Color(0xFFE2E6EF))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        shadowElevation = if (isComplete || isDark) 0.dp else 1.5.dp,
        border = cardBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎒", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isComplete) "Рюкзак собран на 100%!" else "Сбор рюкзака",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isComplete -> if (isDark) SuccessMintBright else Color(0xFF0D5E40)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = if (isComplete) "Все $totalCount предметов на месте ✓" else "Собрано: $checkedCount из $totalCount (осталось ${totalCount - checkedCount})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                isComplete -> if (isDark) Color(0xFFB4EBD5) else Color(0xFF1B7050)
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Пилл-бейдж
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isComplete -> if (isDark) SuccessMintBright else SuccessMint
                        checkedCount > 0 -> if (isDark) PendingAmberDarkBg else PendingAmberLight
                        else -> if (isDark) NeutralPillDarkBg else NeutralPillLight
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isComplete -> Color.Transparent
                            checkedCount > 0 -> if (isDark) PendingAmberBright.copy(alpha = 0.5f) else PendingAmber.copy(alpha = 0.4f)
                            else -> if (isDark) Color(0xFF333A4A) else Color(0xFFD4DAE5)
                        }
                    )
                ) {
                    Text(
                        text = if (isComplete) "Готово ✓" else "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isComplete -> if (isDark) Color(0xFF101216) else Color.White
                            checkedCount > 0 -> if (isDark) PendingAmberBright else PendingAmber
                            else -> if (isDark) NeutralPillBright else NeutralPill
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Прогресс-бар с закруглением
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    isComplete -> if (isDark) SuccessMintBright else SuccessMint
                    else -> if (isDark) AccentDark else Accent
                },
                trackColor = when {
                    isComplete -> if (isDark) SuccessMintBright.copy(alpha = 0.2f) else SuccessMint.copy(alpha = 0.2f)
                    else -> if (isDark) Color(0xFF262B38) else Color(0xFFE2E6EF)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Режим: ДЕНЬ
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayView(
    schedule: Schedule,
    selectedDayIdx: Int,
    expandedLessons: MutableList<Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val todayIdx = todayIndex()
    val tomorrowIdx = tomorrowIndex()
    val dayName  = WEEK_DAYS.getOrElse(selectedDayIdx) { WEEK_DAYS[0] }
    val day      = schedule.days.find { it.name.trim().equals(dayName.trim(), ignoreCase = true) }

    val today = LocalDate.now()
    val dayOffset = selectedDayIdx - todayIdx
    val targetDate = today.plusDays(dayOffset.toLong())
    val dateText = targetDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))

    val tag = when (selectedDayIdx) {
        todayIdx -> "Сегодня"
        tomorrowIdx -> "На завтра"
        else -> null
    }

    val lessons = day?.lessons?.sortedBy { it.number } ?: emptyList()
    val checkedState = remember(selectedDayIdx) { mutableStateMapOf<String, Boolean>() }

    val allItemsKeys = lessons.flatMap { lesson ->
        lesson.items.map { "${lesson.number}_$it" }
    }
    val totalCount = allItemsKeys.size
    val checkedCount = allItemsKeys.count { checkedState[it] == true }

    Column(modifier = Modifier.fillMaxSize()) {
        DayHeader(
            dayName = dayName,
            dateText = dateText,
            tag = tag,
            onPrev = onPrev,
            onNext = onNext,
            prevEnabled = selectedDayIdx > 0,
            nextEnabled = selectedDayIdx < WEEK_DAYS.size - 1
        )

        if (lessons.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Уроков нет — выходной!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            BackpackSummaryCard(
                checkedCount = checkedCount,
                totalCount = totalCount
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lessons, key = { it.number }) { lesson ->
                    val isExpanded = expandedLessons.contains(lesson.number)
                    LessonExpandableCard(
                        lesson = lesson,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            if (isExpanded) {
                                expandedLessons.remove(lesson.number)
                            } else {
                                expandedLessons.add(lesson.number)
                            }
                        },
                        isItemChecked = { item -> checkedState["${lesson.number}_$item"] ?: false },
                        onToggleItem = { item ->
                            val key = "${lesson.number}_$item"
                            checkedState[key] = !(checkedState[key] ?: false)
                        }
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Карточка урока с контролируемым раскрытием (BackHandler-friendly)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LessonExpandableCard(
    lesson: Lesson,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isItemChecked: (String) -> Boolean,
    onToggleItem: (String) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "arrow"
    )
    val haptic = LocalHapticFeedback.current

    val totalItems = lesson.items.size
    val doneCount = if (totalItems > 0) lesson.items.count { isItemChecked(it) } else 0
    val allDone = totalItems > 0 && doneCount == totalItems

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDark) 0.dp else 1.5.dp,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
    ) {
        Column {
            // ── Заголовок урока ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = totalItems > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleExpand()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Номер в мягком кружке
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) AccentDark.copy(alpha = 0.22f) else Accent.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lesson.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color(0xFF96ACFF) else Accent
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Название и время
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (lesson.time.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = lesson.time,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Статусный бейдж готовности
                if (totalItems > 0) {
                    val pillBg = when {
                        allDone -> if (isDark) SuccessMintDarkBg else SuccessMintLight
                        doneCount > 0 -> if (isDark) PendingAmberDarkBg else PendingAmberLight
                        else -> if (isDark) NeutralPillDarkBg else NeutralPillLight
                    }
                    val pillColor = when {
                        allDone -> if (isDark) SuccessMintBright else SuccessMint
                        doneCount > 0 -> if (isDark) PendingAmberBright else PendingAmber
                        else -> if (isDark) NeutralPillBright else NeutralPill
                    }
                    val pillText = when {
                        allDone -> "$doneCount/$totalItems ✓ Готово"
                        else -> "$doneCount/$totalItems"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = pillBg,
                        border = BorderStroke(
                            1.dp,
                            when {
                                allDone -> if (isDark) SuccessMintBright.copy(alpha = 0.5f) else SuccessMint.copy(alpha = 0.4f)
                                doneCount > 0 -> if (isDark) PendingAmberBright.copy(alpha = 0.5f) else PendingAmber.copy(alpha = 0.4f)
                                else -> if (isDark) Color(0xFF333A4A) else Color(0xFFD4DAE5)
                            }
                        )
                    ) {
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = pillColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotation)
                    )
                }
            }

            // ── Раскрывающийся список «Что взять» с четким контрастом ───────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) InnerBoxDarkBg else InnerBoxLightBg,
                    border = BorderStroke(1.dp, if (isDark) InnerBoxDarkBorder else InnerBoxLightBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Что взять к уроку:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF96ACFF) else Accent,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        lesson.items.forEach { item ->
                            val isChecked = isItemChecked(item)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleItem(item)
                                    }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Круглый чекбокс высокой четкости
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChecked) (if (isDark) SuccessMintBright else SuccessMint)
                                            else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFF101216) else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(24.dp),
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(
                                                2.dp,
                                                if (isDark) Color(0xFF657088) else Color(0xFF929DB4)
                                            )
                                        ) {}
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium,
                                    color = if (isChecked) {
                                        if (isDark) Color(0xFF8692A6) else Color(0xFF707A8E)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Режим: НЕДЕЛЯ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeekView(
    schedule: Schedule,
    todayIdx: Int,
    onDayClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(WEEK_DAYS.size) { idx ->
            val dayName = WEEK_DAYS[idx]
            val shortName = WEEK_DAYS_SHORT[idx]
            val day = schedule.days.find { it.name.trim().equals(dayName.trim(), ignoreCase = true) }
            val lessons = day?.lessons?.sortedBy { it.number } ?: emptyList()
            val isToday = idx == todayIdx

            WeekDayCard(
                shortName = shortName,
                fullName = dayName,
                lessons = lessons,
                isToday = isToday,
                onClick = { onDayClick(idx) }
            )
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun WeekDayCard(
    shortName: String,
    fullName: String,
    lessons: List<Lesson>,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    val surfaceColor = if (isToday) {
        if (isDark) AccentDark.copy(alpha = 0.15f) else Accent.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        shadowElevation = if (isToday && !isDark) 2.dp else 0.dp,
        border = BorderStroke(
            1.dp,
            if (isToday) (if (isDark) AccentDark.copy(alpha = 0.5f) else Accent.copy(alpha = 0.4f))
            else (if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) (if (isDark) AccentDark else Accent) else MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isDark) AccentDark else Accent
                    ) {
                        Text(
                            text = "СЕГОДНЯ",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            if (lessons.isEmpty()) {
                Text(
                    "Выходной",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    lessons.take(4).forEach { lesson ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                "${lesson.number}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                lesson.subject,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (lesson.time.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    lesson.time,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    if (lessons.size > 4) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "ещё ${lessons.size - 4}…",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isToday) (if (isDark) AccentDark else Accent) else (if (isDark) Color(0xFF262A36) else SegmentContainer),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(
                        "${lessons.size} ур.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Режим: МЕСЯЦ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthView(
    schedule: Schedule,
    currentMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (Int) -> Unit
) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentMonth.format(formatter).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPrevMonth()
                        },
                    shape = CircleShape,
                    color = if (isDark) Color(0xFF242834) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Предыдущий месяц",
                            modifier = Modifier.size(18.dp),
                            tint = if (isDark) Color.White else Color(0xFF161922)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextMonth()
                        },
                    shape = CircleShape,
                    color = if (isDark) Color(0xFF242834) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Следующий месяц",
                            modifier = Modifier.size(18.dp),
                            tint = if (isDark) Color.White else Color(0xFF161922)
                        )
                    }
                }
            }
        }

        // Дни недели
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            WEEK_DAYS_SHORT.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        val firstDay = currentMonth.atDay(1)
        val startOffset = firstDay.dayOfWeek.value - 1
        val daysInMonth = currentMonth.lengthOfMonth()
        val totalCells = startOffset + daysInMonth
        val weeks = (totalCells + 6) / 7

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(weeks) { weekIdx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0..6) {
                        val dayNum = weekIdx * 7 + col - startOffset + 1
                        val weekDayIdx = col

                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = currentMonth.atDay(dayNum)
                            val isToday = date == today
                            val dayName = WEEK_DAYS.getOrElse(col) { "" }
                            val hasLessons = schedule.days.any {
                                it.name.trim().equals(dayName.trim(), ignoreCase = true)
                                        && it.lessons.isNotEmpty()
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isToday) (if (isDark) AccentDark else Accent) else Color.Transparent
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDayClick(weekDayIdx)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onBackground,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                    if (hasLessons && !isToday) {
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isDark) SuccessMintBright else SuccessMint)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Статус-бар синхронизации
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SyncStatusBar(status: String, isServer: Boolean, errorMessage: String? = null) {
    val isDark = LocalIsDarkTheme.current
    val color = when {
        errorMessage != null -> MaterialTheme.colorScheme.error
        isServer             -> if (isDark) AccentDark else Accent
        else                 -> MaterialTheme.colorScheme.secondary
    }
    val displayText = errorMessage ?: status

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.12f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                displayText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}
