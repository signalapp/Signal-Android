/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out by the host
 * rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface DeleteAccountAction {

  /** Leave the screen. */
  data object NavigateBack : DeleteAccountAction

  /** Open the country picker. */
  data object NavigateToCountryPicker : DeleteAccountAction

  /** Tell the user they have to fill in a calling code before we can check their number. */
  data object ShowNoCountryCode : DeleteAccountAction

  /** Tell the user they have to fill in a phone number before we can check it. */
  data object ShowNoNationalNumber : DeleteAccountAction

  /** Open the system settings for this app, which is where the user can clear its data by hand. */
  data object LaunchAppSettings : DeleteAccountAction
}
