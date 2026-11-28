/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity

/**
 * Browser-style bars above and below scrolling content that slide away as it scrolls down and return as it scrolls up.
 * Only scroll the content consumes moves them, and released part way, they settle at the nearer end. User scrolling
 * also dismisses the keyboard.
 *
 * Attach [nestedScrollConnection] above the content, mark the bars with [collapsingTopBar] and [collapsingBottomBar],
 * and pad the content with [topBarPadding].
 */
@Stable
class CollapsingBarsState internal constructor(private val onUserScroll: () -> Unit) {

  /** How far the bars have slid away, from 0 when showing to 1 when gone. */
  var collapsedFraction by mutableFloatStateOf(0f)
    private set

  var topBarHeightPx by mutableIntStateOf(0)
    internal set

  var bottomBarHeightPx by mutableIntStateOf(0)
    internal set

  private val travelPx: Int
    get() = maxOf(topBarHeightPx, bottomBarHeightPx)

  private val animationMutex = MutatorMutex()

  val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      val travel = travelPx
      if (consumed.y == 0f || travel == 0) {
        return Offset.Zero
      }

      if (source == NestedScrollSource.UserInput) {
        onUserScroll()
      }

      collapsedFraction = (collapsedFraction - consumed.y / travel).coerceIn(0f, 1f)
      return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      val travel = travelPx
      if (travel == 0 || collapsedFraction == 0f || collapsedFraction == 1f) {
        return Velocity.Zero
      }

      animateTo(
        target = if (collapsedFraction >= 0.5f) 1f else 0f,
        initialVelocity = -available.y / travel
      )
      return Velocity.Zero
    }
  }

  /** Brings the bars back into view. */
  suspend fun expand() {
    animateTo(target = 0f, initialVelocity = 0f)
  }

  private suspend fun animateTo(target: Float, initialVelocity: Float) {
    if (collapsedFraction == target) {
      return
    }

    animationMutex.mutate {
      animate(initialValue = collapsedFraction, targetValue = target, initialVelocity = initialVelocity) { value, _ ->
        collapsedFraction = value
      }
    }
  }
}

/** Padding for content that starts below the top bar but scrolls behind it. */
val CollapsingBarsState.topBarPadding: PaddingValues
  @Composable get() = PaddingValues(top = with(LocalDensity.current) { topBarHeightPx.toDp() })

/**
 * Brings the bars back whenever [scrollableState] is at its start, rechecking when [key] changes, so content that can't
 * scroll back can't strand them.
 */
@Composable
fun CollapsingBarsState.ExpandAtTop(scrollableState: ScrollableState, key: Any? = Unit) {
  val atTop by remember(scrollableState) { derivedStateOf { !scrollableState.canScrollBackward } }

  LaunchedEffect(atTop, key) {
    if (atTop) {
      expand()
    }
  }
}

@Composable
fun rememberCollapsingBarsState(): CollapsingBarsState {
  val focusManager = LocalFocusManager.current
  return remember(focusManager) { CollapsingBarsState(onUserScroll = { focusManager.clearFocus() }) }
}

/** Marks this as the bar above [state]'s content. Its container should clip. */
fun Modifier.collapsingTopBar(state: CollapsingBarsState): Modifier {
  return this
    .then(BarHeightElement(state, BarPosition.TOP))
    .graphicsLayer { translationY = -state.topBarHeightPx * state.collapsedFraction }
}

/** Marks this as the bar below [state]'s content. */
fun Modifier.collapsingBottomBar(state: CollapsingBarsState): Modifier {
  return this
    .then(BarHeightElement(state, BarPosition.BOTTOM))
    .graphicsLayer { translationY = state.bottomBarHeightPx * state.collapsedFraction }
}

private enum class BarPosition { TOP, BOTTOM }

private data class BarHeightElement(
  val state: CollapsingBarsState,
  val position: BarPosition
) : ModifierNodeElement<BarHeightNode>() {
  override fun create(): BarHeightNode = BarHeightNode(state, position)

  override fun update(node: BarHeightNode) {
    node.state = state
    node.position = position
  }
}

/** Reports the bar's height, and zero once it is removed. */
private class BarHeightNode(
  var state: CollapsingBarsState,
  var position: BarPosition
) : Modifier.Node(), LayoutAwareModifierNode {

  override fun onRemeasured(size: IntSize) {
    setHeight(size.height)
  }

  override fun onDetach() {
    setHeight(0)
  }

  private fun setHeight(height: Int) {
    when (position) {
      BarPosition.TOP -> state.topBarHeightPx = height
      BarPosition.BOTTOM -> state.bottomBarHeightPx = height
    }
  }
}
