/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.runtime.Immutable

/**
 * On-device storage consumed by the app, bucketed into the categories shown on the manage storage screen.
 * All sizes are in bytes.
 */
@Immutable
data class StorageUsage(
  val photos: Long = 0,
  val videos: Long = 0,
  val files: Long = 0,
  val audio: Long = 0,
  val messages: Long = 0,
  val stickers: Long = 0,
  val other: Long = 0
) {
  val total: Long
    get() = photos + videos + files + audio + messages + stickers + other
}
