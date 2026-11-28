/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter

class TranscodingQualityTest {

  private val target = TranscodingQuality.createManuallyForTesting(
    codec = MediaConverter.VIDEO_CODEC_H264,
    outputShortEdge = 720,
    videoBitrate = 2_000_000,
    audioBitrate = 128_000,
    durationMs = 10_000
  )

  @Test
  fun `source below the target lowers resolution and bitrates to the source`() {
    val capped = target.cappedTo(SourceVideoLimits(shortEdge = 360, videoBitRate = 300_000, audioBitRate = 48_000))

    assertEquals(360, capped.outputResolution)
    assertEquals(300_000, capped.targetVideoBitRate)
    assertEquals(48_000, capped.targetAudioBitRate)
    assertEquals(435_000, capped.byteCountEstimate)
  }

  @Test
  fun `source above the target keeps the target`() {
    val capped = target.cappedTo(SourceVideoLimits(shortEdge = 2160, videoBitRate = 40_000_000, audioBitRate = 320_000))

    assertEquals(720, capped.outputResolution)
    assertEquals(2_000_000, capped.targetVideoBitRate)
    assertEquals(128_000, capped.targetAudioBitRate)
  }

  @Test
  fun `unknown source properties keep the target`() {
    val capped = target.cappedTo(SourceVideoLimits.UNKNOWN)

    assertEquals(720, capped.outputResolution)
    assertEquals(2_000_000, capped.targetVideoBitRate)
    assertEquals(128_000, capped.targetAudioBitRate)
  }

  @Test
  fun `extremely small source values are held at the floor`() {
    val capped = target.cappedTo(SourceVideoLimits(shortEdge = 96, videoBitRate = 10_000, audioBitRate = 8_000))

    assertEquals(240, capped.outputResolution)
    assertEquals(64_000, capped.targetVideoBitRate)
    assertEquals(32_000, capped.targetAudioBitRate)
  }

  @Test
  fun `source without an audio track targets no audio bitrate`() {
    val capped = target.cappedTo(SourceVideoLimits(shortEdge = 360, videoBitRate = 300_000, audioBitRate = 0))

    assertEquals(0, capped.targetAudioBitRate)
    assertEquals(300_000, capped.targetTotalBitRate)
  }
}
