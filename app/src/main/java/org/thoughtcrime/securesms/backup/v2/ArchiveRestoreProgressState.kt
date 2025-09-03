/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.backup.RestoreState

/**
 * In-memory view of the current state of an attachment restore process.
 */
data class ArchiveRestoreProgressState(
  val restoreState: RestoreState,
  val remainingRestoreSize: ByteSize,
  val totalRestoreSize: ByteSize,
  val hasActivelyRestoredThisRun: Boolean = false,
  val totalToRestoreThisRun: ByteSize = 0.bytes,
  val restoreStatus: RestoreStatus
) {
  val completedRestoredSize = totalRestoreSize - remainingRestoreSize

  val progress: Float? = when (this.restoreState) {
    RestoreState.CALCULATING_MEDIA,
    RestoreState.CANCELING_MEDIA -> this.completedRestoredSize.percentageOf(this.totalRestoreSize)

    RestoreState.RESTORING_MEDIA -> {
      when (this.restoreStatus) {
        RestoreStatus.NONE -> null
        RestoreStatus.FINISHED -> 1f
        else -> this.completedRestoredSize.percentageOf(this.totalRestoreSize)
      }
    }

    RestoreState.NONE -> {
      if (this.restoreStatus == RestoreStatus.FINISHED) {
        1f
      } else {
        null
      }
    }

    else -> null
  }

  fun activelyRestoring(): Boolean {
    return restoreState.inProgress
  }

  fun needRestoreMediaService(): Boolean {
    return (restoreState == RestoreState.CALCULATING_MEDIA || restoreState == RestoreState.RESTORING_MEDIA) &&
      totalRestoreSize > 0.bytes &&
      remainingRestoreSize != 0.bytes
  }

  /**
   * Describes the status of an in-progress media download session.
   */
  enum class RestoreStatus {
    NONE,
    RESTORING,
    LOW_BATTERY,
    WAITING_FOR_INTERNET,
    WAITING_FOR_WIFI,
    NOT_ENOUGH_DISK_SPACE,
    FINISHED
  }
}
