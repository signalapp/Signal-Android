/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import org.signal.core.util.censor

sealed interface SignalLoginViewDetailsForManualSaveScreenActions {
  /** Launch the system document picker so the user can choose where to save the login PDF. */
  data object LaunchSaveAsPdf : SignalLoginViewDetailsForManualSaveScreenActions

  /** Copy the specified text to the clipboard. */
  data class CopyTextToClipboard(val text: String) : SignalLoginViewDetailsForManualSaveScreenActions {
    override fun toString(): String = "CopyTextToClipboard(text=${text.censor()})"
  }
}
