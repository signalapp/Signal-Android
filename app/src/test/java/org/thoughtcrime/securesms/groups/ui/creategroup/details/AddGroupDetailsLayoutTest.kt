/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.recipients.Recipient

class AddGroupDetailsLayoutTest : SignalLayoutStressTest() {

  private val members = listOf(
    AddGroupDetailsPreviewData.previewRecipient(1, "Maximilian Alexander", "Featherstonehaugh-Worthington"),
    AddGroupDetailsPreviewData.previewRecipient(2, "Bartholomew", "Oyelaran-Vandersteen"),
    AddGroupDetailsPreviewData.previewRecipient(3, "Anastasiya", "Konstantinopolskaya")
  )

  private val sameGroups = listOf(
    Recipient(id = RecipientId.from(10), isResolving = false, groupName = "Saturday morning trail runners and their extended families"),
    Recipient(id = RecipientId.from(11), isResolving = false, groupName = "Neighborhood association committee")
  )

  private val populated = AddGroupDetailsState(
    members = members,
    name = "The extremely long group name that someone typed in",
    disappearingMessagesTimer = 2419200,
    sameGroups = sameGroups
  )

  @Test
  fun empty() = assertPassesLayoutChecks {
    Screen(AddGroupDetailsState())
  }

  @Test
  fun populated() = assertPassesLayoutChecks {
    Screen(populated)
  }

  @Test
  fun creating() = assertPassesLayoutChecks {
    Screen(populated.copy(isCreating = true))
  }

  @Test
  fun `remove member dialog`() = assertPassesLayoutChecks {
    Screen(populated.copy(dialog = AddGroupDetailsState.Dialog.ConfirmRemoveMember(members.first())))
  }

  @Test
  fun `discard dialog`() = assertPassesLayoutChecks {
    Screen(populated.copy(dialog = AddGroupDetailsState.Dialog.ConfirmDiscard(sameGroups.first())))
  }

  @Composable
  private fun Screen(state: AddGroupDetailsState) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
      AddGroupDetailsScreen(state = state, onEvent = {})
    }
  }
}
