/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.signal.core.util.censor
import org.signal.registration.RegistrationFlowState
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult

sealed class PhoneNumberEntryScreenEvents {
  /** Emitted once when the screen is created to load initial data into the state. */
  data object Initialize : PhoneNumberEntryScreenEvents()

  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : PhoneNumberEntryScreenEvents()

  /** The phone country code prefix (i.e. +1) was changed by the user. */
  data class CountryCodeChanged(val value: String) : PhoneNumberEntryScreenEvents()

  /**
   * The national number (basically the number without the country code) was changed by the user. Both the previous and
   * new raw field text are provided so the view model can determine whether this was a single typed character or a bulk
   * change (a paste or autofill).
   */
  data class NationalNumberChanged(val oldValue: String, val newValue: String) : PhoneNumberEntryScreenEvents()

  /** The user changed the country via the country picker. */
  data class CountrySelected(val countryCode: Int, val regionCode: String, val countryName: String, val countryEmoji: String) : PhoneNumberEntryScreenEvents()

  /**
   * The user entered their full number all at once. This could be from the Google Play picker or autofilled using READ_PHONE_STATE permission.
   *
   * @param autoConfirm If true, we trust the entry enough to skip straight to the confirmation dialog (as if the user had clicked 'next').
   */
  data class FullPhoneNumberEntered(val e164: String, val autoConfirm: Boolean = false) : PhoneNumberEntryScreenEvents()

  /** The user clicked the 'next' button. */
  data object NextClicked : PhoneNumberEntryScreenEvents()

  /** The user dismissed or otherwise canceled the dialog that was shown to confirm their phone number. */
  data object PhoneNumberCancelled : PhoneNumberEntryScreenEvents()

  /** The user confirmed their phone number in the dialog. */
  data object PhoneNumberConfirmed : PhoneNumberEntryScreenEvents()

  /** The user requested to open the country picker.  */
  data object CountryPicker : PhoneNumberEntryScreenEvents()

  /** The user chose to link this device to an existing account instead of registering a new number. */
  data object LinkDevice : PhoneNumberEntryScreenEvents()

  data class CaptchaCompleted(val token: String) : PhoneNumberEntryScreenEvents() {
    override fun toString(): String = "CaptchaCompleted(token=${token.censor()})"
  }

  /** The pre-registration local backup restore flow returned a result. */
  data class LocalBackupRestoreCompleted(val result: LocalBackupRestoreResult) : PhoneNumberEntryScreenEvents() {
    override fun toString(): String = "LocalBackupRestoreCompleted(result=***)"
  }

  /** The user dismissed the network error dialog. */
  data object NetworkErrorDialogDismissed : PhoneNumberEntryScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : PhoneNumberEntryScreenEvents()

  /** The user dismissed the rate limited dialog. */
  data object RateLimitedDialogDismissed : PhoneNumberEntryScreenEvents()

  /** The user dismissed the unable-to-send-SMS dialog. */
  data object UnableToSendSmsDialogDismissed : PhoneNumberEntryScreenEvents()

  /** The user dismissed the could-not-request-code-with-selected-transport dialog. */
  data object CouldNotRequestCodeWithSelectedTransportDialogDismissed : PhoneNumberEntryScreenEvents()

  /** The user dismissed the invalid phone number dialog. */
  data object InvalidPhoneNumberDialogDismissed : PhoneNumberEntryScreenEvents()
}
