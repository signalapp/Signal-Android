/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import org.signal.registration.screens.quickrestore.QrState
import org.signal.registration.util.DebugLoggableModel

data class LinkAccountScreenState(
  val qrCodeState: QrState = QrState.Loading,
  val displayQrOverlay: Boolean = false
) : DebugLoggableModel()
