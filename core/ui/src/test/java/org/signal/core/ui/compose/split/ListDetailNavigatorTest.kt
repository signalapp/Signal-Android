/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric because the stacks are owned by a [SavedStateHandle], which stores them in a `Bundle`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListDetailNavigatorTest {

  private val detail = TestDetailKey(1)
  private val otherDetail = TestDetailKey(2)
  private val subScreen = TestSubScreenKey(1)

  private fun TestScope.navigator(initialRoot: TestListKey = TestListKey.ROOT): ListDetailNavigator<TestListKey, DetailNavKey> {
    return ListDetailNavigator(
      savedStateHandle = SavedStateHandle(),
      scope = backgroundScope,
      stackKeys = mapOf(
        TestListKey.ROOT to "root_stack",
        TestListKey.OTHER to "other_stack"
      ),
      initialRoot = initialRoot
    )
  }

  @Test
  fun `given a new navigator, then each root has its own stack`() = runTest {
    val navigator = navigator()

    assertEquals(listOf(TestListKey.ROOT), navigator[TestListKey.ROOT])
    assertEquals(listOf(TestListKey.OTHER), navigator[TestListKey.OTHER])
    assertEquals(TestListKey.ROOT, navigator.currentRoot.value)
  }

  @Test
  fun `when pushing detail, then it lands on that root's stack and the detail pane is revealed`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.LIST_ONLY))

    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))

    assertEquals(listOf(TestListKey.ROOT, detail), navigator[TestListKey.ROOT])
    assertEquals(PaneAnchor.DETAIL_ONLY, navigator.paneAnchor.value)
  }

  /**
   * A deep link arriving for another root brings that root forward: content nobody can see would be no
   * arrival at all.
   */
  @Test
  fun `when pushing detail onto another root, then that root is displayed`() = runTest {
    val navigator = navigator()

    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.OTHER))

    assertEquals(listOf(TestListKey.ROOT), navigator[TestListKey.ROOT])
    assertEquals(listOf(TestListKey.OTHER, detail), navigator[TestListKey.OTHER])
    assertEquals(TestListKey.OTHER, navigator.currentRoot.value)
  }

  /**
   * AND-9169: a notification opening a chat while the detail fills the window has to leave the window
   * where the user put it. The push carries the root switch, so nothing asks for the list on the way.
   */
  @Test
  fun `given a full screen detail, when pushing detail onto another root, then the pane stays put`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))

    navigator.processEvent(ListDetailEvents.Push(otherDetail, TestListKey.OTHER))

    assertEquals(TestListKey.OTHER, navigator.currentRoot.value)
    assertEquals(PaneAnchor.DETAIL_ONLY, navigator.paneAnchor.value)
  }

  /**
   * AND-9169 again, for the split window: the divider stays where it was dragged rather than snapping to
   * an end.
   */
  @Test
  fun `given a split window, when pushing detail, then the pane stays split`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.SPLIT))

    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))

    assertEquals(PaneAnchor.SPLIT, navigator.paneAnchor.value)
  }

  @Test
  fun `when exiting detail, then the current stack drops it and the list pane is revealed`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))

    navigator.processEvent(ListDetailEvents.ExitDetail)

    assertEquals(listOf(TestListKey.ROOT), navigator[TestListKey.ROOT])
    assertEquals(PaneAnchor.LIST_ONLY, navigator.paneAnchor.value)
  }

  /**
   * Popping one of several detail entries leaves detail on screen, so the pane it is displayed in has to
   * stay as the user left it.
   */
  @Test
  fun `given stacked detail, when popping, then the list pane is not revealed`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    navigator.processEvent(ListDetailEvents.Push(subScreen, TestListKey.ROOT))
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))

    navigator.processEvent(ListDetailEvents.Back)

    assertEquals(listOf(TestListKey.ROOT, detail), navigator[TestListKey.ROOT])
    assertEquals(PaneAnchor.DETAIL_ONLY, navigator.paneAnchor.value)
  }

  @Test
  fun `given one detail, when popping, then the list pane is revealed`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))

    navigator.processEvent(ListDetailEvents.Back)

    assertEquals(PaneAnchor.LIST_ONLY, navigator.paneAnchor.value)
  }

  @Test
  fun `when going to another root, then it is displayed and the list pane is revealed`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))

    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.OTHER))

    assertEquals(TestListKey.OTHER, navigator.currentRoot.value)
    assertEquals(PaneAnchor.LIST_ONLY, navigator.paneAnchor.value)
  }

  /**
   * Each root keeps its own stack, so a root that had detail open comes back to it rather than to its
   * list.
   */
  @Test
  fun `given detail open on a root, when leaving and returning to it, then the detail is still there`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))

    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.OTHER))
    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.ROOT))

    assertEquals(listOf(TestListKey.ROOT, detail), navigator[TestListKey.ROOT])
  }

  @Test
  fun `when pushing a list onto a root, then it is displayed above that root`() = runTest {
    val navigator = navigator()

    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.PUSHED, root = TestListKey.ROOT, push = true))

    assertEquals(listOf(TestListKey.ROOT, TestListKey.PUSHED), navigator[TestListKey.ROOT])
    assertEquals(TestListKey.ROOT, navigator.currentRoot.value)
  }

  @Test
  fun `given a pushed list, when going back to its root, then the pushed list is dropped`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.PUSHED, root = TestListKey.ROOT, push = true))

    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.ROOT))

    assertEquals(listOf(TestListKey.ROOT), navigator[TestListKey.ROOT])
  }

  @Test
  fun `when revealing the list, then the pane moves without the stack changing`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))

    navigator.processEvent(ListDetailEvents.RevealList)

    assertEquals(PaneAnchor.LIST_ONLY, navigator.paneAnchor.value)
    assertEquals(listOf(TestListKey.ROOT, detail), navigator[TestListKey.ROOT])
  }

  @Test
  fun `given a split window, then no pane is full screen`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.SPLIT))
    runCurrent()

    assertFalse(navigator.isFullScreenPane.value)

    navigator.processEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY))
    runCurrent()

    assertTrue(navigator.isFullScreenPane.value)
  }

  @Test
  fun `when the displayed stack changes, then the displayed list and detail follow it`() = runTest {
    val navigator = navigator()
    runCurrent()

    assertEquals(TestListKey.ROOT, navigator.displayedList.value)
    assertNull(navigator.detail.value)
    assertFalse(navigator.hasDetail.value)

    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    settle()

    assertEquals(detail, navigator.detail.value)
    assertTrue(navigator.hasDetail.value)

    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.PUSHED, root = TestListKey.ROOT, push = true))
    settle()

    assertEquals(TestListKey.PUSHED, navigator.displayedList.value)
  }

  @Test
  fun `when moving to another root, then the displayed detail is that root's`() = runTest {
    val navigator = navigator()
    navigator.processEvent(ListDetailEvents.Push(otherDetail, TestListKey.OTHER))
    navigator.processEvent(ListDetailEvents.Push(detail, TestListKey.ROOT))
    settle()

    assertEquals(detail, navigator.detail.value)

    navigator.processEvent(ListDetailEvents.GoToList(TestListKey.OTHER))
    settle()

    assertEquals(otherDetail, navigator.detail.value)
    assertEquals(TestListKey.OTHER, navigator.displayedList.value)
  }

  /** Publishes snapshot writes to `snapshotFlow`, then lets the flows collecting them run. */
  private fun TestScope.settle() {
    Snapshot.sendApplyNotifications()
    runCurrent()
  }
}
