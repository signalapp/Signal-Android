/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Hex
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.StickerPack
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTable.StickerPackRecordReader
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob

object StickerBackupProcessor {
  fun export(db: SignalDatabase, emitter: BackupFrameEmitter) {
    StickerPackRecordReader(db.stickerTable.allStickerPacks).use { reader ->
      var record: StickerPackRecord? = reader.next
      while (record != null) {
        if (record.isInstalled) {
          val frame = record.toBackupFrame()
          emitter.emit(frame)
        }
        record = reader.next
      }
    }
  }

  fun import(stickerPack: StickerPack) {
    AppDependencies.jobManager.add(
      StickerPackDownloadJob.forInstall(Hex.toStringCondensed(stickerPack.packId.toByteArray()), Hex.toStringCondensed(stickerPack.packKey.toByteArray()), false)
    )
  }
}

private fun StickerPackRecord.toBackupFrame(): Frame {
  val packIdBytes = Hex.fromStringCondensed(packId)
  val packKey = Hex.fromStringCondensed(packKey)
  val pack = StickerPack(
    packId = packIdBytes.toByteString(),
    packKey = packKey.toByteString()
  )
  return Frame(stickerPack = pack)
}
