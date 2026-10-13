/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.signal.core.ui.compose.split.listEntry
import org.signal.core.ui.compose.split.rememberCurrentDecoratedNavEntries
import org.thoughtcrime.securesms.calls.CallsListPane
import org.thoughtcrime.securesms.calls.registerCallsTabDetailRoutes
import org.thoughtcrime.securesms.chats.ArchiveListPane
import org.thoughtcrime.securesms.chats.ChatsListPane
import org.thoughtcrime.securesms.chats.ConversationTransitionState
import org.thoughtcrime.securesms.chats.registerChatsTabDetailRoutes
import org.thoughtcrime.securesms.stories.StoriesListPane
import org.thoughtcrime.securesms.stories.registerStoriesTabDetailRoutes

/**
 * Builds our nav entries and decorates them so they can save state, handing back the entries of whichever
 * tab is displayed.
 *
 * A new *backstack* on [MainNavigationViewModel] is picked up here for free; a new *screen* needs a route
 * registering in [rememberMainNavEntryProvider].
 */
@Composable
fun rememberDecoratedDetailEntries(
  mainNavigationViewModel: MainNavigationViewModel,
  convoTransitionState: ConversationTransitionState,
  isSplitPane: Boolean
): List<NavEntry<NavKey>> {
  return rememberCurrentDecoratedNavEntries(
    navigator = mainNavigationViewModel.navigator,
    entryProvider = rememberMainNavEntryProvider(convoTransitionState, isSplitPane)
  )
}

/**
 * The entry-provider for main screen. The methods which are called are where you'd add additional
 * screens for different tabs.
 */
@Composable
private fun rememberMainNavEntryProvider(
  convoTransitionState: ConversationTransitionState,
  isSplitPane: Boolean
): (NavKey) -> NavEntry<NavKey> {
  return remember(convoTransitionState, isSplitPane) {
    entryProvider {
      registerMainScreenRoutes()
      registerChatsTabDetailRoutes(convoTransitionState)
      registerCallsTabDetailRoutes(isSplitPane)
      registerStoriesTabDetailRoutes()
    }
  }
}

/**
 * Registers the main list of tab routes (list locations)
 */
private fun EntryProviderScope<NavKey>.registerMainScreenRoutes() {
  listEntry<MainListRoute> { location ->
    when (location) {
      MainListRoute.Chats -> ChatsListPane(modifier = Modifier.fillMaxSize())
      MainListRoute.Archive -> ArchiveListPane(modifier = Modifier.fillMaxSize())
      MainListRoute.Calls -> CallsListPane(modifier = Modifier.fillMaxSize())
      MainListRoute.Stories -> StoriesListPane(modifier = Modifier.fillMaxSize())
    }
  }
}
