/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import org.signal.core.ui.compose.Animations.navHostSlideInTransition
import org.signal.core.ui.compose.Animations.navHostSlideOutTransition

/**
 * A navigation host for the stories detail pane of [org.thoughtcrime.securesms.MainActivity].
 *
 * @param currentDestination The current calls destination to navigate to, containing routing information
 * @param contentLayoutData Layout configuration data for responsive UI rendering
 */
@Composable
fun StoriesNavHost(
  navHostController: NavHostController,
  startDestination: MainNavigationDetailLocation.Stories,
  contentLayoutData: MainContentLayoutData
) {
  NavHost(
    navController = navHostController,
    startDestination = startDestination,
    enterTransition = { navHostSlideInTransition { it } },
    exitTransition = { navHostSlideOutTransition { -it } },
    popEnterTransition = { navHostSlideInTransition { -it } },
    popExitTransition = { navHostSlideOutTransition { it } }
  ) {
  }
}
