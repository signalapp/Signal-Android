/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.chats

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.split.LocalDeferredEnter
import org.signal.core.ui.compose.split.deferredEnterMetadata
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsNavHostFragment
import org.thoughtcrime.securesms.compose.AndroidNavHostFragment
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedState
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment
import org.thoughtcrime.securesms.conversationlist.ConversationListArchiveFragment
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment
import org.thoughtcrime.securesms.main.MainDetailRoute
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment

/**
 * Registers the routes available in the chats tab of the main screen.
 */
fun EntryProviderScope<NavKey>.registerChatsTabDetailRoutes() {
  detailEntry<MainDetailRoute.Conversation>(
    metadata = deferredEnterMetadata()
  ) { route ->
    ConversationEntry(route)
  }

  detailEntry<MainDetailRoute.Chats.MessageDetails> { route ->
    MessageDetailsEntry(route)
  }

  detailEntry<MainDetailRoute.Chats.ConversationSettings>(
    metadata = TransitionSpecs.FadeScale.metadata
  ) { route ->
    ConversationSettingsEntry(route)
  }
}

/**
 * List pane content for the chats tab.
 */
@Composable
fun ChatsListPane(modifier: Modifier = Modifier) {
  AndroidFragment(
    clazz = ConversationListFragment::class.java,
    fragmentState = rememberFragmentState(),
    modifier = modifier
  )
}

/**
 * List pane content for the archive, which is displayed within the chats tab.
 */
@Composable
fun ArchiveListPane(modifier: Modifier = Modifier) {
  AndroidFragment(
    clazz = ConversationListArchiveFragment::class.java,
    fragmentState = rememberFragmentState(),
    modifier = modifier
  )
}

@Composable
private fun ConversationEntry(route: MainDetailRoute.Conversation) {
  val context = LocalContext.current
  val navigatorProvider = context as? MainNavigator.NavigatorProvider
  val deferredEnter = LocalDeferredEnter.current
  val fragmentState = key(route) { rememberFragmentState() }
  val arguments = requireNotNull(ConversationIntents.createBuilderSync(context, route.conversationArgs).build().extras) {
    "Handed null Conversation intent arguments."
  }

  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)

  LaunchedEffect(Unit) {
    navigatorProvider?.onFirstRender()
  }

  AndroidFragment(
    clazz = ConversationFragment::class.java,
    fragmentState = fragmentState,
    arguments = arguments,
    modifier = Modifier
      .background(MaterialTheme.colorScheme.background)
      .fillMaxSize()
  ) { fragment ->
    backPressedState.attach(fragment)

    fragment.viewLifecycleOwner.lifecycleScope.launch {
      fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
        fragment.didFirstFrameRender.first { it }
        deferredEnter.start()
      }
    }
  }
}

@Composable
private fun MessageDetailsEntry(route: MainDetailRoute.Chats.MessageDetails) {
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
  )
}

@Composable
private fun ConversationSettingsEntry(route: MainDetailRoute.Chats.ConversationSettings) {
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

    AndroidNavHostFragment(
      clazz = ConversationSettingsNavHostFragment::class.java,
      fragmentState = fragmentState,
      arguments = args,
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) { fragment ->
      backPressedState.attach(fragment)
    }
  }
}
