/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.copied.androidx.compose.material3

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Lifted straight from Compose-Material3
 *
 * This eliminates the content padding on the dropdown menu.
 */
@Suppress("ModifierParameter")
@Composable
internal fun DropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  properties: PopupProperties = PopupProperties(focusable = true),
  content: @Composable ColumnScope.() -> Unit
) {
  val expandedStates = remember { MutableTransitionState(false) }
  expandedStates.targetState = expanded

  if (expandedStates.currentState || expandedStates.targetState) {
    val transformOriginState = remember { mutableStateOf(TransformOrigin.Center) }
    val density = LocalDensity.current
    val popupPositionProvider = DropdownMenuPositionProvider(
      offset,
      density
    ) { parentBounds, menuBounds ->
      transformOriginState.value = calculateTransformOrigin(parentBounds, menuBounds)
    }

    Popup(
      onDismissRequest = onDismissRequest,
      popupPositionProvider = popupPositionProvider,
      properties = properties
    ) {
      DropdownMenuContent(
        expandedStates = expandedStates,
        transformOriginState = transformOriginState,
        modifier = modifier,
        content = content
      )
    }
  }
}
