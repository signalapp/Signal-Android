/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.thoughtcrime.securesms.groups.GroupsInCommonRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class GroupsInCommonViewModel(
  recipientId: RecipientId,
  groupsInCommonRepo: GroupsInCommonRepository
) : ViewModel() {

  val groups: StateFlow<List<Recipient>> = groupsInCommonRepo.getGroupsInCommon(recipientId)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(),
      initialValue = emptyList()
    )
}
