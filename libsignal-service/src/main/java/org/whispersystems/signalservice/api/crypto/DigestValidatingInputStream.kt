/*
 * Copyright (C) 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.signal.libsignal.protocol.InvalidMessageException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * An InputStream that enforces hash validation by calculating a digest as data is read
 * and verifying it against an expected hash when the stream is fully consumed.
 *
 * Important: The validation only occurs if you read the entire stream.
 *
 * @param inputStream The underlying InputStream to read from
 * @param digest The MessageDigest instance to use for hash calculation
 * @param expectedHash The expected hash value to validate against
 */
class DigestValidatingInputStream(
  inputStream: InputStream,
  private val digest: MessageDigest,
  private val expectedHash: ByteArray
) : FilterInputStream(inputStream) {

  var validationAttempted = false
    private set

  @Throws(IOException::class)
  override fun read(): Int {
    val byte = super.read()
    if (byte != -1) {
      digest.update(byte.toByte())
    } else {
      validateDigest()
    }
    return byte
  }

  @Throws(IOException::class)
  override fun read(buffer: ByteArray): Int {
    return read(buffer, 0, buffer.size)
  }

  @Throws(IOException::class)
  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    val bytesRead = super.read(buffer, offset, length)
    if (bytesRead > 0) {
      digest.update(buffer, offset, bytesRead)
    } else if (bytesRead == -1) {
      validateDigest()
    }
    return bytesRead
  }

  /**
   * Validates the calculated digest against the expected hash.
   * Throws InvalidCiphertextException if they don't match.
   */
  @Throws(InvalidMessageException::class)
  private fun validateDigest() {
    if (validationAttempted) {
      return
    }
    validationAttempted = true

    val calculatedHash = digest.digest()
    if (!MessageDigest.isEqual(calculatedHash, expectedHash)) {
      throw InvalidMessageException("Calculated digest does not match expected hash!")
    }
  }
}
