/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.FilterOutputStream
import java.io.OutputStream
import javax.crypto.Mac

/**
 * Calculates a [Mac] as data is written to the target [OutputStream].
 * To get the final MAC, read the [mac] property after the stream has been fully written.
 *
 * Example:
 * ```kotlin
 * val stream = MacOutputStream(myStream, myMac)
 * // write data to stream
 * val mac = stream.mac.doFinal()
 * ```
 */
class MacOutputStream(val wrapped: OutputStream, val mac: Mac) : FilterOutputStream(wrapped) {
  override fun write(byte: Int) {
    wrapped.write(byte)
    mac.update(byte.toByte())
  }

  override fun write(data: ByteArray) {
    write(data, 0, data.size)
  }

  override fun write(data: ByteArray, offset: Int, length: Int) {
    wrapped.write(data, offset, length)
    mac.update(data, offset, length)
  }
}
