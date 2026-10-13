/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.signal.core.ui.compose.BreakpointPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.detailPaneMaxContentWidth
import org.signal.core.ui.horizontalPartitionDefaultSpacerSize
import org.signal.core.ui.listPaneDefaultPreferredWidth
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.ScreenTitlePane

/**
 * Provides the common adaptive layout structure for recipient picker screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientPickerScaffold(
  title: String,
  onNavigateUpClick: () -> Unit,
  topAppBarActions: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
  primaryContent: @Composable () -> Unit,
  floatingActionButton: (@Composable () -> Unit)? = null
) {
  val isSplitPane = LocalResources.current.rememberIsSplitPane()
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
      Scaffolds.DefaultTopAppBar(
        title = if (!isSplitPane) title else "",
        titleContent = { _, titleText -> Text(text = titleText, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = SignalIcons.ArrowStart.imageVector,
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = onNavigateUpClick,
        actions = { topAppBarActions() }
      )
    },
    snackbarHost = {
      SnackbarHost(snackbarHostState)
    }
  ) { paddingValues ->
    if (isSplitPane) {
      SplitPaneLayout(
        title = title,
        windowSizeClass = windowSizeClass,
        modifier = Modifier.padding(paddingValues)
      ) {
        Box(modifier = Modifier.widthIn(max = windowSizeClass.detailPaneMaxContentWidth)) {
          primaryContent()
          FloatingActionButtonContainer(floatingActionButton)
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        primaryContent()
        FloatingActionButtonContainer(floatingActionButton)
      }
    }
  }
}

/**
 * Places the screen title beside [content], capping the title pane at [listPaneDefaultPreferredWidth] and splitting
 * the available width evenly below twice that.
 */
@Composable
private fun SplitPaneLayout(
  title: String,
  windowSizeClass: WindowSizeClass,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val spacerWidth = windowSizeClass.horizontalPartitionDefaultSpacerSize

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val titlePaneWidth = ((maxWidth - spacerWidth) / 2).coerceAtMost(windowSizeClass.listPaneDefaultPreferredWidth)

    Row(modifier = Modifier.fillMaxSize()) {
      ScreenTitlePane(
        title = title,
        modifier = Modifier
          .width(titlePaneWidth)
          .fillMaxHeight()
      )

      Spacer(modifier = Modifier.width(spacerWidth))

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        content()
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.FloatingActionButtonContainer(
  button: (@Composable () -> Unit)?
) {
  if (button != null) {
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .imePadding()
        .padding(
          start = 16.dp,
          end = 16.dp,
          bottom = if (WindowInsets.isImeVisible) 0.dp else 16.dp
        )
    ) {
      button()
    }
  }
}

@BreakpointPreviews
@Composable
private fun RecipientPickerScaffoldPreview() {
  Previews.Preview {
    RecipientPickerScaffold(
      title = "Screen Title",
      onNavigateUpClick = {},
      topAppBarActions = {},
      snackbarHostState = SnackbarHostState(),
      primaryContent = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Gray),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "primaryContent")
        }
      }
    )
  }
}
