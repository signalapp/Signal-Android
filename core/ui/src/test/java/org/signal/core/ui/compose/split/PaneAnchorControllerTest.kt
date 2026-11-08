/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneAnchorControllerTest {

  private fun controller(initial: PaneAnchor? = null): PaneAnchorController {
    val controller = PaneAnchorController(SavedStateHandle())
    if (initial != null) {
      controller.onAnchorSelected(initial)
    }
    return controller
  }

  @Test
  fun `given a fresh controller, then both panes are shown`() {
    assertEquals(PaneAnchor.SPLIT, controller().anchor.value)
  }

  @Test
  fun `given a drag settles on an anchor, then that anchor is selected`() {
    val controller = controller()

    controller.onAnchorSelected(PaneAnchor.DETAIL_ONLY)

    assertEquals(PaneAnchor.DETAIL_ONLY, controller.anchor.value)
  }

  @Test
  fun `given the list fills the window, when detail content opens, then the detail is revealed`() {
    val controller = controller(PaneAnchor.LIST_ONLY)

    controller.revealDetailPane()

    assertEquals(PaneAnchor.DETAIL_ONLY, controller.anchor.value)
  }

  /**
   * A window already showing both panes is an arrangement the user chose; opening a conversation in the
   * detail pane must not collapse the list out from under them.
   */
  @Test
  fun `given both panes are shown, when detail content opens, then the arrangement is left alone`() {
    val controller = controller(PaneAnchor.SPLIT)

    controller.revealDetailPane()

    assertEquals(PaneAnchor.SPLIT, controller.anchor.value)
  }

  @Test
  fun `given the detail already fills the window, when detail content opens, then it stays filled`() {
    val controller = controller(PaneAnchor.DETAIL_ONLY)

    controller.revealDetailPane()

    assertEquals(PaneAnchor.DETAIL_ONLY, controller.anchor.value)
  }

  @Test
  fun `given the detail fills the window, when the list is revealed, then the list fills the window`() {
    val controller = controller(PaneAnchor.DETAIL_ONLY)

    controller.revealListPane()

    assertEquals(PaneAnchor.LIST_ONLY, controller.anchor.value)
  }

  @Test
  fun `given both panes are shown, when the list is revealed, then the arrangement is left alone`() {
    val controller = controller(PaneAnchor.SPLIT)

    controller.revealListPane()

    assertEquals(PaneAnchor.SPLIT, controller.anchor.value)
  }

  @Test
  fun `given the list already fills the window, when the list is revealed, then it stays filled`() {
    val controller = controller(PaneAnchor.LIST_ONLY)

    controller.revealListPane()

    assertEquals(PaneAnchor.LIST_ONLY, controller.anchor.value)
  }

  @Test
  fun `given both panes are shown, then neither pane is full screen`() = runTest {
    assertFalse(controller(PaneAnchor.SPLIT).isFullScreenPane.first())
  }

  @Test
  fun `given one pane fills the window, then it is reported as full screen`() = runTest {
    assertTrue(controller(PaneAnchor.DETAIL_ONLY).isFullScreenPane.first())
    assertTrue(controller(PaneAnchor.LIST_ONLY).isFullScreenPane.first())
  }

  /**
   * The anchor is persisted so that it survives a configuration change without the layout having to
   * re-derive it.
   */
  @Test
  fun `given a selected anchor, when rebuilt from the same saved state, then the anchor is restored`() {
    val savedStateHandle = SavedStateHandle()
    PaneAnchorController(savedStateHandle).onAnchorSelected(PaneAnchor.LIST_ONLY)

    assertEquals(PaneAnchor.LIST_ONLY, PaneAnchorController(savedStateHandle).anchor.value)
  }
}
