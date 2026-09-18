package com.schedule.app.data.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.schedule.app.MainActivity
import com.schedule.app.R

/**
 * Управление системными уведомлениями о выходе новых версий приложения (в шторке Android).
 */
object AppUpdateNotificationHelper {
    private const val TAG = "UpdateNotification"
    const val CHANNEL_ID = "channel_app_updates"
    const val NOTIFICATION_ID = 2001
    const val EXTRA_START_UPDATE = "EXTRA_START_UPDATE"

    /**
     * Создаёт канал уведомлений для обновлений приложения на Android 8.0+ (Oreo).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Обновления приложения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о выходе новых версий расписания"
                enableLights(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Отправляет системное уведомление в шторку Android с кнопкой «Обновить».
     */
    fun showUpdateNotification(context: Context, info: AppUpdateInfo) {
        createNotificationChannel(context)

        // Проверка разрешения POST_NOTIFICATIONS для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
                return
            }
        }

        // Клик по телу уведомления -> открыть приложение и показать диалог обновления
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_UPDATE, true)
        }
        val pendingTapIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Кнопка действия «Обновить» на уведомлении
        val actionIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_UPDATE, true)
        }
        val pendingActionIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notes = if (info.releaseNotes.isNotBlank()) info.releaseNotes else "Улучшения и исправления"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Доступно обновление v${info.versionName}")
            .setContentText("Новая версия приложения готова к установке. Нажмите «Обновить».")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Вышла новая версия v${info.versionName}!\n\nЧто нового:\n$notes\n\nНажмите «Обновить» для быстрой установки.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingTapIntent)
            .addAction(
                android.R.drawable.stat_sys_download,
                "Обновить",
                pendingActionIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting update notification: ${e.message}")
        }
    }

    /**
     * Снимает уведомление из шторки (например, после обновления или отмены).
     */
    fun dismissNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing update notification: ${e.message}")
        }
    }
}
