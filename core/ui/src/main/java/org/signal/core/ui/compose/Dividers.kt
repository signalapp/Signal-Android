/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.R

/**
 * Thin divider lines for separating content.
 */
object Dividers {
  /**
   * A horizontal line across the content, or across an enclosing [fullWidthDividers] component inside its horizontal
   * gutters.
   */
  @Composable
  fun Default(modifier: Modifier = Modifier) {
    Box(
      modifier = modifier
        .padding(vertical = 16.25.dp)
        .fillMaxWidth()
        .height(1.5.dp)
        .then(FullWidthLineElement(MaterialTheme.colorScheme.surfaceVariant, dimensionResource(R.dimen.gutter)))
    )
  }

  @Composable
  fun Vertical(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.5.dp,
    color: Color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    val targetThickness = if (thickness == Dp.Hairline) {
      (1f / LocalDensity.current.density).dp
    } else {
      thickness
    }
    Box(
      modifier
        .width(targetThickness)
        .background(color = color)
    )
  }
}

/**
 * Draws the [Dividers.Default] within this component across its whole width, less the horizontal gutters, for a component whose content is inset
 * from its edges, such as a list kept to a max width in the middle of a wide window.
 */
fun Modifier.fullWidthDividers(): Modifier = this then FullWidthDividersElement

private val ModifierLocalFullWidthDividers = modifierLocalOf<FullWidthDividersNode?> { null }

private data object FullWidthDividersElement : ModifierNodeElement<FullWidthDividersNode>() {
  override fun create() = FullWidthDividersNode()
  override fun update(node: FullWidthDividersNode) = Unit
}

private class FullWidthDividersNode : Modifier.Node(), ModifierLocalModifierNode, LayoutAwareModifierNode {
  var coordinates: LayoutCoordinates? = null
    private set

  override val providedValues = modifierLocalMapOf(ModifierLocalFullWidthDividers to this)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
  }
}

private data class FullWidthLineElement(val color: Color, val gutter: Dp) : ModifierNodeElement<FullWidthLineNode>() {
  override fun create() = FullWidthLineNode(color, gutter)

  override fun update(node: FullWidthLineNode) {
    node.color = color
    node.gutter = gutter
    node.invalidateDraw()
  }
}

private class FullWidthLineNode(var color: Color, var gutter: Dp) : Modifier.Node(), DrawModifierNode, ModifierLocalModifierNode, GlobalPositionAwareModifierNode {
  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    invalidateDraw()
  }

  override fun ContentDrawScope.draw() {
    val container = ModifierLocalFullWidthDividers.current?.coordinates?.takeIf { it.isAttached }
    if (container == null) {
      drawRect(color)
      return
    }

    val inset = gutter.toPx()
    val left = -container.localPositionOf(requireLayoutCoordinates(), Offset.Zero).x + inset
    val width = (container.size.width - 2 * inset).coerceAtLeast(0f)
    drawRect(color = color, topLeft = Offset(left, 0f), size = Size(width, size.height))
  }
}

@DayNightPreviews
@Composable
private fun DefaultPreview() {
  Previews.Preview {
    Dividers.Default()
  }
}

@DayNightPreviews
@Composable
private fun VerticalPreview() {
  Previews.Preview {
    Dividers.Vertical(modifier = Modifier.height(20.dp))
  }
}
