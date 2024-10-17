/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.readVarInt32
import org.thoughtcrime.securesms.backup.v2.local.proto.FilesFrame
import java.io.EOFException
import java.io.InputStream

/**
 * Reads [FilesFrame] protos encoded with their length.
 */
class ArchivedFilesReader(private val inputStream: InputStream) : Iterator<FilesFrame>, AutoCloseable {

  private var next: FilesFrame? = null

  init {
    next = read()
  }

  override fun hasNext(): Boolean {
    return next != null
  }

  override fun next(): FilesFrame {
    next?.let { out ->
      next = read()
      return out
    } ?: throw NoSuchElementException()
  }

  private fun read(): FilesFrame? {
    try {
      val length = inputStream.readVarInt32().also { if (it < 0) return null }
      val frameBytes: ByteArray = inputStream.readNBytesOrThrow(length)

      return FilesFrame.ADAPTER.decode(frameBytes)
    } catch (e: EOFException) {
      return null
    }
  }

  override fun close() {
    inputStream.close()
  }
}
