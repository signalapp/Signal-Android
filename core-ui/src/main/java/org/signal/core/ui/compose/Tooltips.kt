/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
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

    val caretSize = with(LocalDensity.current) {
      TooltipDefaults.caretSize.toSize()
    }

    TooltipBox(
      positionProvider = PositionBelowAnchor,
      state = tooltipState,
      tooltip = {
        PlainTooltip(
          shape = TooltipDefaults.plainTooltipContainerShape,
          containerColor = containerColor,
          contentColor = contentColor,
          modifier = Modifier.drawCaret { anchorLayoutCoordinates ->

            val path = if (anchorLayoutCoordinates != null) {
              val anchorBounds = anchorLayoutCoordinates.boundsInWindow()
              val anchorMid = (anchorBounds.right - anchorBounds.left) / 2
              val position = Offset(size.width - anchorMid, 0f)

              Path().apply {
                moveTo(x = position.x, y = position.y)
                lineTo(x = position.x + caretSize.width / 2, y = position.y)
                lineTo(x = position.x, y = position.y - caretSize.height)
                lineTo(x = position.x - caretSize.width / 2, y = position.y)
                close()
              }
            } else {
              Path()
            }

            onDrawWithContent {
              drawContent()
              drawPath(path = path, color = containerColor)
            }
          }
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

  private object PositionBelowAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
      val x = if (layoutDirection == LayoutDirection.Ltr) {
        anchorBounds.right - popupContentSize.width
      } else {
        anchorBounds.left
      }

      return IntOffset(x, anchorBounds.bottom)
    }
  }
}
