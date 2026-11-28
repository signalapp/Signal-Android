/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.SvrRepository

/**
 * Reads what decides whether the PIN can be turned off, and turns it off once the backup key has been rotated.
 */
object AdvancedPinSettingsRepository {

  fun hasOptedOutOfPin(): Boolean = SignalStore.svr.hasOptedOut()

  fun isRegistrationLockEnabled(): Boolean = SignalStore.svr.isRegistrationLockEnabled

  /** Payments are on but the user hasn't confirmed they recorded the recovery phrase, which they'd lose with the PIN. */
  fun needsPaymentsRecoveryPhrase(): Boolean = SignalStore.payments.mobileCoinPaymentsEnabled() && !SignalStore.payments.userConfirmedMnemonic

  fun areBackupsEnabled(): Boolean = SignalStore.backup.areBackupsEnabled

  /** Turns the PIN off after the backup key was rotated separately, so this doesn't rotate it again. */
  suspend fun optOutOfPinAfterKeyRotation() = withContext(Dispatchers.IO) {
    SvrRepository.optOutOfPin(rotateAep = false)
  }
}
