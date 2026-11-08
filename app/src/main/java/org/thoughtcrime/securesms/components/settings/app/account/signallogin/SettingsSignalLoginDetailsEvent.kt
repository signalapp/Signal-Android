/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents

/**
 * Everything [SettingsSignalLoginDetailsViewModel] can be told about. This wraps the events the shared screen emits and
 * adds the ones that only exist in the settings entry point, namely the recovery key reset confirmation flow.
 */
sealed interface SettingsSignalLoginDetailsEvent {

  /** An event emitted by the shared screen itself. */
  data class Screen(val event: SignalLoginViewDetailsScreenEvents) : SettingsSignalLoginDetailsEvent

  /** The user acknowledged what a recovery key reset entails. */
  data object ResetRecoveryKeyConfirmed : SettingsSignalLoginDetailsEvent

  /** The user backed out of one of the recovery key reset dialogs. */
  data object ResetRecoveryKeyDismissed : SettingsSignalLoginDetailsEvent

  /** The user agreed to turn off optimized storage so their offloaded media comes back down. */
  data object TurnOffOptimizedStorageClicked : SettingsSignalLoginDetailsEvent

  /** The reset flow finished and generated a new recovery key. */
  data object RecoveryKeyRotated : SettingsSignalLoginDetailsEvent
}
