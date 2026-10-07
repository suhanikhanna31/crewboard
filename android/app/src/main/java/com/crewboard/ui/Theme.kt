package com.crewboard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SiteDark = darkColorScheme(
    primary = Color(0xFFFFB81C),        // hi-vis amber
    onPrimary = Color(0xFF111111),
    background = Color(0xFF14161A),
    surface = Color(0xFF1D2025),
    surfaceVariant = Color(0xFF262A31),
    onSurface = Color(0xFFEEF0F2),
    error = Color(0xFFFF5D5D),
    onError = Color(0xFF111111),
)

/** Always dark: readable in sunlight glare and easy on eyes at dawn shifts. */
@Composable
fun CrewBoardTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = SiteDark, content = content)

fun statusColor(status: String) = when (status) {
    "assigned" -> Color(0xFFFFB81C)
    "in_progress" -> Color(0xFF5AA9FF)
    "done" -> Color(0xFF4CC38A)
    "blocked" -> Color(0xFFFF5D5D)
    else -> Color(0xFF9AA3AD)
}

fun statusLabel(status: String) = when (status) {
    "open" -> "Waiting"
    "assigned" -> "New"
    "in_progress" -> "Working"
    "done" -> "Done"
    "blocked" -> "Blocked"
    else -> status
}
