/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import org.signal.core.util.writeVarInt32
import org.thoughtcrime.securesms.backup.v2.local.proto.FilesFrame
import java.io.IOException
import java.io.OutputStream

/**
 * Write [FilesFrame] protos encoded with their length.
 */
class ArchivedFilesWriter(private val output: OutputStream) : AutoCloseable {

  @Throws(IOException::class)
  fun write(frame: FilesFrame) {
    val bytes = frame.encode()
    output.writeVarInt32(bytes.size)
    output.write(bytes)
  }

  override fun close() {
    output.close()
  }
}
