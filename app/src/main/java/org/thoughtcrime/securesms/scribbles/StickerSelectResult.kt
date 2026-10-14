/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("StickerSelectResults")

package org.thoughtcrime.securesms.scribbles

import android.net.Uri
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.renderers.UriGlideRenderer
import org.signal.imageeditor.core.stickers.AnalogClockStickerRenderer
import org.signal.imageeditor.core.stickers.DigitalClockStickerRenderer
import org.signal.imageeditor.core.stickers.FeatureSticker

/**
 * A sticker picked to place in the image editor.
 */
internal sealed interface StickerSelectResult {
  data class Sticker(val uri: Uri) : StickerSelectResult
  data class Feature(val featureSticker: FeatureSticker) : StickerSelectResult
}

/**
 * Turns a sticker pick into a [Renderer] the image editor can place, loading a sticker image at no more than the given
 * size.
 */
internal fun StickerSelectResult.toRenderer(imageMaxWidth: Int, imageMaxHeight: Int): Renderer = when (this) {
  is StickerSelectResult.Sticker -> UriGlideRenderer(uri, true, imageMaxWidth, imageMaxHeight)

  is StickerSelectResult.Feature -> when (featureSticker) {
    FeatureSticker.DIGITAL_CLOCK -> DigitalClockStickerRenderer(System.currentTimeMillis())
    FeatureSticker.ANALOG_CLOCK -> AnalogClockStickerRenderer(System.currentTimeMillis())
  }
}
