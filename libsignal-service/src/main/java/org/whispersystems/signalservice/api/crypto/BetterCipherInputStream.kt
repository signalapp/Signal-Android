/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.annotation.Nonnull
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.ShortBufferException
import kotlin.math.min

/**
 * This is similar to [javax.crypto.CipherInputStream], but it fixes various issues, including proper error propagation,
 * and proper handling of boundary conditions.
 */
class BetterCipherInputStream(
  inputStream: InputStream,
  val cipher: Cipher
) : FilterInputStream(inputStream) {

  private var done = false
  private var overflowBuffer: ByteArray? = null

  @Throws(IOException::class)
  override fun read(): Int {
    val buffer = ByteArray(1)
    var read: Int = read(buffer)
    while (read == 0) {
      read = read(buffer)
    }

    if (read == -1) {
      return read
    }

    return buffer[0].toInt() and 0xFF
  }

  @Throws(IOException::class)
  override fun read(@Nonnull buffer: ByteArray): Int {
    return read(buffer, 0, buffer.size)
  }

  @Throws(IOException::class)
  override fun read(@Nonnull buffer: ByteArray, offset: Int, length: Int): Int {
    return if (!done) {
      readIncremental(buffer, offset, length)
    } else {
      -1
    }
  }

  override fun markSupported(): Boolean = false

  @Throws(IOException::class)
  override fun skip(byteCount: Long): Long {
    val buffer = ByteArray(4096)
    var skipped = 0L

    while (skipped < byteCount) {
      val remaining = byteCount - skipped
      val read = read(buffer, 0, remaining.toInt())

      skipped += read.toLong()
    }

    return skipped
  }

  @Throws(IOException::class)
  private fun readIncremental(outputBuffer: ByteArray, originalOffset: Int, originalLength: Int): Int {
    var offset = originalOffset
    var length = originalLength
    var readLength = 0

    overflowBuffer?.let { overflow ->
      if (overflow.size > length) {
        overflow.copyInto(destination = outputBuffer, destinationOffset = offset, endIndex = length)
        overflowBuffer = overflow.copyOfRange(fromIndex = length, toIndex = overflow.size)
        return length
      } else if (overflow.size == length) {
        overflow.copyInto(destination = outputBuffer, destinationOffset = offset)
        overflowBuffer = null
        return length
      } else {
        overflow.copyInto(destination = outputBuffer, destinationOffset = offset)
        readLength += overflow.size
        offset += readLength
        length -= readLength
        overflowBuffer = null
      }
    }

    val ciphertextBuffer = ByteArray(length)
    val ciphertextRead = super.read(ciphertextBuffer, 0, ciphertextBuffer.size)

    if (ciphertextRead == -1) {
      return readFinal(outputBuffer, offset, length)
    }

    try {
      var plaintextLength = cipher.getOutputSize(ciphertextRead)

      if (plaintextLength <= length) {
        readLength += cipher.update(ciphertextBuffer, 0, ciphertextRead, outputBuffer, offset)
        return readLength
      }

      val plaintextBuffer = ByteArray(plaintextLength)
      plaintextLength = cipher.update(ciphertextBuffer, 0, ciphertextRead, plaintextBuffer, 0)
      if (plaintextLength <= length) {
        plaintextBuffer.copyInto(destination = outputBuffer, destinationOffset = offset, endIndex = plaintextLength)
        readLength += plaintextLength
      } else {
        plaintextBuffer.copyInto(destination = outputBuffer, destinationOffset = offset, endIndex = length)
        overflowBuffer = plaintextBuffer.copyOfRange(fromIndex = length, toIndex = plaintextLength)
        readLength += length
      }
      return readLength
    } catch (e: ShortBufferException) {
      throw AssertionError(e)
    }
  }

  @Throws(IOException::class)
  private fun readFinal(buffer: ByteArray, offset: Int, length: Int): Int {
    try {
      val internal = ByteArray(buffer.size)
      val actualLength = min(length, cipher.doFinal(internal, 0))
      internal.copyInto(destination = buffer, destinationOffset = offset, endIndex = actualLength)

      done = true
      return actualLength
    } catch (e: IllegalBlockSizeException) {
      throw IOException(e)
    } catch (e: BadPaddingException) {
      throw IOException(e)
    } catch (e: ShortBufferException) {
      throw IOException(e)
    }
  }
}
