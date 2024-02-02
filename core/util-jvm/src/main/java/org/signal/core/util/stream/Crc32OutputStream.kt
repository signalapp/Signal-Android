/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * A simple pass-through stream that calculates a CRC32 as data is written to the target [OutputStream].
 */
class Crc32OutputStream(private val wrapped: OutputStream) : FilterOutputStream(wrapped) {
  private val crc32 = CRC32()

  val currentCrc32: Long
    get() = crc32.value

  override fun write(byte: Int) {
    wrapped.write(byte)
    crc32.update(byte)
  }

  override fun write(data: ByteArray) {
    write(data, 0, data.size)
  }

  override fun write(data: ByteArray, offset: Int, length: Int) {
    wrapped.write(data, offset, length)
    crc32.update(data, offset, length)
  }
}
