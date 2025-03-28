/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sqrt

/**
 * Circle Reveal Modifiers found here:
 * https://gist.github.com/darvld/eb3844474baf2f3fc6d3ab44a4b4b5f8
 *
 * A modifier that clips the composable content using a circular shape. The radius of the circle
 * will be determined by the [transitionProgress].
 *
 * The values of the progress should be between 0 and 1.
 *
 * By default, the circle is centered in the content, but custom positions may be specified using
 * [revealFrom]. Specified offsets should be between 0 (left/top) and 1 (right/bottom).
 */
fun Modifier.circularReveal(
  transitionProgress: State<Float>,
  revealFrom: Offset = Offset(0.5f, 0.5f)
): Modifier {
  return drawWithCache {
    val path = Path()

    val center = revealFrom.mapTo(size)
    val radius = calculateRadius(revealFrom, size)

    path.addOval(Rect(center, radius * transitionProgress.value))

    onDrawWithContent {
      clipPath(path) { this@onDrawWithContent.drawContent() }
    }
  }
}

private fun Offset.mapTo(size: Size): Offset {
  return Offset(x * size.width, y * size.height)
}

private fun calculateRadius(normalizedOrigin: Offset, size: Size) = with(normalizedOrigin) {
  val x = (if (x > 0.5f) x else 1 - x) * size.width
  val y = (if (y > 0.5f) y else 1 - y) * size.height

  sqrt(x * x + y * y)
}
