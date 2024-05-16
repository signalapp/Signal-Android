/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.copied.androidx.compose.material3

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max
import kotlin.math.min

@Suppress("ModifierParameter", "TransitionPropertiesLabel")
@Composable
internal fun DropdownMenuContent(
  expandedStates: MutableTransitionState<Boolean>,
  transformOriginState: MutableState<TransformOrigin>,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  // Menu open/close animation.
  val transition = updateTransition(expandedStates, "DropDownMenu")

  val scale by transition.animateFloat(
    transitionSpec = {
      if (false isTransitioningTo true) {
        // Dismissed to expanded
        tween(
          durationMillis = IN_TRANSITION_DURATION,
          easing = LinearOutSlowInEasing
        )
      } else {
        // Expanded to dismissed.
        tween(
          durationMillis = 1,
          delayMillis = OUT_TRANSITION_DURATION - 1
        )
      }
    }
  ) {
    if (it) {
      // Menu is expanded.
      1f
    } else {
      // Menu is dismissed.
      0.8f
    }
  }

  val alpha by transition.animateFloat(
    transitionSpec = {
      if (false isTransitioningTo true) {
        // Dismissed to expanded
        tween(durationMillis = 30)
      } else {
        // Expanded to dismissed.
        tween(durationMillis = OUT_TRANSITION_DURATION)
      }
    }
  ) {
    if (it) {
      // Menu is expanded.
      1f
    } else {
      // Menu is dismissed.
      0f
    }
  }
  Surface(
    modifier = Modifier.graphicsLayer {
      scaleX = scale
      scaleY = scale
      this.alpha = alpha
      transformOrigin = transformOriginState.value
    },
    shape = MaterialTheme.shapes.extraSmall,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 3.dp,
    shadowElevation = 3.dp
  ) {
    Column(
      modifier = modifier
        .width(IntrinsicSize.Max)
        .verticalScroll(rememberScrollState()),
      content = content
    )
  }
}

internal fun calculateTransformOrigin(
  parentBounds: IntRect,
  menuBounds: IntRect
): TransformOrigin {
  val pivotX = when {
    menuBounds.left >= parentBounds.right -> 0f
    menuBounds.right <= parentBounds.left -> 1f
    menuBounds.width == 0 -> 0f
    else -> {
      val intersectionCenter =
        (
          max(parentBounds.left, menuBounds.left) +
            min(parentBounds.right, menuBounds.right)
          ) / 2
      (intersectionCenter - menuBounds.left).toFloat() / menuBounds.width
    }
  }
  val pivotY = when {
    menuBounds.top >= parentBounds.bottom -> 0f
    menuBounds.bottom <= parentBounds.top -> 1f
    menuBounds.height == 0 -> 0f
    else -> {
      val intersectionCenter =
        (
          max(parentBounds.top, menuBounds.top) +
            min(parentBounds.bottom, menuBounds.bottom)
          ) / 2
      (intersectionCenter - menuBounds.top).toFloat() / menuBounds.height
    }
  }
  return TransformOrigin(pivotX, pivotY)
}

@Immutable
internal data class DropdownMenuPositionProvider(
  val contentOffset: DpOffset,
  val density: Density,
  val onPositionCalculated: (IntRect, IntRect) -> Unit = { _, _ -> }
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize
  ): IntOffset {
    // The min margin above and below the menu, relative to the screen.
    val verticalMargin = with(density) { MenuVerticalMargin.roundToPx() }
    // The content offset specified using the dropdown offset parameter.
    val contentOffsetX = with(density) { contentOffset.x.roundToPx() }
    val contentOffsetY = with(density) { contentOffset.y.roundToPx() }

    // Compute horizontal position.
    val toRight = anchorBounds.left + contentOffsetX
    val toLeft = anchorBounds.right - contentOffsetX - popupContentSize.width
    val toDisplayRight = windowSize.width - popupContentSize.width
    val toDisplayLeft = 0
    val x = if (layoutDirection == LayoutDirection.Ltr) {
      sequenceOf(
        toRight,
        toLeft,
        // If the anchor gets outside of the window on the left, we want to position
        // toDisplayLeft for proximity to the anchor. Otherwise, toDisplayRight.
        if (anchorBounds.left >= 0) toDisplayRight else toDisplayLeft
      )
    } else {
      sequenceOf(
        toLeft,
        toRight,
        // If the anchor gets outside of the window on the right, we want to position
        // toDisplayRight for proximity to the anchor. Otherwise, toDisplayLeft.
        if (anchorBounds.right <= windowSize.width) toDisplayLeft else toDisplayRight
      )
    }.firstOrNull {
      it >= 0 && it + popupContentSize.width <= windowSize.width
    } ?: toLeft

    // Compute vertical position.
    val toBottom = maxOf(anchorBounds.bottom + contentOffsetY, verticalMargin)
    val toTop = anchorBounds.top - contentOffsetY - popupContentSize.height
    val toCenter = anchorBounds.top - popupContentSize.height / 2
    val toDisplayBottom = windowSize.height - popupContentSize.height - verticalMargin
    val y = sequenceOf(toBottom, toTop, toCenter, toDisplayBottom).firstOrNull {
      it >= verticalMargin &&
        it + popupContentSize.height <= windowSize.height - verticalMargin
    } ?: toTop

    onPositionCalculated(
      anchorBounds,
      IntRect(x, y, x + popupContentSize.width, y + popupContentSize.height)
    )
    return IntOffset(x, y)
  }
}

// Size defaults.
internal val MenuVerticalMargin = 48.dp

// Menu open/close animation.
internal const val IN_TRANSITION_DURATION = 120
internal const val OUT_TRANSITION_DURATION = 75
