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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
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

/**
 * Mutable holder for bar dimensions, used to pass measurement results from
 * BlurrableContentLayer to PipLayer during the same Layout measurement pass
 * without requiring SubcomposeLayout.
 */
private class BarDimensions {
  var heightPx: Int = 0
  var widthPx: Int = 0
}

/**
 * Arranges call screen content in coordinated layers so the local PiP can avoid centered bars.
 *
 * @param callGridSlot Main call grid content.
 * @param pictureInPictureSlot Local participant PiP content.
 * @param reactionsSlot Reactions overlay content.
 * @param raiseHandSlot Slot for the raised-hand bar.
 * @param callLinkBarSlot Slot for the call-link bar.
 * @param callOverflowSlot Overflow participants strip.
 * @param audioIndicatorSlot Participant audio indicator content.
 * @param bottomInset Bottom inset used to keep content clear of anchored UI.
 * @param bottomSheetWidth Maximum width of centered bottom content.
 * @param localRenderState Current local renderer mode.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun CallElementsLayout(
  callGridSlot: @Composable () -> Unit,
  pictureInPictureSlot: @Composable () -> Unit,
  reactionsSlot: @Composable () -> Unit,
  raiseHandSlot: @Composable () -> Unit,
  callLinkBarSlot: @Composable () -> Unit,
  callOverflowSlot: @Composable () -> Unit,
  audioIndicatorSlot: @Composable () -> Unit,
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

  val barDimensions = remember { BarDimensions() }

  Layout(
    contents = listOf(
      {
        BlurrableContentLayer(
          isFocused = isFocused,
          isPortrait = isPortrait,
          bottomInsetPx = bottomInsetPx,
          bottomSheetWidthPx = bottomSheetWidthPx,
          barsSlot = { Bars() },
          callGridSlot = callGridSlot,
          reactionsSlot = reactionsSlot,
          callOverflowSlot = callOverflowSlot,
          audioIndicatorSlot = audioIndicatorSlot,
          onMeasured = { barsHeight, barsWidth ->
            barDimensions.heightPx = barsHeight
            barDimensions.widthPx = barsWidth
          }
        )
      },
      {
        PipLayer(
          pictureInPictureSlot = pictureInPictureSlot,
          localRenderState = localRenderState,
          bottomInsetPx = bottomInsetPx,
          barDimensions = barDimensions,
          pipSizePx = pipSizePx,
          bottomSheetWidthPx = bottomSheetWidthPx
        )
      }
    ),
    modifier = modifier
  ) { (blurrableMeasurables, pipMeasurables), constraints ->
    val blurrablePlaceables = blurrableMeasurables.map { it.measure(constraints) }
    val pipPlaceables = pipMeasurables.map { it.measure(constraints) }

    layout(constraints.maxWidth, constraints.maxHeight) {
      blurrablePlaceables.forEach { it.place(0, 0) }
      pipPlaceables.forEach { it.place(0, 0) }
    }
  }
}

/**
 * A layer that contains content which can be blurred when the local participant video is focused.
 * Uses a single multi-content Layout pass to avoid re-subcomposing slots that contain
 * SubcomposeLayout (like BoxWithConstraints), which can trigger duplicate key crashes.
 *
 * @param onMeasured Callback invoked during measurement with (barsHeight, barsWidth) to report
 *                   dimensions needed by the parent layout for PipLayer positioning.
 */
@Composable
private fun BlurrableContentLayer(
  isFocused: Boolean,
  isPortrait: Boolean,
  bottomInsetPx: Int,
  bottomSheetWidthPx: Int,
  barsSlot: @Composable () -> Unit,
  callGridSlot: @Composable () -> Unit,
  reactionsSlot: @Composable () -> Unit,
  callOverflowSlot: @Composable () -> Unit,
  audioIndicatorSlot: @Composable () -> Unit,
  onMeasured: (barsHeight: Int, barsWidth: Int) -> Unit
) {
  BlurContainer(
    isBlurred = isFocused,
    modifier = Modifier.fillMaxSize()
  ) {
    Layout(
      contents = listOf(
        { callOverflowSlot() },
        { callGridSlot() },
        { barsSlot() },
        { reactionsSlot() },
        { audioIndicatorSlot() }
      ),
      modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
      val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

      val (overflowMeasurables, gridMeasurables, barsMeasurables, reactionsMeasurables, audioIndicatorMeasurables) = measurables

      val overflowPlaceables = overflowMeasurables.map { it.measure(looseConstraints) }
      val constrainedHeightOffset = if (isPortrait) overflowPlaceables.maxOfOrNull { it.height } ?: 0 else 0
      val constrainedWidthOffset = if (isPortrait) 0 else overflowPlaceables.maxOfOrNull { it.width } ?: 0

      val nonOverflowConstraints = looseConstraints.offset(horizontal = -constrainedWidthOffset, vertical = -constrainedHeightOffset)
      val gridPlaceables = gridMeasurables.map { it.measure(nonOverflowConstraints) }

      val barConstraints = if (bottomInsetPx > constrainedHeightOffset) {
        looseConstraints.offset(-constrainedWidthOffset, -bottomInsetPx)
      } else {
        nonOverflowConstraints
      }

      val barsMaxWidth = minOf(barConstraints.maxWidth, bottomSheetWidthPx)
      val barsConstrainedToSheet = barConstraints.copy(maxWidth = barsMaxWidth)

      val barsPlaceables = barsMeasurables.map { it.measure(barsConstrainedToSheet) }
      val barsHeightPx = barsPlaceables.sumOf { it.height }
      val barsWidthPx = barsPlaceables.maxOfOrNull { it.width } ?: 0

      onMeasured(barsHeightPx, barsWidthPx)

      val reactionsConstraints = barConstraints.offset(vertical = -barsHeightPx)
      val reactionsPlaceables = reactionsMeasurables.map { it.measure(reactionsConstraints) }

      val audioIndicatorPlaceables = audioIndicatorMeasurables.map { it.measure(looseConstraints) }

      layout(looseConstraints.maxWidth, looseConstraints.maxHeight) {
        if (isPortrait) {
          overflowPlaceables.forEach {
            it.place(0, looseConstraints.maxHeight - it.height)
          }
        } else {
          overflowPlaceables.forEach {
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

        audioIndicatorPlaceables.forEach {
          val gutterWidth = (looseConstraints.maxWidth - bottomSheetWidthPx) / 2
          val fitsInGutter = gutterWidth >= it.width
          val y = if (fitsInGutter) {
            looseConstraints.maxHeight - it.height
          } else {
            looseConstraints.maxHeight - bottomInsetPx - barsHeightPx - it.height
          }
          it.place(0, y)
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
  barDimensions: BarDimensions,
  pipSizePx: Size,
  bottomSheetWidthPx: Int
) {
  Layout(
    content = pictureInPictureSlot,
    modifier = Modifier.fillMaxSize()
  ) { measurables, constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

    val centeredContentWidthPx = maxOf(barDimensions.widthPx, bottomSheetWidthPx)

    val pictureInPictureConstraints: Constraints = when (localRenderState) {
      WebRtcLocalRenderState.GONE, WebRtcLocalRenderState.SMALLER_RECTANGLE, WebRtcLocalRenderState.LARGE, WebRtcLocalRenderState.LARGE_NO_VIDEO, WebRtcLocalRenderState.FOCUSED -> constraints
      WebRtcLocalRenderState.SMALL_RECTANGLE, WebRtcLocalRenderState.EXPANDED -> {
        val spaceOnEachSide = (looseConstraints.maxWidth - centeredContentWidthPx) / 2
        val shouldOffset = centeredContentWidthPx > 0 && spaceOnEachSide < pipSizePx.width
        val offsetAmount = bottomInsetPx + barDimensions.heightPx
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
      audioIndicatorSlot = {
        val mutedParticipant = CallParticipant(
          recipient = Recipient(
            id = RecipientId.from(2L),
            isResolving = false,
            systemContactName = "Muted Participant"
          ),
          audioLevel = null
        )

        ParticipantAudioIndicator(
          participant = mutedParticipant,
          selfPipMode = SelfPipMode.NOT_SELF_PIP
        )
      },
      bottomInset = 120.dp,
      bottomSheetWidth = CallScreenMetrics.SheetMaxWidth,
      localRenderState = localRenderState
    )
  }
}
