/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Wraps [AppScaffold], adding a top app bar that spans across both the list and detail panes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppScaffoldWithTopBar(
  navigator: AppScaffoldNavigator<Any> = rememberAppScaffoldNavigator(),
  topBarContent: @Composable () -> Unit = {},
  primaryContent: @Composable () -> Unit = {},
  navRailContent: @Composable () -> Unit = {},
  bottomNavContent: @Composable () -> Unit = {},
  paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
  paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
  animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default,
  secondaryContent: @Composable () -> Unit
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()
  val isSplitPane = windowSizeClass.isSplitPane(
    forceSplitPaneOnCompactLandscape = if (LocalInspectionMode.current) false else SignalStore.internal.forceSplitPaneOnCompactLandscape
  )

  if (isSplitPane) {
    Column {
      topBarContent()

      AppScaffold(
        navigator = navigator,
        primaryContent = primaryContent,
        navRailContent = navRailContent,
        bottomNavContent = bottomNavContent,
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = paneExpansionDragHandle,
        animatorFactory = animatorFactory,
        secondaryContent = secondaryContent
      )
    }
  } else {
    AppScaffold(
      navigator = navigator,
      primaryContent = primaryContent,
      navRailContent = navRailContent,
      bottomNavContent = bottomNavContent,
      paneExpansionState = paneExpansionState,
      paneExpansionDragHandle = paneExpansionDragHandle,
      animatorFactory = animatorFactory,
      secondaryContent = {
        Scaffold(topBar = topBarContent) { paddingValues ->
          Box(modifier = Modifier.padding(paddingValues)) {
            secondaryContent()
          }
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@AllDevicePreviews
@Composable
private fun AppScaffoldWithTopBarPreview() {
  Previews.Preview {
    val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()
    val isSplitPane = windowSizeClass.isSplitPane(forceSplitPaneOnCompactLandscape = false)

    AppScaffoldWithTopBar(
      navigator = rememberAppScaffoldNavigator(),

      topBarContent = {
        Scaffolds.DefaultTopAppBar(
          title = if (!isSplitPane) stringResource(R.string.NewConversationActivity__new_message) else "",
          titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
          navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
          navigationContentDescription = "",
          onNavigationClick = { }
        )
      },

      secondaryContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        ) {
          Text(
            text = "ListContent\n$windowSizeClass",
            textAlign = TextAlign.Center
          )
        }
      },

      primaryContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
        ) {
          Text(
            text = "DetailContent",
            textAlign = TextAlign.Center
          )
        }
      }
    )
  }
}
