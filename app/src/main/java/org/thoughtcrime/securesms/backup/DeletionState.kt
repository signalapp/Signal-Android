/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import org.signal.core.util.LongSerializer

/**
 * Denotes the deletion state for backups.
 */
enum class DeletionState(private val id: Int) {
  /**
   * Something bad happened, and the deletion could not be performed.
   * User should see "backup failed" UX
   */
  FAILED(-1),

  /**
   * No pending, running, failed, or completed deletion.
   * User should not see UX specific to backup deletions.
   */
  NONE(0),

  /**
   * Clear local backup state and delete subscription.
   * User should see a progress spinner.
   */
  CLEAR_LOCAL_STATE(4),

  /**
   * Waiting to download media before deletion.
   * User should see the "restoring media" progress UX
   */
  AWAITING_MEDIA_DOWNLOAD(1),

  /**
   * Media has downloaded so the deletion job can pick up from where it left off.
   */
  MEDIA_DOWNLOAD_FINISHED(5),

  /**
   * Deleting the backups themselves.
   * User should see the "deleting backups..." UX
   */
  DELETE_BACKUPS(2),

  /**
   * Completed deletion.
   * User should see the "backups deleted" UX
   */
  COMPLETE(3);

  fun isInProgress(): Boolean {
    return this != FAILED && this != NONE && this != COMPLETE
  }

  fun isIdle(): Boolean = !isInProgress()

  companion object {
    val serializer: LongSerializer<DeletionState> = Serializer()
  }

  class Serializer : LongSerializer<DeletionState> {
    override fun serialize(data: DeletionState): Long {
      return data.id.toLong()
    }

    override fun deserialize(input: Long): DeletionState {
      return when (input.toInt()) {
        FAILED.id -> FAILED
        CLEAR_LOCAL_STATE.id -> CLEAR_LOCAL_STATE
        AWAITING_MEDIA_DOWNLOAD.id -> AWAITING_MEDIA_DOWNLOAD
        MEDIA_DOWNLOAD_FINISHED.id -> MEDIA_DOWNLOAD_FINISHED
        DELETE_BACKUPS.id -> DELETE_BACKUPS
        COMPLETE.id -> COMPLETE
        else -> NONE
      }
    }
  }
}
