/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.fonts

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * Special monospace font, primarily used for rendering AEPs.
 */
object MonoTypeface {
  private var cached: Typeface? = null

  @Composable
  fun fontFamily(): FontFamily {
    val context = LocalContext.current
    return FontFamily(cached ?: Typeface.createFromAsset(context.assets, "fonts/MonoSpecial-Regular.otf").also { cached = it })
  }
}
