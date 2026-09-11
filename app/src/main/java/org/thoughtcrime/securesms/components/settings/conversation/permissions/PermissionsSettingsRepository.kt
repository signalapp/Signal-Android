/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupChangeException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.IOException

private val TAG = Log.tag(PermissionsSettingsRepository::class)

/**
 * All of the group access control reads and writes behind [PermissionsSettingsViewModel].
 */
class PermissionsSettingsRepository(
  private val context: Context = AppDependencies.application,
  private val groupTable: GroupTable = SignalDatabase.groups
) {

  /**
   * Emits the group's permissions whenever they change. A group change always touches the group's recipient, so
   * recipient updates drive this. Plenty of unrelated activity touches it too -- an incoming message refreshes it --
   * hence the dedupe.
   */
  fun observePermissions(groupId: GroupId): Flow<GroupPermissions> {
    return flow {
      val recipientId = withContext(SignalDispatchers.Default) { Recipient.externalGroupExact(groupId).id }
      emitAll(Recipient.observable(recipientId).asFlow())
    }.mapNotNull { recipient ->
      withContext(SignalDispatchers.Default) {
        groupTable.getGroup(recipient.id).orNull()?.toGroupPermissions()
      }
    }.distinctUntilChanged()
  }

  suspend fun applyMembershipRightsChange(groupId: GroupId, newRights: GroupAccessControl): GroupChangeResult = groupChange {
    GroupManager.applyMembershipAdditionRightsChange(context, groupId.requireV2(), newRights)
  }

  suspend fun applyAttributesRightsChange(groupId: GroupId, newRights: GroupAccessControl): GroupChangeResult = groupChange {
    GroupManager.applyAttributesRightsChange(context, groupId.requireV2(), newRights)
  }

  suspend fun applyAnnouncementGroupChange(groupId: GroupId, isAnnouncementGroup: Boolean): GroupChangeResult = groupChange {
    GroupManager.applyAnnouncementGroupChange(context, groupId.requireV2(), isAnnouncementGroup)
  }

  suspend fun applyMemberLabelRightsChange(groupId: GroupId, newRights: GroupAccessControl): GroupChangeResult = groupChange {
    GroupManager.applyMemberLabelRightsChange(context, groupId.requireV2(), newRights)
  }

  /**
   * Runs a group change, which hits the network, and translates whatever went wrong into something we can show the
   * user.
   */
  private suspend fun groupChange(block: () -> Unit): GroupChangeResult {
    return withContext(SignalDispatchers.IO) {
      try {
        block()
        GroupChangeResult.SUCCESS
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
      }
    }
  }

  private fun GroupRecord.toGroupPermissions(): GroupPermissions {
    return GroupPermissions(
      selfCanEditSettings = isActive && isAdmin(Recipient.self()),
      nonAdminCanAddMembers = membershipAdditionAccessControl == GroupAccessControl.ALL_MEMBERS,
      nonAdminCanEditGroupInfo = attributesAccessControl == GroupAccessControl.ALL_MEMBERS,
      nonAdminCanSendMessages = !isAnnouncementGroup,
      nonAdminCanSetMemberLabel = memberLabelAccessControl == GroupAccessControl.ALL_MEMBERS,
      nonAdminsHaveMemberLabels = hasV2GroupProperties && requireV2GroupProperties().nonAdminMembersWithLabels().isNotEmpty()
    )
  }

  /**
   * Everything the permissions screen reads off of the group's record.
   */
  data class GroupPermissions(
    val selfCanEditSettings: Boolean,
    val nonAdminCanAddMembers: Boolean,
    val nonAdminCanEditGroupInfo: Boolean,
    val nonAdminCanSendMessages: Boolean,
    val nonAdminCanSetMemberLabel: Boolean,
    val nonAdminsHaveMemberLabels: Boolean
  )
}
