/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.ByteSize

class RestoreV2Event(val type: Type, val count: ByteSize, val estimatedTotalCount: ByteSize) {
  enum class Type {
    PROGRESS_DOWNLOAD,
    PROGRESS_RESTORE,
    PROGRESS_FINALIZING
  }

  fun getProgress(): Float {
    if (estimatedTotalCount.inWholeBytes == 0L) {
      return 0f
    }
    return count.inWholeBytes.toFloat() / estimatedTotalCount.inWholeBytes.toFloat()
  }
}
