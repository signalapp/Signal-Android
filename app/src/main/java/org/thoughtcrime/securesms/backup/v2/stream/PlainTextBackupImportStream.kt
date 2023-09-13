/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.signal.core.util.Conversions
import org.signal.core.util.readNBytesOrThrow
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import java.io.EOFException
import java.io.InputStream

/**
 * Reads a plaintext backup import stream one frame at a time.
 */
class PlainTextBackupImportStream(val inputStream: InputStream) : BackupImportStream, Iterator<Frame> {

  var next: Frame? = null

  init {
    next = read()
  }

  override fun hasNext(): Boolean {
    return next != null
  }

  override fun next(): Frame {
    next?.let { out ->
      next = read()
      return out
    } ?: throw NoSuchElementException()
  }

  override fun read(): Frame? {
    try {
      val lengthBytes: ByteArray = inputStream.readNBytesOrThrow(4)
      val length = Conversions.byteArrayToInt(lengthBytes)

      val frameBytes: ByteArray = inputStream.readNBytesOrThrow(length)
      val frame: Frame = Frame.ADAPTER.decode(frameBytes)

      return frame
    } catch (e: EOFException) {
      return null
    }
  }
}
