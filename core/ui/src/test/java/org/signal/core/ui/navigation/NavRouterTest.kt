/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric because the stack is owned by a [SavedStateHandle], which stores it in a `Bundle`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavRouterTest {

  @Serializable
  data class TestRoute(val id: Int) : NavKey

  private val start = TestRoute(0)
  private val first = TestRoute(1)
  private val second = TestRoute(2)

  @Test
  fun `a new router starts on its start route`() {
    val router = NavRouter(SavedStateHandle(), start)

    assertEquals(listOf(start), router.backStack)
  }

  @Test
  fun `navigating stacks routes, and back pops them`() {
    val router = NavRouter(SavedStateHandle(), start)

    router.navigateTo(first)
    router.navigateTo(second)
    assertEquals(listOf(start, first, second), router.backStack)

    router.navigateBack()
    assertEquals(listOf(start, first), router.backStack)
  }

  @Test
  fun `navigating to the route already on top does nothing`() {
    val router = NavRouter(SavedStateHandle(), start)

    router.navigateTo(first)
    router.navigateTo(first)

    assertEquals(listOf(start, first), router.backStack)
  }

  @Test
  fun `back from the start route asks the host to exit and keeps the stack`() = runTest(UnconfinedTestDispatcher()) {
    val router = NavRouter(SavedStateHandle(), start)
    var exited = false
    backgroundScope.launch { router.exitRequests.first().also { exited = true } }

    router.navigateBack()

    assertTrue(exited)
    assertEquals(listOf(start), router.backStack)
  }

  @Test
  fun `the stack survives being saved and restored`() {
    val handle = SavedStateHandle()
    NavRouter(handle, start).apply {
      navigateTo(first)
      navigateTo(second)
    }

    val restored = SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)

    assertEquals(listOf(start, first, second), NavRouter(restored, start).backStack)
  }
}
