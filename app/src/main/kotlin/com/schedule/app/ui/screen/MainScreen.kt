package com.schedule.app.ui.screen

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.schedule.app.ui.theme.NeutralPill
import com.schedule.app.ui.theme.NeutralPillLight
import com.schedule.app.ui.theme.PendingAmber
import com.schedule.app.ui.theme.PendingAmberLight
import com.schedule.app.ui.theme.SegmentContainer
import com.schedule.app.ui.theme.SuccessMint
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

/** Возвращает индекс 0..6 для текущего дня недели (0 = Понедельник) */
private fun todayIndex(): Int = LocalDate.now().dayOfWeek.value - 1

/** Возвращает индекс 0..6 для завтрашнего дня (0 = Понедельник) */
private fun tomorrowIndex(): Int = LocalDate.now().dayOfWeek.value % 7

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

    val isParentMode = isAdminMode || deviceRole == "SERVER"

    val tabs = listOf("День", "Неделя", "Месяц")
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDayIdx by remember { mutableStateOf(tomorrowIndex().coerceIn(0, 6)) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Верхний бар с крупным заголовком и статусом синхронизации
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
                        // Капсульный индикатор статуса облака
                        CloudSyncPill(
                            syncState = syncState,
                            syncStatus = syncStatus,
                            hasError = syncError != null,
                            onSyncClick = { viewModel.manualSync() }
                        )

                        Spacer(Modifier.width(8.dp))

                        // Кнопка настроек
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateToSettings),
                            shape = CircleShape,
                            color = SegmentContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Настройки",
                                    tint = MaterialTheme.colorScheme.onSurface,
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

                // Баннер ошибки синхронизации (если возникла)
                AnimatedVisibility(visible = syncError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Apple-style капсульный переключатель вкладок
                AppleSegmentedControl(
                    items = tabs,
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it },
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
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    containerColor = Accent,
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
                    CircularProgressIndicator(color = Accent)
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
                        onPrev = { if (selectedDayIdx > 0) selectedDayIdx-- },
                        onNext = { if (selectedDayIdx < WEEK_DAYS.size - 1) selectedDayIdx++ }
                    )
                    1 -> WeekView(
                        schedule = state.schedule,
                        todayIdx = todayIndex(),
                        onDayClick = { idx ->
                            selectedDayIdx = idx
                            selectedTab = 0
                        }
                    )
                    2 -> MonthView(
                        schedule = state.schedule,
                        currentMonth = currentMonth,
                        onPrevMonth = { currentMonth = currentMonth.minusMonths(1) },
                        onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                        onDayClick = { weekDayIdx ->
                            selectedDayIdx = weekDayIdx.coerceIn(0, WEEK_DAYS.size - 1)
                            selectedTab = 0
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Apple-Style Капсульный переключатель (Segmented Control)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppleSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = SegmentContainer
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
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Капсула статуса синхронизации в шапке
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloudSyncPill(
    syncState: SyncState,
    syncStatus: String,
    hasError: Boolean,
    onSyncClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val containerColor = when {
        hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        syncState == SyncState.SYNCING -> Accent.copy(alpha = 0.12f)
        syncState == SyncState.SUCCESS -> SuccessMintLight
        else -> SegmentContainer
    }

    val contentColor = when {
        hasError -> MaterialTheme.colorScheme.error
        syncState == SyncState.SYNCING -> Accent
        syncState == SyncState.SUCCESS -> SuccessMint
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
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (syncState) {
                SyncState.SYNCING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Обновление…",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                SyncState.ERROR -> {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Ошибка",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                SyncState.SUCCESS -> {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(contentColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Актуально ☁️",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                SyncState.IDLE -> {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Облако",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Apple Large Title Заголовок дня со стрелками перехода
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
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    letterSpacing = 1.1.sp
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
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Круглые кнопки навигации
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(enabled = prevEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPrev()
                    },
                shape = CircleShape,
                color = if (prevEnabled) MaterialTheme.colorScheme.surface else SegmentContainer.copy(alpha = 0.5f),
                shadowElevation = if (prevEnabled) 1.5.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Предыдущий день",
                        modifier = Modifier.size(18.dp),
                        tint = if (prevEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(enabled = nextEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    },
                shape = CircleShape,
                color = if (nextEnabled) MaterialTheme.colorScheme.surface else SegmentContainer.copy(alpha = 0.5f),
                shadowElevation = if (nextEnabled) 1.5.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Следующий день",
                        modifier = Modifier.size(18.dp),
                        tint = if (nextEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Баннер прогресса рюкзака на день (Apple-Style Widget)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackpackSummaryCard(
    checkedCount: Int,
    totalCount: Int
) {
    if (totalCount == 0) return

    val progress = checkedCount.toFloat() / totalCount
    val isComplete = checkedCount == totalCount
    val percent = (progress * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "backpackProgress"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isComplete) SuccessMintLight else MaterialTheme.colorScheme.surface,
        shadowElevation = if (isComplete) 0.dp else 1.5.dp,
        border = if (isComplete) BorderStroke(1.dp, SuccessMint.copy(alpha = 0.3f)) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎒", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isComplete) "Рюкзак собран на 100%!" else "Сбор рюкзака",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isComplete) SuccessMint else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isComplete) "Все $totalCount предметов на месте ✓" else "Собрано: $checkedCount из $totalCount (осталось ${totalCount - checkedCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isComplete) SuccessMint.copy(alpha = 0.85f) else MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Пилл-бейдж
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isComplete -> SuccessMint
                        checkedCount > 0 -> PendingAmberLight
                        else -> NeutralPillLight
                    }
                ) {
                    Text(
                        text = if (isComplete) "Готово ✓" else "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isComplete -> Color.White
                            checkedCount > 0 -> PendingAmber
                            else -> NeutralPill
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                color = if (isComplete) SuccessMint else Accent,
                trackColor = if (isComplete) SuccessMint.copy(alpha = 0.2f) else SegmentContainer
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
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val todayIdx = todayIndex()
    val tomorrowIdx = tomorrowIndex()
    val dayName  = WEEK_DAYS.getOrElse(selectedDayIdx) { WEEK_DAYS[0] }
    val day      = schedule.days.find { it.name.trim().equals(dayName.trim(), ignoreCase = true) }

    // Локализованная дата для выбранного дня
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

    // Состояние чек-боксов дня (ключ: "${lesson.number}_${item}")
    val checkedState = remember(selectedDayIdx) { mutableStateMapOf<String, Boolean>() }

    // Подсчёт суммарных предметов в рюкзаке
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            // Баннер рюкзака
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
                    LessonExpandableCard(
                        lesson = lesson,
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
// Карточка урока с Apple DNA (раскрывающийся чек-лист и статусный бейдж)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LessonExpandableCard(
    lesson: Lesson,
    isItemChecked: (String) -> Boolean,
    onToggleItem: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
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
        shadowElevation = 1.5.dp
    ) {
        Column {
            // ── Заголовок урока ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = totalItems > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        expanded = !expanded
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Номер в мягком кружке
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lesson.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Accent
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Название и время
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (lesson.time.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = lesson.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Статусный бейдж готовности
                if (totalItems > 0) {
                    val pillBg = when {
                        allDone -> SuccessMintLight
                        doneCount > 0 -> PendingAmberLight
                        else -> NeutralPillLight
                    }
                    val pillColor = when {
                        allDone -> SuccessMint
                        doneCount > 0 -> PendingAmber
                        else -> NeutralPill
                    }
                    val pillText = when {
                        allDone -> "$doneCount/$totalItems ✓ Готово"
                        else -> "$doneCount/$totalItems"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = pillBg
                    ) {
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = pillColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotation)
                    )
                }
            }

            // ── Раскрывающийся список «Что взять» ─────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SegmentContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Что взять к уроку:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp)
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
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Круглый Apple-style чекбокс
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isChecked) SuccessMint else Color.Transparent
                                    )
                                    .then(
                                        if (!isChecked) {
                                            Modifier.background(
                                                color = Color.Transparent,
                                                shape = CircleShape
                                            )
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(22.dp),
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, NeutralPill.copy(alpha = 0.45f))
                                    ) {}
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isChecked) {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
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

    val surfaceColor = if (isToday) {
        Accent.copy(alpha = 0.08f)
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
        shadowElevation = if (isToday) 2.dp else 1.dp,
        border = if (isToday) BorderStroke(1.dp, Accent.copy(alpha = 0.25f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Сокращение дня
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp)
            ) {
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) Accent else MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Accent
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

            // Список уроков
            if (lessons.isEmpty()) {
                Text(
                    "Выходной",
                    style = MaterialTheme.typography.bodyMedium,
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
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                lesson.subject,
                                style = MaterialTheme.typography.bodyMedium,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Бейдж с числом уроков
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isToday) Accent else SegmentContainer,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(
                        "${lessons.size} ур.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Режим: МЕСЯЦ (календарная сетка с Apple-style акцентами)
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPrevMonth()
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Предыдущий месяц",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextMonth()
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Следующий месяц",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
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
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Календарная сетка
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
                                        if (isToday) Accent else Color.Transparent
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onBackground,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (hasLessons && !isToday) {
                                        Box(
                                            Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(SuccessMint)
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
// Статус-бар синхронизации (для совместимости)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SyncStatusBar(status: String, isServer: Boolean, errorMessage: String? = null) {
    val color = when {
        errorMessage != null -> MaterialTheme.colorScheme.error
        isServer             -> Accent
        else                 -> MaterialTheme.colorScheme.secondary
    }
    val displayText = errorMessage ?: status

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.08f),
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
            Text(displayText, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
