/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class PhoneNumberScreenLayoutTest : SignalLayoutStressTest() {

  private val enteredNumber = PhoneNumberEntryState(
    regionCode = "DE",
    countryCode = "49",
    countryName = "Germany",
    countryEmoji = "🇩🇪",
    nationalNumber = "15112345678",
    formattedNumber = "151 12345678",
    isNumberPossible = true
  )

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    PhoneNumberScreen(state = PhoneNumberEntryState(), onEvent = {})
  }

  @Test
  fun `number entered`() = assertPassesLayoutChecks {
    PhoneNumberScreen(state = enteredNumber, onEvent = {})
  }

  @Test
  fun `invalid number`() = assertPassesLayoutChecks {
    PhoneNumberScreen(state = enteredNumber.copy(isNumberPossible = false, isNumberInvalid = true), onEvent = {})
  }

  @Test
  fun `requesting a code`() = assertPassesLayoutChecks {
    PhoneNumberScreen(state = enteredNumber.copy(showSpinner = true), onEvent = {})
  }

  @Test
  fun `confirming the number`() = assertPassesLayoutChecks {
    PhoneNumberScreen(state = enteredNumber.copy(dialogs = PhoneNumberEntryState.Dialogs(confirmNumber = true)), onEvent = {})
  }
}
