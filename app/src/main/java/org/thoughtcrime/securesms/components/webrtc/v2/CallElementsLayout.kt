/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

@Composable
fun CallElementsLayout(
  callGridSlot: @Composable () -> Unit,
  pictureInPictureSlot: @Composable () -> Unit,
  reactionsSlot: @Composable () -> Unit,
  raiseHandSlot: @Composable () -> Unit,
  callLinkBarSlot: @Composable () -> Unit,
  callOverflowSlot: @Composable () -> Unit,
  bottomInset: Dp,
  bottomSheetWidth: Dp,
  localRenderState: WebRtcLocalRenderState,
  modifier: Modifier = Modifier
) {
  val isPortrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
  val isFocused = localRenderState == WebRtcLocalRenderState.FOCUSED

  @Composable
  fun Bars() {
    Column(modifier = Modifier.fillMaxWidth()) {
      raiseHandSlot()
      callLinkBarSlot()
    }
  }

  val density = LocalDensity.current
  val pipSizePx = with(density) {
    (rememberSelfPipSize(localRenderState) + DpSize(32.dp, 0.dp)).toSize()
  }

  val bottomInsetPx = with(density) { bottomInset.roundToPx() }

  val bottomSheetWidthPx = with(density) {
    bottomSheetWidth.roundToPx()
  }

  SubcomposeLayout(modifier = modifier) { constraints ->
    // First, measure the bars to get their height
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

    // Measure overflow to determine constraints for bars
    val overflowPlaceables = subcompose("overflow_measure", callOverflowSlot).map { it.measure(looseConstraints) }
    val constrainedHeightOffset = if (isPortrait) overflowPlaceables.maxOfOrNull { it.height } ?: 0 else 0
    val constrainedWidthOffset = if (isPortrait) { 0 } else overflowPlaceables.maxOfOrNull { it.width } ?: 0

    val nonOverflowConstraints = looseConstraints.offset(horizontal = -constrainedWidthOffset, vertical = -constrainedHeightOffset)

    val barConstraints = if (bottomInsetPx > constrainedHeightOffset) {
      looseConstraints.offset(-constrainedWidthOffset, -bottomInsetPx)
    } else {
      nonOverflowConstraints
    }

    val barsMaxWidth = minOf(barConstraints.maxWidth, bottomSheetWidthPx)
    val barsConstrainedToSheet = barConstraints.copy(maxWidth = barsMaxWidth)

    val barsPlaceables = subcompose("bars_measure") { Bars() }.map { it.measure(barsConstrainedToSheet) }
    val barsHeightPx = barsPlaceables.sumOf { it.height }
    val barsWidthPx = barsPlaceables.maxOfOrNull { it.width } ?: 0

    // Now compose and measure the actual layers with the bars height available
    val blurrableLayerPlaceable = subcompose("blurrable") {
      BlurrableContentLayer(
        isFocused = isFocused,
        isPortrait = isPortrait,
        bottomInsetPx = bottomInsetPx,
        bottomSheetWidthPx = bottomSheetWidthPx,
        barsSlot = { Bars() },
        callGridSlot = callGridSlot,
        reactionsSlot = reactionsSlot,
        callOverflowSlot = callOverflowSlot
      )
    }.map { it.measure(constraints) }

    // Use the wider of bars or bottom sheet for space calculation
    val centeredContentWidthPx = maxOf(barsWidthPx, bottomSheetWidthPx)

    val pipLayerPlaceable = subcompose("pip") {
      PipLayer(
        pictureInPictureSlot = pictureInPictureSlot,
        localRenderState = localRenderState,
        bottomInsetPx = bottomInsetPx,
        barsHeightPx = barsHeightPx,
        pipSizePx = pipSizePx,
        centeredContentWidthPx = centeredContentWidthPx
      )
    }.map { it.measure(constraints) }

    layout(constraints.maxWidth, constraints.maxHeight) {
      blurrableLayerPlaceable.forEach { it.place(0, 0) }
      pipLayerPlaceable.forEach { it.place(0, 0) }
    }
  }
}

private enum class BlurrableContentSlot {
  BARS,
  GRID,
  REACTIONS,
  OVERFLOW
}

@Composable
private fun BlurrableContentLayer(
  isFocused: Boolean,
  isPortrait: Boolean,
  bottomInsetPx: Int,
  bottomSheetWidthPx: Int,
  barsSlot: @Composable () -> Unit,
  callGridSlot: @Composable () -> Unit,
  reactionsSlot: @Composable () -> Unit,
  callOverflowSlot: @Composable () -> Unit
) {
  BlurContainer(
    isBlurred = isFocused,
    modifier = Modifier.fillMaxSize()
  ) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
      val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

      val overflowPlaceables = subcompose(BlurrableContentSlot.OVERFLOW, callOverflowSlot)
        .map { it.measure(looseConstraints) }
      val constrainedHeightOffset = if (isPortrait) overflowPlaceables.maxOfOrNull { it.height } ?: 0 else 0
      val constrainedWidthOffset = if (isPortrait) 0 else overflowPlaceables.maxOfOrNull { it.width } ?: 0

      val nonOverflowConstraints = looseConstraints.offset(horizontal = -constrainedWidthOffset, vertical = -constrainedHeightOffset)
      val gridPlaceables = subcompose(BlurrableContentSlot.GRID, callGridSlot)
        .map { it.measure(nonOverflowConstraints) }

      val barConstraints = if (bottomInsetPx > constrainedHeightOffset) {
        looseConstraints.offset(-constrainedWidthOffset, -bottomInsetPx)
      } else {
        nonOverflowConstraints
      }

      // Cap bars width to sheet max width (bars can be narrower if content doesn't fill)
      val barsMaxWidth = minOf(barConstraints.maxWidth, bottomSheetWidthPx)
      val barsConstrainedToSheet = barConstraints.copy(maxWidth = barsMaxWidth)

      val barsPlaceables = subcompose(BlurrableContentSlot.BARS, barsSlot)
        .map { it.measure(barsConstrainedToSheet) }

      val barsHeightOffset = barsPlaceables.sumOf { it.height }
      val reactionsConstraints = barConstraints.offset(vertical = -barsHeightOffset)
      val reactionsPlaceables = subcompose(BlurrableContentSlot.REACTIONS, reactionsSlot)
        .map { it.measure(reactionsConstraints) }

      layout(looseConstraints.maxWidth, looseConstraints.maxHeight) {
        overflowPlaceables.forEach {
          if (isPortrait) {
            it.place(0, looseConstraints.maxHeight - it.height)
          } else {
            it.place(looseConstraints.maxWidth - it.width, 0)
          }
        }

        gridPlaceables.forEach {
          it.place(0, 0)
        }

        barsPlaceables.forEach {
          val barsX = (looseConstraints.maxWidth - it.width) / 2
          it.place(barsX, barConstraints.maxHeight - it.height)
        }

        reactionsPlaceables.forEach {
          it.place(0, 0)
        }
      }
    }
  }
}

@Composable
private fun PipLayer(
  pictureInPictureSlot: @Composable () -> Unit,
  localRenderState: WebRtcLocalRenderState,
  bottomInsetPx: Int,
  barsHeightPx: Int,
  pipSizePx: Size,
  centeredContentWidthPx: Int
) {
  Layout(
    content = pictureInPictureSlot,
    modifier = Modifier.fillMaxSize()
  ) { measurables, constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

    val pictureInPictureConstraints: Constraints = when (localRenderState) {
      WebRtcLocalRenderState.GONE, WebRtcLocalRenderState.SMALLER_RECTANGLE, WebRtcLocalRenderState.LARGE, WebRtcLocalRenderState.LARGE_NO_VIDEO, WebRtcLocalRenderState.FOCUSED -> constraints
      WebRtcLocalRenderState.SMALL_RECTANGLE, WebRtcLocalRenderState.EXPANDED -> {
        // Check if there's enough space on either side of the centered content (bars/sheet)
        val spaceOnEachSide = (looseConstraints.maxWidth - centeredContentWidthPx) / 2
        val shouldOffset = centeredContentWidthPx > 0 && spaceOnEachSide < pipSizePx.width
        val offsetAmount = bottomInsetPx + barsHeightPx
        looseConstraints.offset(vertical = if (shouldOffset) -offsetAmount else 0)
      }
    }

    val pictureInPicturePlaceables = measurables.map { it.measure(pictureInPictureConstraints) }

    layout(looseConstraints.maxWidth, looseConstraints.maxHeight) {
      pictureInPicturePlaceables.forEach {
        it.place(0, 0)
      }
    }
  }
}

@AllNightPreviews
@Composable
private fun CallElementsLayoutPreview() {
  val metrics = rememberCallScreenMetrics()
  val isPortrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
  val localRenderState = WebRtcLocalRenderState.SMALL_RECTANGLE

  Previews.Preview {
    CallElementsLayout(
      callGridSlot = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(color = Color.Gray)
        )
      },
      pictureInPictureSlot = {
        MoveableLocalVideoRenderer(
          localParticipant = CallParticipant(
            recipient = Recipient(id = RecipientId.from(1L), isResolving = false, systemContactName = "Test")
          ),
          localRenderState = localRenderState,
          savedLocalParticipantLandscape = false,
          onClick = {},
          onFocusLocalParticipantClick = {},
          onToggleCameraDirectionClick = {}
        )
      },
      reactionsSlot = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(color = Color.Yellow)
        )
      },
      raiseHandSlot = {
        Box(
          modifier = Modifier
            .padding(16.dp)
            .height(48.dp)
            .fillMaxWidth()
            .background(color = Color.Green)
        )
      },
      callLinkBarSlot = {
        Box(
          modifier = Modifier
            .padding(16.dp)
            .height(48.dp)
            .fillMaxWidth()
            .background(color = Color.Blue)
        )
      },
      callOverflowSlot = {
        Box(
          modifier = Modifier
            .padding(16.dp)
            .then(
              if (isPortrait) {
                Modifier
                  .fillMaxWidth()
                  .height(metrics.overflowParticipantRendererAvatarSize)
              } else {
                Modifier
                  .fillMaxHeight()
                  .width(metrics.overflowParticipantRendererAvatarSize)
              }
            )
            .background(color = Color.Red)
        )
      },
      bottomInset = 120.dp,
      bottomSheetWidth = CallScreenMetrics.SheetMaxWidth,
      localRenderState = localRenderState
    )
  }
}
