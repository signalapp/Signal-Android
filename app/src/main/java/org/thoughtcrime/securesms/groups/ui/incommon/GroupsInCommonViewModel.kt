/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.groups.GroupsInCommonRepository
import kotlin.time.Duration.Companion.seconds

class GroupsInCommonViewModel(
  context: Context,
  recipientId: RecipientId,
  repository: GroupsInCommonRepository = GroupsInCommonRepository
) : EventDrivenViewModel<GroupsInCommonEvent>(TAG) {
  companion object {
    private val TAG = Log.tag(GroupsInCommonViewModel::class)
  }

  private val _actions = Channel<GroupsInCommonAction>(Channel.BUFFERED)

  val state: StateFlow<GroupsInCommonState> = repository.getNamedGroupsInCommon(context, recipientId)
    .map { groups -> GroupsInCommonState(groups = groups) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
      initialValue = GroupsInCommonState()
    )

  val actions: Flow<GroupsInCommonAction> = _actions.receiveAsFlow()

  private var hasOpenedConversation = false

  override suspend fun processEvent(event: GroupsInCommonEvent) {
    when (event) {
      is GroupsInCommonEvent.GroupClicked -> {
        if (hasOpenedConversation) {
          Log.d(TAG, "Already opening a conversation, ignoring click on ${event.groupId}.")
          return
        }
        val group = state.value.groups.firstOrNull { it.recipient.id == event.groupId }
        if (group == null) {
          Log.w(TAG, "Clicked ${event.groupId} not found in groups in common, ignoring.")
          return
        }
        hasOpenedConversation = true
        _actions.send(GroupsInCommonAction.OpenGroupConversation(group.recipient))
      }

      GroupsInCommonEvent.NavigateBackClicked -> _actions.send(GroupsInCommonAction.NavigateBack)
    }
  }
}
