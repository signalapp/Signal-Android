/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import org.signal.mediakeyboard.data.KeyboardStickerPack

data class StickerPageState(
  val packs: List<KeyboardStickerPack> = emptyList(),
  val selectedPackId: String? = null,
  val scrollTargetPackId: String? = null,
  val allowAnimation: Boolean = true,
  val confirmRemovePack: ConfirmRemovePack? = null
)

/**
 * The pack the user has asked to remove, held until they confirm or dismiss the prompt.
 *
 * @param packKey The pack's key, which identifies it alongside [packId] to whoever uninstalls it.
 */
data class ConfirmRemovePack(
  val packId: String,
  val packKey: String
)
