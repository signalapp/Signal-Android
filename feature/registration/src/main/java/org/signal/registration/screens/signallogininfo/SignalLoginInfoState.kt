/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.signal.core.util.censor

/**
 * State for the screen that hands the user their newly-purchased Signal Login and asks them to save it.
 *
 * The credentials are shown masked on the card, with only a few trailing characters revealed, until the user opts
 * into seeing the full values.
 */
data class SignalLoginInfoState(
  val aci: ServiceId.ACI? = null,
  val aep: AccountEntropyPool? = null,
  val isPasswordManagerAvailable: Boolean = false,
  val showSpinner: Boolean = false,
  /** Whether the sheet that double-checks the login really made it into the password manager is up. */
  val showConfirmSavedSheet: Boolean = false,
  /** Whether an interrupted save has already been retried once, so a second interruption is treated as a failure. */
  val didRetrySave: Boolean = false,
  val dialogs: Dialogs = Dialogs()
) {
  /** The account ID in the form it is shown in and stored in a password manager under. */
  val passwordManagerAccountId: String?
    get() = aci?.toString()?.uppercase()

  /** The recovery key in the form it is shown in and stored in a password manager as. */
  val passwordManagerRecoveryKey: String?
    get() = aep?.displayValue

  override fun toString(): String = "SignalLoginInfoState(aci=${aci?.logString()}, aep=${aep?.value?.censor()}, " +
    "isPasswordManagerAvailable=$isPasswordManagerAvailable, showSpinner=$showSpinner, showConfirmSavedSheet=$showConfirmSavedSheet, didRetrySave=$didRetrySave, dialogs=$dialogs)"

  data class Dialogs(
    val saveFailed: Boolean = false,
    val saveNotConfirmed: Boolean = false,
    val unknownError: Boolean = false
  )
}
