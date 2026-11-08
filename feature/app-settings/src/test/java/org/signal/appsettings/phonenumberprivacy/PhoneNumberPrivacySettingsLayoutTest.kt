/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.phonenumberprivacy

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class PhoneNumberPrivacySettingsLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `sharing`() = assertPassesLayoutChecks {
    PhoneNumberPrivacySettingsScreen(state = PhoneNumberPrivacySettingsState(phoneNumberSharing = true, discoverableByPhoneNumber = true), onEvent = {})
  }

  @Test
  fun `hidden but discoverable`() = assertPassesLayoutChecks {
    PhoneNumberPrivacySettingsScreen(state = PhoneNumberPrivacySettingsState(phoneNumberSharing = false, discoverableByPhoneNumber = true), onEvent = {})
  }

  @Test
  fun `hidden and not discoverable`() = assertPassesLayoutChecks {
    PhoneNumberPrivacySettingsScreen(state = PhoneNumberPrivacySettingsState(phoneNumberSharing = false, discoverableByPhoneNumber = false), onEvent = {})
  }

  @Test
  fun `asking to hide the number first`() = assertPassesLayoutChecks {
    PhoneNumberPrivacySettingsScreen(state = PhoneNumberPrivacySettingsState(phoneNumberSharing = true, snackbar = PhoneNumberPrivacySettingsState.Snackbar.HideNumberFirst), onEvent = {})
  }

  @Test
  fun `confirming not discoverable`() = assertPassesLayoutChecks {
    PhoneNumberPrivacySettingsScreen(state = PhoneNumberPrivacySettingsState(dialog = PhoneNumberPrivacySettingsState.Dialog.ConfirmNotDiscoverable), onEvent = {})
  }
}
