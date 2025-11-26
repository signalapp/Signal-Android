/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.storiesNavGraphBuilder() {
  composable<MainNavigationDetailLocation.Empty> {
    EmptyDetailScreen()
  }
}
