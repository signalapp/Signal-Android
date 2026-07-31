/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.signal.mediasend.screens.edit.image.BrushWidths
import org.thoughtcrime.securesms.video.TranscodingConfig
import org.thoughtcrime.securesms.video.interfaces.MediaInput
import org.thoughtcrime.securesms.video.interfaces.MediaInputFactory
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

/**
 * Stands in for the real factory in previews, which have no video to decode. Consumers skip decoding under inspection,
 * so nothing should ever ask this for an input.
 */
object PreviewMediaInputFactory : MediaInputFactory {
  override fun createForUri(context: Context, uri: Uri): MediaInput = throw UnsupportedOperationException()
}

@Composable
internal fun rememberPreviewState() = remember {
  MediaSendFlowState(
    mediaConstraints = PreviewMediaConstraints,
    sentMediaQuality = SentMediaQuality.STANDARD,
    storyMaxVideoDuration = 30.seconds,
    videoTranscodingTiers = emptyList(),
    storiesEnabled = true,
    brushWidths = BrushWidths(0f, 0f, 0f)
  )
}
