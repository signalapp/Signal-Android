/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.signal.core.ui.compose.Animations.navHostSlideInTransition
import org.signal.core.ui.compose.Animations.navHostSlideOutTransition
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment
import org.thoughtcrime.securesms.serialization.JsonSerializableNavType
import kotlin.reflect.typeOf

/**
 * A navigation host for the chats detail pane of [org.thoughtcrime.securesms.MainActivity].
 *
 * @param currentDestination The current calls destination to navigate to, containing routing information
 * @param contentLayoutData Layout configuration data for responsive UI rendering
 */
@Composable
fun ChatsNavHost(
  currentDestination: MainNavigationDetailLocation.Chats,
  contentLayoutData: MainContentLayoutData
) {
  val navHostController: NavHostController = key(currentDestination.controllerKey) {
    rememberNavController()
  }

  val startDestination = remember(currentDestination.controllerKey) {
    currentDestination as? MainNavigationDetailLocation.Chats.Conversation ?: error("Unsupported start destination.")
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
    composable<MainNavigationDetailLocation.Chats.Conversation>(
      typeMap = mapOf(
        typeOf<ConversationArgs>() to JsonSerializableNavType(ConversationArgs.serializer())
      )
    ) {
      val route = it.toRoute<MainNavigationDetailLocation.Chats.Conversation>()
      val fragmentState = key(route) { rememberFragmentState() }
      val context = LocalContext.current

      LaunchedEffect(route) {
        mainNavigationViewModel.goTo(route)
      }

      AndroidFragment(
        clazz = ConversationFragment::class.java,
        fragmentState = fragmentState,
        arguments = requireNotNull(ConversationIntents.createBuilderSync(context, route.conversationArgs).build().extras) { "Handed null Conversation intent arguments." },
        modifier = Modifier
          .padding(end = contentLayoutData.detailPaddingEnd)
          .clip(contentLayoutData.shape)
          .background(color = MaterialTheme.colorScheme.surface)
          .fillMaxSize()
      )
    }
  }
}
