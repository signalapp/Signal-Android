/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Hex
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ExportSkips
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.StickerPack
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTable
import org.thoughtcrime.securesms.database.StickerTable.StickerPackRecordReader
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import java.io.IOException

private val TAG = Log.tag(StickerArchiveProcessor::class)

/**
 * Handles importing/exporting [StickerPack] frames for an archive.
 */
object StickerArchiveProcessor {
  fun export(db: SignalDatabase, emitter: BackupFrameEmitter) {
    StickerPackRecordReader(db.stickerTable.getAllStickerPacks()).use { reader ->
      var record: StickerPackRecord? = null
      while (reader.getNext()?.let { record = it } != null) {
        if (record!!.isInstalled) {
          val frame = record!!.toBackupFrame() ?: continue
          emitter.emit(frame)
        }
      }
    }
  }

  fun import(stickerPack: StickerPack) {
    SignalDatabase.rawDatabase
      .insertInto(StickerTable.TABLE_NAME)
      .values(
        StickerTable.PACK_ID to Hex.toStringCondensed(stickerPack.packId.toByteArray()),
        StickerTable.PACK_KEY to Hex.toStringCondensed(stickerPack.packKey.toByteArray()),
        StickerTable.PACK_TITLE to "",
        StickerTable.PACK_AUTHOR to "",
        StickerTable.INSTALLED to 1,
        StickerTable.COVER to 1,
        StickerTable.EMOJI to "",
        StickerTable.CONTENT_TYPE to "",
        StickerTable.FILE_PATH to ""
      )
      .run(SQLiteDatabase.CONFLICT_IGNORE)
  }
}

private fun StickerPackRecord.toBackupFrame(): Frame? {
  val packIdBytes = try {
    Hex.fromStringCondensed(this.packId)?.takeIf { it.size == 16 } ?: throw IOException("Incorrect length!")
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.invalidStickerPackId())
    return null
  }

  val packKeyBytes = try {
    Hex.fromStringCondensed(this.packKey)?.takeIf { it.size == 32 } ?: throw IOException("Incorrect length!")
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.invalidStickerPackKey())
    return null
  }

  val pack = StickerPack(
    packId = packIdBytes.toByteString(),
    packKey = packKeyBytes.toByteString()
  )
  return Frame(stickerPack = pack)
}
