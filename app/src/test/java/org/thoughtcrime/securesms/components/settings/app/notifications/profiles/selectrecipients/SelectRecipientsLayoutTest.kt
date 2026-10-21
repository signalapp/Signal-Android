/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.SignalLayoutStressTest

class SelectRecipientsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `nobody picked`() = assertPassesLayoutChecks {
    SelectRecipientsScreen(state = SelectRecipientsState(), onEvent = {})
  }

  @Test
  fun `saving`() = assertPassesLayoutChecks {
    SelectRecipientsScreen(state = SelectRecipientsState(selection = setOf(RecipientId.from(1L)), isSaving = true), onEvent = {})
  }
}
