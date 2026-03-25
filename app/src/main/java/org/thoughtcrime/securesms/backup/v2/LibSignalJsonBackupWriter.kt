/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.archive.proto.BackupInfo
import org.signal.archive.proto.Frame
import org.signal.archive.stream.BackupExportWriter
import org.signal.core.util.logging.Log
import org.signal.core.util.writeVarInt32
import org.signal.libsignal.messagebackup.BackupJsonExporter
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A [BackupExportWriter] that serializes frames to newline-delimited JSON (JSONL) using
 * libsignal's [BackupJsonExporter], which applies sanitization (strips disappearing messages
 * and view-once attachments) and optional validation.
 */
class LibSignalJsonBackupWriter(private val outputStream: OutputStream) : BackupExportWriter {

  private val TAG = Log.tag(LibSignalJsonBackupWriter::class)

  private var exporter: BackupJsonExporter? = null

  override fun write(header: BackupInfo) {
    val (newExporter, initialChunk) = BackupJsonExporter.start(header.encode())
    exporter = newExporter
    outputStream.write(initialChunk.toByteArray())
    outputStream.write("\n".toByteArray())
  }

  override fun write(frame: Frame) {
    val frameBytes = frame.encode()
    val buf = ByteArrayOutputStream(frameBytes.size + 5)
    buf.writeVarInt32(frameBytes.size)
    buf.write(frameBytes)

    val results = exporter!!.exportFrames(buf.toByteArray())
    for (result in results) {
      result.line?.let {
        outputStream.write(it.toByteArray())
        outputStream.write("\n".toByteArray())
      }
      result.errorMessage?.let {
        Log.w(TAG, "Frame validation warning: $it")
      }
    }
  }

  override fun close() {
    exporter?.use {
      val error = it.finishExport()
      if (error != null) {
        Log.w(TAG, "Backup export validation error: $error")
      }
    }
    outputStream.close()
  }
}
