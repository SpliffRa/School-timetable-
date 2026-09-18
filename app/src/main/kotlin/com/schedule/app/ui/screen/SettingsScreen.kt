package com.schedule.app.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.schedule.app.ui.theme.Accent
import com.schedule.app.ui.theme.AccentDark
import com.schedule.app.ui.theme.InnerBoxDarkBg
import com.schedule.app.ui.theme.InnerBoxDarkBorder
import com.schedule.app.ui.theme.InnerBoxLightBg
import com.schedule.app.ui.theme.InnerBoxLightBorder
import com.schedule.app.ui.theme.SuccessMint
import com.schedule.app.ui.theme.SuccessMintBright
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schedule.app.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: () -> Unit = {}
) {
    val currentRole by viewModel.deviceRole.collectAsStateWithLifecycle()
    val serverName  by viewModel.deviceNameServerFlow().collectAsStateWithLifecycle(initialValue = "Телефон папы")
    val clientName  by viewModel.deviceNameClientFlow().collectAsStateWithLifecycle(initialValue = "Планшет Алисы")
    val currentPin  by viewModel.editorPinFlow().collectAsStateWithLifecycle(initialValue = "1234")
    val currentSyncCode by viewModel.syncCodeFlow().collectAsStateWithLifecycle(initialValue = "Алиса-2026")
    val fontScale by viewModel.fontScaleFlow().collectAsStateWithLifecycle(initialValue = 1.0f)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Текущая выбранная вкладка: "CLIENT" (Планшет Алисы) или "SERVER" (Телефон папы)
    var selectedTab by remember(currentRole) { mutableStateOf(currentRole) }

    // Локальные поля имени устройства и кода синхронизации
    var aliceDeviceName by remember(clientName) { mutableStateOf(clientName) }
    var dadDeviceName   by remember(serverName) { mutableStateOf(serverName) }
    var familySyncCode  by remember(currentSyncCode) { mutableStateOf(currentSyncCode) }

    // Диалог офлайн-импорта расписания
    var showImportDialog by remember { mutableStateOf(false) }
    var importText       by remember { mutableStateOf("") }
    var importError      by remember { mutableStateOf(false) }

    // Диалог ввода пароля для перехода на «Телефон папы»
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordDialogError by remember { mutableStateOf(false) }

    // Поля смены пароля (только на вкладке папы)
    var newPin        by remember { mutableStateOf("") }
    var newPinConfirm by remember { mutableStateOf("") }
    var pinMismatch   by remember { mutableStateOf(false) }
    var pinSavedSuccess by remember { mutableStateOf(false) }

    // Диалог запроса пароля при клике на вкладку «Телефон папы»
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

    BackHandler {
        when {
            showImportDialog -> showImportDialog = false
            showPasswordDialog -> {
                showPasswordDialog = false
                passwordDialogError = false
            }
            else -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Размер шрифта ────────────────────────────────────────────────
            FontSizeSettingsCard(
                fontScale = fontScale,
                onScaleChange = { viewModel.saveFontScale(it) }
            )

            // ── Переключатель режимов / вкладок ───────────────────────────────
            Text(
                text = "Режим устройства",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

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
                    Text("Планшет Алисы")
                }

                SegmentedButton(
                    selected = selectedTab == "SERVER",
                    onClick = {
                        if (selectedTab != "SERVER") {
                            // Для перехода на «Телефон папы» требуем пароль!
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
                    Text("Телефон папы")
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // ВКЛАДКА: ПЛАНШЕТ АЛИСЫ (только название, без PIN-кода)
            // ═════════════════════════════════════════════════════════════════
            if (selectedTab == "CLIENT") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.TabletAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Режим: Планшет Алисы",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Планшет получает готовое расписание уроков от папы по Wi-Fi. Редактирование расписания здесь заблокировано.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Поле: Название устройства
                Text(
                    text = "Название устройства",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Это имя отображается при поиске и синхронизации по домашнему Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = aliceDeviceName,
                    onValueChange = { aliceDeviceName = it },
                    label = { Text("Имя устройства") },
                    placeholder = { Text("Планшет Алисы") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(4.dp))

                // Поле: Код семьи (облачная синхронизация)
                Text(
                    text = "Код семьи (Синхронизация через интернет)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Связывает телефон папы и планшет Алисы через интернет. Расписание обновляется в любое время, даже когда телефон папы выключен или заблокирован (код должен быть одинаковым на обоих устройствах).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = familySyncCode,
                    onValueChange = { familySyncCode = it },
                    label = { Text("Код семьи") },
                    placeholder = { Text("Алиса-2026") },
                    leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val name = aliceDeviceName.trim().ifBlank { "Планшет Алисы" }
                        viewModel.saveSettings(
                            role = "CLIENT",
                            deviceName = name,
                            pin = currentPin,
                            syncCode = familySyncCode.trim().ifBlank { "Алиса-2026" }
                        )
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить")
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // ВКЛАДКА: ТЕЛЕФОН ПАПЫ (неограниченный режим, пароль, редактор)
            // ═════════════════════════════════════════════════════════════════
            if (selectedTab == "SERVER") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Режим: Телефон папы (Неограниченный доступ)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Вы вошли в режим управления. Здесь можно свободно вносить, редактировать и удалять дни и уроки. Все изменения сразу передаются на планшет Алисы по Wi-Fi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        // Быстрый переход в редактор расписания
                        Button(
                            onClick = {
                                onNavigateToEditor()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Перейти к редактированию уроков")
                        }
                    }
                }

                // ── Имя устройства ───────────────────────────────────────────
                Text(
                    text = "Имя устройства (Папа)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                OutlinedTextField(
                    value = dadDeviceName,
                    onValueChange = { dadDeviceName = it },
                    label = { Text("Имя телефона в сети") },
                    placeholder = { Text("Телефон папы") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(4.dp))

                // ── Код семьи (Облачная синхронизация) ────────────────────────
                Text(
                    text = "Код семьи (Синхронизация через интернет)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Любые изменения уроков сразу отправляются в защищённое облако по этому коду. Планшет Алисы скачает их в любое время, даже когда телефон папы выключен.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = familySyncCode,
                    onValueChange = { familySyncCode = it },
                    label = { Text("Код семьи") },
                    placeholder = { Text("Алиса-2026") },
                    leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                // ── Пароль администратора ─────────────────────────────────────
                Text(
                    text = "Пароль режима папы",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Этот пароль запрашивается при попытке переключиться на вкладку «Телефон папы» (по умолчанию: 1234).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= 12) {
                            newPin = it
                            pinMismatch = false
                            pinSavedSuccess = false
                        }
                    },
                    label = { Text("Новый пароль (от 4 знаков)") },
                    placeholder = { Text("1234") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )

                OutlinedTextField(
                    value = newPinConfirm,
                    onValueChange = {
                        if (it.length <= 12) {
                            newPinConfirm = it
                            pinMismatch = false
                            pinSavedSuccess = false
                        }
                    },
                    label = { Text("Повторите новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = pinMismatch,
                    supportingText = when {
                        pinMismatch -> {{ Text("Пароли не совпадают", color = MaterialTheme.colorScheme.error) }}
                        pinSavedSuccess -> {{ Text("Пароль успешно обновлён ✓", color = MaterialTheme.colorScheme.primary) }}
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )

                if (newPin.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            if (newPin != newPinConfirm) {
                                pinMismatch = true
                                return@OutlinedButton
                            }
                            viewModel.savePassword(newPin.trim())
                            pinSavedSuccess = true
                            newPin = ""
                            newPinConfirm = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить новый пароль")
                    }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (newPin.isNotBlank() && newPin != newPinConfirm) {
                            pinMismatch = true
                            return@Button
                        }
                        val name = dadDeviceName.trim().ifBlank { "Телефон папы" }
                        val passwordToSave = if (newPin.isNotBlank() && newPin == newPinConfirm) newPin.trim() else currentPin
                        viewModel.saveSettings(
                            role = "SERVER",
                            deviceName = name,
                            pin = passwordToSave,
                            syncCode = familySyncCode.trim().ifBlank { "Алиса-2026" }
                        )
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить настройки")
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Офлайн-передача и резервная копия ─────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Резервная копия и передача (офлайн)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Если нет интернета и Wi-Fi, можно переслать расписание файлом через Telegram, WhatsApp, Bluetooth или сохранить на диск.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier.weight(1f)
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Импорт")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Версия программы
            Text(
                text = "Версия 1.5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Диалог импорта расписания
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт расписания") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Вставьте скопированный текст расписания (из сообщения или файла):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Диалог ввода пароля при переключении на «Телефон папы»
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasswordPromptDialog(
    hasError: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Вход в режим папы")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Введите пароль для переключения на Телефон папы (по умолчанию: 1234):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun FontSizeSettingsCard(
    fontScale: Float,
    onScaleChange: (Float) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val options = listOf(
        1.00f to "100%",
        1.15f to "115%",
        1.30f to "130%",
        1.45f to "145%"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2B303E) else Color(0xFFE2E6EF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FormatSize,
                    contentDescription = null,
                    tint = if (isDark) AccentDark else Accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Размер шрифта",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Масштабирует текст во всем расписании и списках",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Переключатель размеров
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (scale, label) ->
                    val isSelected = kotlin.math.abs(fontScale - scale) < 0.05f
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onScaleChange(scale) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            if (isDark) AccentDark else Accent
                        } else {
                            if (isDark) Color(0xFF242834) else Color(0xFFF3F5FA)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) (if (isDark) AccentDark else Accent)
                            else (if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF))
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Предпросмотр текста в реальном времени
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) InnerBoxDarkBg else InnerBoxLightBg,
                border = BorderStroke(1.dp, if (isDark) InnerBoxDarkBorder else InnerBoxLightBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Пример отображения:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "1. Русский Язык  08:30–09:05",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "✓ Тетрадь в косую линейку",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) SuccessMintBright else SuccessMint,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

