/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider

/**
 * Wraps the given preview with [Previews.Preview]. Does not support RTL so if you want that, you'll need to use [Previews.Preview] directly.
 *
 * This is NOT embedded in [Previews] because doing so is not supported.
 */
class SignalPreviewWrapper : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable (() -> Unit)) {
    Previews.Preview {
      content()
    }
  }
}
