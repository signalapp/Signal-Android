/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme
import kotlin.math.min

@Composable
fun Cutout(
  cutoutShape: Shape,
  modifier: Modifier = Modifier,
  cutoutPadding: PaddingValues = PaddingValues.Zero,
  fillColor: Color = SignalTheme.colors.colorTransparent4
) {
  BoxWithConstraints(
    modifier = modifier
  ) {
    val width = maxWidth
    val height = maxHeight

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val pxPad: Rect = remember(cutoutPadding, density, layoutDirection) {
      with(density) {
        Rect(
          left = cutoutPadding.calculateLeftPadding(layoutDirection).roundToPx().toFloat(),
          top = cutoutPadding.calculateTopPadding().roundToPx().toFloat(),
          right = cutoutPadding.calculateRightPadding(layoutDirection).roundToPx().toFloat(),
          bottom = cutoutPadding.calculateBottomPadding().roundToPx().toFloat()
        )
      }
    }

    val pxSize = remember(density, width, height, pxPad) {
      with(density) {
        val size = min(width.roundToPx(), height.roundToPx()).toFloat()

        Size(size - pxPad.left - pxPad.right, size - pxPad.top - pxPad.bottom)
      }
    }

    val path = remember(density, layoutDirection, pxSize) {
      val outline = cutoutShape.createOutline(pxSize, layoutDirection, density)
      Path().apply {
        addOutline(outline)
      }
    }

    val shapeOffset = remember(pxSize) {
      Offset(pxSize.width / 2f, pxSize.height / 2f)
    }

    val clipPath = remember { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
      clipPath.reset()
      clipPath.addPath(path, center - shapeOffset)

      clipPath(clipPath, clipOp = ClipOp.Difference) {
        drawRect(color = fillColor)
      }
    }
  }
}

@Preview
@Composable
fun CutoutPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = Brush.linearGradient(
            colors = listOf(Color.Red, Color.Green)
          )
        )
    )

    Cutout(
      cutoutShape = CircleShape,
      cutoutPadding = PaddingValues(24.dp),
      modifier = Modifier.fillMaxSize()
    )
  }
}
