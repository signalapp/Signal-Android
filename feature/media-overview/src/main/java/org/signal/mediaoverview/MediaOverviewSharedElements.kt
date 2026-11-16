/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import android.widget.ImageView

/**
 * Tracks the thumbnail views currently showing in the grid, so the host can run the activity shared
 * element transition from the one the user tapped.
 */
class MediaOverviewSharedElements {
  private val views = mutableMapOf<MediaOverviewItem.Key, ImageView>()

  fun get(key: MediaOverviewItem.Key): ImageView? = views[key]

  internal fun register(key: MediaOverviewItem.Key, view: ImageView) {
    views[key] = view
  }

  internal fun unregister(key: MediaOverviewItem.Key, view: ImageView) {
    if (views[key] === view) {
      views.remove(key)
    }
  }
}
