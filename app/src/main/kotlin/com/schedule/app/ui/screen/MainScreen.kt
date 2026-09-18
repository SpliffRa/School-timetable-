package com.schedule.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.app.MainViewModel
import com.schedule.app.SyncState
import com.schedule.app.UiState
import com.schedule.app.data.model.Lesson
import com.schedule.app.data.model.Schedule
import kotlinx.coroutines.launch
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
    val editorPin   by viewModel.editorPinFlow().collectAsStateWithLifecycle(initialValue = "")

    val scope = rememberCoroutineScope()

    // Режим папы — если роль SERVER сохранённая или временно разблокирован
    val isParentMode = isAdminMode || deviceRole == "SERVER"

    val tabs = listOf("День", "Неделя", "Месяц")
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDayIdx by remember { mutableStateOf(tomorrowIndex().coerceIn(0, 6)) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Расписание",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        // ── Синхронизация
                        when (syncState) {
                            SyncState.SYNCING -> Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            SyncState.SUCCESS -> IconButton(onClick = {}) {
                                Icon(
                                    Icons.Filled.Check,
                                    "Синхронизовано",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            SyncState.ERROR -> IconButton(onClick = { viewModel.manualSync() }) {
                                Icon(
                                    Icons.Filled.Warning,
                                    "Ошибка",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            SyncState.IDLE -> IconButton(onClick = { viewModel.manualSync() }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    "Синхронизировать",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        // ── Настройки
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Filled.Settings, "Настройки")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.secondary
                    )
                )
                SyncStatusBar(
                    status = syncStatus,
                    isServer = isParentMode,
                    errorMessage = syncError
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isParentMode) {
                FloatingActionButton(
                    onClick = onNavigateToEditor,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        "Редактировать",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {

                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is UiState.Error -> Box(
                    Modifier.fillMaxSize().padding(24.dp), Alignment.Center
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
                        onDayClick = { idx -> selectedDayIdx = idx; selectedTab = 0 }
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
// Навигационный заголовок ← Название →
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavHeader(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    prevEnabled: Boolean = true,
    nextEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev, enabled = prevEnabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Предыдущий",
                tint = if (prevEnabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Следующий",
                tint = if (nextEnabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
    val today    = todayIndex()
    val tomorrow = tomorrowIndex()
    val dayName  = WEEK_DAYS.getOrElse(selectedDayIdx) { WEEK_DAYS[0] }
    val day      = schedule.days.find { it.name.trim().equals(dayName.trim(), ignoreCase = true) }

    // Формируем заголовок с пометкой «сегодня» / «на завтра»
    val title = when (selectedDayIdx) {
        today    -> "$dayName (сегодня)"
        tomorrow -> "$dayName (на завтра)"
        else     -> dayName
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NavHeader(
            title = title,
            onPrev = onPrev,
            onNext = onNext,
            prevEnabled = selectedDayIdx > 0,
            nextEnabled = selectedDayIdx < WEEK_DAYS.size - 1
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        val lessons = day?.lessons?.sortedBy { it.number } ?: emptyList()

        if (lessons.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Уроков нет",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lessons, key = { it.number }) { lesson ->
                    LessonExpandableCard(lesson = lesson)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Карточка урока с раскрывающимся чек-листом
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LessonExpandableCard(lesson: Lesson) {
    var expanded by remember { mutableStateOf(false) }
    val checkedItems = remember(lesson.subject) { mutableStateMapOf<String, Boolean>() }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column {
            // ── Заголовок урока ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = lesson.items.isNotEmpty()) { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Номер в кружке
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lesson.number.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.subject,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                // Счётчик и стрелка
                if (lesson.items.isNotEmpty()) {
                    val done = checkedItems.values.count { it }
                    val allDone = done == lesson.items.size
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (allDone)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$done/${lesson.items.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allDone)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp).rotate(rotation)
                    )
                }
            }

            // ── Раскрывающийся чек-лист ───────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Что взять:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    lesson.items.forEach { item ->
                        val isChecked = checkedItems[item] ?: false
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkedItems[item] = !isChecked }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checkedItems[item] = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isChecked) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (isChecked) TextDecoration.LineThrough
                                                 else TextDecoration.None
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
        item { Spacer(Modifier.height(80.dp)) }
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
    val surfaceColor = if (isToday)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = surfaceColor,
        shadowElevation = if (isToday) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Сокращение дня
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
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
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                "${lesson.number}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                lesson.subject,
                                style = MaterialTheme.typography.bodyMedium,
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
                        Text(
                            "ещё ${lessons.size - 4}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                // Бейдж с числом уроков
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isToday) 0.2f else 0.1f),
                    modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${lessons.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Режим: МЕСЯЦ (календарная сетка)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthView(
    schedule: Schedule,
    currentMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (Int) -> Unit  // weekDayIdx 0=Пн..6=Вс
) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))

    Column(modifier = Modifier.fillMaxSize()) {
        NavHeader(
            title = currentMonth.format(formatter).replaceFirstChar { it.uppercase() },
            onPrev = onPrevMonth,
            onNext = onNextMonth
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Заголовки дней недели
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            WEEK_DAYS_SHORT.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Календарная сетка
        val firstDay = currentMonth.atDay(1)
        val startOffset = firstDay.dayOfWeek.value - 1  // 0 = Пн
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
                        val weekDayIdx = col // 0=Пн..6=Вс

                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = currentMonth.atDay(dayNum)
                            val isToday = date == today
                            // Есть ли уроки в этот день недели
                            val dayName = WEEK_DAYS.getOrElse(col) { "" }
                            val hasLessons = schedule.days.any {
                                it.name.trim().equals(dayName.trim(), ignoreCase = true)
                                        && it.lessons.isNotEmpty()
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { onDayClick(weekDayIdx) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onBackground,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (hasLessons && !isToday) {
                                        Box(
                                            Modifier.size(4.dp).clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Статус-бар синхронизации
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SyncStatusBar(status: String, isServer: Boolean, errorMessage: String? = null) {
    val color = when {
        errorMessage != null -> MaterialTheme.colorScheme.error
        isServer             -> MaterialTheme.colorScheme.primary
        else                 -> MaterialTheme.colorScheme.secondary
    }
    val displayText = errorMessage ?: status

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(displayText, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог ввода PIN-кода для режима администратора / настроек
// ─────────────────────────────────────────────────────────────────────────────



