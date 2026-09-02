package com.blackwatermacros.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blackwatermacros.app.ui.HomeScreen
import com.blackwatermacros.app.ui.HoyScreen
import com.blackwatermacros.app.ui.LoginScreen

/** Web palette (src/app/globals.css) mapped to Material3 roles. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5838),
    onPrimary = Color(0xFFF8F5EE),
    primaryContainer = Color(0xFFE3E7D8),
    onPrimaryContainer = Color(0xFF1A3520),
    background = Color(0xFFF4F2E9),
    onBackground = Color(0xFF1F2A1C),
    surface = Color(0xFFFBFAF6),
    onSurface = Color(0xFF1F2A1C),
    secondary = Color(0xFF213321),
    onSecondary = Color(0xFFE6E7DC),
    secondaryContainer = Color(0xFFE6E7DC),
    onSecondaryContainer = Color(0xFF213321),
    surfaceVariant = Color(0xFFE9E8DF),
    onSurfaceVariant = Color(0xFF676B5B),
    error = Color(0xFFE7000B),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD9D8CD),
    outlineVariant = Color(0xFFDFDFD4),
    surfaceContainerHighest = Color(0xFFE9E8DF),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF63A471),
    onPrimary = Color(0xFF050E08),
    primaryContainer = Color(0xFF222F22),
    onPrimaryContainer = Color(0xFFE8E4DC),
    background = Color(0xFF08110B),
    onBackground = Color(0xFFDAD7CF),
    surface = Color(0xFF111D15),
    onSurface = Color(0xFFDAD7CF),
    secondary = Color(0xFFE1DED5),
    onSecondary = Color(0xFF1D2A21),
    secondaryContainer = Color(0xFF1D2A21),
    onSecondaryContainer = Color(0xFFE1DED5),
    surfaceVariant = Color(0xFF1D2A21),
    onSurfaceVariant = Color(0xFF999588),
    error = Color(0xFFFF6467),
    onError = Color(0xFF3B0000),
    outline = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFF999588),
    surfaceContainerHighest = Color(0xFF1D2A21),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlackwaterMacrosTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(onLoggedIn = { navController.navigate("home") { popUpTo("login") { inclusive = true } } })
        }
        composable("home") {
            HomeScreen(
                onOpenHoy = { navController.navigate("hoy") },
                onLoggedOut = {
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
            )
        }
        composable("hoy") {
            HoyScreen(
                onLogout = {
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                },
            )
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
        content = content,
    )
}