/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Thin divider lines for separating content.
 */
object Dividers {
  @Composable
  fun Default(modifier: Modifier = Modifier) {
    Divider(
      thickness = 1.5.dp,
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = modifier.padding(vertical = 16.25.dp)
    )
  }

  @Composable
  fun Vertical(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.5.dp,
    color: Color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    val targetThickness = if (thickness == Dp.Hairline) {
      (1f / LocalDensity.current.density).dp
    } else {
      thickness
    }
    Box(
      modifier
        .width(targetThickness)
        .background(color = color)
    )
  }
}

@Preview
@Composable
private fun DefaultPreview() {
  SignalTheme(isDarkMode = false) {
    Dividers.Default()
  }
}

@Preview
@Composable
private fun VerticalPreview() {
  SignalTheme(isDarkMode = false) {
    Dividers.Vertical(modifier = Modifier.height(20.dp))
  }
}
