/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

/**
 * Events that happen in [StickerPackPreviewActivityV2]
 */
sealed interface StickerPackPreviewEvent {
  data object Initialize : StickerPackPreviewEvent
  data object InstallClicked : StickerPackPreviewEvent
  data object UninstallClicked : StickerPackPreviewEvent
  data object UninstallConfirmed : StickerPackPreviewEvent
  data object UninstallCanceled : StickerPackPreviewEvent
  data object SendPackClicked : StickerPackPreviewEvent
  data object LinkPackClicked : StickerPackPreviewEvent
  data object ShareSheetDismissed : StickerPackPreviewEvent
  data object CopyLinkClicked : StickerPackPreviewEvent
  data object ShareExternallyClicked : StickerPackPreviewEvent
}
