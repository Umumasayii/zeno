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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zeno.domain.settings.ZenoSettings

/** Main navigation: Home | Commands | Automations | Settings (docs/07 "Main navigation"). */
enum class TopDestination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    COMMANDS("commands", R.string.tab_commands, Icons.Outlined.RecordVoiceOver),
    AUTOMATIONS("automations", R.string.tab_automations, Icons.Outlined.AutoMode),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun ZenoApp(settings: ZenoSettings = ZenoSettings()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
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
            composable(TopDestination.HOME.route) { HomeScreen(settings) }
            composable(TopDestination.COMMANDS.route) { CommandsScreen() }
            composable(TopDestination.AUTOMATIONS.route) { AutomationsScreen() }
            composable(TopDestination.SETTINGS.route) { SettingsScreen(settings) }
        }
    }
}
