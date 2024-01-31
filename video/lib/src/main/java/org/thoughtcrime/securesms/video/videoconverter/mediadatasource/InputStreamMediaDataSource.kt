/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.mediadatasource

import android.media.MediaDataSource
import androidx.annotation.RequiresApi
import org.signal.core.util.skipNBytesCompat
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Extend this class in order to be able to use the system media framework with any arbitrary [InputStream] of bytes.
 */
@RequiresApi(23)
abstract class InputStreamMediaDataSource : MediaDataSource() {
  private var lastPositionRead = -1L
  private var lastUsedInputStream: InputStream? = null
  private val sink = ByteArray(2048)

  @Throws(IOException::class)
  override fun readAt(position: Long, bytes: ByteArray?, offset: Int, length: Int): Int {
    if (position >= size || position < 0) {
      return -1
    }

    val inputStream = if (lastPositionRead > position || lastUsedInputStream == null) {
      lastUsedInputStream?.close()
      lastPositionRead = position
      createInputStream(position)
    } else {
      lastUsedInputStream!!
    }

    try {
      inputStream.skipNBytesCompat(position - lastPositionRead)
    } catch (e: EOFException) {
      return -1
    }

    var totalRead = 0
    while (totalRead < length) {
      val read: Int = inputStream.read(bytes, offset + totalRead, length - totalRead)
      if (read == -1) {
        return if (totalRead == 0) {
          -1
        } else {
          totalRead
        }
      }
      totalRead += read
    }
    lastPositionRead = totalRead + position
    lastUsedInputStream = inputStream
    return totalRead
  }

  override fun close() {
    lastUsedInputStream?.close()
  }

  abstract override fun getSize(): Long

  @Throws(IOException::class)
  abstract fun createInputStream(position: Long): InputStream
}
