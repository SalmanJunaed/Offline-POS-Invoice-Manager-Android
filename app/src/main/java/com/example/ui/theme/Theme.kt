package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ==========================================
// Stage 05: Single Premium White-Green Theme
// Dark mode & dynamic colors permanently disabled
// ==========================================
private val WhiteGreenColorScheme: ColorScheme = lightColorScheme(
    primary = PosDeepGreen,
    onPrimary = Color.White,
    primaryContainer = PosMintGreen,
    onPrimaryContainer = PosDeepGreen,

    secondary = PosEmerald,
    onSecondary = Color.White,
    secondaryContainer = PosLightGreen,
    onSecondaryContainer = PosCharcoalText,

    tertiary = PosFreshGreen,
    onTertiary = Color.White,
    tertiaryContainer = PosPaleGreen,
    onTertiaryContainer = PosCharcoalText,

    background = PosCleanWhite,
    onBackground = PosCharcoalText,

    surface = PosCleanWhite,
    onSurface = PosCharcoalText,
    surfaceVariant = PosMintGreen,
    onSurfaceVariant = PosCharcoalText,

    outline = PosBorderGreen,
    outlineVariant = PosBorderNeutral,

    error = PosDueRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Dynamic color & dark theme permanently eliminated as per Stage 05 specification.
    // The entire application exclusively uses the polished White-Green theme.
    MaterialTheme(
        colorScheme = WhiteGreenColorScheme,
        typography = Typography,
        content = content
    )
}
