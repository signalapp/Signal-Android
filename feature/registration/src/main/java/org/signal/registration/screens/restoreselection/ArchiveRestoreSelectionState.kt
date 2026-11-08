/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import org.signal.core.util.censor

data class ArchiveRestoreSelectionState(
  val restoreOptions: List<ArchiveRestoreOption> = emptyList(),
  val showSkipWarningDialog: Boolean = false,
  /** Token that, if present, indicates that the user did a quick restore, and we should hit a network endpoint to indicate our restore selection.  */
  val restoreMethodToken: String? = null,
  /** Whether the account already has SVR/PIN data on the server. Determines whether skipping restore leads to PIN entry or PIN creation. */
  val storageCapable: Boolean = false,
  /** Whether the skip is underway. The last of the work it does is a network call, so the skip card shows a spinner until the flow moves on. */
  val isSkipping: Boolean = false,
  /** Whether the account has no phone number, and therefore no PIN. Such an account never goes to PIN entry or PIN creation. */
  val isPhoneNumberlessAccount: Boolean = false
) {
  override fun toString(): String = "ArchiveRestoreSelectionState(restoreOptions=$restoreOptions, showSkipWarningDialog=$showSkipWarningDialog, restoreMethodToken=${restoreMethodToken?.censor()}, storageCapable=$storageCapable, isSkipping=$isSkipping, isPhoneNumberlessAccount=$isPhoneNumberlessAccount)"
}
