package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val AmbientDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFF84FFFF),
    secondary = Color(0xFFB388FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF311B92),
    onSecondaryContainer = Color(0xFFD1C4E9),
    tertiary = Color(0xFF1DE9B6),
    onTertiary = Color.Black,
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2E2E2E),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for Ambient style
    dynamicColor: Boolean = false, // Disable dynamic colors to keep aesthetic
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = AmbientDarkColorScheme, typography = Typography, content = content)
}
