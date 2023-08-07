/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http

import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacOutputStream
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Creates [AttachmentCipherOutputStream] using the provided [key] and [iv].
 *
 * [createIncrementalFor] first wraps the stream in an [IncrementalMacOutputStream] to calculate MAC digests on chunks as the stream is written to.
 *
 * @property key
 * @property iv
 */
class IncrementalAttachmentCipherOutputStreamFactory(private val key: ByteArray, private val iv: ByteArray) : IncrementalOutputStreamFactory {

  private val legacyDelegate = LegacyAttachmentCipherOutputStreamFactory(key, iv)

  companion object {
    private const val AES_KEY_LENGTH = 32
  }

  @Throws(IOException::class)
  override fun createIncrementalFor(wrap: OutputStream?, length: Long, incrementalDigestOut: OutputStream?): DigestingOutputStream {
    if (length > Int.MAX_VALUE) {
      throw IllegalArgumentException("Attachment length overflows int!")
    }

    val privateKey = key.sliceArray(AES_KEY_LENGTH until key.size)
    val chunkSizeChoice = ChunkSizeChoice.inferChunkSize(length.toInt().coerceAtLeast(1))
    val incrementalStream = IncrementalMacOutputStream(wrap, privateKey, chunkSizeChoice, incrementalDigestOut)
    return legacyDelegate.createFor(incrementalStream)
  }
}
