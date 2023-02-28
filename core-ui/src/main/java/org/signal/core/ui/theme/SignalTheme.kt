package org.signal.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp

private val typography = Typography().run {
  copy(
    headlineLarge = headlineLarge.copy(
      lineHeight = 40.sp,
      letterSpacing = 0.sp
    ),
    headlineMedium = headlineMedium.copy(
      lineHeight = 36.sp,
      letterSpacing = 0.sp
    ),
    titleLarge = titleLarge.copy(
      lineHeight = 28.sp,
      letterSpacing = 0.sp
    ),
    titleMedium = titleMedium.copy(
      fontFamily = FontFamily.SansSerif,
      fontStyle = FontStyle.Normal,
      fontSize = 18.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.0125.sp
    ),
    titleSmall = titleSmall.copy(
      fontSize = 16.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.0125.sp
    ),
    bodyLarge = bodyLarge.copy(
      lineHeight = 22.sp,
      letterSpacing = 0.0125.sp
    ),
    bodyMedium = bodyMedium.copy(
      lineHeight = 20.sp,
      letterSpacing = 0.0107.sp
    ),
    bodySmall = bodySmall.copy(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.0192.sp
    ),
    labelLarge = labelLarge.copy(
      lineHeight = 20.sp,
      letterSpacing = 0.0107.sp
    ),
    labelMedium = labelMedium.copy(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.0192.sp
    ),
    labelSmall = labelSmall.copy(
      lineHeight = 16.sp,
      letterSpacing = 0.025.sp
    )
  )
}

private val lightColorScheme = lightColorScheme(
  primary = Color(0xFF2C58C3),
  primaryContainer = Color(0xFFD2DFFB),
  secondary = Color(0xFF586071),
  secondaryContainer = Color(0xFFDCE5F9),
  surface = Color(0xFFFBFCFF),
  surfaceVariant = Color(0xFFE7EBF3),
  background = Color(0xFFFBFCFF),
  error = Color(0xFFBA1B1B),
  errorContainer = Color(0xFFFFDAD4),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF051845),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF151D2C),
  onSurface = Color(0xFF1B1B1D),
  onSurfaceVariant = Color(0xFF545863),
  onBackground = Color(0xFF1B1D1D),
  outline = Color(0xFF808389)
)

private val lightExtendedColors = ExtendedColors(
  neutralSurface = Color(0x99FFFFFF),
  colorOnCustom = Color(0xFFFFFFFF),
  colorOnCustomVariant = Color(0xB3FFFFFF),
  colorSurface1 = Color(0xFFF2F5F9),
  colorSurface2 = Color(0xFFEDF0F6),
  colorSurface3 = Color(0xFFE8ECF4),
  colorSurface4 = Color(0xFFE6EAF3),
  colorSurface5 = Color(0xFFE3E7F1),
  colorTransparent1 = Color(0x14FFFFFF),
  colorTransparent2 = Color(0x29FFFFFF),
  colorTransparent3 = Color(0x8FFFFFFF),
  colorTransparent4 = Color(0xB8FFFFFF),
  colorTransparent5 = Color(0xF5FFFFFF),
  colorNeutral = Color(0xFFFFFFFF),
  colorNeutralVariant = Color(0xB8FFFFFF),
  colorTransparentInverse1 = Color(0x0A000000),
  colorTransparentInverse2 = Color(0x14000000),
  colorTransparentInverse3 = Color(0x66000000),
  colorTransparentInverse4 = Color(0xB8000000),
  colorTransparentInverse5 = Color(0xE0000000),
  colorNeutralInverse = Color(0xFF121212),
  colorNeutralVariantInverse = Color(0xFF5C5C5C)
)

private val darkExtendedColors = ExtendedColors(
  neutralSurface = Color(0x14FFFFFF),
  colorOnCustom = Color(0xFFFFFFFF),
  colorOnCustomVariant = Color(0xB3FFFFFF),
  colorSurface1 = Color(0xFF23242A),
  colorSurface2 = Color(0xFF272A31),
  colorSurface3 = Color(0xFF2C2F37),
  colorSurface4 = Color(0xFF2E3039),
  colorSurface5 = Color(0xFF31343E),
  colorTransparent1 = Color(0x0AFFFFFF),
  colorTransparent2 = Color(0x1FFFFFFF),
  colorTransparent3 = Color(0x29FFFFFF),
  colorTransparent4 = Color(0x7AFFFFFF),
  colorTransparent5 = Color(0xB8FFFFFF),
  colorNeutral = Color(0xFF121212),
  colorNeutralVariant = Color(0xFF5C5C5C),
  colorTransparentInverse1 = Color(0x0A000000),
  colorTransparentInverse2 = Color(0x14000000),
  colorTransparentInverse3 = Color(0x29000000),
  colorTransparentInverse4 = Color(0xB8000000),
  colorTransparentInverse5 = Color(0xF5000000),
  colorNeutralInverse = Color(0xE0FFFFFF),
  colorNeutralVariantInverse = Color(0xA3FFFFFF)
)

private val darkColorScheme = darkColorScheme(
  primary = Color(0xFFB6C5FA),
  primaryContainer = Color(0xFF464B5C),
  secondary = Color(0xFFC1C6DD),
  secondaryContainer = Color(0xFF414659),
  surface = Color(0xFF1B1C1F),
  surfaceVariant = Color(0xFF303133),
  background = Color(0xFF1B1C1F),
  error = Color(0xFFFFB4A9),
  errorContainer = Color(0xFF930006),
  onPrimary = Color(0xFF1E2438),
  onPrimaryContainer = Color(0xFFDBE1FC),
  onSecondary = Color(0xFF2A3042),
  onSecondaryContainer = Color(0xFFDCE1F9),
  onSurface = Color(0xFFE2E1E5),
  onSurfaceVariant = Color(0xFFBEBFC5),
  onBackground = Color(0xFFE2E1E5),
  outline = Color(0xFF5C5E65)
)

@Composable
fun SignalTheme(
  isDarkMode: Boolean,
  content: @Composable () -> Unit
) {
  val extendedColors = if (isDarkMode) darkExtendedColors else lightExtendedColors

  CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
    MaterialTheme(
      colorScheme = if (isDarkMode) darkColorScheme else lightColorScheme,
      typography = typography,
      content = content
    )
  }
}

object SignalTheme {
  val colors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
}
