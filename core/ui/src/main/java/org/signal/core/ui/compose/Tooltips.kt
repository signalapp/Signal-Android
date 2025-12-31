/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot

object Tooltips {

  /**
   * Renders a tooltip below the anchor content regardless of space, aligning the end edge of each.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PlainBelowAnchor(
    onDismiss: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    isTooltipVisible: Boolean,
    tooltipContent: @Composable () -> Unit,
    anchorContent: @Composable () -> Unit
  ) {
    val tooltipState = rememberTooltipState(
      initialIsVisible = isTooltipVisible,
      isPersistent = true
    )

    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
      state = tooltipState,
      enableUserInput = false,
      tooltip = {
        PlainTooltip(
          shape = TooltipDefaults.plainTooltipContainerShape,
          caretShape = TooltipDefaults.caretShape(),
          containerColor = containerColor,
          contentColor = contentColor
        ) {
          tooltipContent()
        }
      }
    ) {
      anchorContent()
    }

    LaunchedEffect(isTooltipVisible) {
      if (isTooltipVisible) {
        tooltipState.show()
      } else {
        tooltipState.dismiss()
      }
    }

    LaunchedEffect(tooltipState) {
      snapshotFlow { tooltipState.isVisible }
        .drop(1)
        .filterNot { it }
        .collect { onDismiss() }
    }
  }
}
