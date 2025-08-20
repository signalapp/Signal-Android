/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.jetbrains.annotations.VisibleForTesting
import org.signal.core.util.stream.LimitedInputStream
import org.signal.core.util.stream.TrimmingInputStream
import org.signal.libsignal.protocol.InvalidMessageException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Mac

/**
 * An InputStream that validates a MAC appended to the end of the stream data.
 * This stream will not exclude the MAC from the data it reads, meaning that you may want to pair this with a [LimitedInputStream] or a [TrimmingInputStream]
 * if you don't want to read that data to be a part of it.
 *
 * Important: The MAC is only validated once the stream has been fully read.
 *
 * @param inputStream The underlying InputStream to read from
 * @param mac The Mac instance to use for validation
 */
class MacValidatingInputStream(
  inputStream: InputStream,
  private val mac: Mac
) : FilterInputStream(inputStream) {

  private val macBuffer = ByteArray(mac.macLength)
  private val macLength = mac.macLength
  private var macBufferPosition = 0
  private var streamEnded = false

  @VisibleForTesting
  var validationAttempted = false
    private set

  @Throws(IOException::class)
  override fun read(): Int {
    val singleByteBuffer = ByteArray(1)
    val bytesRead = read(singleByteBuffer, 0, 1)
    return if (bytesRead == -1) -1 else singleByteBuffer[0].toInt() and 0xFF
  }

  @Throws(IOException::class)
  override fun read(b: ByteArray): Int {
    return read(b, 0, b.size)
  }

  @Throws(IOException::class)
  override fun read(outputBuffer: ByteArray, outputOffset: Int, readLength: Int): Int {
    if (streamEnded) {
      return -1
    }

    val bytesRead = super.read(outputBuffer, outputOffset, readLength)

    if (bytesRead == -1) {
      // End of stream - check if we have enough data for MAC validation
      if (macBufferPosition < macLength) {
        throw InvalidMessageException("Stream ended before MAC could be read. Expected $macLength bytes, got $macBufferPosition")
      }
      validateMacAndMarkStreamEnded()
      return -1
    }

    // If we've read more than `macLength` bytes, we can just snag the last `macLength` bytes and digest the rest
    if (bytesRead >= macLength) {
      // Before replacing the macBuffer, process any pre-existing data
      if (macBufferPosition > 0) {
        mac.update(macBuffer, 0, macBufferPosition)
        macBufferPosition = 0
      }

      // Copy the last `macLength` bytes into the macBuffer
      outputBuffer.copyInto(destination = macBuffer, destinationOffset = 0, startIndex = outputOffset + bytesRead - macLength, endIndex = outputOffset + bytesRead)
      macBufferPosition = macLength

      // Update the mac with the bytes that are not part of the MAC
      if (bytesRead > macLength) {
        mac.update(outputBuffer, outputOffset, bytesRead - macLength)
      }
    } else {
      val totalBytesAvailable = macBufferPosition + bytesRead

      // If the new bytes we've read don't overflow the buffer, we can just append them, and none of them will be digested
      if (totalBytesAvailable <= macLength) {
        outputBuffer.copyInto(destination = macBuffer, destinationOffset = macBufferPosition, startIndex = outputOffset, endIndex = outputOffset + bytesRead)
        macBufferPosition = totalBytesAvailable
      } else {
        // If we have more bytes than we can hold in the buffer, keep the last `macLength` bytes and digest the rest

        // We know that `bytesRead` is less than `macLength`, so we know all of `bytesRead` should go into the buffer
        // And we know that the buffer usage + `bytesRead` is greater than `macLength`, so we're guaranteed to be able to digest the first chunk of the buffer.
        // We also know that there can't possibly be 0 bytes in the buffer because of how the math of those conditions works out.

        val bytesToDigest = totalBytesAvailable - macLength

        val bytesOfBufferToDigest = minOf(macBufferPosition, bytesToDigest)
        val bytesOfReadToDigest = bytesToDigest - bytesOfBufferToDigest

        mac.update(macBuffer, 0, bytesOfBufferToDigest)
        macBuffer.copyInto(destination = macBuffer, destinationOffset = 0, startIndex = bytesOfBufferToDigest, endIndex = macBufferPosition)
        macBufferPosition -= bytesOfBufferToDigest

        if (bytesOfReadToDigest > 0) {
          mac.update(outputBuffer, outputOffset, bytesOfReadToDigest)
        }

        val bytesOfReadRemaining = bytesRead - bytesOfReadToDigest
        if (bytesOfReadRemaining > 0) {
          outputBuffer.copyInto(destination = macBuffer, destinationOffset = macBufferPosition, startIndex = outputOffset + bytesOfReadToDigest, endIndex = outputOffset + bytesRead)
          macBufferPosition += bytesOfReadRemaining
        }
      }
    }

    return bytesRead
  }

  @Throws(InvalidMessageException::class)
  private fun validateMacAndMarkStreamEnded() {
    if (validationAttempted) {
      return
    }
    validationAttempted = true
    streamEnded = true

    val calculatedMac = mac.doFinal()
    if (!MessageDigest.isEqual(calculatedMac, macBuffer)) {
      throw InvalidMessageException("MAC validation failed!")
    }
  }

  private fun minOf(a: Int, b: Int): Int = if (a < b) a else b
}
