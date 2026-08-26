package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SpreTealPrimary,
    onPrimary = Color(0xFF00363D),
    primaryContainer = SpreTealDark,
    onPrimaryContainer = SpreTealLight,
    secondary = SpreCyanAccent,
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF005140),
    onSecondaryContainer = Color(0xFF86F8D5),
    tertiary = SpreIndigo,
    onTertiary = Color.White,
    background = SpreDarkBg,
    onBackground = SpreDarkText,
    surface = SpreDarkSurface,
    onSurface = SpreDarkText,
    surfaceVariant = SpreDarkSurfaceVariant,
    onSurfaceVariant = SpreDarkTextMuted,
    error = SpreErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SpreTealDark,
    onPrimary = Color.White,
    primaryContainer = SpreTealLight,
    onPrimaryContainer = Color(0xFF001F24),
    secondary = SpreCyanAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB4F8E4),
    onSecondaryContainer = Color(0xFF002018),
    tertiary = SpreIndigo,
    onTertiary = Color.White,
    background = SpreLightBg,
    onBackground = SpreLightText,
    surface = SpreLightSurface,
    onSurface = SpreLightText,
    surfaceVariant = SpreLightSurfaceVariant,
    onSurfaceVariant = SpreLightTextMuted,
    error = SpreErrorRed,
    onError = Color.White
)

@Composable
fun SpreDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent SpreDrop teal branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

