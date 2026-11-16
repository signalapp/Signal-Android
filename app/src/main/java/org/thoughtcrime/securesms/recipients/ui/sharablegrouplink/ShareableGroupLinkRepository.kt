/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.chatsettings.screens.sharablegrouplink.GroupLink
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.groups.GroupChangeException
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.storageservice.storage.protos.groups.AccessControl
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.IOException

/**
 * All of the group link reads and writes behind [ShareableGroupLinkViewModel].
 */
class ShareableGroupLinkRepository(
  private val context: Context = AppDependencies.application,
  private val groupTable: GroupTable = SignalDatabase.groups
) {

  companion object {
    private val TAG = Log.tag(ShareableGroupLinkRepository::class)
  }

  /**
   * Emits the group's link whenever it changes. A group change always touches the group's recipient, so recipient
   * updates drive this. Plenty of unrelated activity touches it too -- an incoming message refreshes it -- hence the
   * dedupe.
   */
  fun observeGroupLink(groupId: GroupId.V2): Flow<GroupLink> {
    return flow {
      val recipientId = withContext(SignalDispatchers.Default) { Recipient.externalGroupExact(groupId).id }
      emitAll(Recipient.observable(recipientId).asFlow())
    }.mapNotNull { recipient ->
      withContext(SignalDispatchers.Default) {
        groupTable.getGroup(recipient.id).orNull()?.toGroupLink()
      }
    }.distinctUntilChanged()
  }

  /**
   * Turns the link on or off, and sets whether joining through it needs an admin's approval.
   */
  suspend fun setGroupLinkState(groupId: GroupId.V2, enabled: Boolean, requiresAdminApproval: Boolean): GroupChangeResult = groupChange {
    val state = when {
      !enabled -> GroupManager.GroupLinkState.DISABLED
      requiresAdminApproval -> GroupManager.GroupLinkState.ENABLED_WITH_APPROVAL
      else -> GroupManager.GroupLinkState.ENABLED
    }

    GroupManager.setGroupLinkEnabledState(context, groupId, state)
  }

  /**
   * Gives the group a new link, which stops the previous one from working.
   */
  suspend fun cycleGroupLinkPassword(groupId: GroupId.V2): GroupChangeResult = groupChange {
    GroupManager.cycleGroupLinkPassword(context, groupId)
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

  private fun GroupRecord.toGroupLink(): GroupLink {
    val selfCanEditSettings = isActive && isAdmin(Recipient.self())

    if (!hasV2GroupProperties) {
      return GroupLink.NONE.copy(selfCanEditSettings = selfCanEditSettings)
    }

    val properties = requireV2GroupProperties()
    val decryptedGroup = properties.decryptedGroup

    if (decryptedGroup.inviteLinkPassword.size == 0) {
      return GroupLink.NONE.copy(selfCanEditSettings = selfCanEditSettings)
    }

    return GroupLink(
      enabled = isGroupLinkEnabled,
      requiresAdminApproval = decryptedGroup.accessControl?.addFromInviteLink == AccessControl.AccessRequired.ADMINISTRATOR,
      url = GroupInviteLinkUrl.forGroup(properties.groupMasterKey, decryptedGroup).url,
      selfCanEditSettings = selfCanEditSettings
    )
  }
}
