/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import org.signal.registration.RegistrationFlowState

sealed class ArchiveRestoreSelectionScreenEvents {
  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : ArchiveRestoreSelectionScreenEvents()

  data class RestoreOptionSelected(val option: ArchiveRestoreOption) : ArchiveRestoreSelectionScreenEvents()

  data object ConfirmSkip : ArchiveRestoreSelectionScreenEvents()

  data object DismissSkipWarning : ArchiveRestoreSelectionScreenEvents()
}
