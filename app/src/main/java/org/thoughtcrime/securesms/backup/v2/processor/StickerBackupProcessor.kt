/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import androidx.annotation.WorkerThread
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Hex
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.StickerPack
import org.thoughtcrime.securesms.backup.v2.proto.StickerPackSticker
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTable.StickerPackRecordReader
import org.thoughtcrime.securesms.database.StickerTable.StickerRecordReader
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob

object StickerBackupProcessor {
  fun export(emitter: BackupFrameEmitter) {
    StickerPackRecordReader(SignalDatabase.stickers.allStickerPacks).use { reader ->
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

@WorkerThread
private fun getStickersFromDatabase(packId: String): List<StickerPackSticker> {
  val stickers: MutableList<StickerPackSticker> = java.util.ArrayList()

  SignalDatabase.stickers.getStickersForPack(packId).use { cursor ->
    val reader = StickerRecordReader(cursor)
    var record: StickerRecord? = reader.next
    while (record != null) {
      stickers.add(
        StickerPackSticker(
          emoji = record.emoji,
          id = record.stickerId
        )
      )
      record = reader.next
    }
  }
  return stickers
}

private fun StickerPackRecord.toBackupFrame(): Frame {
  val packIdBytes = Hex.fromStringCondensed(packId)
  val packKey = Hex.fromStringCondensed(packKey)
  val stickers = getStickersFromDatabase(packId)
  val pack = StickerPack(
    packId = packIdBytes.toByteString(),
    packKey = packKey.toByteString(),
    title = title.orElse(""),
    author = author.orElse(""),
    stickers = stickers
  )
  return Frame(stickerPack = pack)
}
