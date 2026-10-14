/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.core.ui.compose.keyboard.KeyboardSheetController
import org.signal.core.ui.compose.keyboard.LocalKeyboardSheetController
import org.signal.core.util.Result
import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.data.GifFetchError
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.data.GifPage
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.MediaKeyboardRepository
import org.signal.mediakeyboard.data.StickerKeyboardRepository

class MediaKeyboardLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `emoji tab`() = assertPassesLayoutChecks { Keyboard(MediaKeyboardTab.EMOJI) }

  @Test
  fun `sticker tab`() = assertPassesLayoutChecks { Keyboard(MediaKeyboardTab.STICKER) }

  @Test
  fun `gif tab`() = assertPassesLayoutChecks { Keyboard(MediaKeyboardTab.GIF) }

  @androidx.compose.runtime.Composable
  private fun Keyboard(tab: MediaKeyboardTab) {
    CompositionLocalProvider(LocalKeyboardSheetController provides KeyboardSheetController()) {
      MediaKeyboard(repository = FakeRepository, onAction = {}, initialTab = tab)
    }
  }

  private object FakeRepository : MediaKeyboardRepository {
    override val emoji = object : EmojiKeyboardRepository {
      override suspend fun getEmojiPages() = EmojiKeyboardCategory.entries.map { category ->
        EmojiCategoryPage(category = category, emoji = List(40) { KeyboardEmoji("😀") })
      }

      override suspend fun getRecentEmoji() = emptyList<KeyboardEmoji>()
      override suspend fun search(query: String) = emptyList<KeyboardEmoji>()
      override suspend fun getPreferredVariations() = emptyMap<String, String>()
      override fun setPreferredVariation(canonical: String, variation: String) = Unit
      override suspend fun onEmojiUsed(emoji: String) = Unit
    }

    override val stickers = object : StickerKeyboardRepository {
      override val allowStickerAnimation = false
      override val favoritesEnabled = false
      override fun observeStickerPacks(): Flow<List<KeyboardStickerPack>> = flowOf(emptyList())
    }

    override val gifs = object : GifKeyboardRepository {
      override suspend fun getGifs(query: String, offset: Int, limit: Int): Result<GifPage, GifFetchError> {
        return Result.success(GifPage(gifs = emptyList(), hasMore = false))
      }
    }
  }
}
