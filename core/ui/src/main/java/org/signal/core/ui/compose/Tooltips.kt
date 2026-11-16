/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot

object Tooltips {

  /** Which side of its anchor a [Popup] tooltip appears on. */
  enum class Position {
    Above,
    Below
  }

  /**
   * A tooltip that floats over [anchorContent], pointing at it.
   *
   * @param isPersistent whether the tooltip stays up until it's dismissed, rather than hiding itself after a moment.
   * @param enableUserInput whether long-pressing or hovering the anchor also shows the tooltip.
   * @param onDismiss called when the tooltip hides, so the caller can turn [isTooltipVisible] off.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun Popup(
    isTooltipVisible: Boolean,
    onDismiss: () -> Unit,
    tooltipContent: @Composable () -> Unit,
    position: Position = Position.Above,
    isPersistent: Boolean = true,
    enableUserInput: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    anchorContent: @Composable () -> Unit
  ) {
    val tooltipState = rememberTooltipState(
      initialIsVisible = isTooltipVisible,
      isPersistent = isPersistent
    )

    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        when (position) {
          Position.Above -> TooltipAnchorPosition.Above
          Position.Below -> TooltipAnchorPosition.Below
        }
      ),
      state = tooltipState,
      enableUserInput = enableUserInput,
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

  /**
   * A tooltip laid out in place, under whatever it describes, with an arrow pointing up at it. It stays until the
   * caller removes it, so [content] should include a way to close it.
   */
  @Composable
  fun Inline(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable BoxScope.() -> Unit
  ) {
    Box(modifier = modifier) {
      Canvas(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .size(width = 19.dp, height = 14.dp)
      ) {
        val arrow = Path().apply {
          moveTo(size.width / 2f, 0f)
          lineTo(size.width / 20f, size.height * 0.75f)
          lineTo(size.width * 19f / 20f, size.height * 0.75f)
          close()
        }
        drawPath(arrow, containerColor)
      }

      Box(
        modifier = Modifier
          .padding(top = 9.dp)
          .fillMaxWidth()
          .background(containerColor, RoundedCornerShape(18.dp))
      ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
          content()
        }
      }
    }
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun InlinePreview() {
  Tooltips.Inline(modifier = Modifier.padding(24.dp)) {
    Text(text = "Tooltip content", modifier = Modifier.padding(16.dp))
  }
}
