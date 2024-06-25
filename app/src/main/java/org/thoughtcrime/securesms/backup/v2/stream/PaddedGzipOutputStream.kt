/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/**
 * GZIPs the content of the provided [outputStream], but also adds padding to the end of the stream using the same algorithm as [PaddingInputStream].
 * We do this to fit files into a smaller number of size buckets to avoid fingerprinting. And it turns out that bolting on zeros to the end of a GZIP stream is
 * fine, because GZIP is smart enough to ignore it. This means readers of this data don't have to do anything special.
 */
class PaddedGzipOutputStream private constructor(private val outputStream: SizeObservingOutputStream) : GZIPOutputStream(outputStream) {

  constructor(outputStream: OutputStream) : this(SizeObservingOutputStream(outputStream))

  override fun finish() {
    super.finish()

    val totalLength = outputStream.size
    val paddedSize: Long = PaddingInputStream.getPaddedSize(totalLength)
    val paddingToAdd: Int = (paddedSize - totalLength).toInt()

    outputStream.write(ByteArray(paddingToAdd))
  }

  /**
   * We need to know the size of the *compressed* stream to know how much padding to add at the end.
   */
  private class SizeObservingOutputStream(val wrapped: OutputStream) : FilterOutputStream(wrapped) {

    var size: Long = 0L
      private set

    override fun write(b: Int) {
      wrapped.write(b)
      size++
    }

    override fun write(b: ByteArray) {
      wrapped.write(b)
      size += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      wrapped.write(b, off, len)
      size += len
    }
  }
}
