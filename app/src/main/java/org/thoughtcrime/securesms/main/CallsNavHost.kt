/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.signal.core.ui.compose.Animations.navHostSlideInTransition
import org.signal.core.ui.compose.Animations.navHostSlideOutTransition
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameScreen
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsScreen
import org.thoughtcrime.securesms.serialization.JsonSerializableNavType
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import kotlin.reflect.typeOf

/**
 * A navigation host for the calls detail pane of [org.thoughtcrime.securesms.MainActivity].
 *
 * @param currentDestination The current calls destination to navigate to, containing routing information
 * @param contentLayoutData Layout configuration data for responsive UI rendering
 */
@Composable
fun CallsNavHost(
  currentDestination: MainNavigationDetailLocation.Calls,
  contentLayoutData: MainContentLayoutData
) {
  val navHostController = key(currentDestination.controllerKey) {
    rememberNavController()
  }

  val startDestination = remember(currentDestination.controllerKey) {
    MainNavigationDetailLocation.Calls.CallLinkDetails(currentDestination.controllerKey)
  }

  LaunchedEffect(navHostController) {
    navHostController.enableOnBackPressed(true)
  }

  LaunchedEffect(currentDestination) {
    if (currentDestination != startDestination) {
      navHostController.navigate(currentDestination)
    }
  }

  val mainNavigationViewModel = viewModel<MainNavigationViewModel>(viewModelStoreOwner = LocalContext.current as ComponentActivity) {
    error("Should already exist.")
  }

  NavHost(
    navController = navHostController,
    startDestination = startDestination,
    enterTransition = { navHostSlideInTransition { it } },
    exitTransition = { navHostSlideOutTransition { -it } },
    popEnterTransition = { navHostSlideInTransition { -it } },
    popExitTransition = { navHostSlideOutTransition { it } },
    modifier = Modifier.fillMaxSize()
  ) {
    composable<MainNavigationDetailLocation.Calls.CallLinkDetails>(
      typeMap = mapOf(
        typeOf<CallLinkRoomId>() to JsonSerializableNavType(CallLinkRoomId.serializer())
      )
    ) {
      val route = it.toRoute<MainNavigationDetailLocation.Calls.CallLinkDetails>()

      LaunchedEffect(route) {
        mainNavigationViewModel.goTo(route)
      }

      MainActivityDetailContainer(contentLayoutData) {
        CallLinkDetailsScreen(roomId = route.callLinkRoomId)
      }
    }

    composable<MainNavigationDetailLocation.Calls.EditCallLinkName>(
      typeMap = mapOf(
        typeOf<CallLinkRoomId>() to JsonSerializableNavType(CallLinkRoomId.serializer())
      )
    ) {
      val parent = navHostController.getBackStackEntry(startDestination)
      val route = it.toRoute<MainNavigationDetailLocation.Calls.EditCallLinkName>()

      LaunchedEffect(route) {
        mainNavigationViewModel.goTo(route)
      }

      MainActivityDetailContainer(contentLayoutData) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
          EditCallLinkNameScreen(roomId = route.callLinkRoomId)
        }
      }
    }
  }
}
