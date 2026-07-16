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
  val storageCapable: Boolean = false
) {
  override fun toString(): String = "ArchiveRestoreSelectionState(restoreOptions=$restoreOptions, showSkipWarningDialog=$showSkipWarningDialog, restoreMethodToken=${restoreMethodToken?.censor()}, storageCapable=$storageCapable)"
}
