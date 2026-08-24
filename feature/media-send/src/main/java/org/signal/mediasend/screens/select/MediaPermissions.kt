/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import org.signal.core.ui.util.StorageUtil

/**
 * How much of the device's media store the user has let us read. Drives what the select screen can show and
 * which call to action, if any, it puts in front of the user.
 */
enum class MediaPermissions {
  /** No read access at all. There is nothing to show, so we have to ask. */
  NONE,

  /** Android 14+ selected-photos access. We only see the items the user picked out for us. */
  PARTIAL,

  /** Full read access to the media store. */
  FULL;

  companion object {
    /**
     * Reads the current level of access. Selected-photos access also satisfies
     * [StorageUtil.canReadAnyFromMediaStore], so it has to be checked first.
     */
    fun current(): MediaPermissions {
      return when {
        StorageUtil.canOnlyReadSelectedMediaStore() -> PARTIAL
        StorageUtil.canReadAnyFromMediaStore() -> FULL
        else -> NONE
      }
    }
  }
}
