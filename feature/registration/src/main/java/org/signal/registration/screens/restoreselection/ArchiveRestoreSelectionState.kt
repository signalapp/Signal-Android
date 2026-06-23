/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import org.signal.registration.util.DebugLoggableModel

data class ArchiveRestoreSelectionState(
  val restoreOptions: List<ArchiveRestoreOption> = emptyList(),
  val showSkipWarningDialog: Boolean = false,
  /** Token that, if present, indicates that the user did a quick restore, and we should hit a network endpoint to indicate our restore selection.  */
  val restoreMethodToken: String? = null
) : DebugLoggableModel() {
  val showSkipButton: Boolean get() = ArchiveRestoreOption.None !in restoreOptions
}
