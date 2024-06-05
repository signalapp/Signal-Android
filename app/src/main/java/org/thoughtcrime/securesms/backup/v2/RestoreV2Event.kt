/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

class RestoreV2Event(val type: Type, val count: Long, val estimatedTotalCount: Long) {
  enum class Type {
    PROGRESS_DOWNLOAD,
    PROGRESS_RESTORE,
    PROGRESS_MEDIA_RESTORE,
    FINISHED
  }

  fun getProgress(): Float {
    if (estimatedTotalCount == 0L) {
      return 0f
    }
    return count.toFloat() / estimatedTotalCount.toFloat()
  }
}
