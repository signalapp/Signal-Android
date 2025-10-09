/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment
import org.thoughtcrime.securesms.serialization.JsonSerializableNavType
import kotlin.reflect.typeOf

fun NavGraphBuilder.chatNavGraphBuilder() {
  composable<MainNavigationDetailLocation.Empty> {
    EmptyDetailScreen()
  }

  composable<MainNavigationDetailLocation.Chats.Conversation>(
    typeMap = mapOf(
      typeOf<ConversationArgs>() to JsonSerializableNavType(ConversationArgs.serializer())
    )
  ) { navBackStackEntry ->
    val route = navBackStackEntry.toRoute<MainNavigationDetailLocation.Chats.Conversation>()
    val fragmentState = key(route) { rememberFragmentState() }
    val context = LocalContext.current
    val insets by rememberVerticalInsets()
    val insetFlow = remember { snapshotFlow { insets } }

    AndroidFragment(
      clazz = ConversationFragment::class.java,
      fragmentState = fragmentState,
      arguments = requireNotNull(ConversationIntents.createBuilderSync(context, route.conversationArgs).build().extras) { "Handed null Conversation intent arguments." },
      modifier = Modifier
        .fillMaxSize()
    ) { fragment ->
      fragment.viewLifecycleOwner.lifecycleScope.launch {
        fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
          insetFlow.collect {
            fragment.applyRootInsets(insets)
          }
        }
      }
    }
  }
}
