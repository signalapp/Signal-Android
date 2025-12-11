/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.signal.core.ui.compose.theme.SignalTheme

object Previews {
  /**
   * The default wrapper for previews. Properly sets the theme and provides a drawing surface.
   */
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

  /**
   * A preview wrapper for bottom sheet content. There will be no bottom sheet UI trimmings, just the content of the sheet. Properly sets the theme and an
   * appropriate surface color.
   */
  @Composable
  fun BottomSheetContentPreview(
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

  /**
   * A preview wrapper for a bottom sheet. You'll see the full bottom sheet UI in the expanded state.
   */
  @Composable
  fun BottomSheetPreview(
    forceRtl: Boolean = false,
    content: @Composable () -> Unit
  ) {
    val dir = if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
      SignalTheme(incognitoKeyboardEnabled = false) {
        val sheetState = SheetState(
          skipPartiallyExpanded = true,
          initialValue = SheetValue.Expanded,
          positionalThreshold = { 1f },
          velocityThreshold = { 1f }
        )
        BottomSheets.BottomSheet(sheetState = sheetState, onDismissRequest = {}) {
          content()
        }
      }
    }
  }
}
