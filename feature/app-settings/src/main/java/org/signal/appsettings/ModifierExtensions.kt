/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.LocalSplitPaneSettingsEnabled
import org.signal.core.ui.compose.fullWidthDividers

internal val AppSettingsMaxContentWidth = 624.dp

/**
 * Modifier rules that should be applied to all settings screens for consistency: content is kept to a max width, while
 * dividers still span the whole screen. The max width only applies under a host that enables
 * [LocalSplitPaneSettingsEnabled].
 *
 * Scrolling screens should apply this after their scroll modifier, so the scroll container stays full width. Lazy lists
 * can't constrain their items with a modifier, and use [AppSettingsLazyColumn] instead.
 */
fun Modifier.appSettingsScreen(): Modifier {
  return this
    .fillMaxWidth()
    .fullWidthDividers()
    .then(AppSettingsMaxContentWidthElement)
}

private data object AppSettingsMaxContentWidthElement : ModifierNodeElement<AppSettingsMaxContentWidthNode>() {
  override fun create() = AppSettingsMaxContentWidthNode()
  override fun update(node: AppSettingsMaxContentWidthNode) = Unit
}

private class AppSettingsMaxContentWidthNode : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
  override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
    if (!currentValueOf(LocalSplitPaneSettingsEnabled)) {
      val placeable = measurable.measure(constraints)
      return layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
      }
    }

    val contentWidth = constraints.maxWidth.coerceAtMost(AppSettingsMaxContentWidth.roundToPx())
    val placeable = measurable.measure(constraints.copy(minWidth = contentWidth, maxWidth = contentWidth))
    val width = constraints.constrainWidth(placeable.width)

    return layout(width, placeable.height) {
      placeable.placeRelative((width - placeable.width) / 2, 0)
    }
  }
}
