/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restore

import org.signal.core.ui.compose.QrCodeData

sealed class QrState {
  data object Loading : QrState()
  data class Loaded(val qrCodeData: QrCodeData) : QrState()
  data object Scanned : QrState()
  data object Failed : QrState()
}

data class RestoreViaQrState(
  val qrState: QrState = QrState.Loading,
  val isRegistering: Boolean = false,
  val showRegistrationError: Boolean = false,
  val errorMessage: String? = null
)
