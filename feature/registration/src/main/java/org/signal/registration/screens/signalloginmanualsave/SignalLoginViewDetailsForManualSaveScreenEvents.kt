/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import org.signal.core.util.censor
import org.signal.registration.RegistrationFlowState

sealed class SignalLoginViewDetailsForManualSaveScreenEvents {
  /** The parent registration flow state changed. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : SignalLoginViewDetailsForManualSaveScreenEvents()

  /** The user tapped the back arrow. */
  data object BackClicked : SignalLoginViewDetailsForManualSaveScreenEvents()

  /** The user tapped the copy button on the account ID field. */
  data class CopyAccountIdClicked(val accountId: String) : SignalLoginViewDetailsForManualSaveScreenEvents() {
    override fun toString(): String = "CopyAccountIdClicked(accountId=${accountId.censor()})"
  }

  /** The user tapped the copy button on the recovery key field. */
  data class CopyRecoveryKeyClicked(val recoveryKey: String) : SignalLoginViewDetailsForManualSaveScreenEvents() {
    override fun toString(): String = "CopyRecoveryKeyClicked(recoveryKey=${recoveryKey.censor()})"
  }

  /** The user chose to save the credentials as a PDF. */
  data object SaveAsPdfClicked : SignalLoginViewDetailsForManualSaveScreenEvents()

  /** The user says they're done recording their login. */
  data object ContinueClicked : SignalLoginViewDetailsForManualSaveScreenEvents()

  /** The user confirmed on the sheet that their login really is saved. */
  data object ConfirmSavedContinueClicked : SignalLoginViewDetailsForManualSaveScreenEvents()

  /** The user tapped "show login info again" on the sheet, sending them back to the keys. */
  data object ShowLoginInfoAgainClicked : SignalLoginViewDetailsForManualSaveScreenEvents()

  /** The user dismissed the confirm-you-saved-it sheet without answering it. */
  data object ConfirmSavedSheetDismissed : SignalLoginViewDetailsForManualSaveScreenEvents()
}
