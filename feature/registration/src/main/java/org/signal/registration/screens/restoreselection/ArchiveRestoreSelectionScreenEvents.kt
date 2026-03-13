/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import org.signal.registration.util.DebugLoggableModel

sealed class ArchiveRestoreSelectionScreenEvents : DebugLoggableModel() {
  data class RestoreOptionSelected(val option: ArchiveRestoreOption) : ArchiveRestoreSelectionScreenEvents()
  data object Skip : ArchiveRestoreSelectionScreenEvents()
  data object ConfirmSkip : ArchiveRestoreSelectionScreenEvents()
  data object DismissSkipWarning : ArchiveRestoreSelectionScreenEvents()
}
