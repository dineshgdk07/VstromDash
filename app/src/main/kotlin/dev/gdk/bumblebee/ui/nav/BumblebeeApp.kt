package dev.gdk.bumblebee.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun BumblebeeApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            NavigationBar {
                Pillar.entries.forEach { pillar ->
                    NavigationBarItem(
                        selected = currentRoute == pillar.route,
                        onClick = {
                            navController.navigate(pillar.route) {
                                popUpTo(Pillar.COCKPIT.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(pillar.icon, contentDescription = pillar.label) },
                        label = { Text(pillar.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Pillar.COCKPIT.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            Pillar.entries.forEach { pillar ->
                composable(pillar.route) { PlaceholderScreen(pillar.label) }
            }
        }
    }
}
