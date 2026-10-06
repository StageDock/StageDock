package com.stagedock.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0618)
val Surface = Color(0xFF17102B)
val Magenta = Color(0xFFFF2E93)
val Cyan = Color(0xFF2EE6FF)

private val scheme = darkColorScheme(
    primary = Magenta,
    secondary = Cyan,
    background = Background,
    surface = Surface,
    onPrimary = Color.White,
    onBackground = Color(0xFFEDE7FF),
    onSurface = Color(0xFFEDE7FF),
)

@Composable
fun StageDockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
