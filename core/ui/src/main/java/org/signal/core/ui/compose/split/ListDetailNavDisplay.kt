/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.signal.core.ui.navigation.TransitionSpecs

/**
 * The decorated entries of whichever stack [navigator] is currently displaying, which is what [entries]
 * wants.
 *
 * @param navigator the navigator whose stacks are displayed, from the view model that owns it.
 * @param entryProvider builds the [NavEntry] for a key, from `entryProvider { }`.
 */
@Composable
fun rememberCurrentDecoratedNavEntries(
  navigator: ListDetailNavigator<*, *>,
  entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
  val currentRoot by navigator.currentRoot.collectAsStateWithLifecycle()

  var currentEntries: List<NavEntry<NavKey>>? = null
  for ((root, stack) in navigator.stacks) {
    // Each stack needs decorators of its own: they are where its state is kept, and sharing them would
    // mean sharing that state.
    val entries = key(root) {
      rememberDecoratedNavEntries(
        backStack = stack,
        entryDecorators = listOf(
          rememberSaveableStateHolderNavEntryDecorator(),
          rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider
      )
    }

    if (root == currentRoot) {
      currentEntries = entries
    }
  }

  return checkNotNull(currentEntries) { "$currentRoot has no stack of its own." }
}

/**
 * Boilerplate code that displays [entries] as a list/detail split, or as a single pane when [layout] is null.
 *
 * Wires up the scene strategy, the pane transitions, the geometry and chrome the scene reads, and the
 * back handler for a detail pane filling the window.
 *
 * @param entries the stack to display. Which pane an entry lands in comes from how it was registered:
 *   [listEntry] for the list, [detailEntry] for the detail, and a plain `entry` for a screen that takes
 *   the whole window.
 * @param isSplitPane whether the window is wide enough to show both panes. When false the entries are
 *   displayed one at a time, whatever [layout] says.
 * @param paneAnchor where the divider currently sits, which decides whether back exits the detail pane.
 * @param onBack called when the user wishes to navigate back, which should go through your view model.
 * @param onExitDetail called when the user wishes to exit the detail pane, such as when the pane covers the whole screen.
 * @param modifier passed through to NavDisplay
 * @param layout the split geometry, from [rememberListDetailPaneLayout]. Null shows the list on its own.
 * @param listPaneChrome extra content around the list that is static, it will not animate as the list changes.
 * @param emptyDetailContent empty content when there's no detail specified.
 */
@Composable
fun ListDetailNavDisplay(
  entries: List<NavEntry<NavKey>>,
  isSplitPane: Boolean,
  paneAnchor: PaneAnchor,
  onBack: () -> Unit,
  onExitDetail: () -> Unit,
  modifier: Modifier = Modifier,
  layout: ListDetailPaneLayout? = null,
  listPaneChrome: ListPaneChrome? = null,
  emptyDetailContent: @Composable () -> Unit = {}
) {
  // A full-screen entry is above the detail pane rather than in it, so back pops it and leaves the anchor
  // beneath alone.
  val isFullScreen = entries.lastOrNull()?.isFullScreen == true

  BackHandler(!isFullScreen && isSplitPane && paneAnchor == PaneAnchor.DETAIL_ONLY) {
    onExitDetail()
  }

  val sceneStrategy = remember(isSplitPane) { ListDetailSceneStrategy(isSplitPane) }

  val paneShift = TransitionSpecs.paneShift()
  val paneShiftPop = TransitionSpecs.paneShift(pop = true)

  CompositionLocalProvider(
    LocalListDetailPaneLayout provides layout,
    LocalListPaneChrome provides listPaneChrome,
    LocalEmptyDetailContent provides emptyDetailContent
  ) {
    NavDisplay(
      entries = entries,
      sceneStrategies = listOf(sceneStrategy),
      transitionSpec = { paneShift },
      popTransitionSpec = { paneShiftPop },
      predictivePopTransitionSpec = { paneShiftPop },
      onBack = { onBack() },
      modifier = modifier
    )
  }
}
