/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import org.signal.core.ui.compose.SignalIcons
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository

/**
 * A [StickerPageState] with a recents pack and two installed packs, using icons as sticker images so previews render
 * without shipping any sticker assets.
 */
@Composable
fun rememberPreviewStickerPageState(): StickerPageState {
  val dayAndNight = listOf(SignalIcons.Daytime, SignalIcons.Nighttime, SignalIcons.FlashOn, SignalIcons.Bell, SignalIcons.Calendar, SignalIcons.Mic, SignalIcons.Camera)
    .map { it.imageVector }
  val people = listOf(SignalIcons.Emoji, SignalIcons.PersonCircle, SignalIcons.Group, SignalIcons.Favorite, SignalIcons.Key, SignalIcons.Lock)
    .map { it.imageVector }

  return remember(dayAndNight, people) {
    val dayAndNightPack = previewPack(id = "pack-1", title = "Day and Night", images = dayAndNight)

    StickerPageState(
      packs = listOf(
        previewPack(id = StickerKeyboardRepository.RECENT_PACK_ID, title = null, images = people.take(3) + dayAndNight.take(2)).copy(packKey = null),
        dayAndNightPack,
        previewPack(id = "pack-2", title = "Bandit the Cat", images = people)
      ),
      selectedPackId = dayAndNightPack.id
    )
  }
}

private fun previewPack(id: String, title: String?, images: List<ImageVector>): KeyboardStickerPack {
  return KeyboardStickerPack(
    id = id,
    packKey = "$id-key",
    title = title,
    cover = images.first(),
    stickers = images.mapIndexed { index, image ->
      KeyboardSticker(
        packId = id,
        packKey = "$id-key",
        stickerId = index.toLong(),
        emoji = null,
        image = image
      )
    }
  )
}
