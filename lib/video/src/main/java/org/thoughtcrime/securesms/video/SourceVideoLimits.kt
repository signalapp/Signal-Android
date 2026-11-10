/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

/**
 * The resolution and bitrates of a source video, used as upper bounds so that a transcode never produces more than the source had.
 * A null value means the property could not be determined and should not be used as a bound.
 */
data class SourceVideoLimits(
  val shortEdge: Int?,
  val videoBitRate: Int?,
  val audioBitRate: Int?
) {
  companion object {
    val UNKNOWN = SourceVideoLimits(shortEdge = null, videoBitRate = null, audioBitRate = null)
  }
}
