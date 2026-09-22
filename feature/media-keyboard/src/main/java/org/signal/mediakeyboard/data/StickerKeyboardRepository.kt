/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

import kotlinx.coroutines.flow.Flow

/**
 * Data source for the sticker page of the media keyboard.
 */
interface StickerKeyboardRepository {

  /**
   * Whether animated (e.g. APNG) stickers should animate on this device.
   */
  val allowStickerAnimation: Boolean
    get() = true

  /**
   * The user's installed sticker packs, re-emitted whenever the underlying data changes.
   * If the user has recently-used stickers, the first pack should be a synthetic pack with
   * id [RECENT_PACK_ID].
   */
  fun observeStickerPacks(): Flow<List<KeyboardStickerPack>>

  /**
   * Called whenever the user picks a sticker, so that recents can be tracked.
   */
  fun onStickerUsed(sticker: KeyboardSticker) = Unit

  /**
   * Forget every recently-used sticker, emptying the synthetic [RECENT_PACK_ID] pack.
   */
  fun clearRecentStickers() = Unit

  companion object {
    const val RECENT_PACK_ID = "media-keyboard-recents"
  }
}

/**
 * A single selectable sticker.
 *
 * @param packKey The key of the pack this came from, which identifies the pack alongside [packId]
 *   wherever it is opened. Carried on the sticker rather than the pack because recents mixes packs.
 * @param image A Glide-loadable model for the sticker image (e.g. a Uri or resource id).
 * @param isAnimated Whether the image is an animated (APNG) sticker.
 */
data class KeyboardSticker(
  val packId: String,
  val packKey: String,
  val stickerId: Long,
  val emoji: String?,
  val image: Any,
  val isAnimated: Boolean = false
)

/**
 * @param packKey The pack's key, which identifies it alongside [id] wherever it is opened or
 *   forwarded. Null for the synthetic recents pack, which is not a real pack.
 * @param cover A Glide-loadable model for the pack's cover image.
 */
data class KeyboardStickerPack(
  val id: String,
  val packKey: String?,
  val title: String?,
  val cover: Any?,
  val stickers: List<KeyboardSticker>
)
