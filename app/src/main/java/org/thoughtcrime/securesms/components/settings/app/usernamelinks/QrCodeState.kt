/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks

sealed class QrCodeState {
  /** QR code data exists and is available. */
  data class Present(val data: QrCodeData) : QrCodeState()

  /** QR code data does not exist. */
  object NotSet : QrCodeState()

  /** QR code data is in an indeterminate loading state. */
  object Loading : QrCodeState()
}
