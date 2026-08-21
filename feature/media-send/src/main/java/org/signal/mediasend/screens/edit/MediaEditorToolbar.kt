/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.copied.androidx.compose.material3.IconButtonColors
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.mediasend.EditorState
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.test.TestTags

@Composable
internal fun MediaEditorToolbar(
  modifier: Modifier = Modifier,
  leading: @Composable () -> Unit = {},
  trailing: @Composable () -> Unit = {},
  content: @Composable () -> Unit
) {
  val windowBreakpoint = rememberWindowBreakpoint()
  val isRow = windowBreakpoint is WindowBreakpoint.Small

  if (isRow) {
    Row(modifier = modifier.height(48.dp)) {
      leading()

      Spacer(modifier = Modifier.weight(1f))

      Row(
        modifier = Modifier
          .fillMaxHeight()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
      ) {
        content()
      }

      Spacer(modifier = Modifier.weight(1f))

      trailing()
    }
  } else {
    Column(modifier = modifier.width(48.dp)) {
      trailing()

      Spacer(modifier = Modifier.size(16.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
      ) {
        content()
      }

      Spacer(modifier = Modifier.size(16.dp))

      leading()
    }
  }
}

@Composable
internal fun MediaEditorToolbarButton(
  imageVector: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  contentDescription: String? = null,
  colors: IconButtonColors = IconButtons.iconButtonColors()
) {
  IconButtons.IconButton(
    onClick = onClick,
    enabled = enabled,
    colors = colors,
    modifier = modifier
  ) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
  }
}

@Composable
internal fun MediaEditorToolbarSharedButtons(
  state: MediaEditState,
  editorState: EditorState,
  onEvent: (MediaEditScreenEvents) -> Unit,
  enabled: Boolean = true
) {
  if (isQualityVisible(state, editorState)) {
    var isSelectingQuality by rememberSaveable { mutableStateOf(false) }

    if (isSelectingQuality) {
      QualitySelectorBottomSheet(
        quality = state.sentMediaQuality,
        onQualitySelected = { onEvent(MediaEditScreenEvents.SetMediaQuality(it)) },
        onDismiss = { isSelectingQuality = false }
      )
    }

    MediaEditorToolbarButton(
      imageVector = if (state.sentMediaQuality == SentMediaQuality.HIGH) {
        SignalIcons.QualityHigh.imageVector
      } else {
        SignalIcons.QualityHighSlash.imageVector
      },
      onClick = { isSelectingQuality = true },
      enabled = enabled,
      modifier = Modifier.testTag(TestTags.MEDIA_EDITOR_TOOLBAR_QUALITY_BUTTON)
    )
  }

  if (editorState is EditorState.VideoTrim && isMuteVisible(state, editorState)) {
    MediaEditorToolbarButton(
      imageVector = if (editorState.videoTrimData.isMuted) SignalIcons.SpeakerSlash.imageVector else SignalIcons.Speaker.imageVector,
      onClick = { onEvent(MediaEditScreenEvents.ToggleVideoMuted) },
      enabled = enabled,
      modifier = Modifier.testTag(TestTags.MEDIA_EDITOR_TOOLBAR_MUTE_BUTTON)
    )
  }

  if (isSaveVisible(editorState)) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.Save.imageVector,
      onClick = { onEvent(MediaEditScreenEvents.SaveMedia) },
      enabled = enabled,
      modifier = Modifier.testTag(TestTags.MEDIA_EDITOR_TOOLBAR_SAVE_BUTTON)
    )
  }

  if (isAddMediaVisible(state, editorState)) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.AlbumPlus.imageVector,
      onClick = { onEvent(MediaEditScreenEvents.NavigateToGallery) },
      enabled = enabled,
      modifier = Modifier.testTag(TestTags.MEDIA_EDITOR_TOOLBAR_ADD_MEDIA_BUTTON)
    )
  }
}

private fun isQualityVisible(state: MediaEditState, editorState: EditorState): Boolean {
  return !state.isStory && editorState !is EditorState.Document
}

private fun isSaveVisible(editorState: EditorState): Boolean {
  return editorState is EditorState.Image || editorState is EditorState.Gif
}

private fun isMuteVisible(state: MediaEditState, editorState: EditorState): Boolean {
  return state.isMuteVideoAudioEnabled && editorState is EditorState.VideoTrim
}

/**
 * Adding a second attachment would silently drop view-once, so the entry point -- and the selection rail it belongs to
 * -- goes away while it is on.
 */
internal fun isAddMediaVisible(state: MediaEditState, editorState: EditorState?): Boolean {
  return !state.isViewOnceEnabled && editorState !is EditorState.Document
}

/**
 * Whether [MediaEditorToolbarSharedButtons] would render anything, so callers with no buttons of their own can skip the
 * toolbar rather than leave an empty one behind.
 */
internal fun hasSharedToolbarButtons(state: MediaEditState, editorState: EditorState): Boolean {
  return isQualityVisible(state, editorState) || isMuteVisible(state, editorState) || isSaveVisible(editorState) || isAddMediaVisible(state, editorState)
}
