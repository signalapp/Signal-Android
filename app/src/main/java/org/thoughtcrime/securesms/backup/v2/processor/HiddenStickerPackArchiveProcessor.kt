/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import okio.ByteString.Companion.toByteString
import org.signal.archive.proto.Frame
import org.signal.archive.proto.StickerPack
import org.signal.archive.stream.BackupFrameEmitter
import org.signal.core.util.Hex
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.backup.v2.ExportSkips
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import java.io.IOException

private val TAG = Log.tag(HiddenStickerPackArchiveProcessor::class)

/**
 * Handles importing/exporting hidden [StickerPack] frames for an archive. For installed packs, see [InstalledStickerPackArchiveProcessor].
 */
object HiddenStickerPackArchiveProcessor {
  fun export(db: SignalDatabase, emitter: BackupFrameEmitter) {
    db.stickerTables.getAllHiddenStickerPacks().forEach { cursor ->
      val frame = toBackupFrame(
        packId = cursor.requireNonNullString(StickerTables.Pack.PACK_ID),
        packKey = cursor.requireNonNullString(StickerTables.Pack.PACK_KEY)
      ) ?: return@forEach
      emitter.emit(frame)
    }
  }

  fun import(stickerPack: StickerPack) {
    SignalDatabase.stickers.insertHiddenPack(
      packId = Hex.toStringCondensed(stickerPack.packId.toByteArray()),
      packKey = Hex.toStringCondensed(stickerPack.packKey.toByteArray())
    )
  }
}

private fun toBackupFrame(packId: String, packKey: String): Frame? {
  val packIdBytes = try {
    Hex.fromStringCondensed(packId)?.takeIf { it.size == 16 } ?: throw IOException("Incorrect length!")
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.invalidStickerPackId())
    return null
  }

  val packKeyBytes = try {
    Hex.fromStringCondensed(packKey)?.takeIf { it.size == 32 } ?: throw IOException("Incorrect length!")
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.invalidStickerPackKey())
    return null
  }

  val pack = StickerPack(
    packId = packIdBytes.toByteString(),
    packKey = packKeyBytes.toByteString()
  )
  return Frame(hiddenStickerPack = pack)
}
