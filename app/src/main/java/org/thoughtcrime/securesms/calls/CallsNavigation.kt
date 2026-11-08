/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameScreen
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsScreen
import org.thoughtcrime.securesms.calls.log.CallLogFragment
import org.thoughtcrime.securesms.main.MainDetailRoute

/**
 * Registers the routes utilized for the main screen calls tab.
 */
fun EntryProviderScope<NavKey>.registerCallsTabDetailRoutes(isSplitPane: Boolean) {
  detailEntry<MainDetailRoute.CallLinkDetails>(
    metadata = if (isSplitPane) TransitionSpecs.None.metadata else emptyMap()
  ) { route ->
    CallLinkDetailsEntry(route)
  }

  detailEntry<MainDetailRoute.Calls.CallLinks.EditCallLinkName> { route ->
    EditCallLinkNameEntry(route)
  }
}

/**
 * List pane content for the calls tab.
 */
@Composable
fun CallsListPane(modifier: Modifier = Modifier) {
  AndroidFragment(
    clazz = CallLogFragment::class.java,
    fragmentState = rememberFragmentState(),
    modifier = modifier
  )
}

@Composable
private fun CallLinkDetailsEntry(route: MainDetailRoute.CallLinkDetails) {
  informNavigatorWeAreReady()

  CallLinkDetailsScreen(roomId = route.callLinkRoomId)
}

@Composable
private fun EditCallLinkNameEntry(route: MainDetailRoute.Calls.CallLinks.EditCallLinkName) {
  informNavigatorWeAreReady()

  EditCallLinkNameScreen(
    roomId = route.callLinkRoomId,
    initialName = route.currentName
  )
}

@Composable
private fun informNavigatorWeAreReady() {
  val navigator = LocalActivity.current as? MainNavigator.NavigatorProvider
  LaunchedEffect(navigator) {
    navigator?.onFirstRender()
  }
}
