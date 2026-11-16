/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.profilename

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class EditProfileNameLayoutTest : SignalLayoutStressTest() {

  private val populated = EditProfileNameState(
    givenName = "Maximiliana-Josephine",
    familyName = "Wolfeschlegelsteinhausen"
  )

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    EditProfileNameScreen(state = EditProfileNameState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    EditProfileNameScreen(state = populated, onEvent = {})
  }

  @Test
  fun `saving`() = assertPassesLayoutChecks {
    EditProfileNameScreen(state = populated.copy(saving = true), onEvent = {})
  }
}
