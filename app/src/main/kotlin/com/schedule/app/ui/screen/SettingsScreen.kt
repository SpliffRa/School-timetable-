package com.schedule.app.ui.screen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.app.MainViewModel
import com.schedule.app.ui.theme.Accent
import com.schedule.app.ui.theme.AccentDark
import com.schedule.app.ui.theme.InnerBoxDarkBg
import com.schedule.app.ui.theme.InnerBoxDarkBorder
import com.schedule.app.ui.theme.InnerBoxLightBg
import com.schedule.app.ui.theme.InnerBoxLightBorder
import com.schedule.app.ui.theme.LocalIsDarkTheme
import com.schedule.app.ui.theme.SuccessMint
import com.schedule.app.ui.theme.SuccessMintBright
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: () -> Unit = {}
) {
    val currentRole by viewModel.deviceRole.collectAsStateWithLifecycle()
    val currentSyncCode by viewModel.syncCodeFlow().collectAsStateWithLifecycle(initialValue = "Алиса-2026")
    val fontScale by viewModel.fontScaleFlow().collectAsStateWithLifecycle(initialValue = 1.0f)
    val themeMode by viewModel.themeModeFlow().collectAsStateWithLifecycle(initialValue = "SYSTEM")

    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember(currentRole) { mutableStateOf(currentRole) }
    var familySyncCode by remember(currentSyncCode) { mutableStateOf(currentSyncCode) }
    var syncCodeSavedSuccess by remember { mutableStateOf(false) }

    // Диалоги
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordDialogError by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }

    // Пошаговая кнопка «Назад»
    BackHandler {
        when {
            showThemeDialog -> showThemeDialog = false
            showFontSizeDialog -> showFontSizeDialog = false
            showChangePinDialog -> showChangePinDialog = false
            showImportDialog -> showImportDialog = false
            showPasswordDialog -> {
                showPasswordDialog = false
                passwordDialogError = false
            }
            else -> onNavigateBack()
        }
    }

    // Диалог ввода пароля для перехода на «Телефон папы»
    if (showPasswordDialog) {
        PasswordPromptDialog(
            hasError = passwordDialogError,
            onConfirm = { entered ->
                scope.launch {
                    val ok = viewModel.switchToParentPhone(entered)
                    if (ok) {
                        showPasswordDialog = false
                        passwordDialogError = false
                        selectedTab = "SERVER"
                    } else {
                        passwordDialogError = true
                    }
                }
            },
            onDismiss = {
                showPasswordDialog = false
                passwordDialogError = false
            }
        )
    }

    // Диалог смены пароля
    if (showChangePinDialog) {
        ChangePinDialog(
            onSave = { newPin ->
                viewModel.savePassword(newPin)
                showChangePinDialog = false
            },
            onDismiss = { showChangePinDialog = false }
        )
    }

    // Диалог выбора размера шрифта
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

    // Диалог выбора темы оформления
    if (showThemeDialog) {
        ThemeSelectorDialog(
            currentThemeMode = themeMode,
            onSelectThemeMode = { newMode ->
                viewModel.saveThemeMode(newMode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Диалог импорта расписания
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт расписания") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Вставьте скопированный текст расписания:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            importError = false
                        },
                        label = { Text("Текст расписания") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        isError = importError,
                        supportingText = if (importError) {
                            { Text("Ошибка: неверный формат расписания", color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = viewModel.importScheduleJson(importText.trim())
                        if (ok) {
                            showImportDialog = false
                        } else {
                            importError = true
                        }
                    },
                    enabled = importText.isNotBlank()
                ) {
                    Text("Загрузить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = if (isDark) Color.White else Color(0xFF161922)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ═════════════════════════════════════════════════════════════════
            // 1. СЕКЦИЯ: ОБЛАЧНАЯ СИНХРОНИЗАЦИЯ (ГЛАВНАЯ НАСТРОЙКА)
            // ═════════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDark) AccentDark.copy(alpha = 0.2f) else Accent.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = if (isDark) AccentDark else Accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Код семьи (Синхронизация)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Связывает телефон и планшет через интернет",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = familySyncCode,
                            onValueChange = {
                                familySyncCode = it
                                syncCodeSavedSuccess = false
                            },
                            label = { Text("Код семьи") },
                            placeholder = { Text("Алиса-2026") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Button(
                            onClick = {
                                val code = familySyncCode.trim().ifBlank { "Алиса-2026" }
                                viewModel.saveSyncCode(code)
                                syncCodeSavedSuccess = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ОК")
                        }
                    }

                    if (syncCodeSavedSuccess) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Код сохранён ✓ Расписание будет обновляться автоматически.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) SuccessMintBright else SuccessMint,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Один и тот же код должен быть указан на обоих устройствах.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 2. СЕКЦИЯ: РЕЖИМ УСТРОЙСТВА
            // ═════════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Режим работы устройства",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == "CLIENT",
                            onClick = {
                                if (selectedTab != "CLIENT") {
                                    viewModel.switchToAliceTablet()
                                    selectedTab = "CLIENT"
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    Icons.Filled.TabletAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text("Планшет Алисы", fontWeight = FontWeight.SemiBold)
                        }

                        SegmentedButton(
                            selected = selectedTab == "SERVER",
                            onClick = {
                                if (selectedTab != "SERVER") {
                                    passwordDialogError = false
                                    showPasswordDialog = true
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(
                                    if (selectedTab == "SERVER") Icons.Filled.PhoneAndroid else Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text("Телефон папы", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (selectedTab == "CLIENT") {
                        Text(
                            text = "Режим Алисы: только просмотр уроков и сбор рюкзака. Случайное изменение расписания отключено.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Режим папы активен: вам доступно полное редактирование расписания уроков и списков предметов.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Button(
                                onClick = onNavigateToEditor,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) AccentDark else Accent)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Перейти к редактированию уроков", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 3. СЕКЦИЯ: ВНЕШНИЙ ВИД И БЕЗОПАСНОСТЬ (ЭЛЕМЕНТЫ МЕНЮ)
            // ═════════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
            ) {
                Column {
                    // Пункт: Тема оформления
                    SettingsMenuItem(
                        icon = when (themeMode) {
                            "LIGHT" -> Icons.Filled.LightMode
                            "DARK"  -> Icons.Filled.DarkMode
                            else    -> Icons.Filled.BrightnessAuto
                        },
                        title = "Тема оформления",
                        subtitle = "Светлая, тёмная или как в системе",
                        badge = when (themeMode) {
                            "LIGHT" -> "Светлая"
                            "DARK"  -> "Тёмная"
                            else    -> "Системная"
                        },
                        onClick = { showThemeDialog = true }
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isDark) Color(0xFF2B303E) else Color(0xFFE8ECF4))
                    )

                    // Пункт: Размер шрифта
                    SettingsMenuItem(
                        icon = Icons.Filled.FormatSize,
                        title = "Размер текста (шрифта)",
                        subtitle = "Масштаб для комфортного чтения уроков",
                        badge = when {
                            kotlin.math.abs(fontScale - 1.45f) < 0.05f -> "145%"
                            kotlin.math.abs(fontScale - 1.30f) < 0.05f -> "130%"
                            kotlin.math.abs(fontScale - 1.15f) < 0.05f -> "115%"
                            else -> "100%"
                        },
                        onClick = { showFontSizeDialog = true }
                    )

                    // Пункт: Смена пароля (только в режиме папы)
                    if (selectedTab == "SERVER") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(if (isDark) Color(0xFF2B303E) else Color(0xFFE8ECF4))
                        )

                        SettingsMenuItem(
                            icon = Icons.Filled.Lock,
                            title = "Пароль режима папы",
                            subtitle = "Защита доступа к редактированию",
                            badge = "Сменить",
                            onClick = { showChangePinDialog = true }
                        )
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 4. СЕКЦИЯ: РЕЗЕРВНАЯ КОПИЯ И ЭКСПОРТ (ОФЛАЙН)
            // ═════════════════════════════════════════════════════════════════
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Офлайн-передача расписания",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Если нет интернета, можно передать расписание файлом через Telegram, WhatsApp или Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val jsonString = viewModel.exportScheduleJson()
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, jsonString)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Поделиться расписанием"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Поделиться")
                        }

                        OutlinedButton(
                            onClick = {
                                importText = ""
                                importError = false
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Импорт")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Версия
            Text(
                text = "Версия 2.4 • Apple UI Design",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Компонент: Элемент меню в стиле iOS Settings
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) AccentDark.copy(alpha = 0.18f) else Accent.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDark) AccentDark else Accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color(0xFF242834) else Color(0xFFEFF2F7),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFDEE3ED))
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) AccentDark else Accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог смены пароля режима папы
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChangePinDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var newPinConfirm by remember { mutableStateOf("") }
    var errorMismatch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Смена пароля папы")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Задайте новый пароль для входа в режим редактирования:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= 12) {
                            newPin = it
                            errorMismatch = false
                        }
                    },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPinConfirm,
                    onValueChange = {
                        if (it.length <= 12) {
                            newPinConfirm = it
                            errorMismatch = false
                        }
                    },
                    label = { Text("Повторите пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorMismatch,
                    supportingText = if (errorMismatch) {
                        { Text("Пароли не совпадают", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin != newPinConfirm) {
                        errorMismatch = true
                        return@Button
                    }
                    if (newPin.isNotBlank()) {
                        onSave(newPin.trim())
                    }
                },
                enabled = newPin.isNotBlank() && newPinConfirm.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог запроса пароля при клике на «Телефон папы»
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasswordPromptDialog(
    hasError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Вход для родителей")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Введите пароль для управления и редактирования расписания:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 12) password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    isError = hasError,
                    supportingText = if (hasError) {
                        { Text("Неверный пароль. Попробуйте ещё раз.", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Войти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог выбора темы оформления (Системная / Светлая / Тёмная)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ThemeSelectorDialog(
    currentThemeMode: String,
    onSelectThemeMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val options = listOf(
        Triple("SYSTEM", "Системная", "Следовать теме операционной системы"),
        Triple("LIGHT", "Светлая", "Всегда светлая тема"),
        Triple("DARK", "Тёмная", "Всегда тёмная тема")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (currentThemeMode) {
                        "LIGHT" -> Icons.Filled.LightMode
                        "DARK"  -> Icons.Filled.DarkMode
                        else    -> Icons.Filled.BrightnessAuto
                    },
                    contentDescription = null,
                    tint = if (isDark) AccentDark else Accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Тема оформления",
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
                    "Выберите оформление приложения:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(4.dp))

                options.forEach { (mode, label, desc) ->
                    val isSelected = currentThemeMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectThemeMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            if (isDark) AccentDark.copy(alpha = 0.2f) else Accent.copy(alpha = 0.12f)
                        } else {
                            if (isDark) Color(0xFF242834) else Color(0xFFF3F5FA)
                        },
                        border = BorderStroke(
                            1.5.dp,
                            if (isSelected) (if (isDark) AccentDark else Accent) else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (mode) {
                                    "LIGHT" -> Icons.Filled.LightMode
                                    "DARK"  -> Icons.Filled.DarkMode
                                    else    -> Icons.Filled.BrightnessAuto
                                },
                                contentDescription = null,
                                tint = if (isSelected) {
                                    if (isDark) AccentDark else Accent
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
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
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Выбрано",
                                    tint = if (isDark) AccentDark else Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
