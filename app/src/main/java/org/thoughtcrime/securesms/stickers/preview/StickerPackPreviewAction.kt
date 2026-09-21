/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import org.thoughtcrime.securesms.database.model.StickerPackParams

/**
 * Actions that happen in [StickerPackPreviewActivityV2]
 */
sealed interface StickerPackPreviewAction {
  data class SendPack(val params: StickerPackParams) : StickerPackPreviewAction
  data class ShareExternally(val params: StickerPackParams) : StickerPackPreviewAction
  data object LinkCopied : StickerPackPreviewAction
  data object PackUnavailable : StickerPackPreviewAction
}
