/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.groups.v2.processing

import org.signal.storageservice.protos.groups.local.DecryptedGroup

/**
 * Result of updating a local group state via P2P group change or a server pull.
 */
data class GroupUpdateResult(val updateStatus: UpdateStatus, val latestServer: DecryptedGroup?) {

  companion object {
    val CONSISTENT_OR_AHEAD = GroupUpdateResult(UpdateStatus.GROUP_CONSISTENT_OR_AHEAD, null)

    fun updated(updatedGroupState: DecryptedGroup): GroupUpdateResult {
      return GroupUpdateResult(UpdateStatus.GROUP_UPDATED, updatedGroupState)
    }
  }

  enum class UpdateStatus {
    /** The local group was successfully updated to be consistent with the message revision */
    GROUP_UPDATED,

    /** The local group is already consistent with the message revision or is ahead of the message revision */
    GROUP_CONSISTENT_OR_AHEAD
  }
}
