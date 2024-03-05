/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.core.util.Base64

/**
 * Safe typing around a mediaId, which is a 15-byte array.
 */
@JvmInline
value class MediaId(val value: ByteArray) {

  init {
    require(value.size == 15) { "MediaId must be 15 bytes!" }
  }

  override fun toString(): String {
    return Base64.encodeUrlSafeWithPadding(value)
  }
}
