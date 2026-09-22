/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack

sealed interface StickerPageScreenEvents {
  data object Initialize : StickerPageScreenEvents
  data class PacksUpdated(val packs: List<KeyboardStickerPack>) : StickerPageScreenEvents
  data class PackSelected(val packId: String) : StickerPageScreenEvents
  data class VisiblePackChanged(val packId: String) : StickerPageScreenEvents
  data object ScrollTargetConsumed : StickerPageScreenEvents
  data class StickerClicked(val sticker: KeyboardSticker) : StickerPageScreenEvents
  data object SearchClicked : StickerPageScreenEvents
  data class ViewStickerPackClicked(val packId: String, val packKey: String) : StickerPageScreenEvents
  data class SendStickerPackClicked(val packId: String, val packKey: String) : StickerPageScreenEvents
  data class RemoveStickerPackClicked(val packId: String, val packKey: String) : StickerPageScreenEvents
  data object ClearRecentStickersClicked : StickerPageScreenEvents
}
