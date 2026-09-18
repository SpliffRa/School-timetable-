package com.schedule.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.schedule.app.ui.screen.EditScheduleScreen
import com.schedule.app.ui.screen.MainScreen
import com.schedule.app.ui.screen.SettingsScreen
import com.schedule.app.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge отрисовка — Scaffold/TopAppBar сами обрабатывают insets
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val fontScale by viewModel.fontScaleFlow().collectAsStateWithLifecycle(initialValue = 1.0f)
            val themeMode by viewModel.themeModeFlow().collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK"  -> true
                else    -> isSystemDark
            }

            // Управление автоматической синхронизацией:
            // ON_START (при открытии или возврате из фона) -> запуск проверки и таймера раз в минуту
            // ON_STOP (при сворачивании приложения) -> приостановка синхронизации
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val currentDensity = LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * fontScale
                )
            ) {
                ScheduleTheme(darkTheme = isDark) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToEditor = { navController.navigate("editor") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEditor = { navController.navigate("editor") }
                            )
                        }
                        composable("editor") {
                            EditScheduleScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
