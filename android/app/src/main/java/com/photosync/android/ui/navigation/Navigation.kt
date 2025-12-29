package com.photosync.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photosync.android.ui.screens.GalleryScreen
import com.photosync.android.ui.screens.HomeScreen
import com.photosync.android.ui.screens.PairingScreen
import com.photosync.android.ui.screens.SettingsScreen
import com.photosync.android.ui.screens.SyncStatusScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Gallery : Screen("gallery", "Gallery", Icons.Default.Info)
    data object Sync : Screen("sync", "Sync", Icons.Default.Add)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Pairing : Screen("pairing", "Pairing", Icons.Default.QrCode)
}

@Composable
fun PhotoSyncNavigation(
    startDestination: String = Screen.Home.route,
    onStartSync: () -> Unit = {}
) {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToGallery = {
                        navController.navigate(Screen.Gallery.route)
                    }
                )
            }
            
            composable(Screen.Gallery.route) {
                GalleryScreen()
            }
            
            composable(Screen.Sync.route) {
                SyncStatusScreen()
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToPairing = {
                        navController.navigate(Screen.Pairing.route)
                    }
                )
            }
            
            composable(Screen.Pairing.route) {
                PairingScreen(
                    onPairingSuccess = {
                        navController.popBackStack()
                    }
                )
            }

            composable("permissions") {
                com.photosync.android.ui.screens.PermissionScreen(
                    onPermissionsGranted = {
                        navController.navigate("onboarding_discovery")
                    }
                )
            }

            composable("onboarding_discovery") {
                com.photosync.android.ui.screens.OnboardingServerDiscoveryScreen(
                    onOnboardingCompleted = {
                        onStartSync()
                        navController.navigate(Screen.Home.route) {
                            popUpTo("permissions") { inclusive = true }
                        }
                    }
                )
            }


        }
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val screens = listOf(
        Screen.Home,
        Screen.Gallery,
        Screen.Sync,
        Screen.Settings
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Glass Navigation Bar
    // We use a Box with background brush to simulate glass fade up
    Box(
        modifier = Modifier
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, com.photosync.android.ui.theme.Background),
                    startY = 0f
                )
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent, // Transparent to let gradient show
            contentColor = com.photosync.android.ui.theme.TextPrimary,
            tonalElevation = 0.dp
        ) {
            screens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                
                NavigationBarItem(
                    icon = { 
                        Icon(
                            imageVector = screen.icon, 
                            contentDescription = screen.title,
                            tint = if (selected) com.photosync.android.ui.theme.Primary else com.photosync.android.ui.theme.TextSecondary
                        ) 
                    },
                    label = { 
                        Text(
                            text = screen.title,
                            color = if (selected) com.photosync.android.ui.theme.Primary else com.photosync.android.ui.theme.TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        ) 
                    },
                    selected = selected,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.photosync.android.ui.theme.Primary,
                        selectedTextColor = com.photosync.android.ui.theme.Primary,
                        indicatorColor = com.photosync.android.ui.theme.Primary.copy(alpha = 0.1f),
                        unselectedIconColor = com.photosync.android.ui.theme.TextSecondary,
                        unselectedTextColor = com.photosync.android.ui.theme.TextSecondary
                    ),
                    onClick = {
                        navController.navigate(screen.route) {
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
