/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.signal.appsettings.chats.ChatsSettingsScreen
import org.signal.appsettings.chats.chatsSettingsPreviewState
import org.signal.appsettings.home.AppSettingsScreen
import org.signal.appsettings.home.PreviewAvatar
import org.signal.appsettings.home.appSettingsPreviewState
import org.signal.core.ui.compose.BreakpointPreviews
import org.signal.core.ui.compose.LocalSplitPaneSettingsEnabled
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.ListDetailNavDisplay
import org.signal.core.ui.compose.split.PaneAnchor
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.compose.split.listEntry
import org.signal.core.ui.compose.split.rememberCurrentDecoratedNavEntries
import org.signal.core.ui.compose.split.rememberListDetailPaneLayout
import org.signal.core.ui.compose.systemBarsCompat
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberIsSplitPane

/**
 * Displays app settings as a list/detail split, with the home screen in the list pane and every [AppSettingsRoute] in
 * the detail pane beside it, or one at a time on a narrow window. The split is fixed; the divider can't be dragged. Navigation arrives as [AppSettingsNavigationEvent]s
 * sent to [router], whose navigator owns the back stack.
 *
 * @param router the router whose [AppSettingsRouter.onEvent] the screens are handed as their parent event emitter.
 * @param onClose called when a screen asks to navigate back from the home screen, such as with its navigation icon. A
 *   system back with nothing left to pop isn't handled here, and falls through to the host's own back handling.
 * @param home the home screen's content, given the section to mark as selected. Always null outside of split-pane,
 *   where the list is never shown beside the section it opened.
 * @param emptyDetailContent shown in the detail pane of a split-pane window while no [AppSettingsRoute] is open.
 * @param detailEntries registers the content for each [AppSettingsRoute], with `detailEntry`.
 */
@Composable
fun AppSettingsNavigation(
  router: AppSettingsRouter,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  home: @Composable (selectedRoute: AppSettingsRoute?) -> Unit,
  emptyDetailContent: @Composable () -> Unit = {},
  detailEntries: EntryProviderScope<NavKey>.() -> Unit
) {
  val currentOnClose by rememberUpdatedState(onClose)
  val navigator = router.navigator

  val navigate: (AppSettingsNavigationEvent) -> Unit = remember(router) {
    { event ->
      if (!router.navigate(event)) {
        currentOnClose()
      }
    }
  }

  LaunchedEffect(router) {
    router.events.collect(navigate)
  }

  val isSplitPane = LocalResources.current.rememberIsSplitPane()

  val entryProvider = remember(router, isSplitPane, home, detailEntries) {
    entryProvider {
      listEntry<AppSettingsHomeRoute> {
        val selectedRoute by router.selectedRoute.collectAsStateWithLifecycle()
        home(selectedRoute.takeIf { isSplitPane })
      }
      detailEntries()
    }
  }

  val entries = rememberCurrentDecoratedNavEntries(navigator, entryProvider)

  val insetsModifier = if (isSplitPane) {
    Modifier
      .windowInsetsPadding(WindowInsets.systemBarsCompat)
      .displayCutoutPadding()
  } else {
    Modifier.windowInsetsPadding(
      WindowInsets.navigationBarsCompat.only(WindowInsetsSides.Horizontal)
        .add(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    )
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(if (isSplitPane) SignalTheme.colors.colorSurface4 else MaterialTheme.colorScheme.background)
      .then(insetsModifier)
  ) {
    // Settings screens are shared with the fragment host, which keeps their current look until it goes away.
    CompositionLocalProvider(LocalSplitPaneSettingsEnabled provides true) {
      ListDetailNavDisplay(
        entries = entries,
        isSplitPane = isSplitPane,
        paneAnchor = PaneAnchor.SPLIT,
        onBack = { navigate(AppSettingsNavigationEvent.NavigateBack) },
        onExitDetail = { navigator.processEvent(ListDetailEvents.ExitDetail) },
        layout = rememberListDetailPaneLayout(
          paneAnchor = PaneAnchor.SPLIT,
          maxWidth = maxWidth,
          onAnchorSelected = {},
          isResizable = false
        ),
        emptyDetailContent = emptyDetailContent
      )
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@BreakpointPreviews
@Composable
private fun AppSettingsNavigationPreview() {
  AppSettingsNavigationPreviewContent(openRoute = null)
}

@PreviewWrapper(SignalPreviewWrapper::class)
@BreakpointPreviews
@Composable
private fun AppSettingsNavigationWithDetailPreview() {
  AppSettingsNavigationPreviewContent(openRoute = AppSettingsRoute.ChatsRoute.Chats)
}

@Composable
private fun AppSettingsNavigationPreviewContent(openRoute: AppSettingsRoute?) {
  val router = remember {
    AppSettingsRouter(SavedStateHandle()).apply {
      openRoute?.let { navigator.processEvent(ListDetailEvents.Push(it)) }
    }
  }

  AppSettingsNavigation(
    router = router,
    onClose = {},
    home = { selectedRoute ->
      AppSettingsScreen(
        state = appSettingsPreviewState,
        onEvent = {},
        selectedRoute = selectedRoute,
        avatar = { PreviewAvatar() }
      )
    },
    detailEntries = {
      detailEntry<AppSettingsRoute.ChatsRoute.Chats> {
        ChatsSettingsScreen(
          state = chatsSettingsPreviewState,
          onEvent = {}
        )
      }
    }
  )
}
