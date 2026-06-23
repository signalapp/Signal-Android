/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.stories

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri

/**
 * The stacked, overlapping story media thumbnails shown as the first item in the contact list in [StoriesMultiselectForwardActivity].
 *
 * The first preview sits in front with a background-colored stroke; a second, if present, is tucked behind it,
 * rotated and offset to the side.
 */
@Composable
fun StoryMediaPreviews(
  previews: List<Uri>,
  modifier: Modifier = Modifier
) {
  if (previews.isEmpty()) {
    return
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 22.5.dp),
    contentAlignment = Alignment.Center
  ) {
    if (previews.size > 1) {
      Box(
        modifier = Modifier
          .size(width = 110.dp, height = 177.dp)
          .offset(x = (-28).dp)
          .rotate(-15f)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
      ) {
        GlideImage(
          model = remember { DecryptableUri(previews[1]) },
          scaleType = GlideImageScaleType.CENTER_CROP,
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    Box(
      modifier = Modifier
        .size(width = 120.dp, height = 215.dp)
        .border(width = 3.dp, color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp))
        .padding(1.5.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      GlideImage(
        model = remember { DecryptableUri(previews[0]) },
        scaleType = GlideImageScaleType.CENTER_CROP,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun StoryMediaPreviewsPreview() {
  Previews.Preview {
    StoryMediaPreviews(
      previews = listOf(Uri.EMPTY, Uri.EMPTY)
    )
  }
}
