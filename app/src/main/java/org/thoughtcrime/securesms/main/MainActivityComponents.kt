/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import org.thoughtcrime.securesms.R

/**
 * Displayed when the user has not selected content for a given tab.
 */
@Composable
fun EmptyDetailScreen() {
  Box(
    modifier = Modifier
      .background(color = MaterialTheme.colorScheme.surface)
      .fillMaxSize()
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_signal_logo_large),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
      modifier = Modifier
        .size(80.dp)
        .align(Alignment.Center)
    )
  }
}

/**
 * Emits [MainNavigationDetailLocation] whenever a change occurs, and persists the latest value.
 *
 * In order to ensure proper behaviour when moving from the inner to outer screen, and to ensure we don't accidentally end up
 * back on an unexpected Empty screen, we utilize a LaunchedEffect that subscribes to our detailLocation Flow instead of directly
 * utilizing collectAsStateWithLifecycle. Then the latest value is remembered as a saveable using the default [MainNavigationDetailLocation.Saver]
 */
@Composable
fun rememberMainNavigationDetailLocation(
  mainNavigationViewModel: MainNavigationViewModel,
  onWillFocusPrimary: suspend () -> Unit = {}
): State<MainNavigationDetailLocation> {
  val state = rememberSaveable(
    stateSaver = MainNavigationDetailLocation.Saver(
      mainNavigationViewModel.earlyNavigationDetailLocationRequested
    )
  ) {
    mutableStateOf(mainNavigationViewModel.earlyNavigationDetailLocationRequested ?: MainNavigationDetailLocation.Empty)
  }

  LaunchedEffect(Unit) {
    mainNavigationViewModel.detailLocation.collect {
      if (state.value == it) {
        mainNavigationViewModel.setFocusedPane(
          if (it == MainNavigationDetailLocation.Empty) {
            ThreePaneScaffoldRole.Secondary
          } else {
            onWillFocusPrimary()
            ThreePaneScaffoldRole.Primary
          }
        )
      }

      state.value = it
    }
  }

  return state
}

@Composable
fun rememberFocusRequester(
  mainNavigationViewModel: MainNavigationViewModel,
  currentListLocation: MainNavigationListLocation,
  isTargetListLocation: (MainNavigationListLocation) -> Boolean
): (ThreePaneScaffoldRole) -> Unit {
  return remember(currentListLocation, isTargetListLocation, mainNavigationViewModel) {
    if (isTargetListLocation(currentListLocation)) {
      {
        mainNavigationViewModel.setFocusedPane(it)
      }
    } else {
      {}
    }
  }
}

@Composable
fun rememberDetailNavHostController(
  onRequestFocus: (ThreePaneScaffoldRole) -> Unit,
  builder: NavGraphBuilder.(NavHostController) -> Unit
): NavHostController {
  val navHostController = rememberNavController()
  val viewModelStore = LocalViewModelStoreOwner.current!!.viewModelStore

  remember {
    val graph = navHostController.createGraph(
      startDestination = MainNavigationDetailLocation.Empty,
      builder = { builder(navHostController) }
    )

    navHostController.setViewModelStore(viewModelStore)
    navHostController.setGraph(graph, null)
    graph
  }

  val entry by navHostController.currentBackStackEntryAsState()
  LaunchedEffect(entry) {
    if (entry != null && entry?.destination?.route != MainNavigationDetailLocation.Empty::class.qualifiedName) {
      onRequestFocus(ThreePaneScaffoldRole.Primary)
    } else {
      onRequestFocus(ThreePaneScaffoldRole.Secondary)
    }
  }

  return navHostController
}

fun NavHostController.navigateToDetailLocation(location: MainNavigationDetailLocation) {
  navigate(location) {
    if (location.isContentRoot) {
      popUpTo(graph.id) { inclusive = true }
    }
  }
}

@Composable
fun DetailsScreenNavHost(navHostController: NavHostController, contentLayoutData: MainContentLayoutData) {
  NavHost(
    navController = navHostController,
    graph = navHostController.graph,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
    modifier = Modifier
      .padding(end = contentLayoutData.detailPaddingEnd)
      .clip(contentLayoutData.shape)
      .background(color = MaterialTheme.colorScheme.surface)
      .fillMaxSize()
  )
}
