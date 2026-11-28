/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

import android.content.Context

/**
 * A sticker image that depends on the theme it is drawn over, resolved when it is shown rather than when it is loaded so
 * that it follows theme changes. Implementations should have value equality, since a new instance resolves anew.
 */
fun interface ThemedStickerImage {
  /**
   * @param isDarkTheme Whether the surface behind the sticker is dark.
   * @return A Glide-loadable model for the image, which is drawn in a single place.
   */
  fun create(context: Context, isDarkTheme: Boolean): Any
}
