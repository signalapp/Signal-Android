/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.signal.libsignal.protocol.InvalidMessageException
import org.whispersystems.signalservice.internal.util.Util
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Mac
import kotlin.math.max

/**
 * This is meant as a helper stream to go along with [org.signal.libsignal.protocol.incrementalmac.IncrementalMacInputStream].
 * That class does not validate the overall digest, nor the overall MAC. This class does that for us.
 *
 * To use, wrap the IncremtalMacInputStream around this class, and then this class should wrap the lowest-level data stream.
 */
class IncrementalMacAdditionalValidationsInputStream(
  wrapped: InputStream,
  fileLength: Long,
  private val mac: Mac,
  private val theirDigest: ByteArray
) : FilterInputStream(wrapped) {

  private val digest: MessageDigest = MessageDigest.getInstance("SHA256")
  private val macLength: Int = mac.macLength
  private val macBuffer: ByteArray = ByteArray(macLength)

  private var validated = false
  private var bytesRemaining: Int = fileLength.toInt()
  private var macBufferPosition: Int = 0

  override fun read(): Int {
    throw UnsupportedOperationException()
  }

  /**
   * We need to be very careful to keep track of what data is part of the MAC and what isn't, based on how far we've read into the file.
   * As a recap, the digest needs to ingest the entire file, while the MAC needs to ingest everything except the last [macLength] bytes.
   * (Because the last [macLength] bytes represents the MAC we're going to verify against.)
   *
   * The wrapping stream may request the full length of the file, so we need to do some bookkeeping to remember the last [macLength] bytes
   * for comparison purposes during [validate] while not ingesting them into the MAC that we're calculating.
   */
  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    val bytesRead = super.read(buffer, offset, length)
    if (bytesRead == -1) {
      validate()
      return bytesRead
    }

    bytesRemaining -= bytesRead

    // This indicates we've read into the last [macLength] bytes of the file, so we need to start our bookkeeping
    if (bytesRemaining < macLength) {
      val bytesOfMacRead = macLength - bytesRemaining
      val newBytesOfMacRead = bytesOfMacRead - macBufferPosition

      // There's a possibility that the reader has only partially read the last [macLength] bytes, so we need to keep track of a position in our
      // MAC buffer and copy over just the new parts we've read
      if (newBytesOfMacRead > 0) {
        System.arraycopy(buffer, offset + bytesRead - newBytesOfMacRead, macBuffer, macBufferPosition, newBytesOfMacRead)
        macBufferPosition += newBytesOfMacRead
      }

      // Even though we're reading into the MAC, many of the bytes read in this method call could be non-MAC bytes, so we need to copy
      // those over, while excluding the bytes that are part of the MAC.
      val bytesOfNonMacRead = max(0, bytesRead - bytesOfMacRead)
      if (bytesOfNonMacRead > 0) {
        mac.update(buffer, offset, bytesOfNonMacRead)
      }
    } else {
      mac.update(buffer, offset, bytesRead)
    }

    digest.update(buffer, offset, bytesRead)

    if (bytesRemaining == 0) {
      validate()
    }

    return bytesRead
  }

  override fun close() {
    // We only want to validate the digest if we've otherwise read the entire stream.
    // It's valid to close the stream early, and in this case, we don't want to force reading the whole rest of the stream.
    if (bytesRemaining > macLength) {
      super.close()
      return
    }

    if (bytesRemaining > 0) {
      Util.readFullyAsBytes(this)
    }

    super.close()
  }

  private fun validate() {
    if (validated) {
      return
    }
    validated = true

    val ourMac = mac.doFinal()
    val theirMac = macBuffer

    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw InvalidMessageException("MAC doesn't match!")
    }

    val ourDigest = digest.digest()
    if (!MessageDigest.isEqual(ourDigest, theirDigest)) {
      throw InvalidMessageException("Digest doesn't match!")
    }
  }
}
