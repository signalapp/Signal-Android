/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.ScreenTitlePane
import org.thoughtcrime.securesms.window.AppScaffold
import org.thoughtcrime.securesms.window.detailPaneMaxContentWidth
import org.thoughtcrime.securesms.window.isSplitPane
import org.thoughtcrime.securesms.window.rememberAppScaffoldNavigator

/**
 * Provides the common adaptive layout structure for recipient picker screens.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun RecipientPickerScaffold(
  title: String,
  forceSplitPane: Boolean,
  onNavigateUpClick: () -> Unit,
  topAppBarActions: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
  primaryContent: @Composable () -> Unit
) {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val isSplitPane = windowSizeClass.isSplitPane(forceSplitPane = forceSplitPane)

  AppScaffold(
    topBarContent = {
      Scaffolds.DefaultTopAppBar(
        title = if (!isSplitPane) title else "",
        titleContent = { _, titleText -> Text(text = titleText, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = onNavigateUpClick,
        actions = { topAppBarActions() }
      )
    },

    secondaryContent = {
      if (isSplitPane) {
        ScreenTitlePane(
          title = title,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        primaryContent()
      }
    },

    primaryContent = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        Box(modifier = Modifier.widthIn(max = windowSizeClass.detailPaneMaxContentWidth)) {
          primaryContent()
        }
      }
    },

    snackbarHost = {
      SnackbarHost(snackbarHostState)
    },

    navigator = rememberAppScaffoldNavigator(
      isSplitPane = isSplitPane
    )
  )
}

@AllDevicePreviews
@Composable
private fun RecipientPickerScaffoldPreview() {
  Previews.Preview {
    RecipientPickerScaffold(
      title = "Screen Title",
      forceSplitPane = false,
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
