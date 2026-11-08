/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.createBackStack

/**
 * Owns a single persisted back stack of [T]s for a `NavDisplay`, and the [resultBus] its screens pass results through.
 *
 * Held as a view model so that screens' view models can be handed emitters that capture this rather than the host,
 * which doesn't outlive a configuration change.
 *
 * @param startRoute the bottom of the stack, which [navigateBack] never pops.
 */
class NavRouter<T : NavKey>(
  savedStateHandle: SavedStateHandle,
  startRoute: T
) : ViewModel() {

  companion object {
    private const val BACK_STACK_KEY = "nav_router.back_stack"
  }

  val backStack: NavBackStack<NavKey> = savedStateHandle.createBackStack(BACK_STACK_KEY, startRoute)

  val resultBus = ResultEventBus()

  private val _exitRequests = Channel<Unit>(Channel.CONFLATED)

  /** Emits when something asks to go back from [startRoute], which leaves the stack altogether and so is up to the host. */
  val exitRequests: Flow<Unit> = _exitRequests.receiveAsFlow()

  /** Pushes [route], unless it's already on top. */
  fun navigateTo(route: T) {
    if (backStack.lastOrNull() != route) {
      backStack.add(route)
    }
  }

  /** Pops the top of the stack, or asks the host to exit when there's nothing beneath it. */
  fun navigateBack() {
    if (backStack.size > 1) {
      backStack.removeAt(backStack.lastIndex)
    } else {
      _exitRequests.trySend(Unit)
    }
  }
}
