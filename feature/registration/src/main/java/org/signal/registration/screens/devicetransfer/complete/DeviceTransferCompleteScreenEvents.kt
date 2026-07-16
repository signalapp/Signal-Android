/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

sealed class DeviceTransferCompleteScreenEvents {
  data object ContinueClicked : DeviceTransferCompleteScreenEvents()
}
