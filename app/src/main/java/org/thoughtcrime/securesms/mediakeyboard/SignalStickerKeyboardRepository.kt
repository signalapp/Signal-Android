/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.signal.core.models.database.StickerRecord
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.nullIfBlank
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables.StickerPackRecordReader
import org.thoughtcrime.securesms.database.StickerTables.StickerRecordReader
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * [StickerKeyboardRepository] backed by the app's sticker database.
 */
class SignalStickerKeyboardRepository(private val context: Context) : StickerKeyboardRepository {

  companion object {
    private const val RECENT_LIMIT = 24
  }

  override val allowStickerAnimation: Boolean = true

  override fun observeStickerPacks(): Flow<List<KeyboardStickerPack>> {
    return callbackFlow {
      val observer = DatabaseObserver.Observer { trySend(Unit) }

      AppDependencies.databaseObserver.registerStickerObserver(observer)
      AppDependencies.databaseObserver.registerStickerPackObserver(observer)
      trySend(Unit)

      awaitClose {
        AppDependencies.databaseObserver.unregisterObserver(observer)
      }
    }
      .conflate()
      .map { loadStickerPacks() }
      .flowOn(Dispatchers.Default)
  }

  override fun onStickerUsed(sticker: KeyboardSticker) {
    SignalExecutors.BOUNDED_IO.execute {
      SignalDatabase.stickers.updateStickerLastUsedTime(sticker.packId, sticker.stickerId.toInt(), System.currentTimeMillis())
    }
  }

  private fun loadStickerPacks(): List<KeyboardStickerPack> {
    val stickerTable = SignalDatabase.stickers

    val packRecords = StickerPackRecordReader(stickerTable.getInstalledStickerPacks()).use { reader -> reader.asSequence().toList() }

    val packs = packRecords.map { pack ->
      val stickers = StickerRecordReader(stickerTable.getStickersForPack(pack.packId)).use { reader ->
        reader.asSequence().map { it.toKeyboardSticker() }.toList()
      }

      KeyboardStickerPack(
        id = pack.packId,
        title = pack.title.nullIfBlank(),
        cover = DecryptableUri(pack.cover.uri),
        stickers = stickers
      )
    }

    val recentStickers = StickerRecordReader(stickerTable.getRecentlyUsedStickers(RECENT_LIMIT)).use { reader -> reader.asSequence().toList() }
    if (recentStickers.isEmpty()) {
      return packs
    }

    val recentPack = KeyboardStickerPack(
      id = StickerKeyboardRepository.RECENT_PACK_ID,
      title = context.getString(R.string.StickerKeyboard__recently_used),
      cover = null,
      stickers = recentStickers.map { it.toKeyboardSticker() }
    )

    return listOf(recentPack) + packs
  }

  private fun StickerRecord.toKeyboardSticker(): KeyboardSticker {
    return KeyboardSticker(
      packId = packId,
      packKey = packKey,
      stickerId = stickerId.toLong(),
      emoji = emoji.nullIfBlank(),
      image = DecryptableUri(uri),
      // Sticker packs only carry static WebP and animated APNG, so a PNG sticker is an APNG.
      isAnimated = contentType == MediaUtil.IMAGE_PNG
    )
  }
}
