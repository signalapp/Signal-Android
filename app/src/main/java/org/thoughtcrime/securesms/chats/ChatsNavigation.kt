/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.chats

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsNavHostFragment
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedState
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment
import org.thoughtcrime.securesms.main.EmptyDetailScreen
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment

fun EntryProviderScope<NavKey>.chatsNavEntries(
  transitionState: ConversationTransitionState
) {
  entry<MainNavigationDetailLocation.Empty> {
    NoConvoSelectedEntry()
  }

  entry<MainNavigationDetailLocation.Conversation>(
    // disable slide animation - it's unnecessary in split pane mode and is handled by ConversationLoadingMask for single pane mode.
    metadata = TransitionSpecs.None.metadata
  ) { route ->
    ConversationEntry(route, transitionState)
  }

  entry<MainNavigationDetailLocation.Chats.MessageDetails> { route ->
    MessageDetailsEntry(route)
  }

  entry<MainNavigationDetailLocation.Chats.ConversationSettings> { route ->
    ConversationSettingsEntry(route)
  }
}

@Composable
private fun NoConvoSelectedEntry() {
  EmptyDetailScreen()
}

@Composable
private fun ConversationEntry(
  route: MainNavigationDetailLocation.Conversation,
  transitionState: ConversationTransitionState
) {
  val context = LocalContext.current
  val navigatorProvider = context as? MainNavigator.NavigatorProvider
  val fragmentState = key(route) { rememberFragmentState() }
  val arguments = requireNotNull(ConversationIntents.createBuilderSync(context, route.conversationArgs).build().extras) {
    "Handed null Conversation intent arguments."
  }

  val fragmentContentReady = remember { MutableStateFlow(false) }
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)

  ConversationLoadingMask(
    transitionState = transitionState,
    contentReady = fragmentContentReady,
    onFirstRender = { navigatorProvider?.onFirstRender() }
  ) { modifier ->
    AndroidFragment(
      clazz = ConversationFragment::class.java,
      fragmentState = fragmentState,
      arguments = arguments,
      modifier = modifier
        .background(MaterialTheme.colorScheme.background)
        .fillMaxSize()
    ) { fragment ->
      backPressedState.attach(fragment)

      fragment.viewLifecycleOwner.lifecycleScope.launch {
        fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
          fragment.didFirstFrameRender.collectLatest { fragmentContentReady.value = it }
        }
      }
    }
  }
}

@Composable
private fun MessageDetailsEntry(route: MainNavigationDetailLocation.Chats.MessageDetails) {
  val navigatorProvider = LocalContext.current as? MainNavigator.NavigatorProvider
  val fragmentState = key(route) { rememberFragmentState() }

  LaunchedEffect(Unit) {
    navigatorProvider?.onFirstRender()
  }

  AndroidFragment(
    clazz = MessageDetailsFragment::class.java,
    fragmentState = fragmentState,
    arguments = MessageDetailsFragment.args(route.recipientId, route.messageId),
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding()
  )
}

@Composable
private fun ConversationSettingsEntry(route: MainNavigationDetailLocation.Chats.ConversationSettings) {
  val navigatorProvider = LocalContext.current as? MainNavigator.NavigatorProvider
  val fragmentState = key(route) { rememberFragmentState() }
  val arguments: Bundle? by produceState(null, route.recipientId) {
    value = ConversationSettingsNavHostFragment.createArgs(route.recipientId)
  }

  LaunchedEffect(Unit) {
    navigatorProvider?.onFirstRender()
  }

  arguments?.let { args ->
    val backPressedState = remember { FragmentBackPressedState() }
    FragmentBackHandler(backPressedState)

    AndroidFragment(
      clazz = ConversationSettingsNavHostFragment::class.java,
      fragmentState = fragmentState,
      arguments = args,
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .navigationBarsPadding()
    ) { fragment ->
      backPressedState.attach(fragment)
    }
  }
}
