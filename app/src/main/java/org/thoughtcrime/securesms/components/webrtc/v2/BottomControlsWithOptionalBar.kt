/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal enum class BottomControlsLayoutId {
  CONTROLS,
  BAR
}

/**
 * A custom layout that positions an optional bar (like WaitingToBeLetInBar or CallLinkInfoCard)
 * and a controls row (audio indicator + camera toggle).
 *
 * Positioning logic:
 * - The bar is always positioned above the bottom sheet (offset by [bottomSheetPadding]) and
 *   constrained to the sheet's max width.
 * - If there's enough gutter space on the sides of the centered bottom sheet, the controls
 *   are placed at the screen edge (bottom of screen).
 * - If there's not enough gutter space and there's a bar, controls are stacked above the bar.
 *
 * Usage: Apply `Modifier.layoutId(BottomControlsLayoutId.CONTROLS)` to the controls content
 * and `Modifier.layoutId(BottomControlsLayoutId.BAR)` to the bar content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomControlsWithOptionalBar(
  bottomSheetPadding: Dp,
  modifier: Modifier = Modifier,
  controlsRow: @Composable () -> Unit,
  barSlot: @Composable () -> Unit
) {
  val sheetMaxWidthPx = with(LocalDensity.current) { BottomSheetDefaults.SheetMaxWidth.roundToPx() }
  val spacingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
  val elementBottomPaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
  val bottomSheetPaddingPx = with(LocalDensity.current) { bottomSheetPadding.roundToPx() }
  // Estimated space needed for the larger control (camera toggle ~48dp + spacing)
  val spaceNeededForControlPx = with(LocalDensity.current) { (48.dp + 16.dp).roundToPx() }

  Layout(
    content = {
      controlsRow()
      barSlot()
    },
    modifier = modifier
  ) { measurables, constraints ->
    val controlsMeasurable = measurables.find { it.layoutId == BottomControlsLayoutId.CONTROLS }
    val barMeasurable = measurables.find { it.layoutId == BottomControlsLayoutId.BAR }

    // Calculate gutter space: space on each side of the centered bottom sheet
    val sheetWidth = min(constraints.maxWidth, sheetMaxWidthPx)
    val gutterSpace = (constraints.maxWidth - sheetWidth) / 2
    val controlsCanBeAtScreenEdge = gutterSpace >= spaceNeededForControlPx

    // Bar is constrained to sheet width
    val barConstraints = constraints.copy(
      minWidth = 0,
      maxWidth = sheetWidth
    )

    val barPlaceable = barMeasurable?.measure(barConstraints)
    val barWidth = barPlaceable?.width ?: 0
    val barHeight = barPlaceable?.height ?: 0

    val controlsPlaceable = controlsMeasurable?.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val controlsHeight = controlsPlaceable?.height ?: 0

    if (controlsCanBeAtScreenEdge) {
      // Controls at screen edge with 16dp bottom padding, bar above sheet with 16dp bottom padding
      val controlsTotalHeight = controlsHeight + elementBottomPaddingPx
      val barTotalHeight = barHeight + elementBottomPaddingPx
      val totalHeight = max(controlsTotalHeight, bottomSheetPaddingPx + barTotalHeight)
      val barX = (constraints.maxWidth - barWidth) / 2
      val barY = totalHeight - bottomSheetPaddingPx - barTotalHeight
      val controlsY = totalHeight - controlsTotalHeight

      layout(constraints.maxWidth, totalHeight) {
        controlsPlaceable?.placeRelative(0, controlsY)
        barPlaceable?.placeRelative(barX, barY)
      }
    } else if (barPlaceable != null) {
      // Not enough gutter space, controls stacked above bar, both above sheet with 16dp bottom padding
      val contentHeight = controlsHeight + (if (controlsHeight > 0) spacingPx else 0) + barHeight
      val totalHeight = contentHeight + elementBottomPaddingPx + bottomSheetPaddingPx
      val barX = (constraints.maxWidth - barWidth) / 2
      val controlsY = 0
      val barY = controlsHeight + (if (controlsHeight > 0) spacingPx else 0)

      layout(constraints.maxWidth, totalHeight) {
        controlsPlaceable?.placeRelative(0, controlsY)
        barPlaceable.placeRelative(barX, barY)
      }
    } else {
      // No bar, not enough gutter space - controls above sheet with 16dp bottom padding
      val totalHeight = controlsHeight + elementBottomPaddingPx + bottomSheetPaddingPx

      layout(constraints.maxWidth, totalHeight) {
        controlsPlaceable?.placeRelative(0, 0)
      }
    }
  }
}
