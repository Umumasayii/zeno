package com.zeno.assistant.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zeno.assistant.R
import com.zeno.assistant.ui.screens.AutomationsScreen
import com.zeno.assistant.ui.screens.CommandsScreen
import com.zeno.assistant.ui.screens.HomeScreen
import com.zeno.assistant.ui.screens.SettingsScreen
import com.zeno.assistant.ui.screens.StatusScreen

/** Main navigation: Home | Commands | Automations | Settings (docs/07 "Main navigation"). */
enum class TopDestination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    COMMANDS("commands", R.string.tab_commands, Icons.Outlined.RecordVoiceOver),
    AUTOMATIONS("automations", R.string.tab_automations, Icons.Outlined.AutoMode),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

const val STATUS_ROUTE = "status"

/** Collects the ViewModel and re-checks Android state every time the app comes to the foreground. */
@Composable
fun ZenoRoot(vm: ZenoViewModel, assistant: AssistantViewModel) {
    val state by vm.state.collectAsState()
    val assistantState by assistant.state.collectAsState()
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    ZenoApp(state, vm, assistantState, assistant)
}

@Composable
fun ZenoApp(state: ZenoUiState, actions: ZenoActions, assistant: AssistantUiState, assistantActions: AssistantActions) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        actions.dismissMessage()
    }

    val openStatus = { nav.navigate(STATUS_ROUTE) { launchSingleTop = true } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${dest.route}"),
                        selected = current == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TopDestination.HOME.route, modifier = Modifier.padding(padding)) {
            composable(TopDestination.HOME.route) { HomeScreen(state, actions, assistant, assistantActions, openStatus) }
            composable(TopDestination.COMMANDS.route) { CommandsScreen() }
            composable(TopDestination.AUTOMATIONS.route) { AutomationsScreen() }
            composable(TopDestination.SETTINGS.route) { SettingsScreen(state, actions, openStatus) }
            composable(STATUS_ROUTE) { StatusScreen(state, actions) }
        }
    }
}
