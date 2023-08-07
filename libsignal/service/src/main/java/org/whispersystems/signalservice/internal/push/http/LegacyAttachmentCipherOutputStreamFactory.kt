/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http

import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Creates [AttachmentCipherOutputStream] using the provided [key] and [iv].
 *
 * [createFor] is straightforward, and is the legacy behavior.
 *
 * @property key
 * @property iv
 */
class LegacyAttachmentCipherOutputStreamFactory(private val key: ByteArray, private val iv: ByteArray) : OutputStreamFactory {
  @Throws(IOException::class)
  override fun createFor(wrap: OutputStream): DigestingOutputStream {
    return AttachmentCipherOutputStream(key, iv, wrap)
  }
}
