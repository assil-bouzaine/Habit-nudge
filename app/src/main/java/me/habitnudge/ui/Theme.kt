package me.habitnudge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.habitnudge.data.Strictness

/** Facebook-style blue, used across the app, the nudge card, the Takeover screen and the launcher icon. */
val BrandBlue = Color(0xFF1877F2)
val BrandBlueDark = Color(0xFF0B4CB0)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE9FE),
    onPrimaryContainer = Color(0xFF062E6F),
    secondary = Color(0xFF52627A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EAF5),
    onSecondaryContainer = Color(0xFF172539),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFF3F6FB),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFE4E9F1),
    onSurfaceVariant = Color(0xFF5A6474),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFEFF3F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFE3E9F2),
    outline = Color(0xFFC3CBD7),
    outlineVariant = Color(0xFFDDE3EC),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E6),
    onErrorContainer = Color(0xFF5F1410),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6AA6FF),
    onPrimary = Color(0xFF002A66),
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color(0xFFDDE9FE),
    secondary = Color(0xFFB6C4DA),
    onSecondary = Color(0xFF203046),
    secondaryContainer = Color(0xFF2A384D),
    onSecondaryContainer = Color(0xFFDCE5F3),
    tertiary = Color(0xFF4DD0C1),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF232B36),
    onSurfaceVariant = Color(0xFFA3AEBD),
    surfaceContainerLowest = Color(0xFF161B22),
    surfaceContainerLow = Color(0xFF181E26),
    surfaceContainer = Color(0xFF1C232C),
    surfaceContainerHigh = Color(0xFF222A34),
    surfaceContainerHighest = Color(0xFF29323D),
    outline = Color(0xFF3D4754),
    outlineVariant = Color(0xFF2C3540),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F1410),
    errorContainer = Color(0xFF4A1C1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Accent colour per strictness level, used for the little pills. */
fun Strictness.color(): Color = when (this) {
    Strictness.GENTLE -> Color(0xFF1E9E5A)
    Strictness.STICKY -> BrandBlue
    Strictness.NAGGING -> Color(0xFFE09B00)
    Strictness.TAKEOVER -> Color(0xFFE5484D)
}

val SuccessGreen = Color(0xFF1E9E5A)
