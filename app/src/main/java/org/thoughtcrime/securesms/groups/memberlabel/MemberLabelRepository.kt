/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.signal.core.models.ServiceId
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.conversation.colors.ColorizerV2
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Handles the retrieval and modification of group member labels.
 */
class MemberLabelRepository private constructor(
  private val context: Context = AppDependencies.application,
  private val groupsTable: GroupTable = SignalDatabase.groups
) {
  companion object {
    @JvmStatic
    val instance: MemberLabelRepository by lazy { MemberLabelRepository() }
  }

  suspend fun getRecipient(recipientId: RecipientId): Recipient = withContext(Dispatchers.IO) {
    Recipient.resolved(recipientId)
  }

  /**
   * Gets the member label for a specific recipient in the group.
   */
  suspend fun getLabel(groupId: GroupId.V2, recipientId: RecipientId): MemberLabel? {
    return getLabel(groupId, getRecipient(recipientId))
  }

  /**
   * Gets the member label for a specific recipient in the group (blocking version for Java compatibility).
   */
  @WorkerThread
  fun getLabelJava(groupId: GroupId.V2, recipient: Recipient): MemberLabel? = runBlocking { getLabel(groupId, recipient) }

  /**
   * Checks whether the [Recipient] has permission to set their member label in the given group (blocking version for Java compatibility).
   */
  @WorkerThread
  fun canSetLabelJava(groupId: GroupId.V2, recipient: Recipient): Boolean = runBlocking { canSetLabel(groupId, recipient) }

  /**
   * Gets the member label for a specific recipient in the group.
   */
  suspend fun getLabel(groupId: GroupId.V2, recipient: Recipient): MemberLabel? = withContext(Dispatchers.IO) {
    if (!RemoteConfig.receiveMemberLabels) {
      return@withContext null
    }

    val aci = recipient.serviceId.orNull() as? ServiceId.ACI ?: return@withContext null
    val groupRecord = groupsTable.getGroup(groupId).orNull() ?: return@withContext null
    return@withContext groupRecord.requireV2GroupProperties().memberLabel(aci)
  }

  /**
   * Gets member labels for a list of recipients in a group.
   *
   * Returns a map of [RecipientId] to [MemberLabel] for members that have labels.
   */
  suspend fun getLabels(groupId: GroupId.V2, recipients: List<Recipient>): Map<RecipientId, MemberLabel> = withContext(Dispatchers.IO) {
    if (!RemoteConfig.receiveMemberLabels) {
      return@withContext emptyMap()
    }

    val groupRecord = groupsTable.getGroup(groupId).orNull() ?: return@withContext emptyMap()
    val labelsByAci = groupRecord.requireV2GroupProperties().memberLabelsByAci()

    buildMap {
      recipients.forEach { recipient ->
        val aci = recipient.serviceId.orNull() as? ServiceId.ACI
        labelsByAci[aci]?.let { label -> put(recipient.id, label) }
      }
    }
  }

  /**
   * Checks whether the [Recipient] has permission to set their member label in the given group.
   */
  suspend fun canSetLabel(groupId: GroupId.V2, recipient: Recipient): Boolean = withContext(Dispatchers.IO) {
    if (!RemoteConfig.sendMemberLabels) return@withContext false
    val groupRecord = groupsTable.getGroup(groupId).orNull() ?: return@withContext false
    groupRecord.attributesAccessControl.allows(groupRecord.memberLevel(recipient))
  }

  /**
   * Computes the sender [NameColor] for a recipient as seen by other group members.
   */
  suspend fun getSenderNameColor(groupId: GroupId.V2, recipientId: RecipientId): NameColor = withContext(Dispatchers.IO) {
    val recipient = getRecipient(recipientId)

    val groupMemberIds = groupsTable
      .getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF)
      .mapNotNull { it.serviceId.orNull() }

    ColorizerV2(groupMemberIds).getNameColor(context, recipient)
  }

  /**
   * Sets the group member label for the current user.
   */
  suspend fun setLabel(groupId: GroupId.V2, label: MemberLabel): Unit = withContext(Dispatchers.IO) {
    if (!RemoteConfig.sendMemberLabels) {
      throw IllegalStateException("Set member label not allowed due to remote config.")
    }

    GroupManager.updateMemberLabel(context, groupId, label.text, label.emoji.orEmpty())
  }
}
