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
}
