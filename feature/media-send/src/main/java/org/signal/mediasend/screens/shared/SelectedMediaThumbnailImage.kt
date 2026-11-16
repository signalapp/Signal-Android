/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.Previews
import org.signal.glide.compose.GlideImage
import org.signal.mediasend.screens.MediaSendMetrics

/**
 * The image for one tile of a selected media rail. Shows [thumbnailOverride] when there is one, and otherwise loads the
 * media itself.
 */
@Composable
internal fun SelectedMediaThumbnailImage(
  media: Media,
  thumbnailOverride: ImageBitmap?,
  modifier: Modifier = Modifier
) {
  if (thumbnailOverride != null) {
    Image(
      bitmap = thumbnailOverride,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier
    )
  } else if (LocalInspectionMode.current) {
    Box(modifier = modifier.background(color = Previews.rememberRandomColor(seed = media.uri)))
  } else {
    GlideImage(
      model = media.uri,
      imageSize = MediaSendMetrics.SelectedMediaPreviewSize,
      modifier = modifier
    )
  }
}
