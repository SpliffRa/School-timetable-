package com.schedule.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.schedule.app.data.network.AppUpdateNotificationHelper
import com.schedule.app.ui.component.AppUpdateDialog
import com.schedule.app.ui.screen.EditScheduleScreen
import com.schedule.app.ui.screen.MainScreen
import com.schedule.app.ui.screen.SettingsScreen
import com.schedule.app.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {

    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge отрисовка — Scaffold/TopAppBar сами обрабатывают insets
        enableEdgeToEdge()

        // Создаем канал уведомлений
        AppUpdateNotificationHelper.createNotificationChannel(this)

        // Запрос разрешения на отправку уведомлений для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setContent {
            val viewModel: MainViewModel = viewModel()
            mainViewModel = viewModel

            val fontScale by viewModel.fontScaleFlow().collectAsStateWithLifecycle(initialValue = 1.0f)
            val themeMode by viewModel.themeModeFlow().collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK"  -> true
                else    -> isSystemDark
            }

            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            // Обработка клика по системному уведомлению об обновлении
            LaunchedEffect(intent) {
                handleUpdateIntent(intent)
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

                    // Глобальное окно процесса обновления (доступно поверх любого экрана)
                    if (updateState !is UpdateUiState.Idle) {
                        AppUpdateDialog(
                            updateState = updateState,
                            onDownloadAndInstall = { info -> viewModel.startDownloadAndInstall(context, info) },
                            onInstallDownloaded = { file -> viewModel.installDownloadedApk(context, file) },
                            onDismiss = { viewModel.dismissUpdateDialog() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppUpdateNotificationHelper.EXTRA_START_UPDATE, false) == true) {
            mainViewModel?.let { vm ->
                val banner = vm.availableUpdateBanner.value
                if (banner != null) {
                    vm.showUpdateAvailableDialog(banner)
                } else {
                    vm.checkForUpdates(isManual = true)
                }
            }
        }
    }
}
