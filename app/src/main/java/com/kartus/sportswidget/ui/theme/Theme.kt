package com.kartus.sportswidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
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
        // No ripple anywhere. Even at a low alpha it reads as a flash across a dark
        // card, because the whole row lights up at once. Surfaces that need press
        // feedback shift their own container colour instead — see GameCard.
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}
