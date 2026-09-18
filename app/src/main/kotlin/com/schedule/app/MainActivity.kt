package com.schedule.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            ScheduleTheme {
                val navController = rememberNavController()
                // Один ViewModel на все экраны
                val viewModel: MainViewModel = viewModel()

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
