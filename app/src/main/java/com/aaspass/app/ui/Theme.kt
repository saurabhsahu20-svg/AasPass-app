package com.aaspass.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Emerald = Color(0xFF008653)
private val Forest = Color(0xFF064832)
private val Mint = Color(0xFFEAF7F0)
private val Charcoal = Color(0xFF18251E)

private val LightScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Forest,
    secondary = Forest,
    background = Color(0xFFFCFDFC),
    onBackground = Charcoal,
    surface = Color.White,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFF1F5F2),
    outline = Color(0xFFCBD8D0)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF61DFA8),
    onPrimary = Color(0xFF002A1C),
    primaryContainer = Color(0xFF115E42),
    onPrimaryContainer = Color(0xFFD6FFE9),
    secondary = Color(0xFF8FCEAE),
    background = Color(0xFF081610),
    surface = Color(0xFF102019),
    surfaceVariant = Color(0xFF1B3328),
    onBackground = Color(0xFFE6F3EC),
    onSurface = Color(0xFFE6F3EC)
)

@Composable
fun AasPassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}
