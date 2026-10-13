/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.backStack

/**
 * A stack owned by a [SavedStateHandle] is persisted through `kotlinx.serialization` rather than
 * `Parcelable`, via `NavKeySerializer`, which writes each entry's concrete class name and reflects the
 * serializer back on restore. That only fails at runtime, and only after process death, so it is worth
 * covering directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListDetailBackStackPersistenceTest {

  private class Host(savedStateHandle: SavedStateHandle) {
    val backStack: ListDetailBackStack by savedStateHandle.backStack("test_back_stack", TestListKey.ROOT)
  }

  /** Saves and restores the handle the way a process death would. */
  private fun SavedStateHandle.roundTrip(): SavedStateHandle {
    return SavedStateHandle.createHandle(savedStateProvider().saveState(), null)
  }

  @Test
  fun `given a new stack, when restored, then it is still at its root`() {
    val handle = SavedStateHandle()
    Host(handle).backStack

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(listOf(TestListKey.ROOT), restored.toList())
  }

  @Test
  fun `given a stacked detail location, when restored, then the whole stack comes back`() {
    val handle = SavedStateHandle()
    Host(handle).backStack.apply {
      push(TestDetailKey(7))
      push(TestSubScreenKey(7, "movie night"))
    }

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(
      listOf(TestListKey.ROOT, TestDetailKey(7), TestSubScreenKey(7, "movie night")),
      restored.toList()
    )
  }

  /**
   * A list key is free to be an enum, which `@Parcelize` cannot handle — this is what lets list locations
   * sit at the root of a stack.
   */
  @Test
  fun `given a list location on the stack, when restored, then the enum entry comes back`() {
    val handle = SavedStateHandle()
    Host(handle).backStack.push(TestListKey.PUSHED)

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(TestListKey.PUSHED, restored.last())
  }

  @Test
  fun `given a stack mixing list and detail keys, when restored, then order and types are preserved`() {
    val handle = SavedStateHandle()
    Host(handle).backStack.apply {
      push(TestDetailKey(34))
      push(TestListKey.PUSHED)
    }

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(
      listOf(TestListKey.ROOT, TestListKey.PUSHED, TestDetailKey(34)),
      restored.toList()
    )
  }

  /**
   * The stack is a snapshot state list, so a change made after the first save has to be written by the
   * next one rather than the handle holding on to the state it was given.
   */
  @Test
  fun `given a stack that changed since the last save, when restored, then the change is included`() {
    val handle = SavedStateHandle()
    val backStack = Host(handle).backStack
    handle.roundTrip()

    backStack.push(TestDetailKey(1))
    val restored = Host(handle.roundTrip()).backStack

    assertEquals(listOf(TestListKey.ROOT, TestDetailKey(1)), restored.toList())
  }
}
