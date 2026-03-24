/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.FilterInputStream
import java.io.InputStream
import javax.crypto.Mac

/**
 * Calculates a [Mac] as data is read from the target [InputStream].
 * To get the final MAC, read the [mac] property after the stream has been fully read.
 *
 * Example:
 * ```kotlin
 * val stream = MacInputStream(myStream, myMac)
 * stream.readFully()
 * val mac = stream.mac.doFinal()
 * ```
 */
class MacInputStream(val wrapped: InputStream, val mac: Mac) : FilterInputStream(wrapped) {
  override fun read(): Int {
    return wrapped.read().also { byte ->
      if (byte >= 0) {
        mac.update(byte.toByte())
      }
    }
  }

  override fun read(destination: ByteArray): Int {
    return read(destination, 0, destination.size)
  }

  override fun read(destination: ByteArray, offset: Int, length: Int): Int {
    return wrapped.read(destination, offset, length).also { bytesRead ->
      if (bytesRead > 0) {
        mac.update(destination, offset, bytesRead)
      }
    }
  }
}
