/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.eventFlow
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.window.WindowSizeClass.Companion.getWindowSizeClass

/**
 * When the user searches for a conversation and then enters a message, we should clear
 * the search. This is driven by an event bus, which we want to subscribe to only when
 * the screen has been resumed.
 *
 * On COMPACT form factor specifically, we also need to wait until we are in the EMPTY
 * detail location, to avoid weird predictive back animation issues.
 *
 * On other screen types, since we are in a multi-pane mode, we can subscribe immediately.
 */
fun Fragment.listenToEventBusWhileResumed(
  detailLocation: Flow<MainNavigationDetailLocation>
) {
  lifecycleScope.launch {
    detailLocation
      .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
      .collectLatest {
        if (resources.getWindowSizeClass().isCompact()) {
          when (it) {
            is MainNavigationDetailLocation.Chats.Conversation -> unsubscribe()
            MainNavigationDetailLocation.Empty -> subscribe()
            else -> Unit
          }
        } else {
          subscribe()
        }
      }
  }

  lifecycleScope.launch {
    lifecycle.eventFlow.filter { it == Lifecycle.Event.ON_PAUSE }
      .collectLatest { unsubscribe() }
  }
}

private fun Fragment.subscribe() {
  if (!EventBus.getDefault().isRegistered(this)) {
    EventBus.getDefault().register(this)
  }
}

private fun Fragment.unsubscribe() {
  if (EventBus.getDefault().isRegistered(this)) {
    EventBus.getDefault().unregister(this)
  }
}
