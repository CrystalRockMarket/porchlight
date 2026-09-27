package it.kituwa.porchlight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandGreen = Color(0xFF2E9E63)
private val BrandGreenDark = Color(0xFF3DDC97)
private val BrandBlue = Color(0xFF1B5E96)
private val BrandBlueDark = Color(0xFF4FA3E3)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00795A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2D8),
    onPrimaryContainer = Color(0xFF00281C),

    inversePrimary = Color(0xFF6FDCAE),

    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E4F7),
    onSecondaryContainer = Color(0xFF0A1E2E),

    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2B1700),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF101A24),
    onSurface = Color(0xFF101A24),
    onSurfaceVariant = Color(0xFF44566A),
    surfaceTint = Color(0xFF00795A),

    surface = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE3EA),
    surfaceBright = Color(0xFFF6F8FB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFEBF0F5),
    surfaceContainerHigh = Color(0xFFE5EBF1),
    surfaceContainerHighest = Color(0xFFDFE5EC),
    surfaceVariant = Color(0xFFE7EDF4),
    inverseSurface = Color(0xFF2E3A47),
    inverseOnSurface = Color(0xFFEFF3F8),

    outline = Color(0xFF8494A6),
    outlineVariant = Color(0xFFC6D0DB),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005235),
    onPrimaryContainer = Color(0xFF8DF8C4),

    inversePrimary = Color(0xFF00795A),

    secondary = BrandBlueDark,
    onSecondary = Color(0xFF00334F),
    secondaryContainer = Color(0xFF004B77),
    onSecondaryContainer = Color(0xFFCBE5FF),

    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693D00),
    onTertiaryContainer = Color(0xFFFFDDB0),

    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0906),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    background = Color(0xFF0F1B2A),
    onBackground = Color(0xFFE6EDF5),
    onSurface = Color(0xFFE6EDF5),
    onSurfaceVariant = Color(0xFFA9BCCE),
    surfaceTint = BrandGreenDark,

    surface = Color(0xFF0F1B2A),
    surfaceDim = Color(0xFF0F1B2A),
    surfaceBright = Color(0xFF35414E),
    surfaceContainerLowest = Color(0xFF0A1421),
    surfaceContainerLow = Color(0xFF16273A),
    surfaceContainer = Color(0xFF1A2C40),
    surfaceContainerHigh = Color(0xFF243749),
    surfaceContainerHighest = Color(0xFF2F4254),
    surfaceVariant = Color(0xFF1E3247),
    inverseSurface = Color(0xFFE6EDF5),
    inverseOnSurface = Color(0xFF1A2C40),

    outline = Color(0xFF3A5169),
    outlineVariant = Color(0xFF2A3F53),
    scrim = Color(0xFF000000),
)

@Composable
fun PorchlightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
