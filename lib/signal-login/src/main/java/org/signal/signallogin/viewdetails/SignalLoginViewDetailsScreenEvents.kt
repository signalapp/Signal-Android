/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

sealed class SignalLoginViewDetailsScreenEvents {
  /** The user tapped the back arrow. */
  data object BackClicked : SignalLoginViewDetailsScreenEvents()

  /** The user chose to store the credentials with the system password manager. */
  data object SaveToPasswordManagerClicked : SignalLoginViewDetailsScreenEvents()

  /** The user confirmed they want to save to their password manager. */
  data object SaveToPasswordManagerConfirmed : SignalLoginViewDetailsScreenEvents()

  /** The user backed out of the save to password manager confirmation. */
  data object SaveToPasswordManagerConfirmationDismissed : SignalLoginViewDetailsScreenEvents()

  /** The user chose to save the credentials as a PDF. */
  data object SaveAsPdfClicked : SignalLoginViewDetailsScreenEvents()

  /** The user chose to start the flow that replaces their recovery key with a new one. */
  data object ResetRecoveryKeyClicked : SignalLoginViewDetailsScreenEvents()

  /** The user tapped the copy button on the account ID field. */
  data object CopyAccountIdClicked : SignalLoginViewDetailsScreenEvents()

  /** The user tapped the copy button on the recovery key field. */
  data object CopyRecoveryKeyClicked : SignalLoginViewDetailsScreenEvents()

  /** The user acknowledged the do-not-share warning, so the copy or save they started should go ahead. */
  data object DoNotShareWarningGotItClicked : SignalLoginViewDetailsScreenEvents()

  /** The user tapped "learn more" on the do-not-share warning. */
  data object DoNotShareWarningLearnMoreClicked : SignalLoginViewDetailsScreenEvents()

  /** The user dismissed the do-not-share warning without acknowledging it. */
  data object DoNotShareWarningDismissed : SignalLoginViewDetailsScreenEvents()
}
