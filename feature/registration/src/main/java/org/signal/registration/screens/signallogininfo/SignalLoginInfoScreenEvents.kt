/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import org.signal.passwordmanager.CredentialManagerResult
import org.signal.passwordmanager.UsernamePasswordCredential
import org.signal.registration.RegistrationFlowState

sealed class SignalLoginInfoScreenEvents {
  /** The parent registration flow state changed. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : SignalLoginInfoScreenEvents()

  /** The user tapped "view details" on the credential card to reveal the full values. */
  data object ViewDetailsClicked : SignalLoginInfoScreenEvents()

  /** The user chose to store the credentials with the system password manager. */
  data object SaveToPasswordManagerClicked : SignalLoginInfoScreenEvents()

  /** The password manager finished with the save, one way or another. */
  data class SaveToPasswordManagerCompleted(val result: CredentialManagerResult) : SignalLoginInfoScreenEvents()

  /** The user is ready to have the login they just saved checked. */
  data object ConfirmSavedContinueClicked : SignalLoginInfoScreenEvents()

  /** The user wants to look at their login again rather than confirm it right now. */
  data object SeeLoginInfoAgainClicked : SignalLoginInfoScreenEvents()

  /** The user dismissed the confirm-you-saved-it sheet without answering it. */
  data object ConfirmSavedSheetDismissed : SignalLoginInfoScreenEvents()

  /** The password manager handed back what it had stored, or null if nothing came back. */
  data class SavedCredentialRetrieved(val credential: UsernamePasswordCredential?) : SignalLoginInfoScreenEvents()

  /** The user chose to record the credentials themselves rather than using a password manager. */
  data object SaveManuallyClicked : SignalLoginInfoScreenEvents()

  /** The user dismissed the failed-save dialog. */
  data object SaveFailedDialogDismissed : SignalLoginInfoScreenEvents()

  /** The user dismissed the dialog saying the saved login couldn't be confirmed. */
  data object SaveNotConfirmedDialogDismissed : SignalLoginInfoScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : SignalLoginInfoScreenEvents()
}
