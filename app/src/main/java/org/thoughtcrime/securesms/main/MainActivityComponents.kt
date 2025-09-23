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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import org.thoughtcrime.securesms.R

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
      tint = Color(0x58607152),
      modifier = Modifier.align(Alignment.Center)
    )
  }
}

@Composable
fun rememberDetailNavHostController(builder: NavGraphBuilder.(NavHostController) -> Unit): NavHostController {
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
