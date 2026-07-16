/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

import org.signal.core.ui.compose.QrCodeData

data class QuickRestoreQrState(
  val qrState: QrState = QrState.Loading,
  val isRegistering: Boolean = false,
  val showRegistrationError: Boolean = false,
  val errorMessage: String? = null
)

sealed class QrState {
  data object Loading : QrState()
  data class Loaded(val qrCodeData: QrCodeData) : QrState() {
    override fun toString(): String = "Loaded(qrCodeData=***)"
  }
  data object Scanned : QrState()
  data object Failed : QrState()
}
