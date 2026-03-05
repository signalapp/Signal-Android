/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import org.signal.core.util.drain
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * An input stream that will read all but the last [trimSize] bytes of the stream.
 *
 * Important: we have to keep a buffer of size [trimSize] to ensure that we can avoid reading it.
 * That means you should avoid using this for very large values of [trimSize].
 *
 * @param drain If true, the stream will be drained when it reaches the end (but bytes won't be returned). This is useful for ensuring that the underlying
 *   stream is fully consumed.
 */
class TrimmingInputStream(
  private val inputStream: InputStream,
  private val trimSize: Int,
  private val drain: Boolean = false
) : FilterInputStream(inputStream) {

  private val trimBuffer = ByteArray(trimSize)
  private var trimBufferSize: Int = 0
  private var streamEnded = false
  private var hasDrained = false

  private var internalBuffer = ByteArray(4096)
  private var internalBufferPosition: Int = 0
  private var internalBufferSize: Int = 0

  @Throws(IOException::class)
  override fun read(): Int {
    val singleByteBuffer = ByteArray(1)
    val bytesRead = read(singleByteBuffer, 0, 1)
    return if (bytesRead == -1) {
      -1
    } else {
      singleByteBuffer[0].toInt() and 0xFF
    }
  }

  @Throws(IOException::class)
  override fun read(b: ByteArray): Int {
    return read(b, 0, b.size)
  }

  /**
   * The general strategy is that we do bulk reads into an internal buffer (just for perf reasons), and then when new bytes are requested,
   * we fill up a buffer of size [trimSize] with the most recent bytes, and then return the oldest byte from that buffer.
   *
   * This ensures that the last [trimSize] bytes are never returned, while still returning the rest of the bytes.
   *
   * When we hit the end of the stream, we stop returning bytes.
   */
  @Throws(IOException::class)
  override fun read(outputBuffer: ByteArray, outputOffset: Int, readLength: Int): Int {
    if (streamEnded) {
      return -1
    }

    if (trimSize == 0) {
      return super.read(outputBuffer, outputOffset, readLength)
    }

    var outputCount = 0

    while (outputCount < readLength) {
      val nextByte = readNextByte()

      if (nextByte == -1) {
        streamEnded = true
        drainIfNecessary()
        break
      }

      if (trimBufferSize < trimSize) {
        // Still filling the buffer - can't output anything yet
        trimBuffer[trimBufferSize] = nextByte.toByte()
        trimBufferSize++
      } else {
        // Buffer is full - output the oldest byte and add the new one
        outputBuffer[outputOffset + outputCount] = trimBuffer[0]
        outputCount++

        // Shift buffer left and add new byte at the end. In practice, this is a tiny array and copies should be fast.
        System.arraycopy(trimBuffer, 1, trimBuffer, 0, trimSize - 1)
        trimBuffer[trimSize - 1] = nextByte.toByte()
      }
    }

    return if (outputCount == 0) {
      drainIfNecessary()
      -1
    } else {
      outputCount
    }
  }

  @Throws(IOException::class)
  override fun skip(skipCount: Long): Long {
    if (skipCount <= 0) return 0

    var totalSkipped = 0L
    val buffer = ByteArray(8192)

    while (totalSkipped < skipCount) {
      val toRead = min((skipCount - totalSkipped).toInt(), buffer.size)
      val bytesRead = read(buffer, 0, toRead)
      if (bytesRead == -1) {
        break
      }
      totalSkipped += bytesRead
    }

    return totalSkipped
  }

  private fun readNextByte(): Int {
    val hitEndOfStream = if (internalBufferPosition >= internalBufferSize) {
      internalBufferPosition = 0
      internalBufferSize = super.read(internalBuffer, 0, internalBuffer.size)
      internalBufferSize <= 0
    } else {
      false
    }

    if (hitEndOfStream) {
      drainIfNecessary()
      return -1
    }

    return internalBuffer[internalBufferPosition++].toInt() and 0xFF
  }

  private fun drainIfNecessary() {
    if (drain && !hasDrained) {
      inputStream.drain()
      hasDrained = true
    }
  }
}
