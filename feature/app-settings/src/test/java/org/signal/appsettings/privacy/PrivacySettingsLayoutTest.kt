/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PrivacySettingsLayoutTest : SignalLayoutStressTest() {

  private val populated = PrivacySettingsState(
    blockedCount = 1234,
    readReceipts = true,
    typingIndicators = true,
    universalExpireTimer = 4.days,
    screenLock = true,
    screenLockTimeout = 6.days + 23.hours,
    screenSecurity = true,
    incognitoKeyboard = true,
    paymentLock = true
  )

  @Test
  fun `defaults`() = assertPassesLayoutChecks {
    Screen(PrivacySettingsState())
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    Screen(populated)
  }

  @Test
  fun `insecure device without a phone number`() = assertPassesLayoutChecks {
    Screen(populated.copy(isDeviceSecure = false, hasPhoneNumber = false))
  }

  @Test
  fun `legacy passphrase`() = assertPassesLayoutChecks {
    Screen(populated.copy(isObsoletePassphraseEnabled = true, isObsoletePassphraseTimeoutEnabled = true, obsoletePassphraseTimeout = 30.minutes))
  }

  @Test
  fun `confirming passphrase disable`() = assertPassesLayoutChecks {
    Screen(populated.copy(isObsoletePassphraseEnabled = true, dialog = PrivacySettingsState.Dialog.ConfirmDisablePassphrase))
  }

  @Test
  fun `picking a passphrase timeout`() = assertPassesLayoutChecks {
    Screen(populated.copy(isObsoletePassphraseEnabled = true, dialog = PrivacySettingsState.Dialog.PassphraseTimeout))
  }

  @Test
  fun `payment lock needs device security`() = assertPassesLayoutChecks {
    Screen(populated.copy(isDeviceSecure = false, dialog = PrivacySettingsState.Dialog.PaymentLockNeedsDeviceSecurity))
  }

  @Test
  fun `showing a snackbar`() = assertPassesLayoutChecks {
    Screen(populated.copy(snackbar = PrivacySettingsState.Snackbar.ScreenLockNeedsDeviceSecurity))
  }

  @Composable
  private fun Screen(state: PrivacySettingsState) {
    // The biometrics helper needs a FragmentActivity to show its prompt. Inspection mode makes it pass straight through.
    CompositionLocalProvider(LocalInspectionMode provides true) {
      PrivacySettingsScreen(state = state, onEvent = {})
    }
  }
}
