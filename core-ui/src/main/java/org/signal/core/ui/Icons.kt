/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview

object Icons {
  /**
   * Icon that takes a Brush instead of a Color for its foreground
   */
  @Composable
  fun BrushedForeground(
    painter: Painter,
    contentDescription: String?,
    foregroundBrush: Brush,
    modifier: Modifier = Modifier
  ) {
    Icon(
      painter = painter,
      contentDescription = contentDescription,
      modifier = modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
          onDrawWithContent {
            drawContent()
            drawRect(foregroundBrush, blendMode = BlendMode.SrcAtop)
          }
        }
    )
  }
}

@Preview
@Composable
private fun BrushedForegroundPreview() {
  Previews.Preview {
    Icons.BrushedForeground(
      painter = painterResource(id = android.R.drawable.ic_menu_camera),
      contentDescription = null,
      foregroundBrush = Brush.linearGradient(listOf(Color.Red, Color.Blue))
    )
  }
}
