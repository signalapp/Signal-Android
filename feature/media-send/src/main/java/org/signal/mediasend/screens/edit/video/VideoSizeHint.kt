/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.bytes
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.util.formatAsClock
import org.thoughtcrime.securesms.video.TranscodingConfig
import org.thoughtcrime.securesms.video.TranscodingQuality
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long the trimmed video is and how large we expect its upload to be, e.g. "0:04 • 399 KB".
 *
 * The size is what the transcoder targets for a clip of [duration] under [transcodingTiers], so it tracks both the trim
 * handles and the sent-media quality the tiers came from. Renders nothing when the device cannot transcode, since the
 * video is then uploaded as-is and a transcode target would not describe it.
 */
@Composable
internal fun VideoSizeHint(
  transcodingTiers: List<TranscodingConfig.QualityTier>,
  duration: Duration,
  modifier: Modifier = Modifier
) {
  if (!MediaConstraints.isVideoTranscodeAvailable()) {
    return
  }

  // A trim drag emits a flood of recompositions, and formatting is the only work that has to follow each one.
  val text = remember(transcodingTiers, duration) {
    val byteCountEstimate = TranscodingQuality.createFromQualityTiers(transcodingTiers, duration.inWholeMilliseconds).byteCountEstimate

    "${duration.formatAsClock()} • ${byteCountEstimate.bytes.toUnitString()}"
  }

  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
  )
}

@DayNightPreviews
@Composable
private fun VideoSizeHintPreview() {
  Previews.Preview {
    VideoSizeHint(
      transcodingTiers = emptyList(),
      duration = 64.seconds
    )
  }
}
