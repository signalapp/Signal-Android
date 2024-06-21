/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/**
 * Default Navigation utilities for compose.
 */
object Nav {

  @Composable
  fun Host(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    route: String? = null,
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = { slideInHorizontally(initialOffsetX = { it }) },
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = { slideOutHorizontally(targetOffsetX = { -it }) },
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = { slideInHorizontally(initialOffsetX = { -it }) },
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = { slideOutHorizontally(targetOffsetX = { it }) },
    builder: NavGraphBuilder.() -> Unit
  ) {
    NavHost(
      navController = navController,
      startDestination = startDestination,
      modifier = modifier,
      route = route,
      enterTransition = enterTransition,
      exitTransition = exitTransition,
      popEnterTransition = popEnterTransition,
      popExitTransition = popExitTransition,
      builder = builder
    )
  }
}
