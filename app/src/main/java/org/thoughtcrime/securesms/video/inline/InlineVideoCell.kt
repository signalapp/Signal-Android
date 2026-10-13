/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import androidx.media3.common.MediaItem

/**
 * A list item (view holder or item view) that can play inline video. Shows its still image until
 * [InlineVideoController] says otherwise, and should reset to the still when rebound.
 */
interface InlineVideoCell {
  val surfaceHost: InlineVideoHost?
  val mediaItem: MediaItem?

  /** Null loops forever. */
  val loopPolicy: InlineVideoLoopPolicy?
    get() = null

  /** True when the user tapped play, which restarts a video that hit its loop limit. */
  val isPlaybackRequested: Boolean
    get() = false

  fun canPlayContent(): Boolean
  fun showStill()
  fun hideStill()
}
