package com.example.a4kwa.ui.theme

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

private val LightColorScheme = lightColorScheme(
    primary = Green60,
    onPrimary = Color.White,
    primaryContainer = Green80,
    onPrimaryContainer = Color(0xFF002019),
    secondary = GreenGray60,
    onSecondary = Color.White,
    secondaryContainer = GreenGray80,
    onSecondaryContainer = Color(0xFF082019),
    tertiary = Color(0xFF416379),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5E7FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Green40,
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Green80,
    secondary = Color(0xFFB3CDC3),
    onSecondary = Color(0xFF1E352E),
    secondaryContainer = Color(0xFF344B44),
    onSecondaryContainer = GreenGray80,
    tertiary = Color(0xFFAACBE6),
    onTertiary = Color(0xFF0E3447),
    tertiaryContainer = Color(0xFF284B5F),
    onTertiaryContainer = Color(0xFFC5E7FF),
    background = AmoledBlack,
    onBackground = AmoledOnSurface,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceHigh,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerLow = AmoledSurface,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun _4KWATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AmoledDarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
