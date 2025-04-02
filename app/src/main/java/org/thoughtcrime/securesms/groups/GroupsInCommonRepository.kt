/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Centralizes operations for retrieving groups that a given recipient has in common with another user.
 */
class GroupsInCommonRepository(private val context: Context) {

  fun getGroupsInCommon(recipientId: RecipientId): Flow<List<Recipient>> {
    return Recipient.observable(recipientId)
      .asFlow()
      .map { recipient ->
        if (recipient.hasGroupsInCommon) {
          getGroupsContainingRecipient(recipientId)
        } else {
          emptyList()
        }
      }
  }

  private suspend fun getGroupsContainingRecipient(recipientId: RecipientId): List<Recipient> = withContext(Dispatchers.IO) {
    SignalDatabase.groups.getPushGroupsContainingMember(recipientId)
      .asSequence()
      .filter { it.members.contains(Recipient.self().id) }
      .map { groupRecord -> Recipient.resolved(groupRecord.recipientId) }
      .sortedBy { group -> group.getDisplayName(context) }
      .toList()
  }
}
