package com.crewboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.crewboard.data.ConnectionState
import kotlinx.coroutines.flow.StateFlow

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("tasks", "Tasks", Icons.AutoMirrored.Filled.List),
    Tab("crew", "Crew", Icons.Default.Person),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun CrewBoardRoot(
    deepLink: StateFlow<Int?>,
    onDeepLinkHandled: () -> Unit,
    vm: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory),
) {
    val nav = rememberNavController()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val link by deepLink.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(link) {
        link?.let { nav.navigate("task/$it"); onDeepLinkHandled() }
    }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigate(tab.route) { popUpTo("tasks"); launchSingleTop = true } },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ConnectionBanner(connection)
            NavHost(nav, startDestination = "tasks") {
                composable("tasks") { TaskListScreen(vm) { nav.navigate("task/$it") } }
                composable("task/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    TaskDetailScreen(it.arguments!!.getInt("id"), vm, onBack = { nav.popBackStack() })
                }
                composable("crew") { CrewScreen(vm) }
                composable("settings") { SettingsFragmentHost(Modifier.fillMaxSize()) }
            }
        }
    }
}

/** HMI essential: the operator must always know whether what they see is live. */
@Composable
fun ConnectionBanner(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.LIVE -> "LIVE" to Color(0xFF4CC38A)
        ConnectionState.RECONNECTING -> "RECONNECTING…" to Color(0xFFFFB81C)
        ConnectionState.OFFLINE -> "OFFLINE · showing saved tasks" to Color(0xFF9AA3AD)
    }
    Box(Modifier.fillMaxWidth().background(color).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Black, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
