/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups

import android.content.Context
import androidx.annotation.Discouraged
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logV
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Centralizes operations for retrieving groups that a given recipient has in common with another user.
 */
object GroupsInCommonRepository {

  @WorkerThread
  @JvmStatic
  @Discouraged("Use getGroupsInCommonCount instead")
  fun getGroupsInCommonCountSync(recipientId: RecipientId): Int = runBlocking {
    getGroupsInCommonCount(recipientId)
  }

  suspend fun getGroupsInCommonCount(recipientId: RecipientId): Int = withContext(Dispatchers.IO) {
    SignalDatabase.groups
      .getPushGroupsContainingMember(recipientId)
      .count { it.members.contains(Recipient.self().id) }
  }

  fun getGroupsInCommonSummary(context: Context, recipientId: RecipientId): Flow<GroupsInCommonSummary> {
    return getGroupsInCommon(context, recipientId)
      .map(::GroupsInCommonSummary)
  }

  fun getGroupsInCommon(context: Context, recipientId: RecipientId): Flow<List<Recipient>> {
    return Recipient.observable(recipientId)
      .asFlow()
      .map { recipient ->
        if (recipient.hasGroupsInCommon) {
          getGroupsContainingRecipient(context, recipientId)
        } else {
          emptyList()
        }
      }
  }

  private suspend fun getGroupsContainingRecipient(context: Context, recipientId: RecipientId): List<Recipient> = withContext(Dispatchers.IO) {
    SignalDatabase.groups
      .getPushGroupsContainingMember(recipientId)
      .asSequence()
      .filter { it.members.contains(Recipient.self().id) }
      .map { groupRecord -> Recipient.resolved(groupRecord.recipientId) }
      .sortedBy { group -> group.getDisplayName(context) }
      .toList()
  }
}

/**
 * A summary of groups that recipients have in common.
 */
data class GroupsInCommonSummary(
  private val groups: List<Recipient>
) {
  companion object {
    private val TAG = Log.tag(GroupsInCommonSummary::class.java)
  }

  fun toDisplayText(context: Context, displayGroupsLimit: Int? = null): String {
    val displayGroupNames = (displayGroupsLimit?.let(groups::take) ?: groups)
      .map { it.getDisplayName(context) }

    return when (displayGroupNames.size) {
      0 -> "".logV(TAG, "Member with no groups in common!")
      1 -> context.getString(R.string.MessageRequestProfileView_member_of_one_group, displayGroupNames[0])
      2 -> context.getString(R.string.MessageRequestProfileView_member_of_two_groups, displayGroupNames[0], displayGroupNames[1])
      else -> {
        val specificGroupNames = displayGroupNames.take(2)
        val additionalGroupsCount = displayGroupNames.size - specificGroupNames.size
        context.getString(
          R.string.MessageRequestProfileView_member_of_many_groups,
          specificGroupNames[0],
          specificGroupNames[1],
          context.resources.getQuantityString(R.plurals.MessageRequestProfileView_member_of_d_additional_groups, additionalGroupsCount, additionalGroupsCount)
        )
      }
    }
  }
}
