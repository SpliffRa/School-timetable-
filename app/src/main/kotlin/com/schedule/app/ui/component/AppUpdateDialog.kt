package com.schedule.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.schedule.app.BuildConfig
import com.schedule.app.UpdateUiState
import com.schedule.app.data.network.AppUpdateInfo
import com.schedule.app.ui.theme.Accent
import com.schedule.app.ui.theme.AccentDark
import com.schedule.app.ui.theme.LocalIsDarkTheme
import java.io.File

/**
 * Диалоговое окно для отображения статуса проверки, информации о новой версии,
 * прогресса загрузки APK и кнопки запуска установки.
 */
@Composable
fun AppUpdateDialog(
    updateState: UpdateUiState,
    onDownloadAndInstall: (AppUpdateInfo) -> Unit,
    onInstallDownloaded: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (updateState) {
                        is UpdateUiState.Downloading -> Icons.Filled.CloudDownload
                        is UpdateUiState.ReadyToInstall -> Icons.Filled.Check
                        is UpdateUiState.Error -> Icons.Filled.Cloud
                        else -> Icons.Filled.SystemUpdate
                    },
                    contentDescription = null,
                    tint = if (isDark) AccentDark else Accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when (updateState) {
                        is UpdateUiState.Checking -> "Проверка обновлений..."
                        is UpdateUiState.UpToDate -> "Актуальная версия ✓"
                        is UpdateUiState.UpdateAvailable -> "Доступно обновление 🚀"
                        is UpdateUiState.Downloading -> "Загрузка обновления..."
                        is UpdateUiState.ReadyToInstall -> "Готово к установке ✓"
                        is UpdateUiState.Error -> "Ошибка обновления"
                        else -> "Обновление приложения"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (updateState) {
                    is UpdateUiState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "Связываемся с облаком...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UpdateUiState.UpToDate -> {
                        Text(
                            text = "У вас установлена последняя версия приложения (v${BuildConfig.VERSION_NAME}).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Приложение автоматически проверяет наличие новых версий в облаке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    is UpdateUiState.UpdateAvailable -> {
                        val info = updateState.info
                        Text(
                            text = "Новая версия: v${info.versionName} (сборка ${info.versionCode})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) AccentDark else Accent
                        )
                        if (info.releaseDate.isNotBlank()) {
                            Text(
                                text = "Дата выпуска: ${info.releaseDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (info.releaseNotes.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF242834) else Color(0xFFF3F5FA),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF333A4A) else Color(0xFFE2E6EF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Что нового:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = info.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Нажмите кнопку ниже для загрузки и автоматической установки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    is UpdateUiState.Downloading -> {
                        val progress = updateState.progress
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = if (updateState.totalBytes > 0) updateState.totalBytes / (1024f * 1024f) else 0f

                        Spacer(Modifier.height(4.dp))
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) AccentDark else Accent
                                )
                                Text(
                                    text = "%.1f / %.1f МБ".format(downloadedMb, totalMb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )
                            Text(
                                text = "Загружено: %.1f МБ".format(downloadedMb),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "По завершении загрузки автоматически откроется системное окно обновления приложения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Text(
                            text = "Обновление успешно скачано и готово к установке.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Если окно установки не открылось автоматически, нажмите кнопку «Установить».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    is UpdateUiState.Error -> {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateUiState.UpdateAvailable -> {
                    Button(onClick = { onDownloadAndInstall(updateState.info) }) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Скачать и обновить")
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(onClick = { onInstallDownloaded(updateState.apkFile) }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Установить")
                    }
                }
                is UpdateUiState.UpToDate -> {
                    Button(onClick = onDismiss) {
                        Text("Отлично")
                    }
                }
                is UpdateUiState.Error -> {
                    Button(onClick = onDismiss) {
                        Text("Понятно")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (updateState !is UpdateUiState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    )
}
