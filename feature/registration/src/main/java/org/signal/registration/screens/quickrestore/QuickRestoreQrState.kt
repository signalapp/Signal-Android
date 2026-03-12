/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

import org.signal.core.ui.compose.QrCodeData
import org.signal.registration.util.DebugLoggableModel

data class QuickRestoreQrState(
  val qrState: QrState = QrState.Loading,
  val isRegistering: Boolean = false,
  val showRegistrationError: Boolean = false,
  val errorMessage: String? = null
) : DebugLoggableModel()

sealed class QrState : DebugLoggableModel() {
  data object Loading : QrState()
  data class Loaded(val qrCodeData: QrCodeData) : QrState() {
    override fun toSafeString(): String {
      return "Loaded(qrCodeData=***)"
    }
  }
  data object Scanned : QrState()
  data object Failed : QrState()
}

