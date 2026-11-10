/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceVideoRepositoryTest {

  @Test
  fun `declared video bitrate is used when present`() {
    val limits = SourceVideoRepository.createLimits(width = 640, height = 360, videoTrackBitRate = 250_000, audioTrackBitRate = 64_000, hasAudioTrack = true, totalBitRate = 400_000)

    assertEquals(SourceVideoLimits(shortEdge = 360, videoBitRate = 250_000, audioBitRate = 64_000), limits)
  }

  @Test
  fun `missing video bitrate is estimated from the total minus audio`() {
    val limits = SourceVideoRepository.createLimits(width = 360, height = 640, videoTrackBitRate = null, audioTrackBitRate = 64_000, hasAudioTrack = true, totalBitRate = 400_000)

    assertEquals(SourceVideoLimits(shortEdge = 360, videoBitRate = 336_000, audioBitRate = 64_000), limits)
  }

  @Test
  fun `missing audio bitrate is left unknown when an audio track exists`() {
    val limits = SourceVideoRepository.createLimits(width = 640, height = 360, videoTrackBitRate = null, audioTrackBitRate = null, hasAudioTrack = true, totalBitRate = 400_000)

    assertEquals(400_000, limits.videoBitRate)
    assertNull(limits.audioBitRate)
  }

  @Test
  fun `missing dimensions leave the short edge unknown`() {
    val limits = SourceVideoRepository.createLimits(width = null, height = 360, videoTrackBitRate = null, audioTrackBitRate = null, hasAudioTrack = false, totalBitRate = 400_000)

    assertNull(limits.shortEdge)
    assertEquals(0, limits.audioBitRate)
  }
}
