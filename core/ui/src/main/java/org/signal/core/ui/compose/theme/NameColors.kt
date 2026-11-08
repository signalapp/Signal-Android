/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette group member names are colored from, which also fills media cells while they load.
 */
object NameColors {
  val all: List<LightDarkColor> = listOf(
    LightDarkColor(light = Color(0xFF006DA3), dark = Color(0xFF00A7FA)),
    LightDarkColor(light = Color(0xFF067906), dark = Color(0xFF0AB80A)),
    LightDarkColor(light = Color(0xFFB814B8), dark = Color(0xFFF65AF6)),
    LightDarkColor(light = Color(0xFFC13215), dark = Color(0xFFFF6F52)),
    LightDarkColor(light = Color(0xFF5B6976), dark = Color(0xFF8BA1B6)),
    LightDarkColor(light = Color(0xFFCC0066), dark = Color(0xFFF76EB2)),
    LightDarkColor(light = Color(0xFF2E51FF), dark = Color(0xFF8599FF)),
    LightDarkColor(light = Color(0xFF007575), dark = Color(0xFF00B2B2)),
    LightDarkColor(light = Color(0xFF9C5711), dark = Color(0xFFD5920B)),
    LightDarkColor(light = Color(0xFFD00B4D), dark = Color(0xFFFF6B9C)),
    LightDarkColor(light = Color(0xFF8F2AF4), dark = Color(0xFFBF80FF)),
    LightDarkColor(light = Color(0xFF3D7406), dark = Color(0xFF5EB309)),
    LightDarkColor(light = Color(0xFFD00B0B), dark = Color(0xFFFF7070)),
    LightDarkColor(light = Color(0xFF007A3D), dark = Color(0xFF00B85C)),
    LightDarkColor(light = Color(0xFF5151F6), dark = Color(0xFF9494FF)),
    LightDarkColor(light = Color(0xFF866118), dark = Color(0xFFD68F00)),
    LightDarkColor(light = Color(0xFF067953), dark = Color(0xFF00B87A)),
    LightDarkColor(light = Color(0xFFA20CED), dark = Color(0xFFCF7CF8)),
    LightDarkColor(light = Color(0xFF4B7000), dark = Color(0xFF74AD00)),
    LightDarkColor(light = Color(0xFFC70A88), dark = Color(0xFFF76EC9)),
    LightDarkColor(light = Color(0xFFB34209), dark = Color(0xFFF57A3D)),
    LightDarkColor(light = Color(0xFF06792D), dark = Color(0xFF0AB844)),
    LightDarkColor(light = Color(0xFF7A3DF5), dark = Color(0xFFAF8AF9)),
    LightDarkColor(light = Color(0xFF6B6B24), dark = Color(0xFFA4A437)),
    LightDarkColor(light = Color(0xFFD00B2C), dark = Color(0xFFF77389)),
    LightDarkColor(light = Color(0xFF2D7906), dark = Color(0xFF42B309)),
    LightDarkColor(light = Color(0xFFAF0BD0), dark = Color(0xFFE06EF7)),
    LightDarkColor(light = Color(0xFF32763E), dark = Color(0xFF4BAF5C)),
    LightDarkColor(light = Color(0xFF2662D9), dark = Color(0xFF7DA1E8)),
    LightDarkColor(light = Color(0xFF76681E), dark = Color(0xFFB89B0A)),
    LightDarkColor(light = Color(0xFF067462), dark = Color(0xFF09B397)),
    LightDarkColor(light = Color(0xFF6447F5), dark = Color(0xFFA18FF9)),
    LightDarkColor(light = Color(0xFF5E6E0C), dark = Color(0xFF8FAA09)),
    LightDarkColor(light = Color(0xFF077288), dark = Color(0xFF00AED1)),
    LightDarkColor(light = Color(0xFFC20AA3), dark = Color(0xFFF75FDD)),
    LightDarkColor(light = Color(0xFF2D761E), dark = Color(0xFF43B42D))
  )

  /** A stable pick for [key], so the same item keeps its color across recompositions and reloads. */
  fun forKey(key: Any): LightDarkColor = all[Math.floorMod(key.hashCode(), all.size)]
}

@Immutable
data class LightDarkColor(val light: Color, val dark: Color) {
  @Composable
  fun resolve(): Color = if (isSystemInDarkTheme()) dark else light
}
