/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaneExpansionStateTest {

  private val anchorOffsets = mapOf(
    PaneAnchor.DETAIL_ONLY to 100f,
    PaneAnchor.SPLIT to 500f,
    PaneAnchor.LIST_ONLY to 900f
  )

  private val selected = mutableListOf<PaneAnchor>()

  private fun expansion(initialOffsetPx: Float = 500f, withAnchors: Boolean = true): PaneExpansionState {
    return PaneExpansionState(Density(2f), initialOffsetPx) { selected += it }.apply {
      if (withAnchors) {
        updateAnchors(anchorOffsets)
      }
    }
  }

  @Test
  fun `given an initial offset, then the list is that wide at this density`() {
    assertEquals(250.dp, expansion(initialOffsetPx = 500f).listWidth)
  }

  /**
   * The layout publishes the anchors from measurement, which happens after construction. A drag arriving
   * first has nothing to clamp against, so it is dropped rather than moving the divider anywhere.
   */
  @Test
  fun `given anchors have not been published, when dragging, then the divider does not move`() {
    val expansion = expansion(withAnchors = false)

    expansion.dragBy(100f)

    assertEquals(250.dp, expansion.listWidth)
  }

  @Test
  fun `given a drag within the anchors, then the divider follows it`() {
    val expansion = expansion()

    expansion.dragBy(-100f)

    assertEquals(200.dp, expansion.listWidth)
  }

  @Test
  fun `given a drag past the widest anchor, then the divider stops there`() {
    val expansion = expansion()

    expansion.dragBy(1000f)

    assertEquals(450.dp, expansion.listWidth)
  }

  @Test
  fun `given a drag past the narrowest anchor, then the divider stops there`() {
    val expansion = expansion()

    expansion.dragBy(-1000f)

    assertEquals(50.dp, expansion.listWidth)
  }

  @Test
  fun `given a drag that ended nearer another anchor, when settling, then that anchor is selected and moved to`() = runTest {
    val expansion = expansion()
    expansion.onDragStarted()
    expansion.dragBy(300f)

    withContext(TestFrameClock()) { expansion.settle() }

    assertEquals(listOf(PaneAnchor.LIST_ONLY), selected)
    assertEquals(450.dp, expansion.listWidth)
  }

  /**
   * A state change arriving mid-gesture must not yank the divider out from under the user's finger.
   */
  @Test
  fun `given a drag in progress, when told to go to an anchor, then it is ignored`() = runTest {
    val expansion = expansion()
    expansion.onDragStarted()

    withContext(TestFrameClock()) { expansion.goTo(PaneAnchor.DETAIL_ONLY) }

    assertEquals(250.dp, expansion.listWidth)
  }

  @Test
  fun `given no drag in progress, when told to go to an anchor, then the divider moves there`() = runTest {
    val expansion = expansion()

    withContext(TestFrameClock()) { expansion.goTo(PaneAnchor.DETAIL_ONLY) }

    assertEquals(50.dp, expansion.listWidth)
  }

  /**
   * How the divider is operated without a gesture: screen readers cannot drag, so the accessibility action
   * steps through the anchors in declared order and wraps around.
   */
  @Test
  fun `when asked for the next anchor, then it cycles through them in declared order`() {
    assertEquals(PaneAnchor.SPLIT, expansion(initialOffsetPx = 100f).nextAnchor())
    assertEquals(PaneAnchor.LIST_ONLY, expansion(initialOffsetPx = 500f).nextAnchor())
    assertEquals(PaneAnchor.DETAIL_ONLY, expansion(initialOffsetPx = 900f).nextAnchor())
  }

  @Test
  fun `given anchors have not been published, when asked for the next anchor, then there is none`() {
    assertNull(expansion(withAnchors = false).nextAnchor())
  }

  /**
   * Selecting reports the anchor and nothing else — the state change that comes back is what moves the
   * divider, by the same path a drag takes.
   */
  @Test
  fun `when selecting an anchor, then it is reported without moving the divider`() {
    val expansion = expansion()

    expansion.selectAnchor(PaneAnchor.DETAIL_ONLY)

    assertEquals(listOf(PaneAnchor.DETAIL_ONLY), selected)
    assertEquals(250.dp, expansion.listWidth)
  }

  /** Runs animations to completion a frame at a time, without waiting on a real one. */
  private class TestFrameClock : MonotonicFrameClock {
    private var frameTimeNanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
      frameTimeNanos += 16_000_000
      return onFrame(frameTimeNanos)
    }
  }
}
