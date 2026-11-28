/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.hiddencontacts

import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.SignalLayoutStressTest

class HiddenContactsLayoutTest : SignalLayoutStressTest() {

  private val populated = HiddenContactsState(
    contacts = listOf(
      HiddenContactsState.Contact(RecipientId.from(1), "Alexandria Montgomery-Fitzgerald von Hohenzollern-Sigmaringen the Third"),
      HiddenContactsState.Contact(RecipientId.from(2), "Bo"),
      HiddenContactsState.Contact(RecipientId.from(3), "+1 (555) 555-5555")
    ),
    isLoading = false
  )

  private val ellipsized = setOf(HiddenContactsTestTags.TEXT_CONTACT_NAME)

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    HiddenContactsScreen(state = HiddenContactsState(), onEvent = {})
  }

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    HiddenContactsScreen(state = HiddenContactsState(isLoading = false), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks(ignoreTags = ellipsized) {
    HiddenContactsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `confirming an unhide`() = assertPassesLayoutChecks(ignoreTags = ellipsized) {
    HiddenContactsScreen(state = populated.copy(dialog = HiddenContactsState.Dialog.ConfirmUnhide(populated.contacts[1])), onEvent = {})
  }
}
