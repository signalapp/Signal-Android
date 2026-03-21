/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive.local

import org.signal.archive.local.proto.FilesFrame
import org.signal.core.util.writeVarInt32
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
