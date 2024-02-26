/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.signal.core.util.writeVarInt32
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import java.io.IOException
import java.io.OutputStream

/**
 * Writes backup frames to the wrapped stream in plain text. Only for testing!
 */
class PlainTextBackupWriter(private val outputStream: OutputStream) : BackupExportWriter {

  @Throws(IOException::class)
  override fun write(header: BackupInfo) {
    val headerBytes: ByteArray = header.encode()

    outputStream.writeVarInt32(headerBytes.size)
    outputStream.write(headerBytes)
  }

  @Throws(IOException::class)
  override fun write(frame: Frame) {
    val frameBytes: ByteArray = frame.encode()

    outputStream.writeVarInt32(frameBytes.size)
    outputStream.write(frameBytes)
  }

  override fun close() {
    outputStream.close()
  }
}
