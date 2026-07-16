/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import org.signal.mediasend.MediaSendActivityContract
import org.signal.mediasend.MediaSendNavKey
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.R
import org.signal.mediasend.edit.rememberPreviewMedia
import org.signal.mediasend.rememberPreviewState

/**
 * Screen that allows user to capture the media they will send using a camera or text story
 */
@Composable
fun MediaCaptureScreen(
  selectedCaptureScreen: MediaSendNavKey.Capture,
  state: MediaSendState,
  onEvent: (MediaCaptureScreenEvent) -> Unit,
  textStoryEditorSlot: @Composable () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(color = Color.Black)
  ) {
    when (selectedCaptureScreen) {
      is MediaSendNavKey.Capture.TextStory -> textStoryEditorSlot()
      else -> {
        MediaCameraCaptureScreen(
          state = state,
          onEvent = onEvent
        )
      }
    }

    val canDisplayBottomBar = rememberCanDisplayBottomBar(state)
    if (canDisplayBottomBar) {
      MediaCaptureBottomBar(
        canDisplayMediaBar = state.selectedMedia.isNotEmpty(),
        canDisplayToggleSwitch = state.selectedMedia.isEmpty(),
        selectedCaptureScreen = selectedCaptureScreen,
        selectedMedia = state.selectedMedia,
        onEvent = onEvent,
        modifier = Modifier
          .align(Alignment.BottomCenter)
      )
    }
  }
}

@Composable
private fun rememberCanDisplayBottomBar(state: MediaSendState): Boolean {
  return remember(state) {
    state.isCameraFirst && state.storiesEnabled && state.mode == MediaSendActivityContract.Mode.ChooseAfterMediaSelection // TODO [media-send] single story?
  }
}

@Composable
fun MediaCaptureBottomBar(
  canDisplayToggleSwitch: Boolean,
  canDisplayMediaBar: Boolean,
  selectedCaptureScreen: MediaSendNavKey.Capture,
  selectedMedia: List<Media>,
  onEvent: (MediaCaptureScreenEvent) -> Unit,
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
  selectedCaptureScreen: MediaSendNavKey.Capture,
  onEvent: (MediaCaptureScreenEvent) -> Unit,
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
      selected = selectedCaptureScreen == MediaSendNavKey.Capture.Camera,
      onClick = { onEvent(MediaCaptureScreenEvent.ShowCamera) }
    ) {
      Text(text = stringResource(R.string.MediaCaptureScreen__camera))
    }

    SegmentedBarButton(
      selected = selectedCaptureScreen == MediaSendNavKey.Capture.TextStory,
      onClick = { onEvent(MediaCaptureScreenEvent.ShowTextStory) }
    ) {
      Text(text = stringResource(R.string.MediaCaptureScreen__text))
    }
  }
}

@Composable
private fun SingleChoiceSegmentedButtonRowScope.SegmentedBarButton(
  selected: Boolean,
  onClick: () -> Unit,
  content: @Composable () -> Unit
) {
  SegmentedButton(
    selected = selected,
    onClick = onClick,
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
  onEvent: (MediaCaptureScreenEvent) -> Unit,
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
        color = SignalTheme.colors.colorOnCustom
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
  onEvent: (MediaCaptureScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val cameraDisplay = CameraDisplay.rememberCameraDisplay(isLandscape = false)

  IconButton(
    onClick = { onEvent(MediaCaptureScreenEvent.NextClicked) },
    modifier = modifier
      .padding(bottom = cameraDisplay.getNextPaddingBottom().dp, end = cameraDisplay.getNextPaddingEnd().dp)
      .size(48.dp)
      .background(colorResource(org.signal.camera.R.color.CameraHud_control_background), shape = CircleShape)
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
fun MediaCaptureScreenPreview() {
  Previews.Preview {
    MediaCaptureScreen(
      selectedCaptureScreen = MediaSendNavKey.Capture.Camera,
      state = rememberPreviewState()
        .copy(
          isCameraFirst = true,
          storiesEnabled = true,
          mode = MediaSendActivityContract.Mode.ChooseAfterMediaSelection
        ),
      onEvent = {},
      textStoryEditorSlot = {}
    )
  }
}

@NightPreview
@Composable
fun MediaCaptureScreenWithSelectedMediaPreview() {
  val selectedMedia = rememberPreviewMedia(1)

  Previews.Preview {
    MediaCaptureScreen(
      selectedCaptureScreen = MediaSendNavKey.Capture.Camera,
      state = rememberPreviewState()
        .copy(
          isCameraFirst = true,
          storiesEnabled = true,
          mode = MediaSendActivityContract.Mode.ChooseAfterMediaSelection,
          selectedMedia = selectedMedia
        ),
      onEvent = {},
      textStoryEditorSlot = {}
    )
  }
}

@NightPreview
@Composable
fun MediaCaptureToggleBarPreview() {
  var selectedCaptureScreen: MediaSendNavKey.Capture by remember { mutableStateOf(MediaSendNavKey.Capture.Camera) }

  Previews.Preview {
    MediaCaptureToggleBar(
      selectedCaptureScreen = selectedCaptureScreen,
      onEvent = {
        when (it) {
          MediaCaptureScreenEvent.ShowCamera -> selectedCaptureScreen = MediaSendNavKey.Capture.Camera
          MediaCaptureScreenEvent.ShowTextStory -> selectedCaptureScreen = MediaSendNavKey.Capture.TextStory
          else -> Unit
        }
      }
    )
  }
}

@NightPreview
@Composable
fun MediaCaptureMediaBarPreview() {
  var selectedCaptureScreen: MediaSendNavKey.Capture by remember { mutableStateOf(MediaSendNavKey.Capture.Camera) }
  val selectedMedia = rememberPreviewMedia(1)

  Previews.Preview {
    MediaCaptureMediaBar(
      selectedMedia = selectedMedia,
      onEvent = {
        when (it) {
          MediaCaptureScreenEvent.ShowCamera -> selectedCaptureScreen = MediaSendNavKey.Capture.Camera
          MediaCaptureScreenEvent.ShowTextStory -> selectedCaptureScreen = MediaSendNavKey.Capture.TextStory
          else -> Unit
        }
      }
    )
  }
}
