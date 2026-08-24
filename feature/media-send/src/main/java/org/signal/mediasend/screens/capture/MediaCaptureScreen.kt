/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.camera.CameraDisplay
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.PreviewMediaConstraints
import org.signal.mediasend.R
import org.signal.mediasend.screens.edit.rememberPreviewMedia
import org.signal.mediasend.test.TestTags

/**
 * The text story editor slides in over a stationary camera, so it always sits on top.
 */
private const val CAMERA_Z_INDEX = 0f
private const val TEXT_STORY_Z_INDEX = 1f

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

    if (state.canDisplayBottomBar) {
      MediaCaptureBottomBar(
        canDisplayMediaBar = state.canDisplayMediaBar,
        canDisplayToggleSwitch = state.canDisplayToggleSwitch,
        selectedCaptureScreen = state.selectedCaptureScreen,
        selectedMedia = state.selectedMedia,
        onEvent = onEvent,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
      )
    }
  }
}

@Composable
fun MediaCaptureBottomBar(
  canDisplayToggleSwitch: Boolean,
  canDisplayMediaBar: Boolean,
  selectedCaptureScreen: MediaSendRoute.Capture,
  selectedMedia: List<Media>,
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  if (canDisplayToggleSwitch) {
    MediaCaptureToggleBar(
      selectedCaptureScreen = selectedCaptureScreen,
      onEvent = onEvent,
      modifier = modifier
    )
  } else if (canDisplayMediaBar && selectedMedia.isNotEmpty()) {
    MediaCaptureMediaBar(
      selectedMedia = selectedMedia,
      onEvent = onEvent,
      modifier = modifier
    )
  }
}

@Composable
private fun MediaCaptureToggleBar(
  selectedCaptureScreen: MediaSendRoute.Capture,
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val cameraDisplay = CameraDisplay.rememberCameraDisplay(isLandscape = false)

  SingleChoiceSegmentedButtonRow(
    modifier = modifier
      .padding(bottom = cameraDisplay.getToggleBottomMargin().dp)
      .height(44.dp)
      .background(color = colorResource(R.color.MediaSend_controls_color), shape = RoundedCornerShape(50))
      .padding(horizontal = 6.dp, vertical = 6.dp)
  ) {
    SegmentedBarButton(
      selected = selectedCaptureScreen == MediaSendRoute.Capture.Camera,
      onClick = { onEvent(MediaCaptureScreenEvents.ShowCamera) },
      modifier = Modifier.testTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE)
    ) {
      Text(text = stringResource(R.string.MediaCaptureScreen__camera))
    }

    SegmentedBarButton(
      selected = selectedCaptureScreen == MediaSendRoute.Capture.TextStory,
      onClick = { onEvent(MediaCaptureScreenEvents.ShowTextStory) },
      modifier = Modifier.testTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE)
    ) {
      Text(text = stringResource(R.string.MediaCaptureScreen__text))
    }
  }
}

@Composable
private fun SingleChoiceSegmentedButtonRowScope.SegmentedBarButton(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  SegmentedButton(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    shape = RoundedCornerShape(percent = 50),
    icon = {},
    border = BorderStroke(0.dp, Color.Transparent),
    colors = SegmentedButtonDefaults.colors(
      activeContainerColor = SignalTheme.colors.colorTransparent3
    ),
    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    label = content
  )
}

@Composable
private fun MediaCaptureMediaBar(
  selectedMedia: List<Media>,
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val cameraDisplay = CameraDisplay.rememberCameraDisplay(isLandscape = false)

  Box(modifier = modifier.fillMaxWidth()) {
    Row(
      horizontalArrangement = spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .align(Alignment.Center)
        .padding(bottom = cameraDisplay.getToggleBottomMargin().dp)
        .height(44.dp)
        .background(color = colorResource(R.color.MediaSend_controls_color), shape = RoundedCornerShape(50))
        .padding(horizontal = 6.dp, vertical = 6.dp)
        .padding(end = 10.dp)
    ) {
      if (LocalInspectionMode.current) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .background(color = Color.Red, shape = CircleShape)
        )
      } else {
        GlideImage(
          model = DecryptableUri(selectedMedia.last().uri),
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
        )
      }

      Text(
        text = pluralStringResource(R.plurals.MediaCaptureScreen_n_items, selectedMedia.size, selectedMedia.size),
        color = SignalTheme.colors.colorOnCustom,
        modifier = Modifier.testTag(TestTags.MEDIA_CAPTURE_MEDIA_COUNT)
      )
    }

    NextButton(
      onEvent = onEvent,
      modifier = Modifier.align(Alignment.BottomEnd)
    )
  }
}

@Composable
private fun NextButton(
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val cameraDisplay = CameraDisplay.rememberCameraDisplay(isLandscape = false)

  IconButton(
    onClick = { onEvent(MediaCaptureScreenEvents.NextClicked) },
    modifier = modifier
      .padding(bottom = cameraDisplay.getNextPaddingBottom().dp, end = cameraDisplay.getNextPaddingEnd().dp)
      .size(48.dp)
      .background(colorResource(org.signal.camera.R.color.CameraHud_control_background), shape = CircleShape)
      .testTag(TestTags.MEDIA_CAPTURE_NEXT_BUTTON)
  ) {
    Icon(
      imageVector = SignalIcons.ArrowEnd.imageVector,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(24.dp)
    )
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

@Composable
private fun rememberPreviewCaptureState(): MediaCaptureState = remember {
  MediaCaptureState(
    isCameraFirst = true,
    storiesEnabled = true,
    mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
    mediaConstraints = PreviewMediaConstraints
  )
}

@NightPreview
@Composable
fun MediaCaptureToggleBarPreview() {
  var selectedCaptureScreen: MediaSendRoute.Capture by remember { mutableStateOf(MediaSendRoute.Capture.Camera) }

  Previews.Preview {
    MediaCaptureToggleBar(
      selectedCaptureScreen = selectedCaptureScreen,
      onEvent = {
        when (it) {
          MediaCaptureScreenEvents.ShowCamera -> selectedCaptureScreen = MediaSendRoute.Capture.Camera
          MediaCaptureScreenEvents.ShowTextStory -> selectedCaptureScreen = MediaSendRoute.Capture.TextStory
          else -> Unit
        }
      }
    )
  }
}

@NightPreview
@Composable
fun MediaCaptureMediaBarPreview() {
  var selectedCaptureScreen: MediaSendRoute.Capture by remember { mutableStateOf(MediaSendRoute.Capture.Camera) }
  val selectedMedia = rememberPreviewMedia(1)

  Previews.Preview {
    MediaCaptureMediaBar(
      selectedMedia = selectedMedia,
      onEvent = {
        when (it) {
          MediaCaptureScreenEvents.ShowCamera -> selectedCaptureScreen = MediaSendRoute.Capture.Camera
          MediaCaptureScreenEvents.ShowTextStory -> selectedCaptureScreen = MediaSendRoute.Capture.TextStory
          else -> Unit
        }
      }
    )
  }
}
