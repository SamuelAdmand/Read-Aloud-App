package com.samuel.readaloud.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.samuel.readaloud.ui.home.HomeScreen
import com.samuel.readaloud.ui.library.LibraryScreen
import com.samuel.readaloud.ui.more.MoreScreen
import com.samuel.readaloud.ui.type.TypeScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    data class NavItem(val label: String, val icon: ImageVector, val route: String)

    val navItems = listOf(
        NavItem("Home", Icons.Filled.Home, "home"),
        NavItem("Library", Icons.Filled.List, "library"),
        NavItem("More", Icons.Filled.Settings, "more")
    )

    // Get current route to determine visibility of Bottom Bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    Scaffold(
        bottomBar = {
            // HIDE Bottom Bar if we are on the 'type_text' screen
            if (currentRoute != "type_text") {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            // This padding automatically adjusts. If bottomBar is hidden, bottom padding is 0.
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable("home") {
                HomeScreen(
                    onTypeTextClick = { navController.navigate("type_text") }
                )
            }
            composable("library") { LibraryScreen() }
            composable("more") { MoreScreen() }
            composable("type_text") {
                TypeScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}