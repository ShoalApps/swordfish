@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.anglerfish.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.shoalapps.anglerfish.data.Settings

// Fallback palette: Anglerfish's colour, generated with Material's colour utilities.
// Used on Android 11 and below, or with dynamic colour turned off.
private val Light = lightColorScheme(
    primary = Color(0xFF605790), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF483F77),
    secondary = Color(0xFF605C71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DFF9), onSecondaryContainer = Color(0xFF484459),
    tertiary = Color(0xFF94687A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF94687A), onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFDF8FF), onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFDF8FF), onSurface = Color(0xFF1C1B20), onSurfaceVariant = Color(0xFF48454E),
    surfaceContainerLow = Color(0xFFF7F2FA), surfaceContainer = Color(0xFFF1ECF4),
    surfaceContainerHigh = Color(0xFFEBE6EE), surfaceContainerHighest = Color(0xFFE6E1E9),
    outline = Color(0xFF76737D), outlineVariant = Color(0xFFC9C4D0), inversePrimary = Color(0xFFCABEFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFCABEFF), onPrimary = Color(0xFF31285F),
    primaryContainer = Color(0xFF483F77), onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFC9C3DC), onSecondary = Color(0xFF312E41),
    secondaryContainer = Color(0xFF484459), onSecondaryContainer = Color(0xFFE6DFF9),
    tertiary = Color(0xFFEDB8CC), onTertiary = Color(0xFF482535),
    tertiaryContainer = Color(0xFFB38396), onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF141318), onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141318), onSurface = Color(0xFFE6E1E9), onSurfaceVariant = Color(0xFFC9C4D0),
    surfaceContainerLow = Color(0xFF1C1B20), surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B292F), surfaceContainerHighest = Color(0xFF36343A),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF48454E), inversePrimary = Color(0xFF605790),
)

@Composable
fun TubeTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
