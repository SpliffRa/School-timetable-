package com.schedule.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

// Текущее состояние темы (светлая / тёмная) для любого Composable экрана
val LocalIsDarkTheme = compositionLocalOf { false }

// Динамические цвета Material You намеренно НЕ используются (по ТЗ)

private val LightColorScheme = lightColorScheme(
    background        = Background,
    surface           = CardBackground,
    surfaceVariant    = Background,
    primary           = Accent,
    onPrimary         = CardBackground,
    onBackground      = TextPrimary,
    onSurface         = TextPrimary,
    secondary         = TextSecondary,
    onSecondary       = CardBackground,
    outline           = Divider,
    outlineVariant    = Divider
)

private val DarkColorScheme = darkColorScheme(
    background        = BackgroundDark,
    surface           = CardBackgroundDark,
    surfaceVariant    = BackgroundDark,
    primary           = AccentDark,
    onPrimary         = TextPrimaryDark,
    onBackground      = TextPrimaryDark,
    onSurface         = TextPrimaryDark,
    secondary         = TextSecondaryDark,
    onSecondary       = BackgroundDark,
    outline           = DividerDark,
    outlineVariant    = DividerDark
)

@Composable
fun ScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography  = ScheduleTypography,
            shapes      = ScheduleShapes,
            content     = content
        )
    }
}
