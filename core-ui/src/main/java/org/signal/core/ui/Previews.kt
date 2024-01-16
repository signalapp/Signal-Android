/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import org.signal.core.ui.theme.SignalTheme

object Previews {
  @Composable
  fun Preview(
    content: @Composable () -> Unit
  ) {
    SignalTheme {
      Surface {
        content()
      }
    }
  }
}
