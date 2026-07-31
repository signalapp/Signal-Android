/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.copied.androidx.compose.material3.IconButtonColors
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.SentMediaQuality

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

      Row(
        modifier = Modifier
          .fillMaxHeight()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
      ) {
        content()
      }

      trailing()
    }
  } else {
    Column(modifier = modifier.width(48.dp)) {
      leading()

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
      ) {
        content()
      }

      trailing()
    }
  }
}

@Composable
internal fun MediaEditorToolbarButton(
  imageVector: ImageVector,
  onClick: () -> Unit,
  contentDescription: String? = null,
  colors: IconButtonColors = IconButtons.iconButtonColors()
) {
  IconButtons.IconButton(
    onClick = onClick,
    colors = colors
  ) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
  }
}

@Composable
internal fun MediaEditorToolbarSharedButtons(
  state: MediaSendState,
  canSave: Boolean,
  onEvent: (MediaEditScreenEvent) -> Unit
) {
  MediaEditorToolbarButton(
    imageVector = if (state.sentMediaQuality == SentMediaQuality.HIGH) {
      SignalIcons.QualityHigh.imageVector
    } else {
      SignalIcons.QualityHighSlash.imageVector
    },
    onClick = { onEvent(MediaEditScreenEvent.ToggleMediaQuality) }
  )

  if (canSave) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.Save.imageVector,
      onClick = { onEvent(MediaEditScreenEvent.SaveMedia) }
    )
  }

  // Adding a second attachment would silently drop view-once, so the entry point goes away while it is on.
  if (!state.isViewOnceEnabled) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.Plus.imageVector, // TODO [alex] - wrong art asset
      onClick = { onEvent(MediaEditScreenEvent.NavigateToGallery) }
    )
  }
}
