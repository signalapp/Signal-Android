/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import okio.ByteString.Companion.toByteString
import org.signal.archive.proto.FavoriteStickerList
import org.signal.archive.proto.Frame
import org.signal.archive.stream.BackupFrameEmitter
import org.signal.core.models.database.StickerRecord
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ExportSkips
import org.thoughtcrime.securesms.backup.v2.ImportSkips
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import java.io.IOException

/**
 * Handles importing/exporting the [FavoriteStickerList] frame for an archive.
 */
object FavoriteStickerArchiveProcessor {

  private val TAG = Log.tag(FavoriteStickerArchiveProcessor::class)

  private const val PACK_ID_LENGTH = 16
  private const val PACK_KEY_LENGTH = 32

  fun export(db: SignalDatabase, emitter: BackupFrameEmitter) {
    val favorites = StickerTables.StickerRecordReader(db.stickerTables.getFavoriteStickersForBackup()).use { reader ->
      reader
        .asSequence()
        .mapNotNull { it.toFavoriteSticker() }
        .take(StickerTables.MAX_FAVORITES)
        .toList()
    }

    if (favorites.isEmpty()) {
      Log.i(TAG, "No favorite stickers to export.")
      return
    }

    emitter.emit(Frame(favoriteStickerList = FavoriteStickerList(favoriteSticker = favorites)))
  }

  fun import(favoriteStickerList: FavoriteStickerList) {
    val favorites = favoriteStickerList.favoriteSticker
      .mapNotNull { favorite ->
        if (favorite.packId.size != PACK_ID_LENGTH || favorite.packKey.size != PACK_KEY_LENGTH || favorite.favoritedAtTimestamp == 0L) {
          Log.w(TAG, ImportSkips.invalidFavoriteSticker())
          return@mapNotNull null
        }

        StickerRecord(
          rowId = 0,
          packId = Hex.toStringCondensed(favorite.packId.toByteArray()),
          packKey = Hex.toStringCondensed(favorite.packKey.toByteArray()),
          stickerId = favorite.stickerId,
          emoji = "",
          contentType = "",
          size = 0,
          isCover = false,
          favoritedAt = favorite.favoritedAtTimestamp
        )
      }
      .sortedByDescending { it.favoritedAt }
      .take(StickerTables.MAX_FAVORITES)

    SignalDatabase.stickers.insertFavoritesFromArchive(favorites)
  }

  private fun StickerRecord.toFavoriteSticker(): FavoriteStickerList.FavoriteSticker? {
    val packIdBytes = try {
      Hex.fromStringCondensed(this.packId)?.takeIf { it.size == PACK_ID_LENGTH } ?: throw IOException("Incorrect length!")
    } catch (e: IOException) {
      Log.w(TAG, ExportSkips.invalidFavoriteStickerPackId())
      return null
    }

    val packKeyBytes = try {
      Hex.fromStringCondensed(this.packKey)?.takeIf { it.size == PACK_KEY_LENGTH } ?: throw IOException("Incorrect length!")
    } catch (e: IOException) {
      Log.w(TAG, ExportSkips.invalidFavoriteStickerPackKey())
      return null
    }

    return FavoriteStickerList.FavoriteSticker(
      packId = packIdBytes.toByteString(),
      packKey = packKeyBytes.toByteString(),
      stickerId = this.stickerId,
      favoritedAtTimestamp = this.favoritedAt
    )
  }
}
