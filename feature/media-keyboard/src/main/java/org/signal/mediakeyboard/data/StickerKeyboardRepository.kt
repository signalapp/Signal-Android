/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

import kotlinx.coroutines.flow.Flow
import org.signal.mediakeyboard.data.StickerKeyboardRepository.Companion.FAVORITES_PACK_ID
import org.signal.mediakeyboard.data.StickerKeyboardRepository.Companion.RECENT_PACK_ID

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
   * The first pack should be a synthetic pack with id [FAVORITES_PACK_ID]. If the user has
   * recently-used stickers, it should be a synthetic pack with id [RECENT_PACK_ID].
   */
  fun observeStickerPacks(): Flow<List<KeyboardStickerPack>>

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
  }
}

/**
 * A single selectable sticker.
 *
 * @param packKey The key of the pack this came from, which identifies the pack alongside [packId]
 *   wherever it is opened. Carried on the sticker rather than the pack because recents mixes packs.
 * @param image A Glide-loadable model for the sticker image (e.g. a Uri or resource id).
 */
data class KeyboardSticker(
  val packId: String,
  val packKey: String,
  val stickerId: Long,
  val emoji: String?,
  val image: Any,
  val isFavorite: Boolean = false
)

/**
 * @param packKey The pack's key, which identifies it alongside [id] wherever it is opened or
 *   forwarded. Null for the synthetic favorites and recents packs, which are not real packs.
 * @param cover A Glide-loadable model for the pack's cover image.
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
