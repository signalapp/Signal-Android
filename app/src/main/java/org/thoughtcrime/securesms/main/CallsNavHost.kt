/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameScreen
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsScreen
import org.thoughtcrime.securesms.serialization.JsonSerializableNavType
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import kotlin.reflect.typeOf

fun NavGraphBuilder.callNavGraphBuilder(navHostController: NavHostController) {
  composable<MainNavigationDetailLocation.Empty> {
    EmptyDetailScreen()
  }

  composable<MainNavigationDetailLocation.Calls.CallLinks.CallLinkDetails>(
    typeMap = mapOf(
      typeOf<CallLinkRoomId>() to JsonSerializableNavType(CallLinkRoomId.serializer())
    )
  ) {
    informNavigatorWeAreReady()

    val route = it.toRoute<MainNavigationDetailLocation.Calls.CallLinks.CallLinkDetails>()

    CallLinkDetailsScreen(roomId = route.callLinkRoomId)
  }

  composable<MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName>(
    typeMap = mapOf(
      typeOf<CallLinkRoomId>() to JsonSerializableNavType(CallLinkRoomId.serializer())
    )
  ) {
    informNavigatorWeAreReady()

    val route = it.toRoute<MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName>()
    val parent = navHostController.previousBackStackEntry ?: return@composable

    CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
      EditCallLinkNameScreen(roomId = route.callLinkRoomId)
    }
  }
}

@Composable
private fun informNavigatorWeAreReady() {
  val navigator = LocalContext.current as? MainNavigator.NavigatorProvider
  LaunchedEffect(navigator) {
    navigator?.onFirstRender()
  }
}
