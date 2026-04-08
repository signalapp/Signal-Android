/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview

/**
 * Lays out [primary] and [secondary] side by side in a row. [primaryProportion] controls what
 * fraction of the total width [primary] receives (e.g. 0.5f = equal split).
 */
@Composable
fun SideBySideLayout(
  primary: @Composable () -> Unit,
  secondary: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  primaryProportion: Float = 0.5f
) {
  SubcomposeLayout(modifier = modifier) { constraints ->
    val primaryWidth = (constraints.maxWidth * primaryProportion).toInt()
    val secondaryWidth = constraints.maxWidth - primaryWidth

    val primaryPlaceables = subcompose("primary", primary).map { m ->
      m.measure(constraints.copy(minWidth = primaryWidth, maxWidth = primaryWidth, minHeight = 0))
    }
    val primaryHeight = primaryPlaceables.maxOfOrNull { it.height } ?: 0

    val secondaryMeasurables = subcompose("secondary", secondary)
    val secondaryMinHeight = secondaryMeasurables.maxOfOrNull { it.minIntrinsicHeight(secondaryWidth) } ?: 0
    val layoutHeight = maxOf(primaryHeight, secondaryMinHeight)

    val secondaryPlaceables = secondaryMeasurables.map { m ->
      m.measure(constraints.copy(minWidth = secondaryWidth, maxWidth = secondaryWidth, minHeight = layoutHeight, maxHeight = layoutHeight))
    }

    layout(constraints.maxWidth, layoutHeight) {
      primaryPlaceables.forEach { it.placeRelative(0, 0) }
      secondaryPlaceables.forEach { it.placeRelative(primaryWidth, 0) }
    }
  }
}

@Preview
@Composable
private fun SideBySideLayoutPreview() {
  Previews.Preview {
    SideBySideLayout(
      primary = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        )
      },
      secondary = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
        )
      },
      modifier = Modifier.fillMaxSize()
    )
  }
}
