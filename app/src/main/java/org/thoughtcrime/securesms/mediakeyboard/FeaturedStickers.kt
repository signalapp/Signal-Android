/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import android.content.Context
import org.signal.imageeditor.core.stickers.AnalogClockStickerDrawable
import org.signal.imageeditor.core.stickers.DigitalClockStickerDrawable
import org.signal.imageeditor.core.stickers.FeatureSticker
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.signal.mediakeyboard.data.ThemedStickerImage
import org.thoughtcrime.securesms.R

/**
 * The synthetic pack of [FeatureSticker]s, such as the clocks, that are not part of any real sticker pack.
 */
fun createFeaturedStickerPack(context: Context): KeyboardStickerPack {
  return KeyboardStickerPack(
    id = StickerKeyboardRepository.FEATURED_PACK_ID,
    packKey = null,
    title = context.getString(R.string.ScribbleStickersFragment__featured_stickers),
    cover = FeatureStickerImage(FeatureSticker.ANALOG_CLOCK),
    stickers = FeatureSticker.entries.map { featureSticker ->
      KeyboardSticker(
        packId = StickerKeyboardRepository.FEATURED_PACK_ID,
        packKey = null,
        stickerId = featureSticker.featuredStickerId,
        emoji = null,
        image = FeatureStickerImage(featureSticker)
      )
    }
  )
}

/**
 * The [FeatureSticker] a sticker from [createFeaturedStickerPack] stands for, or null for any other sticker.
 */
fun KeyboardSticker.toFeatureSticker(): FeatureSticker? {
  if (packId != StickerKeyboardRepository.FEATURED_PACK_ID) {
    return null
  }

  return FeatureSticker.entries.firstOrNull { it.featuredStickerId == stickerId }
}

private val FeatureSticker.featuredStickerId: Long
  get() = when (this) {
    FeatureSticker.ANALOG_CLOCK -> 1
    FeatureSticker.DIGITAL_CLOCK -> 2
  }

/**
 * A live clock drawable for a [FeatureSticker], with the digital clock's text colored to contrast with the theme.
 */
private data class FeatureStickerImage(val featureSticker: FeatureSticker) : ThemedStickerImage {
  override fun create(context: Context, isDarkTheme: Boolean): Any {
    return when (featureSticker) {
      FeatureSticker.ANALOG_CLOCK -> AnalogClockStickerDrawable(context)
      FeatureSticker.DIGITAL_CLOCK -> DigitalClockStickerDrawable(context).apply {
        setStyle(if (isDarkTheme) DigitalClockStickerDrawable.Style.LIGHT_NO_BG else DigitalClockStickerDrawable.Style.DARK_NO_BG)
      }
    }
  }
}
