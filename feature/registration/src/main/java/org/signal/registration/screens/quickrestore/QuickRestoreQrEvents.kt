/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

sealed class QuickRestoreQrEvents {
  data object RetryQrCode : QuickRestoreQrEvents()
  data object Cancel : QuickRestoreQrEvents()
  data object DismissError : QuickRestoreQrEvents()
}
