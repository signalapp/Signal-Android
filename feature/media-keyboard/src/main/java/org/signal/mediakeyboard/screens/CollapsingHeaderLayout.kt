/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import org.signal.core.ui.compose.keyboard.KeyboardSheetDragRegion
import kotlin.math.roundToInt

/** Past this, in pixels per second, a release is taken as heading somewhere rather than just letting go. */
private const val SETTLE_VELOCITY_THRESHOLD = 300f

/**
 * Lays out [header] above [content], collapsed to nothing until the content is pulled down past its start.
 *
 * Like an app bar in a CoordinatorLayout, the header is something the user pulls down to rather than something
 * occupying a row's worth of room. It starts collapsed, so it never shows before content arrives and then has to be
 * scrolled away, and it takes up only as much room as it has been pulled open by. Once showing, it is as good a place
 * to drag the keyboard's sheet from as the chrome above it.
 *
 * @param onRevealedChange Reports whether the header is fully open, for hosts that offer the same thing elsewhere
 *   while it is closed.
 * @param resetKey Closes the header again whenever this changes, for content that is being replaced wholesale.
 */
@Composable
internal fun CollapsingHeaderLayout(
  header: @Composable () -> Unit,
  onRevealedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  resetKey: Any? = Unit,
  content: @Composable () -> Unit
) {
  var headerHeightPx by remember { mutableIntStateOf(0) }
  var revealedPx by remember(resetKey) { mutableFloatStateOf(0f) }

  val scrollConnection = remember(headerHeightPx) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || revealedPx <= 0f) {
          return Offset.Zero
        }

        // Dragging up closes the header before the content underneath moves at all.
        val taken = -minOf(-available.y, revealedPx)
        revealedPx += taken
        return Offset(0f, taken)
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0f || revealedPx >= headerHeightPx) {
          return Offset.Zero
        }

        // Whatever downward drag the content had no room for opens the header instead.
        val taken = minOf(available.y, headerHeightPx - revealedPx)
        revealedPx += taken
        return Offset(0f, taken)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        val height = headerHeightPx
        if (height <= 0 || revealedPx <= 0f || revealedPx >= height) {
          return Velocity.Zero
        }

        // Released part way, so the header goes to whichever end the drag was heading for, or the nearer one when it
        // was not really heading anywhere. Scrollable cancels this if the user takes hold again.
        val target = when {
          available.y > SETTLE_VELOCITY_THRESHOLD -> height.toFloat()
          available.y < -SETTLE_VELOCITY_THRESHOLD -> 0f
          revealedPx >= height / 2f -> height.toFloat()
          else -> 0f
        }

        animate(initialValue = revealedPx, targetValue = target, initialVelocity = available.y) { value, _ ->
          revealedPx = value
        }

        return Velocity(0f, available.y)
      }
    }
  }

  val revealed by remember { derivedStateOf { headerHeightPx > 0 && revealedPx >= headerHeightPx } }

  LaunchedEffect(revealed) {
    onRevealedChange(revealed)
  }

  Layout(
    contents = listOf(
      { Box(modifier = Modifier.onSizeChanged { headerHeightPx = it.height }) { KeyboardSheetDragRegion { header() } } },
      content
    ),
    modifier = modifier
      .fillMaxSize()
      .clipToBounds()
      .nestedScroll(scrollConnection)
  ) { (headerMeasurables, contentMeasurables), constraints ->
    // Measured at full height even while collapsed, so the header's own size is what decides how far it can open.
    val headerPlaceable = headerMeasurables.first().measure(constraints.copy(minHeight = 0))
    val reveal = revealedPx.roundToInt().coerceIn(0, headerPlaceable.height)
    val contentPlaceable = contentMeasurables.first().measure(
      constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - reveal).coerceAtLeast(0))
    )

    layout(constraints.maxWidth, constraints.maxHeight) {
      headerPlaceable.placeRelative(0, reveal - headerPlaceable.height)
      contentPlaceable.placeRelative(0, reveal)
    }
  }
}
