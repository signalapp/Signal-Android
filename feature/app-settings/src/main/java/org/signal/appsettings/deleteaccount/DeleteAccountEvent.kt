/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import org.signal.core.util.censor

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface DeleteAccountEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : DeleteAccountEvent

  /** The user tapped the country row, which opens the picker. */
  data object CountryPickerClicked : DeleteAccountEvent

  /** The user picked [regionCode] out of the country picker. */
  data class CountrySelected(val regionCode: String) : DeleteAccountEvent

  /** The user typed in the calling code field. */
  data class CountryCodeChanged(val countryCode: String) : DeleteAccountEvent

  /** The user typed in the phone number field. */
  data class NationalNumberChanged(val nationalNumber: String) : DeleteAccountEvent {
    override fun toString(): String = "NationalNumberChanged(nationalNumber=${nationalNumber.censor()})"
  }

  /** The user asked to delete their account, which we only act on once they've confirmed. */
  data object DeleteAccountClicked : DeleteAccountEvent

  /** The user confirmed the deletion, either from the confirmation dialog or by retrying a failed one. */
  data object DeletionConfirmed : DeleteAccountEvent

  /** The user asked to be taken to the system settings for this app so they can clear its data by hand. */
  data object LaunchAppSettingsClicked : DeleteAccountEvent

  /** Dismisses whatever is in [DeleteAccountState.dialog]. */
  data object DialogDismissed : DeleteAccountEvent
}
