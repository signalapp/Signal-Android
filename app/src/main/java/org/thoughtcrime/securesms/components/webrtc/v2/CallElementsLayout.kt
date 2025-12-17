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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.window.core.layout.WindowSizeClass
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
  val isCompactPortrait = !currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

  @Composable
  fun Bars() {
    Column {
      raiseHandSlot()
      callLinkBarSlot()
    }
  }

  val density = LocalDensity.current
  val pipSizePx = with(density) {
    (rememberSelfPipSize(localRenderState) + DpSize(32.dp, 0.dp)).toSize()
  }

  val bottomInsetPx = with(density) {
    if (isCompactPortrait) 0 else bottomInset.roundToPx()
  }

  val bottomSheetWidthPx = with(density) {
    bottomSheetWidth.roundToPx()
  }

  Layout(
    contents = listOf(::Bars, callGridSlot, reactionsSlot, pictureInPictureSlot, callOverflowSlot),
    modifier = if (isCompactPortrait) { Modifier.padding(bottom = bottomInset).then(modifier) } else modifier
  ) { measurables, constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val overflowPlaceables = measurables[4].map { it.measure(looseConstraints) }
    val constrainedHeightOffset = if (isPortrait) overflowPlaceables.maxOfOrNull { it.height } ?: 0 else 0
    val constrainedWidthOffset = if (isPortrait) { 0 } else overflowPlaceables.maxOfOrNull { it.width } ?: 0

    val nonOverflowConstraints = looseConstraints.offset(horizontal = -constrainedWidthOffset, vertical = -constrainedHeightOffset)
    val gridPlaceables = measurables[1].map { it.measure(nonOverflowConstraints) }

    val barConstraints = if (bottomInsetPx > constrainedHeightOffset) {
      looseConstraints.offset(-constrainedWidthOffset, -bottomInsetPx)
    } else {
      nonOverflowConstraints
    }

    val barsPlaceables = measurables[0].map { it.measure(barConstraints) }

    val barsHeightOffset = barsPlaceables.sumOf { it.height }
    val reactionsConstraints = barConstraints.offset(vertical = -barsHeightOffset)
    val reactionsPlaceables = measurables[2].map { it.measure(reactionsConstraints) }

    val pictureInPictureConstraints: Constraints = when (localRenderState) {
      WebRtcLocalRenderState.GONE, WebRtcLocalRenderState.SMALLER_RECTANGLE, WebRtcLocalRenderState.LARGE, WebRtcLocalRenderState.LARGE_NO_VIDEO, WebRtcLocalRenderState.FOCUSED -> constraints
      WebRtcLocalRenderState.SMALL_RECTANGLE, WebRtcLocalRenderState.EXPANDED -> {
        val hasBars = barsPlaceables.sumOf { it.width } > 0
        if (hasBars) {
          looseConstraints.offset(vertical = reactionsConstraints.maxHeight - looseConstraints.maxHeight)
        } else if (bottomInsetPx > 0) {
          if (looseConstraints.maxWidth - pipSizePx.width - pipSizePx.width - bottomSheetWidthPx < 0) {
            looseConstraints.offset(vertical = -bottomInsetPx)
          } else {
            looseConstraints
          }
        } else {
          looseConstraints
        }
      }
    }

    val pictureInPicturePlaceables = measurables[3].map { it.measure(pictureInPictureConstraints) }

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
        it.place(0, barConstraints.maxHeight - it.height)
      }

      reactionsPlaceables.forEach {
        it.place(0, 0)
      }

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
          onClick = {},
          onFocusLocalParticipantClick = {},
          onToggleCameraDirectionClick = {},
          localRenderState = localRenderState
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
      bottomSheetWidth = 640.dp,
      localRenderState = localRenderState
    )
  }
}
