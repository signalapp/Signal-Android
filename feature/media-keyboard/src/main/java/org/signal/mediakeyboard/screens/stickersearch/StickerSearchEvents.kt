/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.stickersearch

import org.signal.mediakeyboard.screens.sticker.StickerPageScreenEvents

internal sealed interface StickerSearchEvents {
  /** Wrapped event from the keyboard page screen */
  data class StickerPageScreenEvent(val event: StickerPageScreenEvents) : StickerSearchEvents

  /** User clicked the back arrow */
  data object NavigateBack : StickerSearchEvents
}
