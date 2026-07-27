package com.kartus.sportswidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B2545),
    secondary = Color(0xFFD7263D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8DA),
    secondary = Color(0xFFFF8A9A),
)

/**
 * The stock ripple is tuned for light surfaces. Against a dark scoreboard card it
 * reads as a bright flash across the whole row, which is far louder than the
 * feedback a tap needs. These alphas keep the press legible as a soft tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val SubtleRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        pressedAlpha = 0.05f,
        focusedAlpha = 0.05f,
        draggedAlpha = 0.05f,
        hoveredAlpha = 0.03f,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Material You where the platform supports it; the hand-picked palette otherwise.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides SubtleRipple,
            content = content,
        )
    }
}
