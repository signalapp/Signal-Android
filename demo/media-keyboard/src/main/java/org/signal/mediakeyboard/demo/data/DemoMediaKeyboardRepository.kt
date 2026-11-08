/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.data

import android.content.Context
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.data.MediaKeyboardRepository
import org.signal.mediakeyboard.data.StickerKeyboardRepository

class DemoMediaKeyboardRepository(context: Context) : MediaKeyboardRepository {
  override val emoji: EmojiKeyboardRepository = DemoEmojiKeyboardRepository()
  override val stickers: StickerKeyboardRepository = DemoStickerKeyboardRepository(context)
  override val gifs: GifKeyboardRepository = DemoGifKeyboardRepository(context)
}
