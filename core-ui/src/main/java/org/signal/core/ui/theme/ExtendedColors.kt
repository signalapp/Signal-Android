package org.signal.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
  val neutralSurface: Color,
  val colorOnCustom: Color,
  val colorOnCustomVariant: Color,
  val colorSurface1: Color,
  val colorSurface2: Color,
  val colorSurface3: Color,
  val colorSurface4: Color,
  val colorSurface5: Color,
  val colorTransparent1: Color,
  val colorTransparent2: Color,
  val colorTransparent3: Color,
  val colorTransparent4: Color,
  val colorTransparent5: Color,
  val colorNeutral: Color,
  val colorNeutralVariant: Color,
  val colorTransparentInverse1: Color,
  val colorTransparentInverse2: Color,
  val colorTransparentInverse3: Color,
  val colorTransparentInverse4: Color,
  val colorTransparentInverse5: Color,
  val colorNeutralInverse: Color,
  val colorNeutralVariantInverse: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
  ExtendedColors(
    neutralSurface = Color.Unspecified,
    colorOnCustom = Color.Unspecified,
    colorOnCustomVariant = Color.Unspecified,
    colorSurface1 = Color.Unspecified,
    colorSurface2 = Color.Unspecified,
    colorSurface3 = Color.Unspecified,
    colorSurface4 = Color.Unspecified,
    colorSurface5 = Color.Unspecified,
    colorTransparent1 = Color.Unspecified,
    colorTransparent2 = Color.Unspecified,
    colorTransparent3 = Color.Unspecified,
    colorTransparent4 = Color.Unspecified,
    colorTransparent5 = Color.Unspecified,
    colorNeutral = Color.Unspecified,
    colorNeutralVariant = Color.Unspecified,
    colorTransparentInverse1 = Color.Unspecified,
    colorTransparentInverse2 = Color.Unspecified,
    colorTransparentInverse3 = Color.Unspecified,
    colorTransparentInverse4 = Color.Unspecified,
    colorTransparentInverse5 = Color.Unspecified,
    colorNeutralInverse = Color.Unspecified,
    colorNeutralVariantInverse = Color.Unspecified
  )
}
