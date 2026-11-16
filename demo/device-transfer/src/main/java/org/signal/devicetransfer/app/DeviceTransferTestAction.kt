/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

sealed interface DeviceTransferTestAction {

  data class RequestPermissions(val permissions: List<String>) : DeviceTransferTestAction
}
