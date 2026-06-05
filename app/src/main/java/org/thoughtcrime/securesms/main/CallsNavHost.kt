/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameScreen
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsScreen
import org.thoughtcrime.securesms.serialization.JsonSerializableNavType
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import kotlin.reflect.typeOf

private val callLinkRoomIdType = typeOf<CallLinkRoomId>()

fun NavGraphBuilder.callNavGraphBuilder(navHostController: NavHostController) {
  composable<MainNavigationDetailLocation.Empty> {
    if (LocalResources.current.rememberIsSplitPane()) {
      EmptyDetailScreen()
    }
  }

  composable<MainNavigationDetailLocation.CallLinkDetails>(
    typeMap = mapOf(
      callLinkRoomIdType to JsonSerializableNavType(CallLinkRoomId.serializer())
    )
  ) {
    informNavigatorWeAreReady()

    val route = it.toRoute<MainNavigationDetailLocation.CallLinkDetails>()

    CallLinkDetailsScreen(roomId = route.callLinkRoomId)
  }

  composable<MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName>(
    typeMap = mapOf(
      callLinkRoomIdType to JsonSerializableNavType(CallLinkRoomId.serializer())
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
  val navigator = LocalActivity.current as? MainNavigator.NavigatorProvider
  LaunchedEffect(navigator) {
    navigator?.onFirstRender()
  }
}
