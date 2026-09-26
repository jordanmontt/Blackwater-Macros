package com.blackwatermacros.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blackwatermacros.app.ui.AdminScreen
import com.blackwatermacros.app.ui.AppTab
import com.blackwatermacros.app.ui.BlackwaterShapes
import com.blackwatermacros.app.ui.BlackwaterTypography
import com.blackwatermacros.app.ui.BottomNavBar
import com.blackwatermacros.app.ui.HoyScreen
import com.blackwatermacros.app.ui.LoginScreen
import com.blackwatermacros.app.ui.MethodologyScreen
import com.blackwatermacros.app.ui.PesoScreen
import com.blackwatermacros.app.ui.SettingsScreen
import com.blackwatermacros.app.ui.StatsScreen

/**
 * "Blackwater" palette — a warm, organic dark-forest look with a single warm
 * ember accent (tertiary) used sparingly. Light theme keeps a warm paper
 * background; dark theme is a deep, atmospheric green-black.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F4A37),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE8D6),
    onPrimaryContainer = Color(0xFF122A19),
    secondary = Color(0xFF4A5949),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE5DB),
    onSecondaryContainer = Color(0xFF1E2B21),
    tertiary = Color(0xFFB2561F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF3B2103),
    background = Color(0xFFF8F6EF),
    onBackground = Color(0xFF1A1D18),
    surface = Color(0xFFFBFAF6),
    onSurface = Color(0xFF1A1D18),
    surfaceVariant = Color(0xFFE4E6DA),
    onSurfaceVariant = Color(0xFF5C6359),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F4EC),
    surfaceContainer = Color(0xFFF1EFE7),
    surfaceContainerHigh = Color(0xFFEBE9E1),
    surfaceContainerHighest = Color(0xFFE4E6DA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFC2C7BA),
    outlineVariant = Color(0xFFDDE1D6),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FB7A2),
    onPrimary = Color(0xFF123018),
    primaryContainer = Color(0xFF293D2C),
    onPrimaryContainer = Color(0xFFC7E3C9),
    secondary = Color(0xFFB6C4B8),
    onSecondary = Color(0xFF22301F),
    secondaryContainer = Color(0xFF303C27),
    onSecondaryContainer = Color(0xFFDCE8DE),
    tertiary = Color(0xFFFB9E63),
    onTertiary = Color(0xFF3E2002),
    tertiaryContainer = Color(0xFF5A3000),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF0C0F0C),
    onBackground = Color(0xFFDDE0D9),
    surface = Color(0xFF141814),
    onSurface = Color(0xFFDDE0D9),
    surfaceVariant = Color(0xFF2A2E27),
    onSurfaceVariant = Color(0xFFA6A99F),
    surfaceContainerLowest = Color(0xFF070A07),
    surfaceContainerLow = Color(0xFF111510),
    surfaceContainer = Color(0xFF151A14),
    surfaceContainerHigh = Color(0xFF1A1F19),
    surfaceContainerHighest = Color(0xFF252A23),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF3F0000),
    outline = Color(0xFF8A8F83),
    outlineVariant = Color(0xFF3A4038),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}

private val AllTabRoutes = setOf(
    AppTab.HOY.route,
    AppTab.PESO.route,
    AppTab.ESTADISTICAS.route,
    AppTab.AJUSTES.route,
)

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val systemDark = isSystemInDarkTheme()
    var darkTheme by remember { mutableStateOf(systemDark) }

    BlackwaterMacrosTheme(darkTheme = darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (currentRoute in AllTabRoutes) {
                    BottomNavBar(
                        currentRoute = currentRoute ?: "",
                        onTabSelected = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(
                                    navController.graph.findStartDestination().id,
                                ) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                innerPadding = innerPadding,
                darkTheme = darkTheme,
                onThemeChanged = { darkTheme = it },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    darkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = Modifier,
    ) {
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(AppTab.HOY.route) {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }
        composable(AppTab.HOY.route) {
            HoyScreen(modifier = Modifier.padding(innerPadding))
        }
        composable(AppTab.PESO.route) {
            PesoScreen(modifier = Modifier.padding(innerPadding))
        }
        composable(AppTab.ESTADISTICAS.route) {
            StatsScreen(
                onOpenMetodologia = { navController.navigate("metodologia") },
                modifier = Modifier.padding(innerPadding),
            )
        }
        composable(AppTab.AJUSTES.route) {
            SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                darkTheme = darkTheme,
                onThemeChanged = onThemeChanged,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenMetodologia = { navController.navigate("metodologia") },
                onOpenAdmin = { navController.navigate("admin") },
            )
        }
        composable("metodologia") {
            MethodologyScreen(onBack = { navController.popBackStack() })
        }
        composable(
            "admin",
        ) {
            AdminScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun BlackwaterMacrosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BlackwaterTypography,
        shapes = BlackwaterShapes,
        content = content,
    )
}