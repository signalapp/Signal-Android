/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Lets an expandable keyboard's sheet be dragged from anywhere in [content], not just its handle.
 *
 * Taps and horizontal drags still reach [content]; only a vertical drag past touch slop is taken for the sheet. Meant
 * for chrome, since anything in [content] that scrolls vertically keeps its drags for itself.
 *
 * Does nothing outside a [KeyboardSheetScaffold]'s keyboard content, or for a keyboard that is not expandable.
 */
@Composable
fun KeyboardSheetDragRegion(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Box(modifier = modifier.then(LocalKeyboardSheetDragRegion.current)) {
    content()
  }
}

internal val LocalKeyboardSheetDragRegion = staticCompositionLocalOf<Modifier> { Modifier }
