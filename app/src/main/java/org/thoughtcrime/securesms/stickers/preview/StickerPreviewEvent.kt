/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

/**
 * Events that happen in [StickerPreviewBottomSheet]
 */
sealed interface StickerPreviewEvent {
  data object Initialize : StickerPreviewEvent
  data object FavoriteClicked : StickerPreviewEvent
  data object RemoveFavoriteConfirmed : StickerPreviewEvent
  data object RemoveFavoriteCanceled : StickerPreviewEvent
}
