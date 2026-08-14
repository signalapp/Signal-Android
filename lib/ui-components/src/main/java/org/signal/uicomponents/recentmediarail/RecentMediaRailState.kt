/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.recentmediarail

import android.net.Uri

/**
 * State of a [RecentMediaRail]. Owned by a [RecentMediaRailPresenter] and expected to be mirrored into the state of
 * whatever screen the rail sits in.
 */
data class RecentMediaRailState(
  val media: List<RecentMedia> = emptyList(),
  val loaded: Boolean = false
) {

  /**
   * Whether the rail belongs in the layout at all. It stays put while [loaded] is false so that media arriving later
   * fills space that was already there instead of shoving the rest of the screen down, and is only dropped once we know
   * there's nothing to show.
   */
  val visible: Boolean = !loaded || media.isNotEmpty()
}

/** A single thumbnail in a [RecentMediaRail]. */
data class RecentMedia(
  val thumbnailUri: Uri?,
  val availability: Availability,
  /** Which frame of a video the thumbnail should show, for media that was trimmed before it was sent. */
  val thumbnailTimeUs: Long = 0
) {

  /** Whether tapping an item can show it, and if not, what to do instead. */
  enum class Availability {
    /** On the device and ready to view. */
    AVAILABLE,

    /** Offloaded, and needs to be downloaded before it can be viewed. */
    RESTORABLE,

    /** Not here, and not something we can go get. */
    UNAVAILABLE
  }
}
