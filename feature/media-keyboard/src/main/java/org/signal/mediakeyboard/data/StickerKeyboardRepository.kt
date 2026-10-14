/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

import kotlinx.coroutines.flow.Flow
import org.signal.mediakeyboard.data.StickerKeyboardRepository.Companion.FAVORITES_PACK_ID
import org.signal.mediakeyboard.data.StickerKeyboardRepository.Companion.FEATURED_PACK_ID
import org.signal.mediakeyboard.data.StickerKeyboardRepository.Companion.RECENT_PACK_ID
import org.signal.mediakeyboard.data.StickerKeyboardRepository.Companion.SEARCH_RESULTS_PACK_ID

/**
 * Data source for the sticker page of the media keyboard.
 */
interface StickerKeyboardRepository {

  /**
   * Whether animated (e.g. APNG) stickers should animate on this device.
   */
  val allowStickerAnimation: Boolean
    get() = true

  val favoritesEnabled: Boolean
    get() = true

  /**
   * The user's installed sticker packs, re-emitted whenever the underlying data changes.
   * The first pack should be a synthetic pack with id [FAVORITES_PACK_ID], preceded only by a synthetic pack with id
   * [FEATURED_PACK_ID] when the host offers featured stickers. If the user has
   * recently-used stickers, it should be a synthetic pack with id [RECENT_PACK_ID].
   */
  fun observeStickerPacks(): Flow<List<KeyboardStickerPack>>

  /**
   * The stickers matching [query], for a host that offers search, which is never blank. Leads with a synthetic pack with
   * id [SEARCH_RESULTS_PACK_ID] of the stickers whose emoji matches, followed by the packs whose title or author does.
   */
  suspend fun searchStickerPacks(query: String): List<KeyboardStickerPack> = emptyList()

  /**
   * Called whenever the user sends a sticker, so that recents can be tracked.
   */
  fun onStickerUsed(sticker: KeyboardSticker) = Unit

  /**
   * Forget every recently-used sticker, emptying the synthetic [RECENT_PACK_ID] pack.
   */
  fun clearRecentStickers() = Unit

  companion object {
    const val RECENT_PACK_ID = "media-keyboard-recents"
    const val FAVORITES_PACK_ID = "media-keyboard-favorites"

    /** A synthetic pack of stickers that are not part of any real pack, such as the clock stickers. */
    const val FEATURED_PACK_ID = "media-keyboard-featured"

    /** A synthetic pack of the stickers whose emoji matches a search, shown without a header. */
    const val SEARCH_RESULTS_PACK_ID = "media-keyboard-search-results"
  }
}

/**
 * A single selectable sticker.
 *
 * @param packKey The key of the pack this came from, which identifies the pack alongside [packId]
 *   wherever it is opened. Carried on the sticker rather than the pack because recents mixes packs. Null for a sticker
 *   that belongs to no real pack, such as a featured one, which can then only be sent.
 * @param image A Glide-loadable model for the sticker image (e.g. a Uri or resource id), or a [ThemedStickerImage].
 */
data class KeyboardSticker(
  val packId: String,
  val packKey: String?,
  val stickerId: Long,
  val emoji: String?,
  val image: Any,
  val isFavorite: Boolean = false
)

/**
 * @param packKey The pack's key, which identifies it alongside [id] wherever it is opened or
 *   forwarded. Null for the synthetic favorites and recents packs, which are not real packs.
 * @param cover A Glide-loadable model for the pack's cover image, or a [ThemedStickerImage].
 */
data class KeyboardStickerPack(
  val id: String,
  val packKey: String?,
  val title: String?,
  val cover: Any?,
  val stickers: List<KeyboardSticker>
) {
  val isEmptyFavorites: Boolean
    get() = id == FAVORITES_PACK_ID && stickers.isEmpty()
}
