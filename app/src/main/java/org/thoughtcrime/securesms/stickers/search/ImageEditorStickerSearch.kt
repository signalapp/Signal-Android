/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.screens.stickersearch.StickerSearch
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mediakeyboard.SignalStickerKeyboardRepository
import org.thoughtcrime.securesms.mediakeyboard.toFeatureSticker
import org.thoughtcrime.securesms.scribbles.StickerSelectResult

/**
 * [StickerSearch] for placing a sticker in the image editor, which leads with the featured stickers. Reports the sticker
 * the user picks to [onResult], or null if they leave without picking one.
 */
@Composable
internal fun ImageEditorStickerSearch(onResult: (StickerSelectResult?) -> Unit) {
  val repository = remember { SignalStickerKeyboardRepository(AppDependencies.application, includeFeaturedStickers = true) }

  StickerSearch(
    repository = repository,
    onAction = { action ->
      if (action is MediaKeyboardAction.StickerSendClicked) {
        onResult(action.sticker.toStickerSelectResult(repository))
      }
    },
    onClose = { onResult(null) }
  )
}

private fun KeyboardSticker.toStickerSelectResult(repository: SignalStickerKeyboardRepository): StickerSelectResult? {
  toFeatureSticker()?.let { return StickerSelectResult.Feature(it) }
  return repository.getStickerUri(this)?.let { StickerSelectResult.Sticker(it) }
}
