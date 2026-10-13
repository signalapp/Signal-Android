/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.grouppermissions

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsEvents
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsState
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.ui.GroupErrors

/**
 * View model behind [GroupPermissionsScreen].
 */
class GroupPermissionsViewModel(
  private val groupId: GroupId,
  private val repository: GroupPermissionsRepository = GroupPermissionsRepository()
) : EventDrivenViewModel<GroupPermissionsEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(GroupPermissionsViewModel::class)
  }

  private val _state = MutableStateFlow(GroupPermissionsState())

  val state: StateFlow<GroupPermissionsState> = _state.asStateFlow()

  init {
    repository
      .observePermissions(groupId)
      .onEach { onEvent(GroupPermissionsEvents.PermissionsChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: GroupPermissionsEvents) {
    when (event) {
      is GroupPermissionsEvents.PermissionsChanged -> {
        _state.update { it.copy(permissions = event.permissions) }
      }

      is GroupPermissionsEvents.SetNonAdminCanAddMembers -> {
        applyChange { repository.applyMembershipRightsChange(groupId, event.allowed.asGroupAccessControl()) }
      }

      is GroupPermissionsEvents.SetNonAdminCanEditGroupInfo -> {
        applyChange { repository.applyAttributesRightsChange(groupId, event.allowed.asGroupAccessControl()) }
      }

      is GroupPermissionsEvents.SetNonAdminCanSendMessages -> {
        applyChange { repository.applyAnnouncementGroupChange(groupId, isAnnouncementGroup = !event.allowed) }
      }

      is GroupPermissionsEvents.SetNonAdminCanSetMemberLabel -> {
        if (!event.allowed && _state.value.permissions.nonAdminsHaveMemberLabels) {
          _state.update { it.copy(dialog = Dialog.MemberLabelsWillBeCleared) }
        } else {
          applyChange { repository.applyMemberLabelRightsChange(groupId, event.allowed.asGroupAccessControl()) }
        }
      }

      GroupPermissionsEvents.MemberLabelsWillBeClearedConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        applyChange { repository.applyMemberLabelRightsChange(groupId, GroupAccessControl.ONLY_ADMINS) }
      }

      GroupPermissionsEvents.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }

      GroupPermissionsEvents.SnackbarDismissed -> {
        _state.update { it.copy(errorMessage = null) }
      }
    }
  }

  /**
   * Submits a group change without waiting for it, since events are processed one at a time and a change takes a
   * network round trip. Waiting would leave whatever the user does next -- another permission, or the dialog that
   * warns them about clearing member labels -- sitting in the queue looking like it did nothing.
   */
  private fun applyChange(change: suspend () -> GroupChangeResult) {
    viewModelScope.launch {
      val result = change()

      if (!result.isSuccess) {
        _state.update { it.copy(errorMessage = GroupErrors.getUserDisplayMessage(result.failureReason)) }
      }
    }
  }

  private fun Boolean.asGroupAccessControl(): GroupAccessControl {
    return if (this) {
      GroupAccessControl.ALL_MEMBERS
    } else {
      GroupAccessControl.ONLY_ADMINS
    }
  }
}
