/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.signal.core.ui.NavigationType
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.ListDetailNavDisplay
import org.signal.core.ui.compose.split.ListPaneChrome
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.compose.split.listEntry
import org.signal.core.ui.compose.split.rememberCurrentDecoratedNavEntries
import org.signal.core.ui.compose.split.rememberListDetailPaneLayout
import org.signal.core.ui.compose.split.rememberListDetailPaneMetrics
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberIsSplitPane
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log

/**
 * A small list/detail app built on `org.signal.core.ui.compose.split`.
 *
 * [DemoScreen] is where it comes together, and every argument [ListDetailNavDisplay] takes is built in
 * this file. The nav keys are in `DemoRoutes.kt`, the stacks behind them in `DemoViewModel.kt`, and the
 * panes' content in `DemoScreens.kt`.
 */
class MainActivity : ComponentActivity() {

  private val viewModel: DemoViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // So that the event each screen reports shows up in logcat, which is half the point of the pattern.
    Log.initialize(AndroidLogger)

    enableEdgeToEdge()

    setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        DemoScreen(viewModel)
      }
    }
  }
}

@Composable
private fun DemoScreen(viewModel: DemoViewModel) {
  val onEvent: (DemoEvents) -> Unit = viewModel::onEvent

  val isSplitPane = LocalResources.current.rememberIsSplitPane()
  val paneAnchor by viewModel.paneAnchor.collectAsStateWithLifecycle()
  val displayedList by viewModel.displayedList.collectAsStateWithLifecycle()
  val hasRail = NavigationType.rememberNavigationType() == NavigationType.RAIL

  val entries = rememberDemoEntries(viewModel)
  val listPaneChrome: ListPaneChrome = remember(displayedList, onEvent) { { content -> DemoListPaneChrome(displayedList, onEvent, content) } }
  val emptyDetailContent: @Composable () -> Unit = remember { { EmptyDetailPane() } }

  SignalTheme(incognitoKeyboardEnabled = false) {
    Surface {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
          .background(if (isSplitPane) SignalTheme.colors.colorSurface1 else MaterialTheme.colorScheme.surface)
          .systemBarsPadding()
      ) {
        ListDetailNavDisplay(
          entries = entries,
          isSplitPane = isSplitPane,
          paneAnchor = paneAnchor,
          onBack = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Back)) },
          onExitDetail = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.ExitDetail)) },
          layout = rememberListDetailPaneLayout(
            paneAnchor = paneAnchor,
            maxWidth = maxWidth,
            onAnchorSelected = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.AnchorSelected(it))) },
            collapsedListWidth = if (hasRail) RAIL_WIDTH else 0.dp
          ),
          listPaneChrome = listPaneChrome,
          emptyDetailContent = emptyDetailContent
        )
      }
    }
  }
}

/**
 * The entry provider, and the decorated entries of the displayed tab's stack. Lists go through
 * [listEntry], detail content through [detailEntry], and a screen that takes the whole window through a
 * plain `entry`.
 */
@Composable
private fun rememberDemoEntries(viewModel: DemoViewModel): List<NavEntry<NavKey>> {
  val entryProvider = remember(viewModel) {
    entryProvider {
      listEntry<DemoListRoute> { route ->
        val detail by viewModel.detail.collectAsStateWithLifecycle()

        ItemListPane(
          route = route,
          selectedItemId = detail?.itemId,
          onEvent = viewModel::onEvent
        )
      }

      detailEntry<DemoDetailRoute.Item> { route ->
        ItemDetailPane(
          item = DemoData[route.itemId],
          onEvent = viewModel::onEvent
        )
      }

      detailEntry<DemoDetailRoute.Notes> { route ->
        ItemNotesPane(
          item = DemoData[route.itemId],
          onEvent = viewModel::onEvent
        )
      }

      // A plain entry: neither pane claims it, so it is displayed over both of them.
      entry<DemoSettingsRoute> {
        SettingsScreen(onEvent = viewModel::onEvent)
      }
    }
  }

  return rememberCurrentDecoratedNavEntries(viewModel.navigator, entryProvider)
}

/**
 * List pane chrome that won't animate when the list changes (other than your own animations ofc.) for stuff like nav rails, megaphones, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoListPaneChrome(
  displayedList: DemoListRoute,
  onEvent: (DemoEvents) -> Unit,
  content: @Composable () -> Unit
) {
  val navigationType = NavigationType.rememberNavigationType()
  val metrics = rememberListDetailPaneMetrics()

  Row(modifier = Modifier.fillMaxSize()) {
    if (navigationType == NavigationType.RAIL) {
      NavigationRail(containerColor = Color.Transparent) {
        TABS.forEach { (route, icon) ->
          NavigationRailItem(
            selected = displayedList.tab == route,
            onClick = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.GoToList(route))) },
            icon = { TabIcon(icon, route) },
            label = { Text(text = route.label) }
          )
        }
      }
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface, metrics.shape)
        .clip(metrics.shape)
    ) {
      TopAppBar(
        title = { Text(text = displayedList.label) },
        navigationIcon = {
          if (displayedList == DemoListRoute.ARCHIVE) {
            IconButton(onClick = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Back)) }) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        actions = {
          IconButton(onClick = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Push(DemoSettingsRoute))) }) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
      )

      Box(modifier = Modifier.weight(1f)) {
        content()
      }

      if (navigationType == NavigationType.BAR) {
        NavigationBar(
          containerColor = Color.Transparent,
          modifier = Modifier.clip(metrics.navigationBarShape)
        ) {
          TABS.forEach { (route, icon) ->
            NavigationBarItem(
              selected = displayedList.tab == route,
              onClick = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.GoToList(route))) },
              icon = { TabIcon(icon, route) },
              label = { Text(text = route.label) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TabIcon(icon: ImageVector, route: DemoListRoute) {
  Icon(imageVector = icon, contentDescription = route.label)
}

/** The tabs the chrome offers. The archive is reached from inside the inbox, so it is not one of them. */
private val TABS = listOf(
  DemoListRoute.INBOX to Icons.Filled.Inbox,
  DemoListRoute.CONTACTS to Icons.Filled.Person
)

/** What is left of the list pane once the detail fills the window: the navigation rail, when there is one. */
private val RAIL_WIDTH = 80.dp
