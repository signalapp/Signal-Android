/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.ServiceId
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Handles the retrieval and modification of group member labels.
 */
class MemberLabelRepository(
  private val groupId: GroupId.V2,
  private val context: Context = AppDependencies.application,
  private val groupsTable: GroupTable = SignalDatabase.groups
) {
  /**
   * Gets the member label for a specific recipient in the group.
   */
  suspend fun getLabel(recipientId: RecipientId): MemberLabel? = withContext(Dispatchers.IO) {
    val recipient = Recipient.resolved(recipientId)
    val aci = recipient.serviceId.orNull() as? ServiceId.ACI ?: return@withContext null
    val groupRecord = groupsTable.getGroup(groupId).orNull() ?: return@withContext null

    return@withContext groupRecord.requireV2GroupProperties().memberLabel(aci)
  }

  /**
   * Sets the group member label for the current user.
   */
  suspend fun setLabel(label: MemberLabel): Unit = withContext(Dispatchers.IO) {
    GroupManager.updateMemberLabel(context, groupId, label.text, label.emoji ?: "")
  }

  /**
   * Clears the group member label for the current user.
   */
  suspend fun removeLabel(): Unit = withContext(Dispatchers.IO) {
    GroupManager.updateMemberLabel(context, groupId, "", "")
  }
}

/**
 * A member's custom label within a group.
 */
data class MemberLabel(
  val emoji: String?,
  val text: String
)
