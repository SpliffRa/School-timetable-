package com.schedule.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.app.MainViewModel
import com.schedule.app.UiState
import com.schedule.app.data.model.Day
import com.schedule.app.data.model.Lesson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Вспомогательные функции разбора времени
// ─────────────────────────────────────────────────────────────────────────────

private data class TimeParts(
    val startH: Int, val startM: Int,
    val endH: Int,   val endM: Int
)

/** Разбирает "08:30–09:15" или "8:30-9:15" в четыре числа */
private fun parseTimeParts(time: String): TimeParts {
    val clean = time.trim()
    val sep = when {
        clean.contains('–') -> '–'
        clean.contains('-') -> '-'
        else -> null
    }
    return if (sep != null) {
        val idx = clean.indexOf(sep)
        val s = parseHM(clean.substring(0, idx))
        val e = parseHM(clean.substring(idx + 1))
        TimeParts(s.first, s.second, e.first, e.second)
    } else {
        val s = parseHM(clean)
        TimeParts(s.first, s.second, (s.first + 0).coerceAtMost(22), (s.second + 45).let { if (it >= 60) it - 60 else it })
    }
}

private fun parseHM(s: String): Pair<Int, Int> {
    val parts = s.trim().split(":")
    val h = parts.getOrElse(0) { "8" }.toIntOrNull() ?: 8
    val m = parts.getOrElse(1) { "0" }.take(2).toIntOrNull() ?: 0
    return h.coerceIn(0, 23) to m.coerceIn(0, 59)
}

private fun fmtTime(h: Int, m: Int) =
    "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"

/**
 * Возвращает стандартное время для урока по его номеру:
 * 1 урок: 08:30–09:05
 * 2 урок: 09:25–10:00
 * 3 урок: 10:20–10:55
 * 4 урок: 11:05–11:40
 * 5 урок: 11:50–12:25
 * 6 урок: 12:35–13:10
 * 7 урок: 13:20–13:55
 * 8 урок: 14:05–14:40
 */
private fun defaultTimeForLesson(number: Int): String = when (number) {
    1    -> "08:30–09:05"
    2    -> "09:25–10:00"
    3    -> "10:20–10:55"
    4    -> "11:05–11:40"
    5    -> "11:50–12:25"
    6    -> "12:35–13:10"
    7    -> "13:20–13:55"
    8    -> "14:05–14:40"
    else -> "08:30–09:05"
}

// ─────────────────────────────────────────────────────────────────────────────
// EditScheduleScreen — основной экран редактора
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditScheduleScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // ── Состояния диалогов ───────────────────────────────────────────────────
    var showAddDay    by remember { mutableStateOf(false) }
    var editingLesson by remember { mutableStateOf<Pair<String, Lesson?>?>(null) }  // (dayName, lesson|null)

    BackHandler {
        when {
            editingLesson != null -> editingLesson = null
            showAddDay -> showAddDay = false
            else -> onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Scaffold ──────────────────────────────────────────────────────────
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Редактор", style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDay = true }) {
                        Icon(Icons.Filled.Add, "Добавить день", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.secondary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        when (val state = uiState) {
            is UiState.Loading -> Box(
                Modifier.fillMaxSize().padding(paddingValues), Alignment.Center
            ) { CircularProgressIndicator() }

            is UiState.Error -> Box(
                Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), Alignment.Center
            ) { Text(state.message) }

            is UiState.Success -> {
                val schedule = state.schedule
                if (schedule.days.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(paddingValues), Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("📅", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Нет дней в расписании",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Нажмите кнопку ниже, чтобы сразу создать все дни с понедельника по воскресенье.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.restoreAllWeekDays() }) {
                                Icon(Icons.Filled.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Создать все 7 дней (Пн-Вс)")
                            }
                        }
                    }
                    return@Scaffold
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    schedule.days.forEach { day ->

                        // Заголовок дня
                        stickyHeader(key = "hdr_${day.name}") {
                            DayHeader(
                                name = day.name,
                                onAddLesson = { editingLesson = day.name to null },
                                onDeleteDay  = { viewModel.deleteDay(day.name) }
                            )
                        }

                        // Уроки
                        items(
                            items = day.lessons.sortedBy { it.number },
                            key = { "${day.name}_${it.number}" }
                        ) { lesson ->
                            LessonEditorCard(
                                lesson = lesson,
                                onEdit   = { editingLesson = day.name to lesson },
                                onDelete = { viewModel.deleteLesson(day.name, lesson.number) }
                            )
                        }

                        // Пустой день
                        if (day.lessons.isEmpty()) {
                            item(key = "empty_${day.name}") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Уроков пока нет",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        TextButton(onClick = { editingLesson = day.name to null }) {
                                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Добавить урок")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Диалог добавления дня ─────────────────────────────────────────────
    if (showAddDay) {
        val currentDays = (uiState as? UiState.Success)?.schedule?.days?.map { it.name } ?: emptyList()
        AddDayDialog(
            existingDays = currentDays,
            onConfirm = { name -> viewModel.addDay(name); showAddDay = false },
            onRestoreAll = { viewModel.restoreAllWeekDays(); showAddDay = false },
            onDismiss = { showAddDay = false }
        )
    }

    // ── Оверлей добавления/редактирования урока (адаптивен к экранной клавиатуре)
    editingLesson?.let { (dayName, lesson) ->
        val isNew = lesson == null

        // Для нового урока вычисляем следующий свободный номер и предзаполняем время
        val initialLesson = if (isNew) {
            val schedule = (uiState as? UiState.Success)?.schedule
            val existingNumbers = schedule?.days
                ?.find { it.name == dayName }
                ?.lessons?.map { it.number }
                ?: emptyList()
            val nextNum = (1..10).firstOrNull { it !in existingNumbers } ?: 1
            Lesson(
                number  = nextNum,
                time    = defaultTimeForLesson(nextNum),
                subject = "",
                items   = emptyList()
            )
        } else {
            lesson!!
        }

        LessonEditDialog(
            title = if (isNew) "Добавить урок" else "Изменить урок",
            initial = initialLesson,
            onConfirm = { updated ->
                if (isNew) viewModel.addLesson(dayName, updated)
                else viewModel.updateLesson(dayName, updated, oldNumber = lesson?.number ?: updated.number)
                editingLesson = null
            },
            onDismiss = { editingLesson = null }
        )
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// Заголовок дня в редакторе
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayHeader(
    name: String,
    onAddLesson: () -> Unit,
    onDeleteDay: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddLesson) {
                Icon(Icons.Filled.Add, "Добавить урок", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDeleteDay) {
                Icon(Icons.Filled.Delete, "Удалить день", tint = MaterialTheme.colorScheme.secondary)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Карточка урока в редакторе
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LessonEditorCard(
    lesson: Lesson,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    lesson.number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.subject.ifBlank { "(без названия)" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (lesson.time.isNotBlank()) {
                    Text(
                        lesson.time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (lesson.items.isNotEmpty()) {
                    Text(
                        "Вещи: ${lesson.items.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, "Изменить", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// Диалог добавления дня со списком дней недели
// ─────────────────────────────────────────────────────────────────────────────

private val ALL_WEEK_DAYS_ORDERED = listOf(
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
)

@Composable
private fun AddDayDialog(
    existingDays: List<String>,
    onConfirm: (String) -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val missingDays = remember(existingDays) {
        ALL_WEEK_DAYS_ORDERED.filter { weekDay ->
            existingDays.none { it.trim().equals(weekDay, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить учебный день") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (missingDays.isNotEmpty()) {
                    Text(
                        "Выберите день недели для быстрого добавления:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // Сетка/список кнопок доступных дней недели
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        missingDays.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { dayName ->
                                    Button(
                                        onClick = { onConfirm(dayName) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(dayName, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (missingDays.size >= 2) {
                        Button(
                            onClick = onRestoreAll,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Добавить все недостающие дни (Пн-Вс)")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                Text(
                    "Или введите своё название:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название дня") },
                    placeholder = { Text("Например: Факультатив") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Барабан выбора числа (WheelNumberPicker) с инерцией, snapping и haptics
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: List<Int>,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 38.dp,
    format: (Int) -> String = { it.toString().padStart(2, '0') }
) {
    val haptic = LocalHapticFeedback.current

    val totalHeight = itemHeight * 3
    val initialIndex = remember { range.indexOf(value).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Вычисляем индекс элемента, находящегося ближе всего к оптическому центру
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.firstVisibleItemIndex
            } else {
                val centerOffset = layoutInfo.viewportStartOffset +
                        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                visibleItems.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - centerOffset)
                }?.index ?: listState.firstVisibleItemIndex
            }
        }
    }

    // При внешнем изменении значения (шаблон, сброс, ввод номера)
    // синхронизируем позицию барабана без ложных промежуточных событий
    LaunchedEffect(value) {
        val targetIndex = range.indexOf(value)
        if (targetIndex >= 0 && !listState.isScrollInProgress) {
            val currentCenter = centeredIndex
            if (currentCenter != targetIndex) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Вызываем onValueChange ТОЛЬКО когда пользователь крутит барабан пальцем (isScrollInProgress)
    LaunchedEffect(listState) {
        snapshotFlow { centeredIndex to listState.isScrollInProgress }
            .collect { (idx, scrolling) ->
                if (scrolling && idx in range.indices) {
                    val newValue = range[idx]
                    if (newValue != value) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (_: Exception) {}
                        onValueChange(newValue)
                    }
                }
            }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // ▲ Кнопка шага назад / вверх
        IconButton(
            onClick = {
                val curIdx = range.indexOf(value).coerceAtLeast(0)
                val nextIdx = if (curIdx > 0) curIdx - 1 else range.lastIndex
                onValueChange(range[nextIdx])
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Предыдущее значение",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Барабан
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(totalHeight)
                .drawWithContent {
                    drawContent()
                    // Мягкое затенение верхнего и нижнего краев для эффекта цилиндра
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(surfaceColor.copy(alpha = 0.88f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.32f
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, surfaceColor.copy(alpha = 0.88f)),
                            startY = size.height * 0.68f,
                            endY = size.height
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Подложка активного значения по центру
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {}

            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                items(count = range.size) { index ->
                    val itemValue = range[index]
                    val isSelected = index == centeredIndex

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable {
                                if (index != centeredIndex) {
                                    onValueChange(itemValue)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = format(itemValue),
                            style = if (isSelected) {
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ▼ Кнопка шага вперед / вниз
        IconButton(
            onClick = {
                val curIdx = range.indexOf(value).coerceAtLeast(0)
                val nextIdx = if (curIdx < range.lastIndex) curIdx + 1 else 0
                onValueChange(range[nextIdx])
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Следующее значение",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог добавления / редактирования урока
// ─────────────────────────────────────────────────────────────────────────────

private val COMMON_SCHOOL_ITEMS = listOf(
    "Учебник", "Тетрадь", "Пенал", "Сменка", "Форма", "Дневник", "Альбом", "Краски", "Клей", "Ножницы"
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun LessonEditDialog(
    title: String,
    initial: Lesson,
    onConfirm: (Lesson) -> Unit,
    onDismiss: () -> Unit
) {
    // Перехват системной кнопки "Назад" для закрытия оверлея
    BackHandler(onBack = onDismiss)

    val tp = remember(initial.time) { parseTimeParts(initial.time) }

    var number    by remember { mutableStateOf(initial.number.toString()) }
    var subject   by remember { mutableStateOf(initial.subject) }
    var startH    by remember { mutableStateOf(tp.startH) }
    var startM    by remember { mutableStateOf(tp.startM) }
    var endH      by remember { mutableStateOf(tp.endH) }
    var endM      by remember { mutableStateOf(tp.endM) }
    var itemsText by remember { mutableStateOf(initial.items.joinToString("\n")) }

    fun applyTemplateForNumber(num: Int) {
        val defaultTime = defaultTimeForLesson(num)
        val p = parseTimeParts(defaultTime)
        startH = p.startH
        startM = p.startM
        endH = p.endH
        endM = p.endM
    }

    val currentLessonNum = number.toIntOrNull() ?: initial.number
    val templateTimeForCurrentNum = defaultTimeForLesson(currentLessonNum)
    val currentTimeStr = "${fmtTime(startH, startM)}–${fmtTime(endH, endM)}"
    val isUsingTemplate = currentTimeStr == templateTimeForCurrentNum

    // По умолчанию для стандартного шаблона скрываем громоздкие барабаны для экономии места на экране
    var isTimeCustomExpanded by remember { mutableStateOf(!isUsingTemplate) }

    val currentItems = remember(itemsText) {
        itemsText.lines().map { it.trim() }.filter { it.isNotBlank() }
    }

    fun toggleItem(item: String) {
        val list = itemsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (list.any { it.equals(item, ignoreCase = true) }) {
            list.removeAll { it.equals(item, ignoreCase = true) }
        } else {
            list.add(item)
        }
        itemsText = list.joinToString("\n")
    }

    val startTotalMinutes = startH * 60 + startM
    val endTotalMinutes = endH * 60 + endM
    val durationMinutes = if (endTotalMinutes >= startTotalMinutes) {
        endTotalMinutes - startTotalMinutes
    } else {
        (endTotalMinutes + 24 * 60) - startTotalMinutes
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Полупрозрачный фон (Scrim) на весь экран окна Activity
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // Контейнер, напрямую реагирующий на экранную клавиатуру (IME) внутри окна Activity
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = maxHeight

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = maxDialogHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* блокируем закрытие при клике по телу карточки */ }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Заголовок карточки с крестиком закрытия (всегда зафиксирован сверху)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Скроллируемое тело формы: сжимается при клавиатуре и позволяет проскроллить к любому полю
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Поле номера урока (при 1..8 автоматически ставит время по шаблону)
                        OutlinedTextField(
                            value = number,
                            onValueChange = { input ->
                                val filtered = input.filter { c -> c.isDigit() }.take(2)
                                number = filtered
                                val n = filtered.toIntOrNull()
                                if (n != null && n in 1..8) {
                                    applyTemplateForNumber(n)
                                }
                            },
                            label = { Text("Номер урока (число)") },
                            placeholder = { Text("1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        // ── Предмет ───────────────────────────────────────────────
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Предмет") },
                            placeholder = { Text("Математика") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            )
                        )

                        // ── Время урока (Компактный вид + возможность раскрыть барабаны) ─────
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "Время: $currentTimeStr",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "⏱ $durationMinutes мин",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = { isTimeCustomExpanded = !isTimeCustomExpanded },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            if (isTimeCustomExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isTimeCustomExpanded) "Свернуть" else "Изменить",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                if (isUsingTemplate && !isTimeCustomExpanded) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "По шаблону ($templateTimeForCurrentNum)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Развернутые барабаны часов и минут
                                if (isTimeCustomExpanded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Начало
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "Начало",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                WheelNumberPicker(
                                                    value = startH,
                                                    onValueChange = { startH = it },
                                                    range = (0..23).toList()
                                                )
                                                Text(
                                                    ":",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                )
                                                WheelNumberPicker(
                                                    value = startM,
                                                    onValueChange = { startM = it },
                                                    range = (0..59).toList()
                                                )
                                            }
                                        }

                                        Text(
                                            "→",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(top = 16.dp)
                                        )

                                        // Конец
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "Конец",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                WheelNumberPicker(
                                                    value = endH,
                                                    onValueChange = { endH = it },
                                                    range = (0..23).toList()
                                                )
                                                Text(
                                                    ":",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                )
                                                WheelNumberPicker(
                                                    value = endM,
                                                    onValueChange = { endM = it },
                                                    range = (0..59).toList()
                                                )
                                            }
                                        }
                                    }

                                    // Индикатор / кнопка восстановления шаблона
                                    if (isUsingTemplate) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                "Время по шаблону ($templateTimeForCurrentNum)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Время изменено вручную",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            TextButton(
                                                onClick = { applyTemplateForNumber(currentLessonNum) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Filled.Restore, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "Шаблон ($templateTimeForCurrentNum)",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Что взять с собой ─────────────────────────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Что взять с собой:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Быстрый выбор популярных школьных вещей
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                COMMON_SCHOOL_ITEMS.forEach { item ->
                                    val isSelected = currentItems.any { it.equals(item, ignoreCase = true) }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { toggleItem(item) },
                                        label = { Text(item, style = MaterialTheme.typography.bodySmall) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = itemsText,
                                onValueChange = { itemsText = it },
                                label = { Text("Список вещей (каждое с новой строки)") },
                                placeholder = { Text("Учебник\nТетрадь\nПенал") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 75.dp, max = 120.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch {
                                                delay(200)
                                                bringIntoViewRequester.bringIntoView()
                                                scrollState.animateScrollTo(scrollState.maxValue)
                                            }
                                        }
                                    },
                                shape = MaterialTheme.shapes.small,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Sentences
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Кнопки (зафиксированы внизу карточки над клавиатурой)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val timeStr = "${fmtTime(startH, startM)}–${fmtTime(endH, endM)}"
                                onConfirm(
                                    Lesson(
                                        number = number.toIntOrNull() ?: initial.number,
                                        time = timeStr,
                                        subject = subject.trim(),
                                        items = itemsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                )
                            },
                            enabled = subject.isNotBlank()
                        ) { Text("Сохранить") }
                    }
                }
            }
        }
    }
}
