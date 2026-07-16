/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.thoughtcrime.securesms.video.TranscodingConfig
import kotlin.time.Duration.Companion.seconds

object PreviewMediaConstraints : MediaConstraints() {
  override fun getImageMaxWidth(): Int = 0

  override fun getImageMaxHeight(): Int = 0

  override fun getImageMaxSize(): Int = 0

  override fun getVideoTranscodingSettings(): List<TranscodingConfig.QualityTier?>? = null

  override fun getImageDimensionTargets(): IntArray? = null

  override fun getGifMaxSize(): Long = 0L

  override fun getVideoMaxSize(): Long = 0L

  override fun getAudioMaxSize(): Long = 0L

  override fun getDocumentMaxSize(): Long = 0L

  override fun getMaxAttachmentSize(): Long = 0L
}

@Composable
internal fun rememberPreviewState() = remember {
  MediaSendState(
    mediaConstraints = PreviewMediaConstraints,
    storyMaxVideoDuration = 30.seconds,
    storiesEnabled = true
  )
}
