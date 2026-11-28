/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.uicomponents.zooming.ZoomingImageView

/**
 * A zoomable image page, backed by [ZoomingImageView] for region decoding of huge images, manual
 * EXIF orientation, UltraHDR gainmap detection, and animated GIFs.
 */
@Composable
internal fun MediaViewerImagePage(
  item: MediaViewerItem,
  isActive: Boolean,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  if (LocalInspectionMode.current) {
    Box(modifier = modifier.background(Color.DarkGray))
    return
  }

  val reportMediaReady by rememberUpdatedState(isActive)
  AndroidView(
    factory = { context -> ZoomingImageView(context) },
    update = { view ->
      view.setOnClickListener { onEvent(MediaViewerEvent.MediaTapped) }
      view.setOnGainmapDetectedListener { onEvent(MediaViewerEvent.HdrDetected(item.uri)) }
      view.setOnZoomStateChangedListener { onEvent(MediaViewerEvent.ImageZoomChanged(item.attachmentId, it)) }

      if (view.tag != item.uri) {
        view.tag = item.uri
        view.setImageUri(Glide.with(view), item.uri, item.contentType.orEmpty()) {
          if (reportMediaReady) {
            onEvent(MediaViewerEvent.MediaReady)
          }
        }
      }
    },
    onRelease = { it.cleanup() },
    modifier = modifier
  )
}
