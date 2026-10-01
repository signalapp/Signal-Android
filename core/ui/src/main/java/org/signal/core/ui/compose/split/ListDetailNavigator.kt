/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.signal.core.ui.createBackStack

/**
 * This is the navigation's brain, and allows for managing multiple backstacks for a single nav display.
 *
 * Intended to be held by a view model, which keeps its own screens' rules and delegates the stack and
 * pane bookkeeping here.
 *
 * @param savedStateHandle of the view-model who owns this object.
 * @param scope of the view-model who owns this object.
 * @param stackKeys one saved-state key per root, which is also the key the root's stack starts at. The
 *   keys are persisted, so changing one drops the stack it named.
 * @param initialRoot the root displayed before anything navigates. Unlike the stacks themselves this is not
 *   persisted, so a restored navigator comes back here with each root's stack intact.
 */
class ListDetailNavigator<L : ListNavKey, D : DetailNavKey>(
  savedStateHandle: SavedStateHandle,
  scope: CoroutineScope,
  stackKeys: Map<L, String>,
  initialRoot: L
) {

  /**
   * Every root's stack, keyed by root. Created up front: allocating a stack inside [snapshotsOf]'s
   * read-only snapshot would throw.
   */
  val stacks: Map<L, ListDetailBackStack> = stackKeys.mapValues { (root, key) ->
    savedStateHandle.createBackStack(key, root)
  }

  private val paneAnchorController = PaneAnchorController(savedStateHandle)

  private val internalCurrentRoot = MutableStateFlow(initialRoot)

  /** The root whose stack is currently displayed. */
  val currentRoot: StateFlow<L> = internalCurrentRoot.asStateFlow()

  /** How a split-pane window currently divides the two panes. Persisted. */
  val paneAnchor: StateFlow<PaneAnchor> = paneAnchorController.anchor

  /**
   * Whether one pane currently occupies the whole window. Only meaningful in split-pane layouts;
   * consumers that care about the single-pane case check the window size themselves.
   */
  val isFullScreenPane: StateFlow<Boolean> = paneAnchorController.isFullScreenPane
    .stateIn(scope, SharingStarted.Eagerly, false)

  /** The list displayed by the current root's stack. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val displayedList: StateFlow<L> = internalCurrentRoot
    .flatMapLatest { root -> snapshotsOf(root) { listLocation() } }
    .stateIn(scope, SharingStarted.Eagerly, currentStack.listLocation())

  /** The detail content displayed above the current list, or null when the list is showing on its own. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val detail: StateFlow<D?> = internalCurrentRoot
    .flatMapLatest { root -> snapshotsOf(root) { detailLocation() } }
    .stateIn(scope, SharingStarted.Eagerly, currentStack.detailLocation())

  /** Whether the current root's stack is displaying detail content. */
  val hasDetail: StateFlow<Boolean> = detail
    .map { it != null }
    .stateIn(scope, SharingStarted.Eagerly, false)

  /** The stack belonging to [root]. */
  operator fun get(root: L): ListDetailBackStack = stacks.getValue(root)

  /** The stack currently displayed. */
  private val currentStack: ListDetailBackStack
    get() = this[internalCurrentRoot.value]

  /**
   * Observes [read] against [root]'s stack.
   */
  fun <T> snapshotsOf(root: L, read: ListDetailBackStack.() -> T): Flow<T> {
    val stack = this[root]
    return snapshotFlow { stack.read() }
  }

  /**
   * Applies [event] to these stacks.
   */
  @Suppress("UNCHECKED_CAST")
  fun processEvent(event: ListDetailEvents) {
    when (event) {
      is ListDetailEvents.Push -> push((event.root as L?) ?: internalCurrentRoot.value, event.location)
      is ListDetailEvents.GoToList -> goToList(event.listRoute as L, event.root as L, event.push)
      ListDetailEvents.Back -> popCurrentDetail()
      ListDetailEvents.ExitDetail -> exitDetail()
      is ListDetailEvents.AnchorSelected -> onAnchorSelected(event.anchor)
      ListDetailEvents.RevealList -> revealList()
    }
  }

  /** The user dragged the pane divider to [anchor]. */
  private fun onAnchorSelected(anchor: PaneAnchor) {
    paneAnchorController.onAnchorSelected(anchor)
  }

  /** Reveals the list pane if the detail was filling the window, leaving the stacks as they are. */
  private fun revealList() {
    paneAnchorController.revealListPane()
  }

  /**
   * Pushes [location] onto [root]'s stack, wherever its kind belongs there, and displays that stack.
   *
   * Displaying [root] is part of the push rather than a separate move, so a push that arrives for another
   * root — a notification or deep link, say — reaches the window as one navigation. Asking for the root
   * first would mean asking for its list, and the pane would reveal that list on the way to content that
   * immediately covers it again.
   */
  private fun push(root: L, location: NavKey) {
    this[root].push(location)
    internalCurrentRoot.update { root }

    if (location is DetailNavKey) {
      paneAnchorController.revealDetailPane()
    }
  }

  /**
   * Drops the detail content above the current list, leaving that list displayed on its own.
   */
  private fun exitDetail() {
    currentStack.exitDetail()
    paneAnchorController.revealListPane()
  }

  /**
   * Pops the stack belonging to whichever root is displayed, revealing the list once the detail it was
   * covering is gone.
   */
  private fun popCurrentDetail() {
    val stack = currentStack
    stack.pop()

    if (!stack.hasDetail) {
      paneAnchorController.revealListPane()
    }
  }

  /**
   * Displays [listRoute], on the stack rooted at [root].
   *
   * @param listRoute the list to display.
   * @param root the stack it lives on. Defaults to [listRoute] itself, which is the case for a root list.
   * @param push whether to stack [listRoute] above the current list rather than returning to one already
   *   beneath it. Either way the detail content above stays where it is.
   */
  private fun goToList(listRoute: L, root: L = listRoute, push: Boolean = false) {
    val stack = this[root]

    if (push) {
      stack.push(listRoute)
    } else {
      stack.popToList(listRoute)
    }

    internalCurrentRoot.update { root }
    paneAnchorController.revealListPane()
  }

  @Suppress("UNCHECKED_CAST")
  private fun ListDetailBackStack.listLocation(): L = this[listIndex] as L

  @Suppress("UNCHECKED_CAST")
  private fun ListDetailBackStack.detailLocation(): D? = lastOrNull() as? D
}
