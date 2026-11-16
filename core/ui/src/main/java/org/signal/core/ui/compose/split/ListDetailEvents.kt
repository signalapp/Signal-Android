/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.navigation3.runtime.NavKey

/**
 * What a screen can ask of a [ListDetailNavigator]: the moves that are navigation and nothing else.
 */
sealed interface ListDetailEvents {

  /**
   * Push [location] onto [root]'s stack, or onto the displayed one when [root] is null, and display that
   * stack. See [ListDetailNavigator.push].
   */
  data class Push(val location: NavKey, val root: ListNavKey? = null) : ListDetailEvents

  /** Display [listRoute], on [root]'s stack. See [ListDetailNavigator.goToList]. */
  data class GoToList(
    val listRoute: ListNavKey,
    val root: ListNavKey = listRoute,
    val push: Boolean = false
  ) : ListDetailEvents

  /** Back, which drops the top of the displayed stack. */
  data object Back : ListDetailEvents

  /** Drop all the detail above the displayed list, leaving that list on its own. */
  data object ExitDetail : ListDetailEvents

  /** The user dragged the pane divider. */
  data class AnchorSelected(val anchor: PaneAnchor) : ListDetailEvents

  /** Reveal the list pane if the detail was filling the window, leaving the stacks alone. */
  data object RevealList : ListDetailEvents

  /**
   * Hold back the entry the next push displays until it calls [DeferredEnter.start], or [DEFERRED_ENTER_TIMEOUT] passes,
   * for a window that isn't on screen yet. While held, the push shows its entry without an enter transition, since there
   * is nothing on screen to animate from. A push that leaves the same entry on top releases the hold straight away.
   */
  data object HoldNextEnter : ListDetailEvents

  /** The entry on top of the displayed stack is ready to be shown, which ends a [HoldNextEnter] waiting on it. */
  data object TopEntryReady : ListDetailEvents
}
