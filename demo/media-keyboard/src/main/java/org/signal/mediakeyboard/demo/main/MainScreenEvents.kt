/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.main

import org.signal.mediakeyboard.data.KeyboardGif
import org.signal.mediakeyboard.data.KeyboardSticker

sealed class MainScreenEvents {
  data object OpenMediaKeyboard : MainScreenEvents()
  data object MediaKeyboardDismissed : MainScreenEvents()
  data class ComposerTextChanged(val text: String) : MainScreenEvents()
  data object SendClicked : MainScreenEvents()
  data class EmojiSelected(val emoji: String) : MainScreenEvents()
  data object BackspacePressed : MainScreenEvents()
  data class StickerSelected(val sticker: KeyboardSticker) : MainScreenEvents()
  data class GifSelected(val gif: KeyboardGif) : MainScreenEvents()
}
