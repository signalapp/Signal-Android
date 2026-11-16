/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import org.signal.camera.CameraDisplay
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.endFadingEdge
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.screens.edit.rememberPreviewMedia
import org.signal.mediasend.screens.shared.NEXT_BUTTON_CIRCLE_SIZE
import org.signal.mediasend.screens.shared.NEXT_BUTTON_HEIGHT
import org.signal.mediasend.screens.shared.NEXT_BUTTON_TOUCH_TARGET
import org.signal.mediasend.screens.shared.NextButton
import org.signal.mediasend.screens.shared.chatColorFor
import org.signal.mediasend.test.TestTags
import org.signal.camera.R as CameraR
import org.signal.core.ui.R as CoreUiR

/**
 * The text story editor slides in over a stationary camera, so it always sits on top.
 */
private const val CAMERA_Z_INDEX = 0f
private const val TEXT_STORY_Z_INDEX = 1f

/** The row the mode bar and the next button share, kept tall enough that neither moves when the other comes or goes. */
private val BOTTOM_CONTROLS_HEIGHT = NEXT_BUTTON_HEIGHT

/** Drops the mode bar onto the centerline of the next button's circle, which is what it reads as lined up with. */
private val MODE_BAR_BOTTOM_INSET = (NEXT_BUTTON_TOUCH_TARGET - MODE_BAR_HEIGHT) / 2

/** How far back from the next button the bar has finished fading, so no legible label reaches it. */
private val MODE_BAR_FADE_WIDTH = 40.dp

/** How long the bar and the button take to come and go, which the bar's fade follows them over. */
private const val CONTROL_FADE_DURATION_MS = 150

/**
 * Screen that allows user to capture the media they will send using a camera or text story
 */
@Composable
internal fun MediaCaptureScreen(
  state: MediaCaptureState,
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  textStoryEditorSlot: @Composable () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(color = Color.Black)
      .testTag(TestTags.MEDIA_CAPTURE_SCREEN)
  ) {
    Crossfade(
      targetState = state.selectedCaptureScreen
    ) { captureScreen ->
      when (captureScreen) {
        is MediaSendRoute.Capture.TextStory -> textStoryEditorSlot()
        else -> {
          MediaCameraCaptureScreen(
            state = state,
            onEvent = onEvent
          )
        }
      }
    }

    MediaCaptureBottomControls(
      state = state,
      onEvent = onEvent,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
    )
  }
}

/**
 * The mode bar and the next button, which share the bottom of the screen: the bar runs the full width with its selection
 * centered, and the button floats over its end.
 *
 * The row keeps a fixed height so neither one moving in or out shifts the other.
 *
 * The text story editor floats its own send button over that same end from underneath, so the bar is held off that
 * corner while the editor is up.
 */
@Composable
private fun MediaCaptureBottomControls(
  state: MediaCaptureState,
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val cameraDisplay = CameraDisplay.rememberCameraDisplay(isLandscape = false)
  val endMargin = cameraDisplay.getNextPaddingEnd().dp

  // The bar only has to get out of the way while the button is over it, and follows it in and out so the fade does not
  // snap on around a button that is still arriving.
  val fadeFraction by animateFloatAsState(
    targetValue = if (state.canDisplayNextButton) 1f else 0f,
    animationSpec = tween(durationMillis = CONTROL_FADE_DURATION_MS),
    label = "ModeBarFade"
  )

  // The bottom margin is applied outside the height rather than inside it, so the row is that tall in addition to
  // sitting that far up rather than squeezing the bar and the button into what is left.
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = cameraDisplay.getToggleBottomMargin().dp)
      .height(BOTTOM_CONTROLS_HEIGHT)
  ) {
    AnimatedVisibility(
      visible = state.canDisplayModeBar,
      enter = fadeIn(animationSpec = tween(durationMillis = CONTROL_FADE_DURATION_MS)),
      exit = fadeOut(animationSpec = tween(durationMillis = CONTROL_FADE_DURATION_MS)),
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(bottom = MODE_BAR_BOTTOM_INSET)
    ) {
      MediaCaptureModeBar(
        availableCaptureModes = state.availableCaptureModes,
        selectedCaptureMode = state.selectedCaptureMode,
        onEvent = onEvent,
        endReservation = if (state.selectedCaptureScreen is MediaSendRoute.Capture.TextStory) endMargin + NEXT_BUTTON_TOUCH_TARGET else 0.dp,
        modifier = Modifier.endFadingEdge(
          fadeWidth = MODE_BAR_FADE_WIDTH * fadeFraction,
          inset = (endMargin + NEXT_BUTTON_CIRCLE_SIZE) * fadeFraction
        )
      )
    }

    AnimatedVisibility(
      visible = state.canDisplayNextButton,
      enter = fadeIn(animationSpec = tween(durationMillis = CONTROL_FADE_DURATION_MS)),
      exit = fadeOut(animationSpec = tween(durationMillis = CONTROL_FADE_DURATION_MS)),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = endMargin)
    ) {
      NextButton(
        selectedMediaCount = state.selectedMedia.size,
        onClick = { onEvent(MediaCaptureScreenEvents.NextClicked) },
        recipientChatColor = chatColorFor(state.recipientId),
        containerColor = colorResource(CameraR.color.CameraHud_control_background),
        contentColor = colorResource(CoreUiR.color.signal_dark_colorOnCustom)
      )
    }
  }
}

@NightPreview
@Composable
private fun MediaCaptureScreenPreview() {
  Previews.Preview {
    MediaCaptureScreen(
      state = rememberPreviewCaptureState(),
      onEvent = {},
      textStoryEditorSlot = {}
    )
  }
}

/**
 * A flow already carrying a capture: the text story is withdrawn from the bar, the next button floats over its end, and
 * the bar has faded out behind the button rather than running under it.
 */
@NightPreview
@Composable
private fun MediaCaptureScreenWithSelectedMediaPreview() {
  val selectedMedia = rememberPreviewMedia(1)

  Previews.Preview {
    MediaCaptureScreen(
      state = rememberPreviewCaptureState().copy(selectedMedia = selectedMedia),
      onEvent = {},
      textStoryEditorSlot = {}
    )
  }
}

/** A count wide enough to push the badge past the circle it straddles, which widens the button. */
@NightPreview
@Composable
private fun MediaCaptureScreenWithManySelectedMediaPreview() {
  val selectedMedia = rememberPreviewMedia(12)

  Previews.Preview {
    MediaCaptureScreen(
      state = rememberPreviewCaptureState().copy(selectedMedia = selectedMedia),
      onEvent = {},
      textStoryEditorSlot = {}
    )
  }
}

@Composable
internal fun rememberPreviewCaptureState(): MediaCaptureState = remember {
  MediaCaptureState(
    isCameraFirst = true,
    storiesEnabled = true,
    mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection
  )
}
