/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gifsearch

import org.signal.mediakeyboard.screens.gif.GifPageScreenEvents

internal sealed interface GifSearchEvents {
  /** Wrapped event from the keyboard page screen */
  data class GifPageScreenEvent(val event: GifPageScreenEvents) : GifSearchEvents

  /** User clicked the back arrow */
  data object NavigateBack : GifSearchEvents
}
