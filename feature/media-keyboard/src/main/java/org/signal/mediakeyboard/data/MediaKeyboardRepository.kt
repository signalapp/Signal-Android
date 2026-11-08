/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

/**
 * The full set of data sources the media keyboard needs to operate. The main app provides
 * implementations backed by its real data stores, while demo apps can provide fakes.
 */
interface MediaKeyboardRepository {
  val emoji: EmojiKeyboardRepository
  val stickers: StickerKeyboardRepository
  val gifs: GifKeyboardRepository
}
