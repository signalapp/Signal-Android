/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.mediadatasource

import android.media.MediaDataSource
import java.io.IOException
import java.io.InputStream

/**
 * Extend this class in order to be able to use the system media framework with any arbitrary [InputStream] of bytes.
 *
 * The stream is kept open between reads and only re-created when a read is not contiguous with the previous one.
 */
abstract class InputStreamMediaDataSource : MediaDataSource() {

  private var inputStream: InputStream? = null
  private var inputStreamPosition: Long = -1

  @Throws(IOException::class)
  @Synchronized
  override fun readAt(position: Long, bytes: ByteArray?, offset: Int, length: Int): Int {
    if (position >= size) {
      return -1
    }

    val stream = streamAt(position)

    var totalRead = 0
    try {
      while (totalRead < length) {
        val read: Int = stream.read(bytes, offset + totalRead, length - totalRead)
        if (read == -1) {
          break
        }
        totalRead += read
      }
    } catch (e: IOException) {
      closeInputStream()
      throw e
    }

    inputStreamPosition += totalRead

    return if (totalRead == 0) {
      -1
    } else {
      totalRead
    }
  }

  @Synchronized
  override fun close() {
    closeInputStream()
  }

  @Throws(IOException::class)
  abstract fun createInputStream(position: Long): InputStream

  private fun streamAt(position: Long): InputStream {
    val existing = inputStream
    if (existing != null && inputStreamPosition == position) {
      return existing
    }

    closeInputStream()

    return createInputStream(position).also {
      inputStream = it
      inputStreamPosition = position
    }
  }

  private fun closeInputStream() {
    try {
      inputStream?.close()
    } catch (_: IOException) {
    } finally {
      inputStream = null
      inputStreamPosition = -1
    }
  }
}
