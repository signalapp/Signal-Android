/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import android.content.Context
import org.signal.mediakeyboard.MediaKeyboardTab
import org.signal.mediakeyboard.data.MediaKeyboardRepository
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * [MediaKeyboardRepository] backed by the app's real emoji, sticker, and gif data sources.
 */
class SignalMediaKeyboardRepository(context: Context, recentEmoji: RecentEmojiPageModel) : MediaKeyboardRepository {

  private val appContext: Context = context.applicationContext

  override val emoji: SignalEmojiKeyboardRepository = SignalEmojiKeyboardRepository(appContext, recentEmoji)
  override val stickers: SignalStickerKeyboardRepository = SignalStickerKeyboardRepository(appContext)
  override val gifs: SignalGifKeyboardRepository = SignalGifKeyboardRepository()

  override suspend fun getAvailableTabs(): Set<MediaKeyboardTab> {
    return setOfNotNull(
      MediaKeyboardTab.EMOJI,
      MediaKeyboardTab.STICKER,
      MediaKeyboardTab.GIF.takeIf { RemoteConfig.gifSearchAvailable }
    )
  }
}
