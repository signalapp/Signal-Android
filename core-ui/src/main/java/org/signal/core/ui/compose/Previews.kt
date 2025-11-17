/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.signal.core.ui.compose.theme.SignalTheme

object Previews {
  @Composable
  fun Preview(
    forceRtl: Boolean = false,
    content: @Composable () -> Unit
  ) {
    val dir = if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
      SignalTheme(
        incognitoKeyboardEnabled = false
      ) {
        Surface {
          content()
        }
      }
    }
  }

  @Composable
  fun BottomSheetPreview(
    forceRtl: Boolean = false,
    content: @Composable () -> Unit
  ) {
    val dir = if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
      SignalTheme(incognitoKeyboardEnabled = false) {
        Surface {
          Box(modifier = Modifier.background(color = SignalTheme.colors.colorSurface1)) {
            content()
          }
        }
      }
    }
  }
}
