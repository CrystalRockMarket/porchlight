package it.kituwa.stackmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF06281B),
    secondary = Color(0xFF4FA3E3),
    onSecondary = Color(0xFF04233A),
    background = Color(0xFF0F1B2A),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF16273A),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1E3247),
    onSurfaceVariant = Color(0xFFA9BCCE),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0906),
    outline = Color(0xFF3A5169),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00795A),
    onPrimary = Color.White,
    secondary = Color(0xFF1B5E96),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF101A24),
    surface = Color.White,
    onSurface = Color(0xFF101A24),
    surfaceVariant = Color(0xFFE7EDF4),
    onSurfaceVariant = Color(0xFF44566A),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFFB4C2D1),
)

@Composable
fun StackMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
