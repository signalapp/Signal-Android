/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyboard.sticker.StickerSearchRepository
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * [StickerKeyboardRepository] backed by the app's sticker database.
 *
 * @param includeFeaturedStickers Leads with the featured pack, such as the clock stickers, ahead of the user's own.
 */
class SignalStickerKeyboardRepository(
  private val context: Context,
  private val includeFeaturedStickers: Boolean = false
) : StickerKeyboardRepository {

  companion object {
    private const val RECENT_LIMIT = 24
  }

  override val allowStickerAnimation: Boolean = true

  override val favoritesEnabled: Boolean
    get() = RemoteConfig.internalUser

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

  /**
   * The stored image behind a [KeyboardSticker] this repository produced, or null for one it did not, such as a featured
   * sticker.
   */
  fun getStickerUri(sticker: KeyboardSticker): Uri? {
    return (sticker.image as? DecryptableUri)?.uri
  }

  override fun onStickerUsed(sticker: KeyboardSticker) {
    // Featured stickers are not stored, so there is nothing to record.
    if (sticker.packId == StickerKeyboardRepository.FEATURED_PACK_ID) {
      return
    }

    SignalExecutors.BOUNDED_IO.execute {
      SignalDatabase.stickers.updateStickerLastUsedTime(sticker.packId, sticker.stickerId.toInt(), System.currentTimeMillis())
    }
  }

  override fun clearRecentStickers() {
    SignalExecutors.BOUNDED_IO.execute {
      SignalDatabase.stickers.clearRecentlyUsedStickers()
    }
  }

  private fun loadStickerPacks(): List<KeyboardStickerPack> {
    val featuredPack = if (includeFeaturedStickers) createFeaturedStickerPack(context) else null
    val stickerTable = SignalDatabase.stickers

    val packs = loadInstalledPackRecords().map { it.toKeyboardStickerPack() }

    val favoritesPack = if (favoritesEnabled) {
      val favoriteStickers = StickerRecordReader(stickerTable.getFavoriteStickers()).use { reader -> reader.asSequence().toList() }

      KeyboardStickerPack(
        id = StickerKeyboardRepository.FAVORITES_PACK_ID,
        packKey = null,
        title = null,
        cover = null,
        stickers = favoriteStickers.map { it.toKeyboardSticker() }
      )
    } else {
      null
    }

    val recentStickers = StickerRecordReader(stickerTable.getRecentlyUsedStickers(RECENT_LIMIT)).use { reader -> reader.asSequence().toList() }
    if (recentStickers.isEmpty()) {
      return listOfNotNull(featuredPack, favoritesPack) + packs
    }

    val recentPack = KeyboardStickerPack(
      id = StickerKeyboardRepository.RECENT_PACK_ID,
      packKey = null,
      title = context.getString(R.string.StickerKeyboard__recently_used),
      cover = null,
      stickers = recentStickers.map { it.toKeyboardSticker() }
    )

    return listOfNotNull(featuredPack, favoritesPack, recentPack) + packs
  }

  override suspend fun searchStickerPacks(query: String): List<KeyboardStickerPack> = withContext(Dispatchers.Default) {
    val matchingStickers = StickerSearchRepository().search(query)
    val resultsPack = if (matchingStickers.isNotEmpty()) {
      KeyboardStickerPack(
        id = StickerKeyboardRepository.SEARCH_RESULTS_PACK_ID,
        packKey = null,
        title = null,
        cover = null,
        stickers = matchingStickers.map { it.toKeyboardSticker() }
      )
    } else {
      null
    }

    val matchingPacks = loadInstalledPackRecords()
      .filter { it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
      .map { it.toKeyboardStickerPack() }

    listOfNotNull(resultsPack) + matchingPacks
  }

  private fun loadInstalledPackRecords(): List<StickerPackRecord> {
    return StickerPackRecordReader(SignalDatabase.stickers.getInstalledStickerPacks()).use { reader -> reader.asSequence().toList() }
  }

  private fun StickerPackRecord.toKeyboardStickerPack(): KeyboardStickerPack {
    val stickers = StickerRecordReader(SignalDatabase.stickers.getStickersForPack(packId)).use { reader ->
      reader.asSequence().map { it.toKeyboardSticker() }.toList()
    }

    return KeyboardStickerPack(
      id = packId,
      packKey = packKey,
      title = title.nullIfBlank(),
      cover = DecryptableUri(cover.uri),
      stickers = stickers
    )
  }

  private fun StickerRecord.toKeyboardSticker(): KeyboardSticker {
    return KeyboardSticker(
      packId = packId,
      packKey = packKey,
      stickerId = stickerId.toLong(),
      emoji = emoji.nullIfBlank(),
      image = DecryptableUri(uri),
      isFavorite = isFavorite
    )
  }
}
