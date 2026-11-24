package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BiotechTeal,
    onPrimary = Color.White,
    primaryContainer = GlacierMint,
    onPrimaryContainer = BiotechTealDark,
    secondary = EnterpriseSlate,
    onSecondary = Color.White,
    secondaryContainer = SoftSurfaceVariant,
    onSecondaryContainer = EnterpriseSlate,
    tertiary = StatusApprovedFg,
    onTertiary = Color.White,
    tertiaryContainer = StatusApprovedBg,
    onTertiaryContainer = StatusApprovedFg,
    background = IceCanvas,
    onBackground = Color(0xFF0F172A),
    surface = PureSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = SlateMuted,
    outlineVariant = SlateBorderLight,
    error = StatusDeclinedFg,
    onError = Color.White,
    errorContainer = StatusDeclinedBg,
    onErrorContainer = StatusDeclinedFg
)

private val DarkColorScheme = darkColorScheme(
    primary = BiotechTealLight,
    onPrimary = Color(0xFF00363B),
    primaryContainer = GlacierMintDeep,
    onPrimaryContainer = GlacierMint,
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = StatusApprovedFgDark,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = StatusApprovedBgDark,
    onTertiaryContainer = StatusApprovedFgDark,
    background = DarkCanvas,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = SlateBorderDark,
    error = StatusDeclinedFgDark,
    onError = Color(0xFF4C0519),
    errorContainer = StatusDeclinedBgDark,
    onErrorContainer = StatusDeclinedFgDark
)

val StaffTrackShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun StaffTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StaffTrackTypography,
        shapes = StaffTrackShapes,
        content = content
    )
}
