package com.rivi.carbonwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rivi.carbonwise.ui.HistoryViewModel
import com.rivi.carbonwise.ui.HomeViewModel
import com.rivi.carbonwise.ui.ViewModelFactory
import com.rivi.carbonwise.ui.screens.DetailScreen
import com.rivi.carbonwise.ui.screens.HistoryScreen
import com.rivi.carbonwise.ui.screens.HomeScreen
import com.rivi.carbonwise.ui.screens.TrendsScreen
import com.rivi.carbonwise.ui.theme.CarbonWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarbonWiseTheme {
                CarbonWiseApp()
            }
        }
    }

    companion object {
        /** Notification tap carries the detected-trip id so Today can surface it. */
        const val EXTRA_OPEN_DETECTION = "extra_open_detection"
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("home", "Today", Icons.Filled.EditNote),
    HISTORY("history", "History", Icons.Filled.History),
    TRENDS("trends", "Trends", Icons.AutoMirrored.Filled.ShowChart),
}

@Composable
private fun CarbonWiseApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val factory = remember(context) { ViewModelFactory(context) }
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Tab.entries.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.TODAY.route) {
                HomeScreen(viewModel = homeViewModel)
            }
            composable(Tab.HISTORY.route) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onOpenDay = { id -> navController.navigate("detail/$id") },
                )
            }
            composable(Tab.TRENDS.route) {
                TrendsScreen(viewModel = historyViewModel)
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                DetailScreen(
                    viewModel = historyViewModel,
                    entryId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
