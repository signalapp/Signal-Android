/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.registration.util.DebugLoggable

/**
 * Base view model that helps one implement an Elm-like architecture, where events are processed and
 * new models are emitted. In particular, this base class exists to setup the core event channel
 * to avoid gotcha's around threading and race conditions.
 */
abstract class EventDrivenViewModel<E : DebugLoggable>(
  private val tag: String
) : ViewModel() {

  private val eventChannel = Channel<E>(Channel.UNLIMITED)

  init {
    viewModelScope.launch {
      for (event in eventChannel) {
        Log.d(tag, "[Event] $event")
        processEvent(event)
      }
    }
  }

  fun onEvent(event: E) {
    // Unlimited buffer means this will always succeed
    eventChannel.trySend(event)
  }

  /**
   * Handle the event how you wish. It's recommended that you use the event to emit a new state model
   * to be observed by the view.
   */
  protected abstract suspend fun processEvent(event: E)
}
