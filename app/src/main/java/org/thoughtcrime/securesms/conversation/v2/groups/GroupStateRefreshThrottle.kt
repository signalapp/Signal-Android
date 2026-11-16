/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.groups

import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.signal.storageservice.storage.protos.groups.AccessControl
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * In-memory per-group throttle for the group state refresh done when opening a conversation.
 */
object GroupStateRefreshThrottle {

  private val TAG = Log.tag(GroupStateRefreshThrottle::class.java)

  private val DEFAULT_THROTTLE = 5.minutes
  private val ADMIN_APPROVAL_THROTTLE = 30.seconds

  private val lastRefreshTimes = ConcurrentHashMap<GroupId.V2, Long>()

  /**
   * Returns true and records [now] as the last refresh time if the group has not been refreshed recently. Admins of groups
   * that require approval to join use a shorter throttle so join requests show up sooner.
   */
  fun shouldRefresh(groupRecord: GroupRecord, now: Long = System.currentTimeMillis()): Boolean {
    val throttleDuration = if (isAdminOfGroupRequiringApproval(groupRecord)) ADMIN_APPROVAL_THROTTLE else DEFAULT_THROTTLE
    val shouldRefresh = shouldRefresh(groupRecord.id.requireV2(), throttleDuration, now)
    if (!shouldRefresh) {
      Log.d(TAG, "Skipping group state refresh, refreshed recently")
    }
    return shouldRefresh
  }

  @VisibleForTesting
  fun shouldRefresh(groupId: GroupId.V2, throttleDuration: Duration, now: Long = System.currentTimeMillis()): Boolean {
    var shouldRefresh = false
    lastRefreshTimes.compute(groupId) { _, lastRefreshTime ->
      if (lastRefreshTime == null || now < lastRefreshTime || now - lastRefreshTime >= throttleDuration.inWholeMilliseconds) {
        shouldRefresh = true
        now
      } else {
        lastRefreshTime
      }
    }
    return shouldRefresh
  }

  private fun isAdminOfGroupRequiringApproval(groupRecord: GroupRecord): Boolean {
    return groupRecord.isAdmin(Recipient.self()) &&
      groupRecord.requireV2GroupProperties().decryptedGroup.accessControl?.addFromInviteLink == AccessControl.AccessRequired.ADMINISTRATOR
  }

  @JvmStatic
  fun clear(groupId: GroupId.V2) {
    lastRefreshTimes.remove(groupId)
  }

  @VisibleForTesting
  fun clear() {
    lastRefreshTimes.clear()
  }
}
