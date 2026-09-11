/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.conversation.permissions.PermissionsSettingsState.Dialog
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult

/**
 * View model behind [PermissionsSettingsScreen].
 */
class PermissionsSettingsViewModel(
  private val groupId: GroupId,
  private val repository: PermissionsSettingsRepository = PermissionsSettingsRepository()
) : EventDrivenViewModel<PermissionsSettingsEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(PermissionsSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(PermissionsSettingsState())

  val state: StateFlow<PermissionsSettingsState> = _state.asStateFlow()

  init {
    repository
      .observePermissions(groupId)
      .onEach { onEvent(PermissionsSettingsEvents.PermissionsChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: PermissionsSettingsEvents) {
    when (event) {
      is PermissionsSettingsEvents.PermissionsChanged -> {
        _state.update {
          it.copy(
            selfCanEditSettings = event.permissions.selfCanEditSettings,
            nonAdminCanAddMembers = event.permissions.nonAdminCanAddMembers,
            nonAdminCanEditGroupInfo = event.permissions.nonAdminCanEditGroupInfo,
            nonAdminCanSendMessages = event.permissions.nonAdminCanSendMessages,
            nonAdminCanSetMemberLabel = event.permissions.nonAdminCanSetMemberLabel,
            nonAdminsHaveMemberLabels = event.permissions.nonAdminsHaveMemberLabels
          )
        }
      }

      is PermissionsSettingsEvents.SetNonAdminCanAddMembers -> {
        applyChange { repository.applyMembershipRightsChange(groupId, event.allowed.asGroupAccessControl()) }
      }

      is PermissionsSettingsEvents.SetNonAdminCanEditGroupInfo -> {
        applyChange { repository.applyAttributesRightsChange(groupId, event.allowed.asGroupAccessControl()) }
      }

      is PermissionsSettingsEvents.SetNonAdminCanSendMessages -> {
        applyChange { repository.applyAnnouncementGroupChange(groupId, isAnnouncementGroup = !event.allowed) }
      }

      is PermissionsSettingsEvents.SetNonAdminCanSetMemberLabel -> {
        if (!event.allowed && _state.value.nonAdminsHaveMemberLabels) {
          _state.update { it.copy(dialog = Dialog.MemberLabelsWillBeCleared) }
        } else {
          applyChange { repository.applyMemberLabelRightsChange(groupId, event.allowed.asGroupAccessControl()) }
        }
      }

      PermissionsSettingsEvents.MemberLabelsWillBeClearedConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        applyChange { repository.applyMemberLabelRightsChange(groupId, GroupAccessControl.ONLY_ADMINS) }
      }

      PermissionsSettingsEvents.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }

      PermissionsSettingsEvents.SnackbarDismissed -> {
        _state.update { it.copy(groupChangeError = null) }
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
        _state.update { it.copy(groupChangeError = result.failureReason) }
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
