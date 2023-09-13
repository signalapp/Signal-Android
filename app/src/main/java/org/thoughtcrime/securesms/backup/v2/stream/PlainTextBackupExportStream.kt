/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.signal.core.util.Conversions
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import java.io.IOException
import java.io.OutputStream

/**
 * Writes backup frames to the wrapped stream in plain text. Only for testing!
 */
class PlainTextBackupExportStream(private val outputStream: OutputStream) : BackupExportStream {

  @Throws(IOException::class)
  override fun write(frame: Frame) {
    val frameBytes: ByteArray = frame.encode()
    val lengthBytes: ByteArray = Conversions.intToByteArray(frameBytes.size)

    outputStream.write(lengthBytes)
    outputStream.write(frameBytes)
  }
}
