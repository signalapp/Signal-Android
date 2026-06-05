/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.ui.platform.LocalResources
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.signal.core.ui.rememberIsSplitPane

fun NavGraphBuilder.storiesNavGraphBuilder() {
  composable<MainNavigationDetailLocation.Empty> {
    if (LocalResources.current.rememberIsSplitPane()) {
      EmptyDetailScreen()
    }
  }
}
