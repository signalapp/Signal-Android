/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import com.google.common.io.CountingInputStream
import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.readVarInt32
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import java.io.EOFException
import java.io.InputStream

/**
 * Reads a plaintext backup import stream one frame at a time.
 */
class PlainTextBackupReader(val dataStream: InputStream, val length: Long) : BackupImportReader {

  val backupInfo: BackupInfo?
  var next: Frame? = null
  val inputStream: CountingInputStream

  init {
    inputStream = CountingInputStream(dataStream)
    backupInfo = readHeader()
    next = read()
  }

  override fun getHeader(): BackupInfo? {
    return backupInfo
  }

  override fun getBytesRead() = inputStream.count

  override fun getStreamLength() = length

  override fun hasNext(): Boolean {
    return next != null
  }

  override fun next(): Frame {
    next?.let { out ->
      next = read()
      return out
    } ?: throw NoSuchElementException()
  }

  override fun close() {
    inputStream.close()
  }

  private fun readHeader(): BackupInfo? {
    try {
      val length = inputStream.readVarInt32().takeIf { it >= 0 } ?: return null
      val headerBytes: ByteArray = inputStream.readNBytesOrThrow(length)

      return BackupInfo.ADAPTER.decode(headerBytes)
    } catch (e: EOFException) {
      return null
    }
  }

  private fun read(): Frame? {
    try {
      val length = inputStream.readVarInt32().also { if (it < 0) return null }
      val frameBytes: ByteArray = inputStream.readNBytesOrThrow(length)

      return Frame.ADAPTER.decode(frameBytes)
    } catch (e: EOFException) {
      return null
    }
  }
}
