package com.schedule.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
            val currentDensity = LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * fontScale
                )
            ) {
                ScheduleTheme {
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
