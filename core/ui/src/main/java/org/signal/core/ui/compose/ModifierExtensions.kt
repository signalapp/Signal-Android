/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.signal.core.ui.R

/**
 * Applies sensible horizontal padding to the given component.
 */
@Composable
fun Modifier.horizontalGutters(
  gutterSize: Dp = dimensionResource(R.dimen.gutter)
): Modifier {
  return padding(horizontal = gutterSize)
}

/**
 * Configures a component to be clickable within its bounds and show a default indication when pressed.
 *
 * This modifier is designed for use on container components, making it easier to create a clickable container with proper accessibility configuration.
 */
@Composable
fun Modifier.clickableContainer(
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  indication: Indication = ripple(bounded = false),
  enabled: Boolean = true,
  contentDescription: String?,
  onClickLabel: String,
  role: Role? = null,
  onClick: () -> Unit
): Modifier = clickable(
  interactionSource = interactionSource,
  indication = indication,
  enabled = enabled,
  onClickLabel = onClickLabel,
  role = role,
  onClick = onClick
).then(
  if (contentDescription != null) {
    Modifier.semantics(mergeDescendants = true) {
      this.contentDescription = contentDescription
    }
  } else {
    Modifier
  }
)

/**
 * Fades this component's content out as it nears its end edge, so that whatever floats over that edge has nothing
 * running underneath it.
 *
 * Content within [inset] of the edge is gone altogether, and [fadeWidth] before that is the ramp into it. The same ramp
 * covers content leaving the viewport, since both are the one edge.
 *
 * The fade follows the layout direction, landing on the left in RTL.
 *
 * @param fadeWidth How far the ramp from solid to gone runs
 * @param inset How far in from the end edge the ramp finishes, leaving everything past it fully faded
 */
fun Modifier.endFadingEdge(fadeWidth: Dp, inset: Dp = 0.dp): Modifier {
  return this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()

      if (fadeWidth <= 0.dp && inset <= 0.dp) {
        return@drawWithContent
      }

      val insetPx = inset.toPx()

      // A ramp with no width is no gradient at all, so it is given the thinnest one that still has two ends.
      val fadePx = fadeWidth.toPx().coerceAtLeast(1f)

      val brush = if (layoutDirection == LayoutDirection.Rtl) {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = insetPx, endX = insetPx + fadePx)
      } else {
        Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - insetPx - fadePx, endX = size.width - insetPx)
      }

      drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }
}

fun Modifier.ensureWidthIsAtLeastHeight(): Modifier {
  return this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val size = maxOf(placeable.width, placeable.height)
    layout(size, size) {
      placeable.placeRelative((size - placeable.width) / 2, (size - placeable.height) / 2)
    }
  }
}

private val SelectionHighlightShape: Shape = InsetRoundedShape(horizontalInset = 10.dp, cornerRadius = 18.dp)

/**
 * Marks a list item as the one whose content is displayed beside the list, such as the open section of a list/detail
 * split. The item is clipped to an inset rounded shape, filled with `secondaryContainer` while [selected].
 *
 * Apply it before the item's click handling so that the ripple is clipped to the same shape. The item is at least 56dp
 * tall, and otherwise keeps its own layout.
 */
@Composable
fun Modifier.selectionHighlight(selected: Boolean): Modifier {
  return this
    .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
    .heightIn(min = 56.dp)
    .clip(SelectionHighlightShape)
    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
}

/**
 * A rounded rectangle inset from the start and end of its bounds, without changing them.
 */
private class InsetRoundedShape(
  private val horizontalInset: Dp,
  private val cornerRadius: Dp
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val inset = with(density) { horizontalInset.toPx() }
    val radius = with(density) { cornerRadius.toPx() }

    return Outline.Rounded(
      RoundRect(
        left = inset,
        top = 0f,
        right = size.width - inset,
        bottom = size.height,
        cornerRadius = CornerRadius(radius)
      )
    )
  }
}
