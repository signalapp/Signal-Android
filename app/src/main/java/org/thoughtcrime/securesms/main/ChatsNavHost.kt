/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
  ) {
    val route = it.toRoute<MainNavigationDetailLocation.Chats.Conversation>()
    val fragmentState = key(route) { rememberFragmentState() }
    val context = LocalContext.current

    AndroidFragment(
      clazz = ConversationFragment::class.java,
      fragmentState = fragmentState,
      arguments = requireNotNull(ConversationIntents.createBuilderSync(context, route.conversationArgs).build().extras) { "Handed null Conversation intent arguments." },
      modifier = Modifier
        .fillMaxSize()
    )
  }
}
