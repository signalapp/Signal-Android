/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerScreenTestTags
import org.signal.mediaviewer.playback.MediaViewerPlaybackController

/**
 * A single pager page: dispatches to the right content for the item's type, or a placeholder
 * while the item is still loading.
 */
@Composable
internal fun MediaViewerPage(
  item: MediaViewerItem?,
  isActive: Boolean,
  isReturningToSharedElement: Boolean,
  playbackController: MediaViewerPlaybackController,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    item == null -> {
      PlaceholderPage(modifier = modifier)
    }
    item.isVideo -> {
      MediaViewerVideoPage(
        item = item,
        isActive = isActive,
        isReturningToSharedElement = isReturningToSharedElement,
        playbackController = playbackController,
        onEvent = onEvent,
        modifier = modifier
      )
    }
    else -> {
      MediaViewerImagePage(
        item = item,
        isActive = isActive,
        onEvent = onEvent,
        modifier = modifier
          .fillMaxSize()
          .testTag(MediaViewerScreenTestTags.PAGE_IMAGE)
      )
    }
  }
}

@Composable
private fun PlaceholderPage(modifier: Modifier = Modifier) {
  var showSpinner by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(SPINNER_DELAY_MS)
    showSpinner = true
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag(MediaViewerScreenTestTags.PAGE_PLACEHOLDER),
    contentAlignment = Alignment.Center
  ) {
    if (showSpinner) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
  }
}

/** How long a placeholder stays blank before showing a spinner, so fast loads never flash one. */
private const val SPINNER_DELAY_MS = 300L
