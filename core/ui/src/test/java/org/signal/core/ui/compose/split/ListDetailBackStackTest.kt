/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListDetailBackStackTest {

  private val contentRoot = TestDetailKey(1)
  private val otherContentRoot = TestDetailKey(2)
  private val subScreen = TestSubScreenKey(1)

  private fun backStack(root: TestListKey = TestListKey.ROOT): ListDetailBackStack {
    return NavBackStack(root)
  }

  @Test
  fun `given a new stack, then it displays its root list with no detail`() {
    val backStack = backStack()

    assertEquals(TestListKey.ROOT, backStack.listLocation<TestListKey>())
    assertFalse(backStack.hasDetail)
  }

  @Test
  fun `given a stack with only a list, when pushing detail, then detail is displayed above it`() {
    val backStack = backStack()

    backStack.push(contentRoot)

    assertTrue(backStack.hasDetail)
    assertEquals(TestListKey.ROOT, backStack.listLocation<TestListKey>())
    assertEquals(listOf(TestListKey.ROOT, contentRoot), backStack.toList())
  }

  @Test
  fun `given detail is displayed, when pushing another content root, then it replaces the previous detail`() {
    val backStack = backStack()

    backStack.push(contentRoot)
    backStack.push(subScreen)
    backStack.push(otherContentRoot)

    assertEquals(listOf(TestListKey.ROOT, otherContentRoot), backStack.toList())
  }

  @Test
  fun `given detail is displayed, when pushing a sub screen, then it stacks on top`() {
    val backStack = backStack()

    backStack.push(contentRoot)
    backStack.push(subScreen)

    assertEquals(listOf(TestListKey.ROOT, contentRoot, subScreen), backStack.toList())
  }

  @Test
  fun `given an entry on top, when pushing that same entry, then it is not duplicated`() {
    val backStack = backStack()

    backStack.push(contentRoot)
    backStack.push(contentRoot)

    assertEquals(listOf(TestListKey.ROOT, contentRoot), backStack.toList())
  }

  @Test
  fun `given a list is displayed, when pushing another list, then that list is displayed`() {
    val backStack = backStack()

    backStack.push(TestListKey.PUSHED)

    assertEquals(TestListKey.PUSHED, backStack.listLocation<TestListKey>())
    assertFalse(backStack.hasDetail)
  }

  @Test
  fun `given a list is already displayed, when pushing it again, then it is not duplicated`() {
    val backStack = backStack()

    backStack.push(TestListKey.ROOT)

    assertEquals(listOf(TestListKey.ROOT), backStack.toList())
  }

  /**
   * Pushing a list while detail is open must not blank the detail pane, so the list is inserted beneath
   * the detail content rather than on top of it.
   */
  @Test
  fun `given detail is displayed, when pushing a list, then the detail stays displayed`() {
    val backStack = backStack()
    backStack.push(contentRoot)

    backStack.push(TestListKey.PUSHED)

    assertEquals(TestListKey.PUSHED, backStack.listLocation<TestListKey>())
    assertTrue(backStack.hasDetail)
    assertEquals(
      listOf(TestListKey.ROOT, TestListKey.PUSHED, contentRoot),
      backStack.toList()
    )
  }

  /**
   * Because lists sit beneath detail content, back closes the detail before leaving the list it was
   * opened from.
   */
  @Test
  fun `given detail over a pushed list, when popping, then the detail closes before the list`() {
    val backStack = backStack()
    backStack.push(contentRoot)
    backStack.push(TestListKey.PUSHED)

    backStack.pop()

    assertEquals(TestListKey.PUSHED, backStack.listLocation<TestListKey>())
    assertFalse(backStack.hasDetail)

    backStack.pop()

    assertEquals(TestListKey.ROOT, backStack.listLocation<TestListKey>())
  }

  @Test
  fun `given a pushed list, when popping to the list beneath it, then that list is displayed`() {
    val backStack = backStack()
    backStack.push(TestListKey.PUSHED)

    backStack.popToList(TestListKey.ROOT)

    assertEquals(listOf(TestListKey.ROOT), backStack.toList())
  }

  @Test
  fun `given detail over a pushed list, when popping to the list beneath it, then the detail stays displayed`() {
    val backStack = backStack()
    backStack.push(contentRoot)
    backStack.push(TestListKey.PUSHED)

    backStack.popToList(TestListKey.ROOT)

    assertEquals(TestListKey.ROOT, backStack.listLocation<TestListKey>())
    assertTrue(backStack.hasDetail)
    assertEquals(listOf(TestListKey.ROOT, contentRoot), backStack.toList())
  }

  @Test
  fun `given the displayed list is already the target, when popping to it, then the stack is unchanged`() {
    val backStack = backStack()
    backStack.push(contentRoot)

    backStack.popToList(TestListKey.ROOT)

    assertEquals(listOf(TestListKey.ROOT, contentRoot), backStack.toList())
  }

  /**
   * Every stack keeps its own root list, so a list belonging to another stack is not on it. Stripping
   * lists in search of one would leave nothing to display.
   */
  @Test
  fun `given a location that is not on the stack, when popping to it, then the stack is unchanged`() {
    val backStack = backStack()
    backStack.push(TestListKey.PUSHED)
    backStack.push(contentRoot)

    backStack.popToList(TestListKey.OTHER)

    assertEquals(
      listOf(TestListKey.ROOT, TestListKey.PUSHED, contentRoot),
      backStack.toList()
    )
  }

  @Test
  fun `given a pushed list, when popped, then the previous list is displayed again`() {
    val backStack = backStack()
    backStack.push(TestListKey.PUSHED)

    assertTrue(backStack.pop())

    assertEquals(TestListKey.ROOT, backStack.listLocation<TestListKey>())
  }

  @Test
  fun `given a stack at its root, when popped, then nothing is removed`() {
    val backStack = backStack()

    assertFalse(backStack.pop())

    assertEquals(listOf(TestListKey.ROOT), backStack.toList())
  }

  /**
   * Handing an empty list to a NavDisplay throws, so popping must never drain the stack no matter how
   * many times it is called.
   */
  @Test
  fun `given a deep stack, when popped past the root, then the stack is never empty`() {
    val backStack = backStack()
    backStack.push(TestListKey.PUSHED)
    backStack.push(contentRoot)
    backStack.push(subScreen)

    repeat(10) { backStack.pop() }

    assertTrue(backStack.isNotEmpty())
    assertEquals(listOf(TestListKey.ROOT), backStack.toList())
  }

  @Test
  fun `given stacked detail, when exiting detail, then only the detail is dropped`() {
    val backStack = backStack()
    backStack.push(contentRoot)
    backStack.push(subScreen)

    backStack.exitDetail()

    assertFalse(backStack.hasDetail)
    assertEquals(listOf(TestListKey.ROOT), backStack.toList())
  }

  @Test
  fun `given detail opened from a pushed list, when exiting detail, then the pushed list stays`() {
    val backStack = backStack()
    backStack.push(TestListKey.PUSHED)
    backStack.push(contentRoot)

    backStack.exitDetail()

    assertEquals(TestListKey.PUSHED, backStack.listLocation<TestListKey>())
    assertFalse(backStack.hasDetail)
    assertEquals(listOf(TestListKey.ROOT, TestListKey.PUSHED), backStack.toList())
  }

  @Test
  fun `given no detail, when exiting detail, then the stack is unchanged`() {
    val backStack = backStack()

    backStack.exitDetail()

    assertEquals(listOf(TestListKey.ROOT), backStack.toList())
  }

  @Test(expected = ClassCastException::class)
  fun `given a list key of another type, when reading the list location, then it throws`() {
    val backStack: ListDetailBackStack = NavBackStack(OtherListKey)

    backStack.listLocation<TestListKey>()
  }

  private object OtherListKey : ListNavKey
}
