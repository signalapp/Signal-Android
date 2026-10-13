/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.fonts

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * Special monospace font, primarily used for rendering AEPs.
 */
object MonoTypeface {
  @Volatile
  private var cached: Typeface? = null

  fun typeface(context: Context): Typeface {
    return cached ?: Typeface.createFromAsset(context.assets, "fonts/MonoSpecial-Regular.otf").also { cached = it }
  }

  @Composable
  fun fontFamily(): FontFamily {
    return FontFamily(typeface(LocalContext.current))
  }
}
