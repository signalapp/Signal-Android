/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restore

sealed class RestoreViaQrScreenEvents {
  data object RetryQrCode : RestoreViaQrScreenEvents()
  data object Cancel : RestoreViaQrScreenEvents()
  data object UseProxy : RestoreViaQrScreenEvents()
  data object DismissError : RestoreViaQrScreenEvents()
}
